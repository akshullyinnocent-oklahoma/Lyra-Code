package com.yukisoffd.lyracode.workspace

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

data class UploadedFile(
    val name: String,
    val content: String,
    val size: Int,
    val uri: String = "",
    val mimeType: String = "text/plain",
    val mediaKind: String = "text",
)

class UploadedFileManager(private val context: Context) {
    fun readText(uri: Uri): Result<UploadedFile> = runCatching {
        val name = displayName(uri)
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val safeMime = mimeType.ifBlank { mimeTypeForName(name) ?: mimeTypeForUri(uri) ?: "application/octet-stream" }
        val mediaKind = mediaKindFor(safeMime)
        if (mediaKind != "text") {
            val bytes = readBytes(uri, MAX_MEDIA_UPLOAD_BYTES)
            return@runCatching UploadedFile(
                name = name,
                content = "data:$safeMime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
                size = bytes.size,
                uri = uri.toString(),
                mimeType = safeMime,
                mediaKind = mediaKind,
            )
        }
        val bytes = readBytes(uri, MAX_UPLOAD_BYTES)
        require(bytes.size <= MAX_UPLOAD_BYTES) { "上传文件超过 1MB，请先放入工作目录后由 Termux 分块读取" }
        UploadedFile(name, bytes.toString(Charsets.UTF_8), bytes.size, uri.toString(), mimeType.ifBlank { "text/plain" }, "text")
    }

    fun saveCapturedImage(bitmap: Bitmap): Result<UploadedFile> = runCatching {
        val dir = File(context.cacheDir, "uploads").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            output.toByteArray()
        }
        file.outputStream().use { output ->
            output.write(bytes)
        }
        UploadedFile(
            name = file.name,
            content = "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}",
            size = bytes.size,
            uri = Uri.fromFile(file).toString(),
            mimeType = "image/jpeg",
            mediaKind = "image",
        )
    }

    private fun readBytes(uri: Uri, maxBytes: Int): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= maxBytes) { "上传文件超过 ${maxBytes / 1024 / 1024}MB，请压缩后重试，或放入工作目录由工具分块读取" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: error("无法读取上传文件")
    }

    private fun displayName(uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "uploaded.txt"
    }

    private fun displaySize(uri: Uri): Int {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0
        } ?: 0
    }

    private fun mediaKindFor(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "image"
        mimeType.startsWith("video/") -> "video"
        mimeType.startsWith("audio/") -> "audio"
        else -> "text"
    }

    private fun mimeTypeForUri(uri: Uri): String? {
        val text = uri.toString().substringBefore('?').lowercase()
        return mimeTypeForName(text)
    }

    private fun mimeTypeForName(name: String): String? {
        val lower = name.substringBefore('?').lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".mp4") || lower.endsWith(".m4v") -> "video/mp4"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".m4a") || lower.endsWith(".aac") -> "audio/aac"
            lower.endsWith(".ogg") -> "audio/ogg"
            else -> null
        }
    }

    companion object {
        private const val MAX_UPLOAD_BYTES = 1_048_576
        private const val MAX_MEDIA_UPLOAD_BYTES = 8 * 1024 * 1024
    }
}
