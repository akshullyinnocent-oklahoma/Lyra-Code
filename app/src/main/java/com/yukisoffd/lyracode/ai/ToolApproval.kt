package com.yukisoffd.lyracode.ai

data class ToolApprovalRequest(
    val conversationId: Long,
    val toolName: String,
    val arguments: String,
    val summary: String,
    val risk: String,
)

data class ToolApprovalDecision(
    val approved: Boolean,
    val rememberForConversation: Boolean = false,
    val feedback: String = "",
) {
    companion object {
        val Approved = ToolApprovalDecision(approved = true)
    }
}
