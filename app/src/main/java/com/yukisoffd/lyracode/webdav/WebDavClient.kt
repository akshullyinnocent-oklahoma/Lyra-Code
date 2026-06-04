package com.yukisoffd.lyracode.webdav

import android.util.Base64
import com.yukisoffd.lyracode.data.WebDavServerConfig
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

data class WebDavFile(
    val path: String,
    val directory: Boolean,
    val size: Long,
    val modified: String,
)

data class TransferProgress(
    val title: String,
    val doneBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
)

class WebDavClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun test(server: WebDavServerConfig): Result<List<WebDavFile>> = runCatching {
        list(server, server.initialPath.ifBlank { "/" }, depth = 0)
    }

    fun list(server: WebDavServerConfig, path: String, depth: Int = 1): List<WebDavFile> {
        val request = Request.Builder()
            .url(resolveUrl(server, path))
            .headers(headers(server).newBuilder().add("Depth", depth.toString()).build())
            .method(
                "PROPFIND",
                """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
                    .toRequestBody("application/xml".toMediaType()),
            )
            .build()
        clientFor(server).newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("WebDAV 列表失败 HTTP ${response.code}: ${body.take(300)}")
            return parsePropfind(body, server)
        }
    }

    fun search(server: WebDavServerConfig, query: String, basePath: String = server.initialPath, limit: Int = 80): List<WebDavFile> {
        val cleanQuery = query.trim().lowercase()
        require(cleanQuery.isNotBlank()) { "搜索关键词不能为空" }
        val result = ArrayList<WebDavFile>()
        val queue = ArrayDeque<String>()
        queue.add(basePath.ifBlank { "/" })
        var visited = 0
        while (queue.isNotEmpty() && result.size < limit && visited < 500) {
            val path = queue.removeFirst()
            visited++
            runCatching { list(server, path, depth = 1) }.getOrDefault(emptyList()).forEach { file ->
                if (file.path != path && file.path.lowercase().contains(cleanQuery)) result += file
                if (file.directory && file.path != path && queue.size < 200) queue.add(file.path)
            }
        }
        return result.take(limit)
    }

    fun download(server: WebDavServerConfig, remotePath: String, onProgress: (TransferProgress) -> Unit = {}): ByteArray {
        val request = Request.Builder().url(resolveUrl(server, remotePath)).headers(headers(server)).get().build()
        clientFor(server).newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("WebDAV 下载失败 HTTP ${response.code}: ${response.body?.string().orEmpty().take(300)}")
            val body = response.body ?: error("WebDAV 响应为空")
            return body.readWithProgress("下载 ${remotePath.substringAfterLast('/')}", onProgress)
        }
    }

    fun upload(server: WebDavServerConfig, remotePath: String, bytes: ByteArray, onProgress: (TransferProgress) -> Unit = {}) {
        ensureParentDirectories(server, remotePath)
        val request = Request.Builder()
            .url(resolveUrl(server, remotePath))
            .headers(headers(server))
            .put(ProgressRequestBody(bytes, "application/octet-stream".toMediaType(), "上传 ${remotePath.substringAfterLast('/')}", onProgress))
            .build()
        clientFor(server).newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("WebDAV 上传失败 HTTP ${response.code}: ${body.take(300)}")
        }
    }

    fun serversJson(servers: List<WebDavServerConfig>): String {
        return JSONObject()
            .put("schema", "lyra_webdav_servers_v1")
            .put("servers", JSONArray().also { array ->
                servers.forEach {
                    array.put(
                        JSONObject()
                            .put("id", it.id)
                            .put("name", it.name)
                            .put("url", if (it.hideAddressInDrawer) "(hidden)" else it.url)
                            .put("initial_path", it.initialPath)
                            .put("enabled", it.enabled)
                            .put("http_insecure", it.url.startsWith("http://", ignoreCase = true)),
                    )
                }
            })
            .toString()
    }

    private fun ensureParentDirectories(server: WebDavServerConfig, remotePath: String) {
        val clean = normalizePath(remotePath)
        val segments = clean.trim('/').split('/').dropLast(1).filter { it.isNotBlank() }
        var current = ""
        segments.forEach { segment ->
            current += "/$segment"
            val request = Request.Builder().url(resolveUrl(server, current)).headers(headers(server)).method("MKCOL", null).build()
            clientFor(server).newCall(request).execute().close()
        }
    }

    private fun clientFor(server: WebDavServerConfig): OkHttpClient {
        return if (server.trustAllCertificates) unsafeClient else client
    }

    private fun ResponseBody.readWithProgress(title: String, onProgress: (TransferProgress) -> Unit): ByteArray {
        val total = contentLength().coerceAtLeast(0L)
        val input = byteStream()
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        val started = System.currentTimeMillis()
        var done = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            done += read
            val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1)
            onProgress(TransferProgress(title, done, total, done * 1000 / elapsed))
        }
        return output.toByteArray()
    }

    private fun parsePropfind(xml: String, server: WebDavServerConfig): List<WebDavFile> {
        val basePath = URI(server.url).path.trimEnd('/')
        val responseRegex = Regex("""(?is)<(?:\w+:)?response\b.*?</(?:\w+:)?response>""")
        return responseRegex.findAll(xml).mapNotNull { match ->
            val block = match.value
            val href = Regex("""(?is)<(?:\w+:)?href>(.*?)</(?:\w+:)?href>""").find(block)?.groupValues?.get(1).orEmpty()
            if (href.isBlank()) return@mapNotNull null
            val decoded = URLDecoder.decode(href, "UTF-8")
            val path = decoded.removePrefix(basePath).ifBlank { "/" }
            WebDavFile(
                path = normalizePath(path),
                directory = block.contains("<d:collection", ignoreCase = true) || block.contains(":collection", ignoreCase = true),
                size = Regex("""(?is)<(?:\w+:)?getcontentlength>(\d+)</(?:\w+:)?getcontentlength>""").find(block)?.groupValues?.get(1)?.toLongOrNull() ?: 0L,
                modified = Regex("""(?is)<(?:\w+:)?getlastmodified>(.*?)</(?:\w+:)?getlastmodified>""").find(block)?.groupValues?.get(1).orEmpty(),
            )
        }.toList()
    }

    private fun headers(server: WebDavServerConfig): Headers {
        val builder = Headers.Builder()
            .add("User-Agent", server.userAgent.ifBlank { "LyraCode/1.0" })
        if (server.username.isNotBlank() || server.password.isNotBlank()) {
            val token = Base64.encodeToString("${server.username}:${server.password}".toByteArray(), Base64.NO_WRAP)
            builder.add("Authorization", "Basic $token")
        }
        return builder.build()
    }

    private fun resolveUrl(server: WebDavServerConfig, path: String): String {
        val base = server.url.trimEnd('/')
        val clean = normalizePath(path).trim('/')
        if (clean.isBlank()) return base
        return base + "/" + clean.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    }

    private fun normalizePath(path: String): String {
        val clean = path.trim().replace('\\', '/').replace(Regex("/+"), "/")
        return if (clean.startsWith("/")) clean else "/$clean"
    }

    companion object {
        private val unsafeClient: OkHttpClient by lazy {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustAll), SecureRandom())
            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAll)
                .hostnameVerifier(HostnameVerifier { _, _ -> true })
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
        }
    }
}

private class ProgressRequestBody(
    private val bytes: ByteArray,
    private val contentType: okhttp3.MediaType,
    private val title: String,
    private val onProgress: (TransferProgress) -> Unit,
) : RequestBody() {
    override fun contentType(): okhttp3.MediaType = contentType
    override fun contentLength(): Long = bytes.size.toLong()
    override fun writeTo(sink: BufferedSink) {
        val started = System.currentTimeMillis()
        var done = 0L
        bytes.inputStream().source().use { source ->
            while (true) {
                val read = source.read(sink.buffer, 32 * 1024)
                if (read < 0) break
                sink.flush()
                done += read
                val elapsed = (System.currentTimeMillis() - started).coerceAtLeast(1)
                onProgress(TransferProgress(title, done, bytes.size.toLong(), done * 1000 / elapsed))
            }
        }
    }
}
