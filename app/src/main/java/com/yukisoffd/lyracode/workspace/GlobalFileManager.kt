package com.yukisoffd.lyracode.workspace

import android.os.Environment
import java.io.File
import java.io.FileNotFoundException

class GlobalFileManager {
    fun listDirectory(path: String = ""): Result<List<WorkspaceFile>> = runCatching {
        val dir = resolve(path)
        require(dir.isDirectory) { "不是目录: $path" }
        dir.listFiles().orEmpty()
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map {
                WorkspaceFile(
                    name = it.name,
                    path = it.toPublicPath(),
                    directory = it.isDirectory,
                    size = it.length(),
                    modifiedAt = it.lastModified(),
                )
            }
    }

    fun readFile(path: String): Result<String> = runCatching {
        val file = resolve(path)
        require(file.isFile) { "不是文件: $path" }
        require(file.length() <= MAX_READ_BYTES) { "文件超过 1MB，请先复制小片段或使用其他方式分块读取: $path" }
        file.readText()
    }

    fun readBytes(path: String, maxBytes: Long = MAX_BINARY_BYTES): Result<ByteArray> = runCatching {
        val file = resolve(path)
        require(file.isFile) { "不是文件: $path" }
        require(file.length() <= maxBytes) { "文件超过 ${maxBytes / 1024 / 1024}MB: $path" }
        file.readBytes()
    }

    fun writeFile(path: String, content: String): Result<String> = runCatching {
        val file = resolveForWrite(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        "已写入 ${content.length} 字符: ${file.toPublicPath()}"
    }

    fun appendFile(path: String, content: String): Result<String> = runCatching {
        val file = resolveForWrite(path)
        file.parentFile?.mkdirs()
        file.appendText(content)
        "已追加 ${content.length} 字符: ${file.toPublicPath()}"
    }

    fun createFolder(path: String): Result<String> = runCatching {
        val dir = resolveForWrite(path)
        require(dir.mkdirs() || dir.isDirectory) { "无法创建目录: $path" }
        "已创建目录: ${dir.toPublicPath()}"
    }

    fun delete(path: String): Result<String> = runCatching {
        val file = resolve(path)
        val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
        require(ok) { "删除失败: $path" }
        "已删除: ${file.toPublicPath()}"
    }

    fun renameMove(from: String, to: String): Result<String> = runCatching {
        val source = resolve(from)
        val target = resolveForWrite(to)
        target.parentFile?.mkdirs()
        require(source.renameTo(target)) { "移动或重命名失败: $from -> $to" }
        "已移动/重命名: ${source.toPublicPath()} -> ${target.toPublicPath()}"
    }

    private fun resolve(path: String): File {
        val file = resolveForWrite(path)
        if (!file.exists()) throw FileNotFoundException("不存在: $path")
        return file
    }

    private fun resolveForWrite(path: String): File {
        val clean = normalize(path)
        val root = publicRoot()
        val file = if (clean.isBlank()) root else File(root, clean)
        val canonicalRoot = root.canonicalFile
        val canonicalFile = file.canonicalFile
        require(canonicalFile.path == canonicalRoot.path || canonicalFile.path.startsWith(canonicalRoot.path + File.separator)) {
            "全局文件工具只能访问 Android 共享存储: /storage/emulated/0"
        }
        val relative = canonicalFile.relativeTo(canonicalRoot).invariantSeparatorsPath
        require(relative.isBlank() || !relative.startsWith("Android/data") && !relative.startsWith("Android/obb")) {
            "不允许访问 Android/data 或 Android/obb"
        }
        return canonicalFile
    }

    private fun normalize(path: String): String {
        var clean = path.trim().replace('\\', '/')
        if (clean.isBlank() || clean == "." || clean == "/" || clean.equals("download", true) || clean.equals("downloads", true)) {
            return if (clean.equals("download", true) || clean.equals("downloads", true)) Environment.DIRECTORY_DOWNLOADS else ""
        }
        clean = clean.removePrefix("/sdcard/").removePrefix("sdcard/")
        clean = clean.removePrefix("/storage/emulated/0/").removePrefix("storage/emulated/0/")
        if (clean.equals("download", true) || clean.equals("downloads", true)) return Environment.DIRECTORY_DOWNLOADS
        clean = clean.trim('/')
        val parts = clean.split('/').filter { it.isNotBlank() && it != "." }
        require(parts.none { it == ".." }) { "路径不能包含 .." }
        require(parts.firstOrNull() != "data") { "不允许访问 /data" }
        return parts.joinToString("/")
    }

    private fun publicRoot(): File = Environment.getExternalStorageDirectory()

    private fun File.toPublicPath(): String {
        val root = publicRoot().canonicalFile
        val file = canonicalFile
        val relative = runCatching { file.relativeTo(root).invariantSeparatorsPath }.getOrDefault(file.path)
        return if (relative.isBlank() || relative == ".") "." else relative
    }

    companion object {
        private const val MAX_READ_BYTES = 1_048_576L
        private const val MAX_BINARY_BYTES = 200L * 1024L * 1024L
    }
}
