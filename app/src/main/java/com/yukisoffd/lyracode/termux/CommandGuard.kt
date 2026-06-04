package com.yukisoffd.lyracode.termux

object CommandGuard {
    private val allowedCommands = setOf(
        "bash", "sh", "ls", "cat", "head", "tail", "pwd", "echo", "printf", "mkdir", "touch",
        "cp", "mv", "rm", "grep", "find", "sed", "awk", "python", "python3", "pip", "pip3",
        "node", "npm", "npx", "pnpm", "yarn", "git", "pkg", "apt", "termux-setup-storage",
        "chmod", "zip", "unzip", "tar", "curl", "wget",
    )

    private val dangerousFragments = listOf(
        "rm -rf /",
        "rm -fr /",
        "> /dev/block",
        ">/dev/block",
        " mkfs",
        "mkfs.",
        " dd ",
        " dd if=",
        ":(){:|:&};:",
        "chmod 777 /",
        "chown -R",
    )

    fun validate(command: String, allowRoot: Boolean = false): Result<Unit> = runCatching {
        val trimmed = command.trim()
        require(trimmed.isNotBlank()) { "命令不能为空" }
        val lowered = " ${trimmed.lowercase()} "
        val dangerous = dangerousFragments.firstOrNull { lowered.contains(it) }
        require(dangerous == null) { "命令包含高风险片段: ${dangerous?.trim()}" }
        require(allowRoot || !trimmed.startsWith("su ") && trimmed != "su") { "Root 命令需要单独授权" }
        val first = firstCommand(trimmed)
        require(first in allowedCommands) { "命令不在白名单内: $first" }
    }

    private fun firstCommand(command: String): String {
        val withoutEnv = command
            .removePrefix("env ")
            .trimStart()
        val firstSegment = withoutEnv
            .split("&&", "||", ";", "|")
            .first()
            .trim()
        return firstSegment.split(Regex("\\s+")).firstOrNull().orEmpty().substringAfterLast("/")
    }
}
