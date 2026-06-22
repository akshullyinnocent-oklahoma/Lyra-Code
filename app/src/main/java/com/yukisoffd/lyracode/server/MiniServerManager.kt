package com.yukisoffd.lyracode.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.yukisoffd.lyracode.R
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.MiniServerConfig
import com.yukisoffd.lyracode.workspace.WorkspaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.ArrayDeque
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

data class MiniServerStatus(
    val running: Boolean,
    val protocol: String,
    val host: String,
    val port: Int,
    val url: String,
    val startedAt: Long,
    val message: String,
)

data class MiniServerLogEntry(
    val timestamp: Long,
    val level: String,
    val method: String,
    val path: String,
    val status: Int,
    val remoteAddress: String,
    val durationMs: Long,
    val message: String,
)

private data class ServeResult(
    val status: Int,
    val message: String,
)

class MiniServerManager(
    context: Context,
    private val settings: AppSettings,
    private val workspaceManager: WorkspaceManager,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var currentConfig: MiniServerConfig = settings.miniServerConfig()

    @Volatile
    private var startedAt: Long = 0L

    @Volatile
    private var lastMessage: String = "未启动"

    private var nsdManager: NsdManager? = null
    private var nsdRegistration: NsdManager.RegistrationListener? = null
    private val logs = ArrayDeque<MiniServerLogEntry>()

    fun status(): MiniServerStatus {
        val config = currentConfig
        return MiniServerStatus(
            running = running.get(),
            protocol = config.protocol,
            host = config.host,
            port = config.port,
            url = serviceUrl(config),
            startedAt = startedAt,
            message = lastMessage,
        )
    }

    fun statusJson(): JSONObject {
        val status = status()
        return JSONObject()
            .put("running", status.running)
            .put("protocol", status.protocol)
            .put("host", status.host)
            .put("port", status.port)
            .put("url", status.url)
            .put("startedAt", status.startedAt)
            .put("message", status.message)
            .put("workspace", workspaceManager.displayName())
            .put("lanUrls", JSONArray(lanUrls(status.protocol, status.port)))
            .put("customUrls", JSONArray(customUrls(currentConfig)))
    }

    @Synchronized
    fun logsJson(limit: Int = DEFAULT_LOG_LIMIT, minimumLevel: String = ""): JSONObject {
        val minPriority = logPriority(minimumLevel)
        val entries = logs.asSequence()
            .filter { logPriority(it.level) >= minPriority }
            .toList()
            .takeLast(limit.coerceIn(1, MAX_LOG_ENTRIES))
        return JSONObject()
            .put("schema", "lyra_mini_server_logs_v1")
            .put("running", running.get())
            .put("workspace", workspaceManager.displayName())
            .put("count", entries.size)
            .put(
                "logs",
                JSONArray().also { array ->
                    entries.forEach { entry ->
                        array.put(
                            JSONObject()
                                .put("timestamp", entry.timestamp)
                                .put("level", entry.level)
                                .put("method", entry.method)
                                .put("path", entry.path)
                                .put("status", entry.status)
                                .put("remoteAddress", entry.remoteAddress)
                                .put("durationMs", entry.durationMs)
                                .put("message", entry.message),
                        )
                    }
                },
            )
    }

    @Synchronized
    fun clearLogs() {
        logs.clear()
        addLog("info", "SYSTEM", "-", 0, "-", 0L, "微型服务器日志已清空")
    }

    @Synchronized
    fun start(config: MiniServerConfig = settings.miniServerConfig()): MiniServerStatus {
        require(workspaceManager.root() != null) { "请先选择工作目录，微型服务器会以工作目录作为站点根目录。" }
        if (running.get()) stop()

        val socket = createServerSocket(config.protocol)
        socket.bind(InetSocketAddress(config.host.ifBlank { AppSettings.DEFAULT_MINI_SERVER_HOST }, config.port))
        serverSocket = socket
        currentConfig = config.copy(enabled = true)
        settings.saveMiniServerConfig(currentConfig)
        startedAt = System.currentTimeMillis()
        running.set(true)
        lastMessage = "正在服务 ${workspaceManager.displayName()}"
        addLog("info", "SYSTEM", serviceUrl(currentConfig), 0, "-", 0L, "微型服务器已启动，工作区：${workspaceManager.displayName()}")
        registerMdns(currentConfig)

        scope.launch {
            while (running.get()) {
                val client = try {
                    socket.accept()
                } catch (error: Throwable) {
                    if (running.get()) {
                        lastMessage = "监听失败：${error.message}"
                        addLog("error", "SYSTEM", "-", 0, "-", 0L, lastMessage)
                    }
                    break
                }
                launch { handleClient(client, currentConfig) }
            }
        }
        return status()
    }

    private fun createServerSocket(protocol: String): ServerSocket {
        if (protocol != AppSettings.MINI_SERVER_PROTOCOL_HTTPS) {
            return ServerSocket().apply { reuseAddress = true }
        }
        val (keyStore, password) = loadCustomKeyStore(currentConfig) ?: loadPemKeyStore(currentConfig) ?: loadBundledKeyStore()
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, password)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagerFactory.keyManagers, null, null)
        return sslContext.serverSocketFactory.createServerSocket().apply { reuseAddress = true }
    }

    private fun loadCustomKeyStore(config: MiniServerConfig): Pair<KeyStore, CharArray>? {
        if (config.tlsKeyStoreBase64.isBlank()) return null
        val bytes = runCatching { Base64.decode(config.tlsKeyStoreBase64, Base64.DEFAULT) }.getOrNull() ?: return null
        val candidatePasswords = listOf(config.tlsKeyStorePassword, "").distinct().map { it.toCharArray() }
        return listOf("PKCS12", "JKS").firstNotNullOfOrNull { type ->
            candidatePasswords.firstNotNullOfOrNull { password ->
                runCatching {
                    KeyStore.getInstance(type).apply {
                        ByteArrayInputStream(bytes).use { load(it, password) }
                    } to password
                }.getOrNull()
            }
        }
    }

    private fun loadPemKeyStore(config: MiniServerConfig): Pair<KeyStore, CharArray>? {
        if (config.tlsCertificateChain.isBlank() || config.tlsPrivateKey.isBlank()) return null
        val certificates = runCatching {
            val factory = CertificateFactory.getInstance("X.509")
            ByteArrayInputStream(config.tlsCertificateChain.toByteArray(StandardCharsets.UTF_8)).use { input ->
                factory.generateCertificates(input).toTypedArray()
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val privateKey = parsePrivateKey(config.tlsPrivateKey) ?: return null
        val password = config.tlsKeyStorePassword.ifBlank { MINI_SERVER_CERT_PASSWORD }.toCharArray()
        return KeyStore.getInstance("PKCS12").apply {
            load(null, password)
            setKeyEntry(MINI_SERVER_CERT_ALIAS, privateKey, password, certificates)
        } to password
    }

    private fun loadBundledKeyStore(): Pair<KeyStore, CharArray> {
        val password = MINI_SERVER_CERT_PASSWORD.toCharArray()
        return KeyStore.getInstance("PKCS12").apply {
            appContext.resources.openRawResource(R.raw.lyra_mini_server).use { input ->
                load(input, password)
            }
        } to password
    }

    private fun parsePrivateKey(pem: String): PrivateKey? {
        val base64 = pem.lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
            .trim()
        val keyBytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull() ?: return null
        val spec = PKCS8EncodedKeySpec(keyBytes)
        return listOf("RSA", "EC", "DSA").firstNotNullOfOrNull { algorithm ->
            runCatching { java.security.KeyFactory.getInstance(algorithm).generatePrivate(spec) }.getOrNull()
        }
    }

    @Synchronized
    fun stop(): MiniServerStatus {
        running.set(false)
        unregisterMdns()
        runCatching { serverSocket?.close() }
        serverSocket = null
        startedAt = 0L
        lastMessage = "已停止"
        currentConfig = currentConfig.copy(enabled = false)
        settings.saveMiniServerConfig(currentConfig)
        addLog("info", "SYSTEM", "-", 0, "-", 0L, "微型服务器已停止")
        return status()
    }

    @Synchronized
    fun restart(config: MiniServerConfig = settings.miniServerConfig()): MiniServerStatus {
        if (running.get()) stop()
        return start(config)
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private fun handleClient(socket: Socket, config: MiniServerConfig) {
        val started = System.currentTimeMillis()
        socket.use { client ->
            val remoteAddress = client.inetAddress?.hostAddress.orEmpty().ifBlank { "-" }
            var method = "-"
            var rawUri = "-"
            fun logRequest(level: String, status: Int, message: String) {
                addLog(level, method, rawUri, status, remoteAddress, System.currentTimeMillis() - started, message)
            }
            client.soTimeout = 15_000
            try {
                val input = BufferedInputStream(client.getInputStream())
                val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
                val requestLine = reader.readLine().orEmpty()
                if (requestLine.isBlank()) return
                val parts = requestLine.split(" ")
                method = parts.getOrNull(0).orEmpty().uppercase(Locale.US)
                rawUri = parts.getOrNull(1).orEmpty()
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) break
                    val index = line.indexOf(':')
                    if (index > 0) headers[line.substring(0, index).trim().lowercase(Locale.US)] = line.substring(index + 1).trim()
                }
                val output = client.getOutputStream()
                if (method == "POST" && rawUri.substringBefore('?') == CLIENT_LOG_ENDPOINT) {
                    val body = readRequestBody(reader, headers["content-length"]?.toIntOrNull() ?: 0)
                    addClientLog(remoteAddress, body)
                    sendBytes(output, 204, "No Content", "text/plain; charset=utf-8", ByteArray(0), extraHeaders = corsHeaders())
                    logRequest("info", 204, "客户端日志已接收")
                    return
                }
                if (method == "OPTIONS") {
                    sendBytes(output, 204, "No Content", "text/plain; charset=utf-8", ByteArray(0), extraHeaders = corsHeaders())
                    logRequest("info", 204, "OPTIONS")
                    return
                }
                if (method != "GET" && method != "HEAD") {
                    sendText(output, 405, "Method Not Allowed", "只支持 GET / HEAD。")
                    logRequest("warn", 405, "不支持的请求方法")
                    return
                }
                if (config.forceHttps && config.protocol == AppSettings.MINI_SERVER_PROTOCOL_HTTP) {
                    sendRedirect(output, httpsLocation(rawUri, headers["host"], config))
                    logRequest("info", 308, "已重定向到 HTTPS")
                    return
                }
                if (!authorized(config, headers["authorization"])) {
                    sendText(
                        output,
                        401,
                        "Unauthorized",
                        "需要密码认证。",
                        extraHeaders = mapOf("WWW-Authenticate" to "Basic realm=\"Lyra Code\""),
                    )
                    logRequest("warn", 401, "认证失败或未提供密码")
                    return
                }
                val relativePath = decodePath(rawUri.substringBefore('?'))
                if (relativePath == null) {
                    sendText(output, 400, "Bad Request", "路径非法。")
                    logRequest("warn", 400, "路径非法")
                    return
                }
                val result = servePath(output, method == "HEAD", relativePath, config)
                logRequest(if (result.status >= 400) "warn" else "info", result.status, result.message)
            } catch (error: Throwable) {
                runCatching { sendText(client.getOutputStream(), 500, "Internal Server Error", "服务器内部错误：${error.message}") }
                logRequest("error", 500, "请求处理失败：${error.message}")
            }
        }
    }

    @Synchronized
    private fun addLog(
        level: String,
        method: String,
        path: String,
        status: Int,
        remoteAddress: String,
        durationMs: Long,
        message: String,
    ) {
        while (logs.size >= MAX_LOG_ENTRIES) {
            logs.removeFirst()
        }
        logs.addLast(
            MiniServerLogEntry(
                timestamp = System.currentTimeMillis(),
                level = level.lowercase(Locale.US).ifBlank { "info" },
                method = method.take(24),
                path = path.take(500),
                status = status,
                remoteAddress = remoteAddress.take(120),
                durationMs = durationMs,
                message = message.take(2_000),
            ),
        )
    }

    private fun logPriority(level: String): Int = when (level.lowercase(Locale.US)) {
        "debug" -> 0
        "info" -> 1
        "warn" -> 2
        "error" -> 3
        else -> 0
    }

    private fun readRequestBody(reader: BufferedReader, contentLength: Int): String {
        val safeLength = contentLength.coerceIn(0, MAX_CLIENT_LOG_BODY_BYTES)
        if (safeLength == 0) return ""
        val buffer = CharArray(safeLength)
        var offset = 0
        while (offset < safeLength) {
            val read = reader.read(buffer, offset, safeLength - offset)
            if (read <= 0) break
            offset += read
        }
        return String(buffer, 0, offset)
    }

    private fun addClientLog(remoteAddress: String, body: String) {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val level = json?.optString("level").orEmpty().lowercase(Locale.US).takeIf { it in CLIENT_LOG_LEVELS } ?: "error"
        val message = json?.optString("message").orEmpty().ifBlank { body }
        val source = json?.optString("source").orEmpty()
        val line = json?.optString("line").orEmpty()
        val detail = buildString {
            append("客户端：")
            append(message.ifBlank { "未知错误" })
            if (source.isNotBlank()) append(" @ ").append(source)
            if (line.isNotBlank()) append(":").append(line)
        }
        addLog(
            level = level,
            method = "CLIENT",
            path = json?.optString("path").orEmpty().ifBlank { "-" },
            status = 0,
            remoteAddress = remoteAddress,
            durationMs = 0L,
            message = detail,
        )
    }

    private fun servePath(output: OutputStream, headOnly: Boolean, relativePath: String, config: MiniServerConfig): ServeResult {
        val root = workspaceManager.root()
        if (root == null) {
            sendText(output, 503, "Service Unavailable", "未选择工作目录。")
            return ServeResult(503, "未选择工作目录")
        }
        var file = resolve(root, relativePath)
        if (file?.isDirectory == true) {
            val index = file.findFile("index.html")
            if (index?.isFile == true) {
                file = index
            } else if (config.directoryListing) {
                sendDirectoryListing(output, relativePath, file)
                return ServeResult(200, "目录列表：/${relativePath.trim('/')}")
            } else {
                sendText(output, 403, "Forbidden", "目录浏览未启用。")
                return ServeResult(403, "目录浏览未启用")
            }
        }
        if (file?.isFile != true && config.spaFallback) {
            file = root.findFile("index.html")
        }
        if (file?.isFile != true) {
            sendText(output, 404, "Not Found", "文件不存在。")
            return ServeResult(404, "文件不存在")
        }
        val mime = mimeType(file.name.orEmpty())
        val length = file.length().takeIf { it >= 0 } ?: -1L
        val headers = corsHeaders() + mapOf(
            "Cache-Control" to "no-cache",
            "Last-Modified" to httpDate(file.lastModified()),
        )
        if (!headOnly && mime.startsWith("text/html")) {
            val html = appContext.contentResolver.openInputStream(file.uri)?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            sendBytes(output, 200, "OK", mime, injectClientLogger(html).toByteArray(StandardCharsets.UTF_8), headers)
            return ServeResult(200, "资源加载：${file.name.orEmpty()} (${mime.substringBefore(';')})")
        }
        output.write(buildHeaders(200, "OK", mime, if (headOnly) 0L else length, headers).toByteArray(StandardCharsets.UTF_8))
        if (!headOnly) {
            appContext.contentResolver.openInputStream(file.uri)?.use { input ->
                input.copyTo(output)
            }
        }
        output.flush()
        return ServeResult(200, "资源加载：${file.name.orEmpty()} (${mime.substringBefore(';')})")
    }

    private fun injectClientLogger(html: String): String {
        val script = """
            <script>
            (function(){
              if (window.__lyraMiniServerLogger) return;
              window.__lyraMiniServerLogger = true;
              function post(level, message, extra) {
                try {
                  var payload = Object.assign({
                    level: level,
                    message: String(message || ''),
                    path: location.pathname,
                    source: '',
                    line: ''
                  }, extra || {});
                  var text = JSON.stringify(payload);
                  if (navigator.sendBeacon) {
                    navigator.sendBeacon('$CLIENT_LOG_ENDPOINT', new Blob([text], { type: 'application/json' }));
                  } else {
                    fetch('$CLIENT_LOG_ENDPOINT', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: text,
                      keepalive: true
                    }).catch(function(){});
                  }
                } catch (ignored) {}
              }
              var originalError = console.error;
              console.error = function() {
                post('error', Array.prototype.slice.call(arguments).join(' '));
                if (originalError) originalError.apply(console, arguments);
              };
              window.addEventListener('error', function(event) {
                post('error', event.message || 'window error', {
                  source: event.filename || '',
                  line: event.lineno || ''
                });
              });
              window.addEventListener('unhandledrejection', function(event) {
                var reason = event.reason && (event.reason.stack || event.reason.message) || event.reason || 'unhandled rejection';
                post('error', reason);
              });
            })();
            </script>
        """.trimIndent()
        val index = html.indexOf("</body>", ignoreCase = true)
        return if (index >= 0) html.substring(0, index) + script + html.substring(index) else html + script
    }

    private fun sendDirectoryListing(output: OutputStream, relativePath: String, directory: DocumentFile) {
        val base = "/" + relativePath.trim('/').let { if (it.isBlank()) "" else "$it/" }
        val html = buildString {
            append("<!doctype html><meta charset=\"utf-8\"><title>Lyra Code</title>")
            append("<style>body{font-family:sans-serif;padding:24px;line-height:1.8}a{display:block;color:#6750a4}</style>")
            append("<h1>Index of $base</h1>")
            directory.listFiles().sortedBy { it.name.orEmpty().lowercase(Locale.US) }.forEach { child ->
                val name = child.name.orEmpty()
                append("<a href=\"")
                append(base)
                append(name)
                if (child.isDirectory) append("/")
                append("\">")
                append(name)
                if (child.isDirectory) append("/")
                append("</a>")
            }
        }
        sendBytes(output, 200, "OK", "text/html; charset=utf-8", html.toByteArray(StandardCharsets.UTF_8), corsHeaders())
    }

    private fun resolve(root: DocumentFile, relativePath: String): DocumentFile? {
        val clean = relativePath.trim('/').replace('\\', '/')
        if (clean.isBlank()) return root
        var current: DocumentFile = root
        clean.split('/').filter { it.isNotBlank() }.forEach { segment ->
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    private fun decodePath(rawUri: String): String? {
        val raw = rawUri.ifBlank { "/" }
        val decoded = runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrNull() ?: return null
        val clean = decoded.trim().removePrefix("/")
        if (clean.split('/').any { it == ".." }) return null
        return clean
    }

    private fun authorized(config: MiniServerConfig, authorization: String?): Boolean {
        if (config.password.isBlank()) return true
        val username = config.username.ifBlank { AppSettings.DEFAULT_MINI_SERVER_USERNAME }
        val expected = "Basic " + Base64.encodeToString("$username:${config.password}".toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        return authorization == expected
    }

    private fun sendText(output: OutputStream, code: Int, reason: String, text: String, extraHeaders: Map<String, String> = emptyMap()) {
        sendBytes(output, code, reason, "text/plain; charset=utf-8", text.toByteArray(StandardCharsets.UTF_8), corsHeaders() + extraHeaders)
    }

    private fun sendBytes(
        output: OutputStream,
        code: Int,
        reason: String,
        contentType: String,
        bytes: ByteArray,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        output.write(buildHeaders(code, reason, contentType, bytes.size.toLong(), extraHeaders).toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendRedirect(output: OutputStream, location: String) {
        val body = "请使用 HTTPS 访问：$location"
        sendBytes(
            output,
            308,
            "Permanent Redirect",
            "text/plain; charset=utf-8",
            body.toByteArray(StandardCharsets.UTF_8),
            corsHeaders() + mapOf("Location" to location),
        )
    }

    private fun buildHeaders(
        code: Int,
        reason: String,
        contentType: String,
        contentLength: Long,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String = buildString {
        append("HTTP/1.1 $code $reason\r\n")
        append("Content-Type: $contentType\r\n")
        if (contentLength >= 0) append("Content-Length: $contentLength\r\n")
        append("Connection: close\r\n")
        extraHeaders.forEach { (key, value) -> append("$key: $value\r\n") }
        append("\r\n")
    }

    private fun corsHeaders(): Map<String, String> = mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Access-Control-Allow-Methods" to "GET, HEAD, POST, OPTIONS",
        "Access-Control-Allow-Headers" to "Authorization, Content-Type",
    )

    private fun serviceUrl(config: MiniServerConfig): String {
        val host = when (config.host) {
            "0.0.0.0", "::" -> "127.0.0.1"
            else -> config.host.ifBlank { AppSettings.DEFAULT_MINI_SERVER_HOST }
        }
        return "${config.protocol}://$host:${config.port}/"
    }

    private fun customUrls(config: MiniServerConfig): List<String> {
        return config.customDomains.map { domain ->
            normalizeDomainUrl(config.protocol, domain, config.port)
        }.filter { it.isNotBlank() }.distinct()
    }

    private fun httpsLocation(rawUri: String, hostHeader: String?, config: MiniServerConfig): String {
        val authority = config.customDomains.firstOrNull()?.let { stripScheme(it).trim('/') }
            ?: hostHeader.orEmpty().ifBlank { "${serviceHost(config)}:${config.port}" }
        return "https://$authority$rawUri"
    }

    private fun serviceHost(config: MiniServerConfig): String = when (config.host) {
        "0.0.0.0", "::" -> "127.0.0.1"
        else -> config.host.ifBlank { AppSettings.DEFAULT_MINI_SERVER_HOST }
    }

    private fun normalizeDomainUrl(protocol: String, domain: String, port: Int): String {
        val clean = stripScheme(domain).trim().trim('/')
        if (clean.isBlank()) return ""
        return "$protocol://$clean${if (clean.contains(':')) "" else ":$port"}/"
    }

    private fun stripScheme(value: String): String =
        value.trim().removePrefix("http://").removePrefix("https://")

    private fun lanUrls(protocol: String, port: Int): List<String> {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filter { !it.isLoopbackAddress && it.hostAddress?.contains(':') != true }
                .mapNotNull { address: InetAddress -> address.hostAddress?.let { "$protocol://$it:$port/" } }
                .distinct()
        }.getOrDefault(emptyList())
    }

    private fun httpDate(timestamp: Long): String {
        val value = if (timestamp > 0) timestamp else System.currentTimeMillis()
        return SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(Date(value))
    }

    private fun registerMdns(config: MiniServerConfig) {
        if (!config.mdnsEnabled) return
        unregisterMdns()
        val manager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                lastMessage = "已通过 mDNS 发布 ${serviceInfo.serviceName}.local"
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                lastMessage = "mDNS 发布失败：$errorCode"
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = config.mdnsName.ifBlank { AppSettings.DEFAULT_MINI_SERVER_MDNS_NAME }
            serviceType = if (config.protocol == AppSettings.MINI_SERVER_PROTOCOL_HTTPS) "_https._tcp." else "_http._tcp."
            port = config.port
        }
        runCatching {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdManager = manager
            nsdRegistration = listener
        }
    }

    private fun unregisterMdns() {
        val manager = nsdManager
        val listener = nsdRegistration
        if (manager != null && listener != null) runCatching { manager.unregisterService(listener) }
        nsdManager = null
        nsdRegistration = null
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
        "html", "htm" -> "text/html; charset=utf-8"
        "js", "mjs" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "json", "map" -> "application/json; charset=utf-8"
        "txt", "md" -> "text/plain; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "wasm" -> "application/wasm"
        "pdf" -> "application/pdf"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        else -> "application/octet-stream"
    }

    private companion object {
        const val MINI_SERVER_CERT_ALIAS = "lyra-mini-server"
        const val MINI_SERVER_CERT_PASSWORD = "lyra-mini-server"
        const val CLIENT_LOG_ENDPOINT = "/__lyra_log"
        const val DEFAULT_LOG_LIMIT = 120
        const val MAX_LOG_ENTRIES = 500
        const val MAX_CLIENT_LOG_BODY_BYTES = 64 * 1024
        val CLIENT_LOG_LEVELS = setOf("debug", "info", "warn", "error")
    }
}
