package com.yukisoffd.lyracode

import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.yukisoffd.lyracode.ai.ChatRecord
import com.yukisoffd.lyracode.ai.ChatUpdate
import com.yukisoffd.lyracode.ai.OpenAiAgent
import com.yukisoffd.lyracode.ai.ToolApprovalDecision
import com.yukisoffd.lyracode.ai.ToolApprovalRequest
import com.yukisoffd.lyracode.ai.TodoItem
import com.yukisoffd.lyracode.ai.toRecord
import com.yukisoffd.lyracode.data.ApiProfile
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.Conversation
import com.yukisoffd.lyracode.data.ConversationStore
import com.yukisoffd.lyracode.workspace.UploadedFile
import com.yukisoffd.lyracode.workspace.UploadedFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PendingToolApproval(
    val id: Long,
    val request: ToolApprovalRequest,
)

class ChatController(
    private val settings: AppSettings,
    private val conversationStore: ConversationStore,
    private val uploadedFileManager: UploadedFileManager,
    private val agent: OpenAiAgent,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = mutableMapOf<Long, Job>()

    val conversations = mutableStateListOf<Conversation>()
    private val _messages = mutableStateOf<List<ChatRecord>>(emptyList())
    val messages: State<List<ChatRecord>> = _messages
    val profiles = mutableStateListOf<ApiProfile>()
    val activeConversationId = mutableStateOf(0L)
    val activeProfileId = mutableStateOf("")
    val activeModel = mutableStateOf("")
    val status = mutableStateOf("")
    val uploadingStatus = mutableStateOf("")
    val pendingUploads = mutableStateListOf<UploadedFile>()
    val pendingToolApproval = mutableStateOf<PendingToolApproval?>(null)
    val todoItems = mutableStateListOf<TodoItem>()
    val settingsRevision = mutableIntStateOf(0)
    private var lastMessageReloadAt = 0L
    private var approvalId = 0L
    private val approvalWaiters = mutableMapOf<Long, CompletableDeferred<ToolApprovalDecision>>()
    private val autoApprovedConversations = mutableSetOf<Long>()
    private val todoByConversation = mutableMapOf<Long, MutableList<TodoItem>>()

    init {
        agent.approvalHandler = ::requestToolApproval
        agent.todoSetHandler = ::setTodos
        agent.todoUpdateHandler = ::updateTodo
        agent.configChangedHandler = ::handleConfigChanged
        reloadProfiles()
        markAbandonedRunsInterrupted()
        reloadConversations()
        val first = conversations.firstOrNull()
        if (!settings.immersiveRoleplayEnabled) {
            when {
                first == null -> newConversation()
                conversationHasUserMessage(first.id) -> newConversation()
                else -> selectConversation(first.id)
            }
        } else if (first == null) {
            newConversation()
        } else {
            selectConversation(first.id)
        }
    }

    fun close() {
        scope.cancel()
    }

    fun reloadProfiles() {
        profiles.clear()
        profiles.addAll(settings.profiles())
        val selected = settings.selectedProfile()
        activeProfileId.value = selected.id
        activeModel.value = selected.selectedModel
    }

    fun saveProfiles(updated: List<ApiProfile>, selectedId: String = activeProfileId.value) {
        settings.saveProfiles(updated, selectedId)
        reloadProfiles()
    }

    private suspend fun handleConfigChanged() {
        withContext(Dispatchers.Main) {
            settingsRevision.intValue++
            reloadProfiles()
            reloadConversations()
        }
    }

    fun selectProfile(profileId: String) {
        val profile = profiles.firstOrNull { it.id == profileId } ?: return
        settings.selectedApiProfileId = profile.id
        activeProfileId.value = profile.id
        activeModel.value = profile.selectedModel
        activeConversationId.value.takeIf { it > 0 }?.let {
            conversationStore.setConversationMeta(it, profileId = profile.id, model = profile.selectedModel)
            reloadConversations()
        }
    }

    fun selectModel(model: String) {
        activeModel.value = model
        val updated = profiles.map {
            if (it.id == activeProfileId.value) it.copy(
                selectedModel = model,
                savedModels = (it.savedModels + model).filter { item -> item.isNotBlank() }.distinct(),
            ) else it
        }
        saveProfiles(updated, activeProfileId.value)
        activeConversationId.value.takeIf { it > 0 }?.let {
            conversationStore.setConversationMeta(it, profileId = activeProfileId.value, model = model)
            reloadConversations()
        }
    }

    fun selectSystemPrompt(promptId: String) {
        settings.selectedSystemPromptId = promptId
        settingsRevision.intValue++
    }

    fun selectReasoningDepth(depth: String) {
        settings.reasoningDepth = depth
        settingsRevision.intValue++
    }

    fun newConversation() {
        val profile = currentProfile()
        val roleplayId = currentRoleplayId()
        val id = conversationStore.createConversation(
            profileId = profile.id,
            model = activeModel.value.ifBlank { profile.selectedModel },
            title = if (isRoleplayMode()) settings.roleplayScenarios().firstOrNull { it.id == roleplayId }?.name ?: "沉浸对话" else "新对话",
            mode = if (isRoleplayMode()) ConversationStore.MODE_ROLEPLAY else ConversationStore.MODE_NORMAL,
            roleplayId = roleplayId,
        )
        todoByConversation[id] = mutableListOf()
        reloadConversations()
        selectConversation(id)
    }

    fun requestNewConversation(): Boolean {
        if (!isRoleplayMode() && isCurrentConversationBlank()) {
            return false
        }
        newConversation()
        return true
    }

    fun selectConversation(id: Long) {
        activeConversationId.value = id
        val conversation = conversationStore.conversation(id)
        if (conversation != null) {
            activeProfileId.value = conversation.profileId.ifBlank { activeProfileId.value }
            activeModel.value = conversation.model.ifBlank { activeModel.value }
        }
        reloadMessages()
        reloadTodos()
    }

    fun deleteConversation(id: Long) {
        jobs.remove(id)?.cancel()
        autoApprovedConversations.remove(id)
        todoByConversation.remove(id)
        conversationStore.deleteConversation(id)
        reloadConversations()
        val next = conversations.firstOrNull()?.id
        if (next == null) newConversation() else selectConversation(next)
    }

    fun renameConversation(id: Long, title: String) {
        conversationStore.setConversationMeta(id, title = title)
        reloadConversations()
    }

    fun setConversationPinned(id: Long, pinned: Boolean) {
        conversationStore.setPinned(id, pinned)
        reloadConversations()
    }

    fun deleteConversations(ids: Collection<Long>) {
        ids.forEach { id ->
            jobs.remove(id)?.cancel()
            autoApprovedConversations.remove(id)
            todoByConversation.remove(id)
            conversationStore.deleteConversation(id)
        }
        reloadConversations()
        if (activeConversationId.value in ids) {
            val next = conversations.firstOrNull()?.id
            if (next == null) newConversation() else selectConversation(next)
        }
    }

    fun setConversationsPinned(ids: Collection<Long>, pinned: Boolean) {
        ids.forEach { conversationStore.setPinned(it, pinned) }
        reloadConversations()
    }

    fun send(text: String) {
        val uploads = pendingUploads.toList()
        if (text.isBlank() && uploads.isEmpty()) return
        val conversationId = activeConversationId.value.takeIf { it > 0 } ?: return
        if (jobs[conversationId]?.isActive == true) return
        val profile = currentProfile()
        val model = activeModel.value.ifBlank { profile.selectedModel }
        val userInput = composeUserInput(text, uploads)
        if (activeConversation()?.title == "新对话") {
            conversationStore.setConversationMeta(conversationId, title = fallbackConversationTitle(userInput))
            reloadConversations()
        }
        pendingUploads.clear()
        uploadingStatus.value = ""
        jobs[conversationId] = scope.launch {
            status.value = "运行中"
            agent.chat(conversationId, userInput, profile, model) {
                withContext(Dispatchers.Main) {
                    applyChatUpdate(it)
                    status.value = it.status
                }
            }
            reloadMessages()
            reloadConversations()
            markConversationFinished(conversationId)
        }
    }

    fun stopActive() {
        val conversationId = activeConversationId.value.takeIf { it > 0 } ?: return
        jobs.remove(conversationId)?.cancel()
        conversationStore.setConversationMeta(conversationId, status = ConversationStore.STATUS_INTERRUPTED)
        pendingToolApproval.value?.takeIf { it.request.conversationId == conversationId }?.let { pending ->
            approvalWaiters.remove(pending.id)?.complete(
                ToolApprovalDecision(approved = false, feedback = "用户中断了当前任务。"),
            )
            pendingToolApproval.value = null
        }
        reloadConversations()
        reloadMessages()
        status.value = "已中断"
    }

    fun continueActive() {
        val conversationId = activeConversationId.value.takeIf { it > 0 } ?: return
        if (jobs[conversationId]?.isActive == true) return
        val profile = currentProfile()
        val model = activeModel.value.ifBlank { profile.selectedModel }
        jobs[conversationId] = scope.launch {
            status.value = "继续运行"
            agent.continueConversation(conversationId, profile, model) {
                withContext(Dispatchers.Main) {
                    applyChatUpdate(it)
                    status.value = it.status
                }
            }
            reloadMessages()
            reloadConversations()
            markConversationFinished(conversationId)
        }
    }

    private fun markConversationFinished(conversationId: Long) {
        status.value = "完成"
        scope.launch {
            delay(2400L)
            if (activeConversationId.value == conversationId && jobs[conversationId]?.isActive != true && status.value == "完成") {
                status.value = ""
            }
        }
    }

    fun attachUploadedFile(uri: Uri) {
        val conversationId = activeConversationId.value.takeIf { it > 0 } ?: return
        scope.launch {
            uploadingStatus.value = "读取上传文件"
            val result = withContext(Dispatchers.IO) { uploadedFileManager.readText(uri) }
            result.fold(
                onSuccess = { file ->
                    pendingUploads += file
                    uploadingStatus.value = "已上传 ${file.name}"
                },
                onFailure = { uploadingStatus.value = it.message.orEmpty() },
            )
        }
    }

    fun attachCapturedImage(bitmap: Bitmap) {
        val conversationId = activeConversationId.value.takeIf { it > 0 } ?: return
        scope.launch {
            uploadingStatus.value = "处理拍照图片"
            val result = withContext(Dispatchers.IO) { uploadedFileManager.saveCapturedImage(bitmap) }
            result.fold(
                onSuccess = { file ->
                    pendingUploads += file
                    uploadingStatus.value = "已上传 ${file.name}"
                },
                onFailure = { uploadingStatus.value = it.message.orEmpty() },
            )
        }
    }

    fun removePendingUpload(index: Int) {
        pendingUploads.getOrNull(index) ?: return
        pendingUploads.removeAt(index)
        uploadingStatus.value = if (pendingUploads.isEmpty()) "" else "待发送 ${pendingUploads.size} 个附件"
    }

    private fun composeUserInput(text: String, uploads: List<UploadedFile>): String {
        return buildString {
            val cleanText = text.trim()
            if (cleanText.isNotBlank()) append(cleanText)
            uploads.forEach { file ->
                if (isNotBlank()) append("\n\n")
                if (file.mediaKind == "text") {
                    append("用户上传文件：").append(file.name).append('\n')
                    append("大小：").append(file.size).append(" bytes\n\n")
                    append("```text\n")
                    append(file.content)
                    append("\n```")
                } else {
                    append("用户上传媒体：").append(file.name).append('\n')
                    append("类型：").append(file.mediaKind).append('\n')
                    append("MIME：").append(file.mimeType).append('\n')
                    if (file.content.startsWith("data:", ignoreCase = true)) {
                        append("DATA_URL：").append(file.content).append('\n')
                    }
                    append("URI：").append(file.uri).append('\n')
                    append("大小：").append(file.size).append(" bytes")
                }
            }
        }
    }

    private fun fallbackConversationTitle(userInput: String): String {
        return userInput.lineSequence()
            .firstOrNull()
            .orEmpty()
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(36)
            .ifBlank { "新对话" }
    }

    fun fetchModels(onDone: (Result<List<String>>) -> Unit) {
        val profile = currentProfile()
        scope.launch {
            status.value = "获取模型列表"
            val result = withContext(Dispatchers.IO) { agent.fetchModels(profile) }
            result.onSuccess { models ->
                val updated = profiles.map {
                    if (it.id == profile.id) it.copy(
                        savedModels = (it.savedModels + models).distinct(),
                    ) else it
                }
                saveProfiles(updated, profile.id)
            }
            status.value = ""
            onDone(result)
        }
    }

    fun fetchModelsForProfile(profile: ApiProfile, onDone: (Result<List<String>>) -> Unit) {
        scope.launch {
            status.value = "获取模型列表"
            val result = withContext(Dispatchers.IO) { agent.fetchModels(profile) }
            status.value = ""
            onDone(result)
        }
    }

    fun reloadConversations() {
        conversations.clear()
        if (isRoleplayMode()) {
            conversations.addAll(conversationStore.conversations(ConversationStore.MODE_ROLEPLAY, currentRoleplayId()))
        } else {
            conversations.addAll(conversationStore.conversations(ConversationStore.MODE_NORMAL))
        }
        val active = activeConversationId.value
        if (active > 0 && conversations.none { it.id == active }) {
            val next = conversations.firstOrNull()?.id
            if (next == null) {
                activeConversationId.value = 0L
                _messages.value = emptyList()
            } else {
                selectConversation(next)
            }
        }
    }

    fun reloadMessages() {
        val id = activeConversationId.value
        _messages.value = if (id <= 0) emptyList() else conversationStore.messages(id).map { it.toRecord() }
        lastMessageReloadAt = System.currentTimeMillis()
    }

    fun reloadTodos() {
        todoItems.clear()
        todoItems.addAll(todoByConversation[activeConversationId.value].orEmpty())
    }

    fun isActiveConversationRunning(): Boolean = jobs[activeConversationId.value]?.isActive == true

    fun activeConversation(): Conversation? = conversationStore.conversation(activeConversationId.value)

    private fun isCurrentConversationBlank(): Boolean {
        val id = activeConversationId.value.takeIf { it > 0 } ?: return false
        return !conversationHasUserMessage(id)
    }

    private fun conversationHasUserMessage(id: Long): Boolean {
        return conversationStore.messages(id).any { it.role == "user" }
    }

    fun answerToolApproval(approved: Boolean, rememberForConversation: Boolean, feedback: String) {
        val pending = pendingToolApproval.value ?: return
        approvalWaiters.remove(pending.id)?.complete(
            ToolApprovalDecision(
                approved = approved,
                rememberForConversation = rememberForConversation,
                feedback = feedback.trim(),
            ),
        )
        if (approved && rememberForConversation) {
            autoApprovedConversations += pending.request.conversationId
        }
        pendingToolApproval.value = null
        status.value = if (approved) "已批准工具调用" else "已拒绝工具调用"
    }

    private fun currentProfile(): ApiProfile {
        return profiles.firstOrNull { it.id == activeProfileId.value } ?: profiles.first()
    }

    fun isRoleplayMode(): Boolean = settings.immersiveRoleplayEnabled && settings.selectedRoleplayId.isNotBlank()

    fun currentRoleplayId(): String = if (settings.immersiveRoleplayEnabled) settings.selectedRoleplayId else ""

    fun switchConversationScope() {
        reloadConversations()
        val first = conversations.firstOrNull()
        if (first == null) newConversation() else selectConversation(first.id)
    }

    fun clearCurrentRoleplayData() {
        val roleplayId = currentRoleplayId().ifBlank { return }
        clearRoleplayData(roleplayId)
    }

    fun clearRoleplayData(roleplayId: String) {
        jobs.keys.toList().forEach { id -> jobs.remove(id)?.cancel() }
        conversationStore.deleteConversationsForRoleplay(roleplayId)
        settings.setRoleplayAffection(roleplayId, 50)
        reloadConversations()
        if (currentRoleplayId() == roleplayId) newConversation()
    }

    private fun reloadMessagesThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastMessageReloadAt < 180L) return
        reloadMessages()
    }

    private fun applyChatUpdate(update: ChatUpdate) {
        if (update.messageId <= 0L) {
            reloadMessagesThrottled()
            return
        }
        val current = _messages.value
        val index = current.indexOfFirst { it.id == update.messageId }
        if (index < 0) {
            reloadMessages()
            return
        }
        val updated = current[index].copy(content = update.content, thinking = update.thinking)
        _messages.value = current.toMutableList().also { it[index] = updated }
        lastMessageReloadAt = System.currentTimeMillis()
        if (update.status.startsWith("工具完成")) {
            reloadConversations()
            reloadMessages()
        }
    }

    private suspend fun requestToolApproval(request: ToolApprovalRequest): ToolApprovalDecision {
        if (request.conversationId in autoApprovedConversations) return ToolApprovalDecision.Approved
        return withContext(Dispatchers.Main) {
            val id = ++approvalId
            val waiter = CompletableDeferred<ToolApprovalDecision>()
            approvalWaiters[id] = waiter
            pendingToolApproval.value = PendingToolApproval(id, request)
            status.value = "等待确认: ${request.toolName}"
            waiter
        }.await()
    }

    private suspend fun setTodos(conversationId: Long, items: List<TodoItem>): String = withContext(Dispatchers.Main) {
        val normalized = items.ifEmpty { listOf(TodoItem("1", "完成当前任务", "pending")) }
            .mapIndexed { index, item ->
                item.copy(
                    id = item.id.ifBlank { (index + 1).toString() },
                    status = item.status.ifBlank { "pending" },
                )
            }
            .toMutableList()
        todoByConversation[conversationId] = normalized
        if (activeConversationId.value == conversationId) reloadTodos()
        "TODO 列表已设置，共 ${normalized.size} 项。"
    }

    private suspend fun updateTodo(conversationId: Long, id: String, status: String, note: String): String = withContext(Dispatchers.Main) {
        val list = todoByConversation.getOrPut(conversationId) { mutableListOf() }
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            list[index] = list[index].copy(
                status = status.ifBlank { list[index].status },
                note = note.ifBlank { list[index].note },
            )
        } else {
            list += TodoItem(id.ifBlank { (list.size + 1).toString() }, note.ifBlank { "未命名步骤" }, status.ifBlank { "completed" })
        }
        if (activeConversationId.value == conversationId) reloadTodos()
        "TODO ${id.ifBlank { list.last().id }} 已标记为 ${status.ifBlank { "completed" }}。"
    }

    private fun markAbandonedRunsInterrupted() {
        conversationStore.conversations()
            .filter { it.status == ConversationStore.STATUS_RUNNING }
            .forEach { conversationStore.setConversationMeta(it.id, status = ConversationStore.STATUS_INTERRUPTED) }
    }
}
