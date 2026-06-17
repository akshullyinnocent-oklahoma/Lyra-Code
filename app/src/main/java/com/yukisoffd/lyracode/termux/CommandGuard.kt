package com.yukisoffd.lyracode.termux

object CommandGuard {
    private val dangerousPatterns = listOf(
        Regex("""(?i)(^|[;&|]\s*)rm\s+(?=[^;&|]*\s/)(?=[^;&|]*(?:-[^\s;&|]*r|--recursive))(?=[^;&|]*(?:-[^\s;&|]*f|--force))[^;&|]*\s/(?:\s|$|[;&|*])"""),
        Regex("""(?i)>\s*/dev/block/"""),
        Regex("""(?i)(^|[;&|]\s*)dd\s+.*\bof=/dev/(block|mmcblk|sda|vda)"""),
        Regex("""(?i)(^|[;&|]\s*)mkfs(?:\.[a-z0-9]+)?\b"""),
        Regex("""(?i):\(\)\s*\{\s*:\|:&\s*};:"""),
        Regex("""(?i)(^|[;&|]\s*)chmod\s+-?R?\s*777\s+/(?:\s|$|[;&|*])"""),
        Regex("""(?i)(^|[;&|]\s*)chown\s+-R\s+\S+\s+/(?:\s|$|[;&|*])"""),
    )

    fun validate(command: String, allowRoot: Boolean = false): Result<Unit> = runCatching {
        val trimmed = command.trim()
        require(trimmed.isNotBlank()) { "命令不能为空" }
        val dangerous = dangerousPatterns.firstOrNull { it.containsMatchIn(trimmed) }
        require(dangerous == null) { "命令包含高风险操作，已拦截" }
        require(allowRoot || !trimmed.startsWith("su ") && trimmed != "su") { "Root 命令需要单独授权" }
    }
}
