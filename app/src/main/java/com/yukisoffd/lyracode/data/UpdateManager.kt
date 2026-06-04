package com.yukisoffd.lyracode.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val apkSha256: String,
    val releaseNotes: String,
    val releaseNotesUrl: String,
    val webUrl: String,
    val mandatory: Boolean,
) {
    fun isNewerThan(currentVersionCode: Long): Boolean = versionCode > currentVersionCode
}

data class UpdateDownloadProgress(
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val status: String = "",
) {
    val percent: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

class UpdateManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun manifestUrl(): String = com.yukisoffd.lyracode.BuildConfig.LYRA_UPDATE_MANIFEST_URL.trim()

    fun currentVersionCode(): Long {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageInfo(appContext.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    fun checkForUpdate(): Result<AppUpdateInfo?> = runCatching {
        val url = manifestUrl()
        require(url.isNotBlank()) { "未配置更新清单地址，请在 gradle.properties 设置 lyra.updateManifestUrl" }
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "版本检测失败：HTTP ${response.code}" }
            val json = JSONObject(response.body?.string().orEmpty())
            val info = parseUpdateInfo(json)
            if (info.isNewerThan(currentVersionCode())) info else null
        }
    }

    fun downloadApk(
        info: AppUpdateInfo,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): Result<File> = runCatching {
        require(info.apkUrl.startsWith("https://") || info.apkUrl.startsWith("http://")) { "安装包下载地址无效" }
        val request = Request.Builder()
            .url(info.apkUrl)
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", "LyraCode/${currentVersionCode()} AndroidUpdateClient")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "下载安装包失败：HTTP ${response.code}" }
            val body = response.body ?: error("下载安装包失败：响应为空")
            val total = body.contentLength()
            val contentType = body.contentType()?.toString().orEmpty()
            val outputDir = File(appContext.externalCacheDir ?: appContext.cacheDir, "updates").also { it.mkdirs() }
            val output = File(outputDir, "LyraCode-${info.versionName.ifBlank { info.versionCode.toString() }}.apk")
            val partial = File(outputDir, "${output.name}.part")
            if (partial.exists()) partial.delete()
            body.byteStream().use { input ->
                partial.outputStream().use { outputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        outputStream.write(buffer, 0, read)
                        downloaded += read
                        onProgress(UpdateDownloadProgress(downloaded, total, "正在下载"))
                    }
                }
            }
            require(partial.length() > 0L) {
                partial.delete()
                "下载安装包失败：文件为空"
            }
            require(isZipApk(partial)) {
                val head = firstBytesHex(partial)
                val length = partial.length()
                partial.delete()
                "下载到的不是 APK 文件。contentType=${contentType.ifBlank { "unknown" }}，size=$length，head=$head"
            }
            if (info.apkSha256.isNotBlank()) {
                val actual = sha256(partial)
                val expected = normalizeSha256(info.apkSha256)
                require(expected != null) {
                    partial.delete()
                    "更新清单中的 apkSha256 格式无效"
                }
                require(actual.equals(expected, ignoreCase = true)) {
                    val size = partial.length()
                    partial.delete()
                    "安装包校验失败：SHA-256 不一致\n期望：$expected\n实际：$actual\nsize=$size，contentType=${contentType.ifBlank { "unknown" }}"
                }
            }
            if (output.exists()) output.delete()
            require(partial.renameTo(output)) {
                partial.delete()
                "保存安装包失败"
            }
            onProgress(UpdateDownloadProgress(total.coerceAtLeast(output.length()), total, "下载完成"))
            output
        }
    }

    fun installOrRequestPermission(apkFile: File): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !appContext.packageManager.canRequestPackageInstalls()) {
            return Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun parseUpdateInfo(json: JSONObject): AppUpdateInfo {
        val versionCode = json.optLong("versionCode").takeIf { it > 0 }
            ?: json.optLong("version_code").takeIf { it > 0 }
            ?: error("更新清单缺少 versionCode")
        val releaseNotesUrl = json.optString("releaseNotesUrl").ifBlank { json.optString("release_notes_url") }
        val inlineNotes = json.optString("releaseNotes").ifBlank { json.optString("release_notes") }
        val releaseNotes = if (inlineNotes.isNotBlank() || releaseNotesUrl.isBlank()) {
            inlineNotes
        } else {
            runCatching {
                val request = Request.Builder().url(releaseNotesUrl).get().build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string().orEmpty() else ""
                }
            }.getOrDefault("")
        }
        return AppUpdateInfo(
            versionName = json.optString("versionName").ifBlank { json.optString("version_name") },
            versionCode = versionCode,
            apkUrl = json.optString("apkUrl").ifBlank { json.optString("apk_url") },
            apkSha256 = json.optString("apkSha256").ifBlank { json.optString("apk_sha256").ifBlank { json.optString("sha256") } },
            releaseNotes = releaseNotes.ifBlank { "发现新版本，暂无更新说明。" },
            releaseNotesUrl = releaseNotesUrl,
            webUrl = json.optString("webUrl").ifBlank { json.optString("web_url") },
            mandatory = json.optBoolean("mandatory", false),
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun normalizeSha256(value: String): String? {
        val trimmed = value.trim().removePrefix("sha256:").removePrefix("SHA256:")
        val hex = trimmed.replace(Regex("[^0-9a-fA-F]"), "")
        if (hex.length == 64) return hex.lowercase()
        return runCatching {
            val decoded = Base64.getDecoder().decode(trimmed)
            if (decoded.size == 32) decoded.joinToString("") { "%02x".format(it) } else null
        }.getOrNull()
    }

    private fun isZipApk(file: File): Boolean {
        val header = ByteArray(4)
        val read = file.inputStream().use { it.read(header) }
        if (read < 4) return false
        return header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] in listOf(0x03.toByte(), 0x05.toByte(), 0x07.toByte()) &&
            header[3] in listOf(0x04.toByte(), 0x06.toByte(), 0x08.toByte())
    }

    private fun firstBytesHex(file: File, count: Int = 16): String {
        val bytes = ByteArray(count)
        val read = file.inputStream().use { it.read(bytes) }.coerceAtLeast(0)
        return bytes.take(read).joinToString(" ") { "%02x".format(it) }
    }
}
