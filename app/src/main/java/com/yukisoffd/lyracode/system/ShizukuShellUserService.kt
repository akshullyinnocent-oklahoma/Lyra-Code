package com.yukisoffd.lyracode.system

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ShizukuShellUserService : ISystemShellService.Stub() {
    override fun execute(command: String, timeoutSeconds: Int): String {
        return runCatching {
            collectProcess(
                ProcessBuilder("/system/bin/sh", "-c", command).start(),
                timeoutSeconds,
            ).toJson()
        }.getOrElse {
            SystemCommandResult(
                ok = false,
                mode = "shell",
                exitCode = -1,
                stdout = "",
                stderr = "",
                message = "Shell 命令启动失败: ${it.message ?: it.javaClass.simpleName}",
            ).toJson()
        }
    }

    override fun destroy() {
        System.exit(0)
    }

    private fun collectProcess(process: Process, timeoutSeconds: Int): SystemCommandResult {
        val readers = Executors.newFixedThreadPool(2)
        return try {
            val stdoutFuture = readers.submit(Callable { process.inputStream.bufferedReader().use { it.readText() } })
            val stderrFuture = readers.submit(Callable { process.errorStream.bufferedReader().use { it.readText() } })
            val timeout = timeoutSeconds.coerceIn(3, 600)
            val finished = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            val stdout = runCatching { stdoutFuture.get(3, TimeUnit.SECONDS) }.getOrDefault("")
            val stderr = runCatching { stderrFuture.get(3, TimeUnit.SECONDS) }.getOrDefault("")
            if (!finished) {
                SystemCommandResult(
                    ok = false,
                    mode = "shell",
                    exitCode = -1,
                    stdout = stdout,
                    stderr = stderr,
                    message = "命令执行超过 ${timeout} 秒，已终止。",
                )
            } else {
                val exitCode = process.exitValue()
                SystemCommandResult(
                    ok = exitCode == 0,
                    mode = "shell",
                    exitCode = exitCode,
                    stdout = stdout,
                    stderr = stderr,
                    message = if (exitCode == 0) "" else "命令退出码为 $exitCode。",
                )
            }
        } finally {
            readers.shutdownNow()
        }
    }
}
