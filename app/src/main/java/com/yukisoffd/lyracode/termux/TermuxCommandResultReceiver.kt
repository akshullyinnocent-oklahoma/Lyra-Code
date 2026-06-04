package com.yukisoffd.lyracode.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

data class TermuxCommandResult(
    val stdout: String,
    val stderr: String,
    val stdoutOriginalLength: String,
    val stderrOriginalLength: String,
    val exitCode: Int,
    val errCode: Int,
    val errMsg: String,
) {
    fun toAgentText(): String = buildString {
        appendLine("exit_code: $exitCode")
        appendLine("termux_err_code: $errCode")
        if (errMsg.isNotBlank()) {
            appendLine("termux_errmsg:")
            appendLine(fenced(errMsg))
        }
        appendLine("stdout_original_length: ${stdoutOriginalLength.ifBlank { stdout.length.toString() }}")
        appendLine("stderr_original_length: ${stderrOriginalLength.ifBlank { stderr.length.toString() }}")
        appendLine("stdout:")
        appendLine(fenced(stdout))
        appendLine("stderr:")
        appendLine(fenced(stderr))
    }

    private fun fenced(text: String): String {
        val safe = text.ifBlank { "(empty)" }.replace("```", "`\u200b``")
        return "```text\n$safe\n```"
    }
}

class TermuxCommandResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, -1)
        val bundle = intent.getBundleExtra(EXTRA_PLUGIN_RESULT_BUNDLE)
            ?: intent.getBundleExtra(EXTRA_LEGACY_RESULT_BUNDLE)
        val result = if (bundle == null) {
            TermuxCommandResult(
                stdout = "",
                stderr = "",
                stdoutOriginalLength = "",
                stderrOriginalLength = "",
                exitCode = Int.MIN_VALUE,
                errCode = Int.MIN_VALUE,
                errMsg = "Termux 未返回 result bundle。请确认 Termux 版本 >= 0.109，且 allow-external-apps=true。",
            )
        } else {
            resultFromBundle(bundle)
        }
        pending.remove(executionId)?.complete(result)
    }

    private fun resultFromBundle(bundle: Bundle): TermuxCommandResult {
        return TermuxCommandResult(
            stdout = bundle.safeString(EXTRA_STDOUT),
            stderr = bundle.safeString(EXTRA_STDERR),
            stdoutOriginalLength = bundle.safeString(EXTRA_STDOUT_ORIGINAL_LENGTH),
            stderrOriginalLength = bundle.safeString(EXTRA_STDERR_ORIGINAL_LENGTH),
            exitCode = bundle.getInt(EXTRA_EXIT_CODE, Int.MIN_VALUE),
            errCode = bundle.getInt(EXTRA_ERR, -1),
            errMsg = bundle.safeString(EXTRA_ERRMSG),
        )
    }

    companion object {
        const val EXTRA_EXECUTION_ID = "execution_id"
        private const val EXTRA_PLUGIN_RESULT_BUNDLE = "result"
        private const val EXTRA_LEGACY_RESULT_BUNDLE = "com.termux.RUN_COMMAND_RESULT"
        private const val EXTRA_STDOUT = "stdout"
        private const val EXTRA_STDOUT_ORIGINAL_LENGTH = "stdout_original_length"
        private const val EXTRA_STDERR = "stderr"
        private const val EXTRA_STDERR_ORIGINAL_LENGTH = "stderr_original_length"
        private const val EXTRA_EXIT_CODE = "exitCode"
        private const val EXTRA_ERR = "err"
        private const val EXTRA_ERRMSG = "errmsg"

        private val pending = ConcurrentHashMap<Int, CompletableDeferred<TermuxCommandResult>>()

        fun register(executionId: Int): CompletableDeferred<TermuxCommandResult> {
            return CompletableDeferred<TermuxCommandResult>().also { pending[executionId] = it }
        }

        fun unregister(executionId: Int) {
            pending.remove(executionId)
        }

        @Suppress("DEPRECATION")
        private fun Bundle.safeString(name: String): String = get(name)?.toString().orEmpty()
    }
}
