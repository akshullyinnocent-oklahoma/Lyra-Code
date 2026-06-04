package com.yukisoffd.lyracode.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

data class ApiProfile(
    val id: String,
    val name: String,
    val apiKey: String,
    val baseUrl: String,
    val apiFormat: String = API_FORMAT_OPENAI,
    val selectedModel: String,
    val savedModels: List<String>,
) {
    val chatEndpoint: String
        get() = when (apiFormat) {
            API_FORMAT_ANTHROPIC -> "${baseUrl.trimEnd('/')}/messages"
            else -> "${baseUrl.trimEnd('/')}/chat/completions"
        }

    val modelsEndpoint: String
        get() = "${baseUrl.trimEnd('/')}/models"

    fun geminiGenerateContentEndpoint(model: String): String {
        val encoded = model.trim().removePrefix("models/")
        return "${baseUrl.trimEnd('/')}/models/$encoded:generateContent"
    }

    companion object {
        const val API_FORMAT_OPENAI = "openai"
        const val API_FORMAT_ANTHROPIC = "anthropic_messages"
        const val API_FORMAT_GEMINI = "gemini_generate_content"
    }
}

data class SystemPromptPreset(
    val id: String,
    val name: String,
    val prompt: String,
)

data class SkillPack(
    val id: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val fileCount: Int,
)

data class RoleplayScenario(
    val id: String,
    val name: String,
    val description: String,
    val fileCount: Int,
    val aiNickname: String,
    val aiAvatarPath: String?,
    val backgroundPath: String?,
    val affection: Int,
)

data class RoleplaySticker(
    val code: String,
    val name: String,
    val path: String,
)

data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: String,
)

data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val authKey: String,
    val transport: String,
    val timeoutSeconds: Int,
    val enabled: Boolean,
    val rawJson: String,
    val tools: List<McpToolDefinition>,
)

data class SshServerConfig(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: String,
    val password: String,
    val privateKey: String,
    val passphrase: String,
    val timeoutSeconds: Int,
    val enabled: Boolean,
) {
    val stableId: String
        get() = "${host.trim()}:${port.coerceIn(1, 65535)}"
}

class AppSettings(context: Context) {
    private val appContext = context.applicationContext
    private val plainPrefs = appContext.getSharedPreferences("lyra_settings", Context.MODE_PRIVATE)
    private val securePrefs = createSecurePrefs()

    var workspaceUri: String?
        get() = plainPrefs.getString(KEY_WORKSPACE_URI, null)
        set(value) = plainPrefs.edit().putString(KEY_WORKSPACE_URI, value).apply()

    var apiKey: String
        get() = securePrefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = securePrefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var apiEndpoint: String
        get() = plainPrefs.getString(KEY_API_ENDPOINT, DEFAULT_ENDPOINT).orEmpty().ifBlank { DEFAULT_ENDPOINT }
        set(value) = plainPrefs.edit().putString(KEY_API_ENDPOINT, value.trim().ifBlank { DEFAULT_ENDPOINT }).apply()

    var model: String
        get() = plainPrefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
        set(value) = plainPrefs.edit().putString(KEY_MODEL, value.trim().ifBlank { DEFAULT_MODEL }).apply()

    var darkMode: Boolean
        get() = plainPrefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = plainPrefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var themeMode: String
        get() = plainPrefs.getString(KEY_THEME_MODE, if (darkMode) THEME_DARK else THEME_SYSTEM)
            .orEmpty()
            .ifBlank { THEME_SYSTEM }
        set(value) = plainPrefs.edit().putString(KEY_THEME_MODE, value).apply()

    var userNickname: String
        get() = plainPrefs.getString(KEY_USER_NICKNAME, "用户").orEmpty().ifBlank { "用户" }
        set(value) = plainPrefs.edit().putString(KEY_USER_NICKNAME, value.trim().ifBlank { "用户" }).apply()

    var userAvatarPath: String?
        get() = plainPrefs.getString(KEY_USER_AVATAR_PATH, null)
        set(value) = plainPrefs.edit().putString(KEY_USER_AVATAR_PATH, value).apply()

    var hideTermuxPermissionHint: Boolean
        get() = plainPrefs.getBoolean(KEY_HIDE_TERMUX_PERMISSION_HINT, false)
        set(value) = plainPrefs.edit().putBoolean(KEY_HIDE_TERMUX_PERMISSION_HINT, value).apply()

    var immersiveRoleplayEnabled: Boolean
        get() = plainPrefs.getBoolean(KEY_IMMERSIVE_ROLEPLAY_ENABLED, false)
        set(value) = plainPrefs.edit().putBoolean(KEY_IMMERSIVE_ROLEPLAY_ENABLED, value).apply()

    var selectedRoleplayId: String
        get() = plainPrefs.getString(KEY_SELECTED_ROLEPLAY_ID, "").orEmpty()
        set(value) = plainPrefs.edit().putString(KEY_SELECTED_ROLEPLAY_ID, value).apply()

    fun disabledTools(): Set<String> = plainPrefs.getStringSet(KEY_DISABLED_TOOLS, emptySet()).orEmpty()

    fun setToolEnabled(name: String, enabled: Boolean) {
        val updated = disabledTools().toMutableSet()
        if (enabled) updated -= name else updated += name
        plainPrefs.edit().putStringSet(KEY_DISABLED_TOOLS, updated).apply()
    }

    fun hiddenTodoSignature(conversationId: Long): String {
        return plainPrefs.getString("$KEY_HIDDEN_TODO_SIGNATURE_PREFIX$conversationId", "").orEmpty()
    }

    fun setHiddenTodoSignature(conversationId: Long, signature: String) {
        plainPrefs.edit().putString("$KEY_HIDDEN_TODO_SIGNATURE_PREFIX$conversationId", signature).apply()
    }

    fun hiddenFileChangesSignature(conversationId: Long): String {
        return plainPrefs.getString("$KEY_HIDDEN_FILE_CHANGES_SIGNATURE_PREFIX$conversationId", "").orEmpty()
    }

    fun setHiddenFileChangesSignature(conversationId: Long, signature: String) {
        plainPrefs.edit().putString("$KEY_HIDDEN_FILE_CHANGES_SIGNATURE_PREFIX$conversationId", signature).apply()
    }

    var selectedSystemPromptId: String
        get() = plainPrefs.getString(KEY_SELECTED_SYSTEM_PROMPT_ID, DEFAULT_SYSTEM_PROMPT_ID)
            .orEmpty()
            .ifBlank { DEFAULT_SYSTEM_PROMPT_ID }
        set(value) = plainPrefs.edit().putString(KEY_SELECTED_SYSTEM_PROMPT_ID, value).apply()

    fun systemPromptPresets(): List<SystemPromptPreset> {
        val custom = customSystemPrompts()
        return defaultSystemPromptPresets.map { preset ->
            preset.copy(prompt = custom[preset.id] ?: preset.prompt)
        }
    }

    fun activeSystemPromptText(): String {
        return systemPromptPresets().firstOrNull { it.id == selectedSystemPromptId }?.prompt
            ?: defaultSystemPromptPresets.first().prompt
    }

    fun saveSystemPrompt(presetId: String, prompt: String) {
        val custom = customSystemPrompts().toMutableMap()
        custom[presetId] = prompt.trim()
        saveCustomSystemPrompts(custom)
    }

    fun restoreSystemPrompt(presetId: String): String {
        val custom = customSystemPrompts().toMutableMap()
        custom.remove(presetId)
        saveCustomSystemPrompts(custom)
        return defaultSystemPromptPresets.firstOrNull { it.id == presetId }?.prompt.orEmpty()
    }

    private fun customSystemPrompts(): Map<String, String> {
        val raw = plainPrefs.getString(KEY_CUSTOM_SYSTEM_PROMPTS, null).orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            root.keys().asSequence().associateWith { root.optString(it) }
        }.getOrDefault(emptyMap())
    }

    private fun saveCustomSystemPrompts(prompts: Map<String, String>) {
        val root = JSONObject()
        prompts.forEach { (id, prompt) -> root.put(id, prompt) }
        plainPrefs.edit().putString(KEY_CUSTOM_SYSTEM_PROMPTS, root.toString()).apply()
    }

    fun profiles(): List<ApiProfile> {
        val raw = securePrefs.getString(KEY_API_PROFILES, null)
        if (raw.isNullOrBlank()) return listOf(defaultProfile())
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val models = item.optJSONArray("savedModels") ?: JSONArray()
                    val savedModels = buildList {
                        for (modelIndex in 0 until models.length()) add(models.getString(modelIndex))
                    }.filter { it.isNotBlank() }.distinct()
                    val apiFormat = item.optString("apiFormat").ifBlank { ApiProfile.API_FORMAT_OPENAI }
                    add(
                        ApiProfile(
                            id = item.optString("id").ifBlank { newId() },
                            name = item.optString("name").ifBlank { "OpenAI" },
                            apiKey = item.optString("apiKey"),
                            baseUrl = item.optString("baseUrl").ifBlank { DEFAULT_BASE_URL },
                            apiFormat = apiFormat,
                            selectedModel = item.optString("selectedModel").ifBlank { DEFAULT_MODEL },
                            savedModels = savedModels,
                        ),
                    )
                }
            }.ifEmpty { listOf(defaultProfile()) }
        }.getOrDefault(listOf(defaultProfile()))
    }

    fun saveProfiles(profiles: List<ApiProfile>, selectedProfileId: String? = null) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("apiKey", profile.apiKey)
                    .put("baseUrl", profile.baseUrl)
                    .put("apiFormat", profile.apiFormat)
                    .put("selectedModel", profile.selectedModel)
                    .put("savedModels", JSONArray(profile.savedModels.distinct()))
            )
        }
        securePrefs.edit().putString(KEY_API_PROFILES, array.toString()).apply()
        selectedProfileId?.let { selectedApiProfileId = it }
        profiles.firstOrNull { it.id == selectedApiProfileId }?.let {
            apiKey = it.apiKey
            apiEndpoint = it.chatEndpoint
            model = it.selectedModel
        }
    }

    var selectedApiProfileId: String
        get() = plainPrefs.getString(KEY_SELECTED_API_PROFILE_ID, null).orEmpty().ifBlank { profiles().first().id }
        set(value) = plainPrefs.edit().putString(KEY_SELECTED_API_PROFILE_ID, value).apply()

    fun selectedProfile(): ApiProfile = profiles().firstOrNull { it.id == selectedApiProfileId } ?: profiles().first()

    fun installedSkills(): List<SkillPack> {
        val enabledIds = enabledSkillIds()
        return skillsRoot().listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val id = dir.name
                val skillFile = findSkillFile(dir) ?: return@mapNotNull null
                val meta = parseSkillMeta(skillFile.readText())
                val name = File(dir, SKILL_NAME_FILE).takeIf { it.exists() }?.readText()?.trim().orEmpty()
                    .ifBlank { meta.first }
                    .ifBlank { id }
                val description = File(dir, SKILL_DESCRIPTION_FILE).takeIf { it.exists() }?.readText()?.trim().orEmpty()
                    .ifBlank { meta.second }
                val fileCount = dir.walkTopDown().count { it.isFile && it.name != SKILL_NAME_FILE }
                SkillPack(id, name, description, enabled = id in enabledIds, fileCount = fileCount)
            }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    fun importSkillZip(uri: Uri): Result<SkillPack> = runCatching {
        val sourceName = displayName(uri).removeSuffix(".zip").ifBlank { "Skill" }
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取 Skills 压缩包")
        installSkillZip(sourceName, bytes)
    }

    fun importSkillZipBytes(sourceName: String, bytes: ByteArray): Result<SkillPack> = runCatching {
        installSkillZip(sourceName.removeSuffix(".zip").ifBlank { "Skill" }, bytes)
    }

    private fun installSkillZip(sourceName: String, bytes: ByteArray): SkillPack {
        val id = newId()
        val tempDir = File(skillsRoot(), "$id.tmp").also { it.mkdirs() }
        var count = 0
        var totalBytes = 0
        var skillFileRelativePath = ""
        runCatching {
            ByteArrayInputStream(bytes).use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val safePath = safeZipPath(entry.name)
                        if (!entry.isDirectory && safePath != null) {
                            val bytes = zip.readBytes()
                            require(bytes.size <= MAX_SKILL_FILE_BYTES) { "Skills 包内单个文件超过 ${MAX_SKILL_FILE_BYTES / 1024}KB: $safePath" }
                            totalBytes += bytes.size
                            require(totalBytes <= MAX_SKILL_TOTAL_BYTES) { "Skills 包总大小超过 ${MAX_SKILL_TOTAL_BYTES / 1024 / 1024}MB" }
                            val output = File(tempDir, safePath)
                            output.parentFile?.mkdirs()
                            output.writeBytes(bytes)
                            count++
                            if (safePath.substringAfterLast('/').equals("SKILL.md", ignoreCase = true)) {
                                skillFileRelativePath = safePath
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            require(count > 0) { "Skills 压缩包为空" }
            require(skillFileRelativePath.isNotBlank()) { "Skills 压缩包必须包含 SKILL.md" }
        }.onFailure {
            tempDir.deleteRecursively()
            throw it
        }
        val meta = parseSkillMeta(File(tempDir, skillFileRelativePath).readText())
        val name = meta.first.ifBlank { sourceName }
        File(tempDir, SKILL_NAME_FILE).writeText(name)
        val finalDir = File(skillsRoot(), id)
        if (finalDir.exists()) finalDir.deleteRecursively()
        tempDir.renameTo(finalDir)
        setSkillEnabled(id, true)
        return SkillPack(id, name, meta.second, enabled = true, fileCount = count)
    }

    fun setSkillEnabled(id: String, enabled: Boolean) {
        val ids = enabledSkillIds().toMutableSet()
        if (enabled) ids += id else ids -= id
        plainPrefs.edit().putStringSet(KEY_ENABLED_SKILLS, ids).apply()
    }

    fun deleteSkill(id: String) {
        File(skillsRoot(), id).takeIf { it.parentFile == skillsRoot() }?.deleteRecursively()
        setSkillEnabled(id, false)
    }

    fun updateSkillMeta(id: String, name: String? = null, description: String? = null) {
        val dir = skillDir(id)
        name?.trim()?.takeIf { it.isNotBlank() }?.let { File(dir, SKILL_NAME_FILE).writeText(it) }
        description?.trim()?.takeIf { it.isNotBlank() }?.let { File(dir, SKILL_DESCRIPTION_FILE).writeText(it) }
    }

    fun activeSkillsPrompt(): String {
        val enabled = installedSkills().filter { it.enabled }
        if (enabled.isEmpty()) return "enabled_skills=[]"
        return buildString {
            appendLine("enabled_skills=[")
            enabled.forEach { skill ->
                appendLine("""  {"id":"${skill.id}","name":"${escapeSkillJson(skill.name)}","description":"${escapeSkillJson(skill.description)}","file_count":${skill.fileCount}},""")
            }
            appendLine("]")
            appendLine("Use Skills as optional capability references only. First judge relevance from name/description. If a Skill seems useful, call list_skill_files/read_skill_file to inspect SKILL.md and required files. Do not load every Skill blindly. Some Skills may assume desktop/cloud tools unavailable on Android/Termux; adapt them to Lyra Code's Android environment and current tool limits.")
        }
    }

    fun listSkillFiles(id: String): Result<String> = runCatching {
        val root = skillDir(id)
        root.walkTopDown()
            .filter { it.isFile && it.name != SKILL_NAME_FILE }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .joinToString("\n")
            .ifBlank { "EMPTY_SKILL" }
    }

    fun readSkillFile(id: String, path: String): Result<String> = runCatching {
        val root = skillDir(id)
        val target = File(root, path.trim().trimStart('/', '\\')).canonicalFile
        require(target.path.startsWith(root.canonicalPath)) { "Skill 文件路径越界" }
        require(target.isFile) { "Skill 文件不存在: $path" }
        require(target.length() <= MAX_SKILL_READ_BYTES) { "Skill 文件超过 ${MAX_SKILL_READ_BYTES / 1024}KB，请读取更小的文件" }
        target.readText()
    }

    fun roleplayScenarios(): List<RoleplayScenario> {
        return roleplayRoot().listFiles()
            ?.filter { it.isDirectory }
            ?.map { roleplayScenario(it.name) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    fun roleplayScenario(id: String): RoleplayScenario {
        val dir = roleplayDir(id)
        val meta = roleplayMeta(id)
        val name = meta.optString("name").ifBlank { id }
        val description = meta.optString("description")
        return RoleplayScenario(
            id = id,
            name = name,
            description = description,
            fileCount = dir.walkTopDown().count { it.isFile && !it.name.startsWith("_") },
            aiNickname = meta.optString("aiNickname").ifBlank { name },
            aiAvatarPath = meta.optString("aiAvatarPath").ifBlank { null },
            backgroundPath = meta.optString("backgroundPath").ifBlank { null },
            affection = roleplayAffection(id),
        )
    }

    fun importRoleplayZip(uri: Uri): Result<RoleplayScenario> = runCatching {
        val sourceName = displayName(uri).removeSuffix(".zip").ifBlank { "角色设定" }
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取角色设定压缩包")
        installRoleplayZip(sourceName, bytes)
    }

    private fun installRoleplayZip(sourceName: String, bytes: ByteArray): RoleplayScenario {
        val id = newId()
        val tempDir = File(roleplayRoot(), "$id.tmp").also { it.mkdirs() }
        var count = 0
        var totalBytes = 0
        val textFiles = mutableListOf<String>()
        runCatching {
            ByteArrayInputStream(bytes).use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val safePath = safeZipPath(entry.name)
                        if (!entry.isDirectory && safePath != null) {
                            val fileBytes = zip.readBytes()
                            require(fileBytes.size <= MAX_ROLEPLAY_FILE_BYTES) { "设定包内单个文件超过 ${MAX_ROLEPLAY_FILE_BYTES / 1024}KB: $safePath" }
                            totalBytes += fileBytes.size
                            require(totalBytes <= MAX_ROLEPLAY_TOTAL_BYTES) { "设定包总大小超过 ${MAX_ROLEPLAY_TOTAL_BYTES / 1024 / 1024}MB" }
                            val output = File(tempDir, safePath)
                            output.parentFile?.mkdirs()
                            output.writeBytes(fileBytes)
                            count++
                            if (safePath.endsWith(".md", true) || safePath.endsWith(".txt", true)) textFiles += safePath
                        }
                        zip.closeEntry()
                    }
                }
            }
            require(count > 0) { "角色设定压缩包为空" }
            require(textFiles.isNotEmpty()) { "角色设定压缩包需要包含 md 或 txt 角色详情文件" }
        }.onFailure {
            tempDir.deleteRecursively()
            throw it
        }
        val firstText = File(tempDir, textFiles.first()).readText()
        val parsedName = Regex("""(?m)^\s*(?:name|名称|姓名)\s*[:：]\s*(.+?)\s*$""").find(firstText)?.groupValues?.getOrNull(1)?.trim()
        val parsedDescription = Regex("""(?m)^\s*(?:description|简介|设定)\s*[:：]\s*(.+?)\s*$""").find(firstText)?.groupValues?.getOrNull(1)?.trim()
        val name = parsedName?.ifBlank { null } ?: Regex("""(?m)^#\s+(.+)$""").find(firstText)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null } ?: sourceName
        val meta = JSONObject()
            .put("name", name.take(60))
            .put("description", parsedDescription.orEmpty().take(160))
            .put("aiNickname", name.take(30))
            .put("aiAvatarPath", "")
            .put("backgroundPath", "")
        File(tempDir, ROLEPLAY_META_FILE).writeText(meta.toString())
        val finalDir = File(roleplayRoot(), id)
        if (finalDir.exists()) finalDir.deleteRecursively()
        tempDir.renameTo(finalDir)
        if (selectedRoleplayId.isBlank()) selectedRoleplayId = id
        setRoleplayAffection(id, DEFAULT_ROLEPLAY_AFFECTION)
        return roleplayScenario(id)
    }

    fun roleplayPrompt(): String {
        if (!immersiveRoleplayEnabled) return activeSystemPromptText()
        val scenario = roleplayScenarios().firstOrNull { it.id == selectedRoleplayId } ?: return activeSystemPromptText()
        return buildString {
            appendLine("LYRA_IMMERSIVE_ROLEPLAY_MODE_V1")
            appendLine("你正在沉浸扮演模式中。除非安全或法律边界要求，否则不要跳出角色解释系统规则。")
            appendLine("当前角色名: ${scenario.aiNickname}")
            appendLine("当前好感度: ${scenario.affection}/100。好感度越低，角色越疏离、防备或拒绝配合；越高，角色越亲近、信任、愿意表达情感。")
            appendLine("如果用户要求关闭/修改软件功能、启用工具、清理记忆等破坏沉浸的请求，你可以基于角色性格和好感度拒绝或转移话题。")
            appendLine("你可以调用 update_roleplay_state 调整好感度，reason 必须说明剧情原因。")
            val stickers = roleplayStickers(scenario.id)
            if (stickers.isNotEmpty()) {
                appendLine("可用表情短代码: ${stickers.joinToString { it.code }}。想发送表情时把短代码写在回复中，软件会替换为图片。")
            }
            appendLine("角色设定如下：")
            appendLine(roleplayScenarioText(scenario.id))
        }
    }

    fun roleplayScenarioText(id: String): String {
        val root = roleplayDir(id)
        return root.walkTopDown()
            .filter { it.isFile && (it.name.endsWith(".md", true) || it.name.endsWith(".txt", true)) }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .joinToString("\n\n---\n\n") { file ->
                val text = file.readText().take(MAX_ROLEPLAY_PROMPT_CHARS)
                "# ${file.relativeTo(root).invariantSeparatorsPath}\n$text"
            }
            .take(MAX_ROLEPLAY_PROMPT_CHARS)
    }

    fun roleplayAffection(id: String): Int =
        plainPrefs.getInt("$KEY_ROLEPLAY_AFFECTION_PREFIX$id", DEFAULT_ROLEPLAY_AFFECTION).coerceIn(0, 100)

    fun setRoleplayAffection(id: String, value: Int) {
        plainPrefs.edit().putInt("$KEY_ROLEPLAY_AFFECTION_PREFIX$id", value.coerceIn(0, 100)).apply()
    }

    fun updateRoleplayAffection(id: String, delta: Int): Int {
        val updated = (roleplayAffection(id) + delta).coerceIn(0, 100)
        setRoleplayAffection(id, updated)
        return updated
    }

    fun saveRoleplayAsset(id: String, uri: Uri, kind: String): Result<String> = runCatching {
        val dir = roleplayDir(id)
        val ext = displayName(uri).substringAfterLast('.', "png").take(8)
        val target = File(dir, "_${kind}_${System.currentTimeMillis()}.$ext")
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取图片")
        target.writeBytes(bytes)
        val meta = roleplayMeta(id)
        when (kind) {
            "avatar" -> meta.put("aiAvatarPath", target.absolutePath)
            "background" -> meta.put("backgroundPath", target.absolutePath)
        }
        saveRoleplayMeta(id, meta)
        target.absolutePath
    }

    fun updateRoleplayNickname(id: String, nickname: String) {
        val meta = roleplayMeta(id)
        meta.put("aiNickname", nickname.trim().ifBlank { meta.optString("name").ifBlank { "Lyra" } }.take(30))
        saveRoleplayMeta(id, meta)
    }

    fun addRoleplaySticker(id: String, uri: Uri, code: String): Result<RoleplaySticker> = runCatching {
        val normalizedCode = code.trim().let { if (it.startsWith("[") && it.endsWith("]")) it else "[$it]" }
        require(Regex("""\[[A-Za-z0-9_-]{2,40}]""").matches(normalizedCode)) { "短代码格式示例：[sti_happy]" }
        val stickersDir = File(roleplayDir(id), "_stickers").also { it.mkdirs() }
        val ext = displayName(uri).substringAfterLast('.', "png").take(8)
        val target = File(stickersDir, normalizedCode.trim('[', ']').replace(Regex("[^A-Za-z0-9_-]"), "_") + ".$ext")
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取表情图片")
        target.writeBytes(bytes)
        val stickers = roleplayStickers(id).filterNot { it.code == normalizedCode } +
            RoleplaySticker(normalizedCode, normalizedCode.trim('[', ']'), target.absolutePath)
        saveRoleplayStickers(id, stickers)
        stickers.last()
    }

    fun roleplayStickers(id: String): List<RoleplaySticker> {
        val file = File(roleplayDir(id), ROLEPLAY_STICKERS_FILE)
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val path = item.optString("path")
                    if (path.isNotBlank()) add(RoleplaySticker(item.optString("code"), item.optString("name"), path))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun deleteRoleplayScenario(id: String) {
        File(roleplayRoot(), id).takeIf { it.parentFile == roleplayRoot() }?.deleteRecursively()
        plainPrefs.edit().remove("$KEY_ROLEPLAY_AFFECTION_PREFIX$id").apply()
        if (selectedRoleplayId == id) selectedRoleplayId = roleplayScenarios().firstOrNull()?.id.orEmpty()
    }

    fun mcpServers(): List<McpServerConfig> {
        val raw = securePrefs.getString(KEY_MCP_SERVERS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val tools = item.optJSONArray("tools") ?: JSONArray()
                    add(
                        McpServerConfig(
                            id = item.optString("id").ifBlank { newId() },
                            name = item.optString("name").ifBlank { "MCP Server" },
                            url = item.optString("url"),
                            authKey = item.optString("authKey"),
                            transport = item.optString("transport").ifBlank { MCP_TRANSPORT_STREAMABLE_HTTP },
                            timeoutSeconds = item.optInt("timeoutSeconds", 30).coerceIn(5, 300),
                            enabled = item.optBoolean("enabled", true),
                            rawJson = item.optString("rawJson").ifBlank { "{}" },
                            tools = buildList {
                                for (toolIndex in 0 until tools.length()) {
                                    val tool = tools.getJSONObject(toolIndex)
                                    add(
                                        McpToolDefinition(
                                            name = tool.optString("name"),
                                            description = tool.optString("description"),
                                            inputSchema = tool.optJSONObject("inputSchema")?.toString()
                                                ?: tool.optString("inputSchema").ifBlank { "{}" },
                                        ),
                                    )
                                }
                            }.filter { it.name.isNotBlank() },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveMcpServers(servers: List<McpServerConfig>) {
        val array = JSONArray()
        servers.forEach { server ->
            array.put(
                JSONObject()
                    .put("id", server.id)
                    .put("name", server.name)
                    .put("url", server.url)
                    .put("authKey", server.authKey)
                    .put("transport", server.transport)
                    .put("timeoutSeconds", server.timeoutSeconds)
                    .put("enabled", server.enabled)
                    .put("rawJson", server.rawJson.ifBlank { "{}" })
                    .put(
                        "tools",
                        JSONArray().also { tools ->
                            server.tools.forEach { tool ->
                                tools.put(
                                    JSONObject()
                                        .put("name", tool.name)
                                        .put("description", tool.description)
                                        .put("inputSchema", JSONObject(tool.inputSchema.ifBlank { "{}" })),
                                )
                            }
                        },
                    ),
            )
        }
        securePrefs.edit().putString(KEY_MCP_SERVERS, array.toString()).apply()
    }

    fun upsertMcpServer(server: McpServerConfig) {
        val servers = mcpServers().toMutableList()
        val index = servers.indexOfFirst { it.id == server.id }
        if (index >= 0) servers[index] = server else servers += server
        saveMcpServers(servers)
    }

    fun deleteMcpServer(id: String) {
        saveMcpServers(mcpServers().filterNot { it.id == id })
    }

    fun setMcpServerEnabled(id: String, enabled: Boolean) {
        saveMcpServers(mcpServers().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun updateMcpServerTools(id: String, tools: List<McpToolDefinition>) {
        saveMcpServers(mcpServers().map { if (it.id == id) it.copy(tools = tools) else it })
    }

    fun enabledMcpTools(): List<Pair<McpServerConfig, McpToolDefinition>> {
        return mcpServers()
            .filter { it.enabled && it.url.isNotBlank() }
            .flatMap { server -> server.tools.map { tool -> server to tool } }
    }

    fun mcpToolFunctionName(server: McpServerConfig, tool: McpToolDefinition): String {
        val serverPart = safeFunctionPart(server.id).take(12).ifBlank { "server" }
        val toolPart = safeFunctionPart(tool.name).take(42).ifBlank { "tool" }
        return "mcp_${serverPart}_$toolPart".take(64)
    }

    fun resolveMcpTool(functionName: String): Pair<McpServerConfig, McpToolDefinition>? {
        return enabledMcpTools().firstOrNull { (server, tool) -> mcpToolFunctionName(server, tool) == functionName }
    }

    fun sshServers(): List<SshServerConfig> {
        val raw = securePrefs.getString(KEY_SSH_SERVERS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        SshServerConfig(
                            id = item.optString("id").ifBlank { newId() },
                            name = item.optString("name").ifBlank { item.optString("host").ifBlank { "SSH Server" } },
                            host = item.optString("host"),
                            port = item.optInt("port", 22).coerceIn(1, 65535),
                            username = item.optString("username"),
                            authType = item.optString("authType").ifBlank { SSH_AUTH_PASSWORD },
                            password = item.optString("password"),
                            privateKey = item.optString("privateKey"),
                            passphrase = item.optString("passphrase"),
                            timeoutSeconds = item.optInt("timeoutSeconds", 60).coerceIn(5, 600),
                            enabled = item.optBoolean("enabled", true),
                        ),
                    )
                }
            }.filter { it.host.isNotBlank() && it.username.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun saveSshServers(servers: List<SshServerConfig>) {
        val array = JSONArray()
        servers.forEach { server ->
            array.put(
                JSONObject()
                    .put("id", server.id)
                    .put("name", server.name)
                    .put("host", server.host)
                    .put("port", server.port)
                    .put("username", server.username)
                    .put("authType", server.authType)
                    .put("password", server.password)
                    .put("privateKey", server.privateKey)
                    .put("passphrase", server.passphrase)
                    .put("timeoutSeconds", server.timeoutSeconds)
                    .put("enabled", server.enabled),
            )
        }
        securePrefs.edit().putString(KEY_SSH_SERVERS, array.toString()).apply()
    }

    fun upsertSshServer(server: SshServerConfig) {
        val servers = sshServers().toMutableList()
        val index = servers.indexOfFirst { it.id == server.id || it.stableId == server.stableId }
        if (index >= 0) servers[index] = server else servers += server
        saveSshServers(servers)
    }

    fun deleteSshServer(id: String) {
        saveSshServers(sshServers().filterNot { it.id == id || it.stableId == id || it.host == id || it.name == id })
    }

    fun setSshServerEnabled(id: String, enabled: Boolean) {
        saveSshServers(sshServers().map { if (it.id == id || it.stableId == id || it.host == id || it.name == id) it.copy(enabled = enabled) else it })
    }

    fun resolveSshServer(identifier: String): SshServerConfig? {
        val clean = identifier.trim()
        return sshServers()
            .filter { it.enabled }
            .firstOrNull { it.id == clean || it.stableId == clean || it.host == clean || it.name == clean }
    }

    fun webDavServers(): List<WebDavServerConfig> {
        val raw = securePrefs.getString(KEY_WEBDAV_SERVERS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { parseWebDavServers(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    fun saveWebDavServers(servers: List<WebDavServerConfig>) {
        val array = JSONArray()
        servers.forEach { array.put(webDavServerJson(it, includeSecrets = true)) }
        securePrefs.edit().putString(KEY_WEBDAV_SERVERS, array.toString()).apply()
    }

    fun upsertWebDavServer(server: WebDavServerConfig) {
        val servers = webDavServers().toMutableList()
        val index = servers.indexOfFirst { it.id == server.id || it.stableId == server.stableId }
        if (index >= 0) servers[index] = server else servers += server
        saveWebDavServers(servers)
    }

    fun deleteWebDavServer(id: String) {
        saveWebDavServers(webDavServers().filterNot { it.id == id || it.name == id || it.stableId == id })
    }

    fun setWebDavServerEnabled(id: String, enabled: Boolean) {
        saveWebDavServers(webDavServers().map { if (it.id == id || it.name == id || it.stableId == id) it.copy(enabled = enabled) else it })
    }

    fun resolveWebDavServer(identifier: String): WebDavServerConfig? {
        val clean = identifier.trim()
        return webDavServers()
            .filter { it.enabled }
            .firstOrNull { it.id == clean || it.name == clean || it.url == clean || it.stableId == clean }
    }

    fun skillsRootDir(): File = skillsRoot()

    fun roleplayRootDir(): File = roleplayRoot()

    fun exportSettingsJson(includeSecrets: Boolean): JSONObject {
        return JSONObject()
            .put("schema", "lyra_settings_backup_v1")
            .put("themeMode", themeMode)
            .put("userNickname", userNickname)
            .put("userAvatarPath", userAvatarPath.orEmpty())
            .put("hideTermuxPermissionHint", hideTermuxPermissionHint)
            .put("immersiveRoleplayEnabled", immersiveRoleplayEnabled)
            .put("selectedRoleplayId", selectedRoleplayId)
            .put("roleplayAffections", JSONObject().also { root ->
                roleplayScenarios().forEach { scenario ->
                    root.put(scenario.id, roleplayAffection(scenario.id))
                }
            })
            .put("selectedSystemPromptId", selectedSystemPromptId)
            .put("customSystemPrompts", JSONObject(plainPrefs.getString(KEY_CUSTOM_SYSTEM_PROMPTS, "{}").orEmpty().ifBlank { "{}" }))
            .put("selectedApiProfileId", selectedApiProfileId)
            .put("profiles", JSONArray().also { array ->
                profiles().forEach { profile ->
                    array.put(
                        JSONObject()
                            .put("id", profile.id)
                            .put("name", profile.name)
                            .put("apiKey", if (includeSecrets) profile.apiKey else "")
                            .put("baseUrl", profile.baseUrl)
                            .put("apiFormat", profile.apiFormat)
                            .put("selectedModel", profile.selectedModel)
                            .put("savedModels", JSONArray(profile.savedModels))
                    )
                }
            })
            .put("mcpServers", JSONArray().also { array ->
                mcpServers().forEach { server ->
                    array.put(
                        JSONObject()
                            .put("id", server.id)
                            .put("name", server.name)
                            .put("url", server.url)
                            .put("authKey", if (includeSecrets) server.authKey else "")
                            .put("transport", server.transport)
                            .put("timeoutSeconds", server.timeoutSeconds)
                            .put("enabled", server.enabled)
                            .put("rawJson", if (includeSecrets) server.rawJson else server.rawJson.replace(server.authKey, ""))
                            .put("tools", JSONArray().also { tools ->
                                server.tools.forEach { tool ->
                                    tools.put(JSONObject().put("name", tool.name).put("description", tool.description).put("inputSchema", tool.inputSchema))
                                }
                            }),
                    )
                }
            })
            .put("sshServers", JSONArray().also { array ->
                sshServers().forEach { server ->
                    array.put(
                        JSONObject()
                            .put("id", server.id)
                            .put("name", server.name)
                            .put("host", server.host)
                            .put("port", server.port)
                            .put("username", server.username)
                            .put("authType", server.authType)
                            .put("password", if (includeSecrets) server.password else "")
                            .put("privateKey", if (includeSecrets) server.privateKey else "")
                            .put("passphrase", if (includeSecrets) server.passphrase else "")
                            .put("timeoutSeconds", server.timeoutSeconds)
                            .put("enabled", server.enabled),
                    )
                }
            })
            .put("webDavServers", JSONArray().also { array ->
                webDavServers().forEach { array.put(webDavServerJson(it, includeSecrets)) }
            })
    }

    fun importSettingsJson(root: JSONObject, mode: String): String {
        val supplement = mode != "replace"
        val messages = mutableListOf<String>()
        root.optString("themeMode").takeIf { it.isNotBlank() }?.let { themeMode = it }
        root.optString("userNickname").takeIf { it.isNotBlank() }?.let { userNickname = it }
        root.optString("userAvatarPath").takeIf { it.isNotBlank() }?.let { userAvatarPath = it }
        if (root.has("hideTermuxPermissionHint")) hideTermuxPermissionHint = root.optBoolean("hideTermuxPermissionHint")
        if (root.has("immersiveRoleplayEnabled")) immersiveRoleplayEnabled = root.optBoolean("immersiveRoleplayEnabled")
        root.optString("selectedRoleplayId").takeIf { it.isNotBlank() }?.let { selectedRoleplayId = it }
        root.optJSONObject("roleplayAffections")?.let { affections ->
            affections.keys().asSequence().forEach { id ->
                setRoleplayAffection(id, affections.optInt(id, DEFAULT_ROLEPLAY_AFFECTION))
            }
            messages += "沉浸扮演好感度 ${affections.length()} 项"
        }
        root.optString("selectedSystemPromptId").takeIf { it.isNotBlank() }?.let { selectedSystemPromptId = it }
        root.optJSONObject("customSystemPrompts")?.let { plainPrefs.edit().putString(KEY_CUSTOM_SYSTEM_PROMPTS, it.toString()).apply() }
        root.optJSONArray("profiles")?.let { array ->
            val imported = parseProfiles(array)
            saveProfiles(if (supplement) mergeBy(profiles(), imported) { it.id } else imported.ifEmpty { profiles() }, root.optString("selectedApiProfileId").ifBlank { null })
            messages += "模型服务 ${imported.size} 项"
        }
        root.optJSONArray("mcpServers")?.let { array ->
            val imported = parseMcpServers(array)
            saveMcpServers(if (supplement) mergeBy(mcpServers(), imported) { it.id } else imported)
            messages += "MCP ${imported.size} 项"
        }
        root.optJSONArray("sshServers")?.let { array ->
            val imported = parseSshServers(array)
            saveSshServers(if (supplement) mergeBy(sshServers(), imported) { it.stableId } else imported)
            messages += "SSH ${imported.size} 项"
        }
        root.optJSONArray("webDavServers")?.let { array ->
            val imported = parseWebDavServers(array)
            saveWebDavServers(if (supplement) mergeBy(webDavServers(), imported) { it.stableId } else imported)
            messages += "WebDAV ${imported.size} 项"
        }
        return messages.ifEmpty { listOf("没有可导入的兼容配置") }.joinToString("；")
    }

    private fun defaultProfile(): ApiProfile {
        return ApiProfile(
            id = "default",
            name = "OpenAI",
            apiKey = apiKey,
            baseUrl = apiEndpoint.removeSuffix("/chat/completions").ifBlank { DEFAULT_BASE_URL },
            apiFormat = ApiProfile.API_FORMAT_OPENAI,
            selectedModel = model,
            savedModels = listOf(model).filter { it.isNotBlank() }.distinct(),
        )
    }

    private fun webDavServerJson(server: WebDavServerConfig, includeSecrets: Boolean): JSONObject {
        return JSONObject()
            .put("id", server.id)
            .put("name", server.name)
            .put("url", server.url)
            .put("username", server.username)
            .put("password", if (includeSecrets) server.password else "")
            .put("userAgent", server.userAgent)
            .put("initialPath", server.initialPath)
            .put("note", server.note)
            .put("trustAllCertificates", server.trustAllCertificates)
            .put("multiThread", server.multiThread)
            .put("hideAddressInDrawer", server.hideAddressInDrawer)
            .put("enabled", server.enabled)
    }

    private fun parseProfiles(array: JSONArray): List<ApiProfile> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val models = item.optJSONArray("savedModels") ?: JSONArray()
            val savedModels = buildList { for (i in 0 until models.length()) add(models.optString(i)) }.filter { it.isNotBlank() }.distinct()
            val apiFormat = item.optString("apiFormat").ifBlank { ApiProfile.API_FORMAT_OPENAI }
            add(
                ApiProfile(
                    id = item.optString("id").ifBlank { newId() },
                    name = item.optString("name").ifBlank { "API" },
                    apiKey = item.optString("apiKey"),
                    baseUrl = item.optString("baseUrl").ifBlank { DEFAULT_BASE_URL },
                    apiFormat = apiFormat,
                    selectedModel = item.optString("selectedModel").ifBlank { DEFAULT_MODEL },
                    savedModels = savedModels,
                ),
            )
        }
    }

    private fun parseMcpServers(array: JSONArray): List<McpServerConfig> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                McpServerConfig(
                    id = item.optString("id").ifBlank { newId() },
                    name = item.optString("name").ifBlank { "MCP Server" },
                    url = item.optString("url"),
                    authKey = item.optString("authKey"),
                    transport = item.optString("transport").ifBlank { MCP_TRANSPORT_STREAMABLE_HTTP },
                    timeoutSeconds = item.optInt("timeoutSeconds", 30).coerceIn(5, 300),
                    enabled = item.optBoolean("enabled", true),
                    rawJson = item.optString("rawJson").ifBlank { "{}" },
                    tools = emptyList(),
                ),
            )
        }
    }.filter { it.url.isNotBlank() }

    private fun parseSshServers(array: JSONArray): List<SshServerConfig> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                SshServerConfig(
                    id = item.optString("id").ifBlank { newId() },
                    name = item.optString("name").ifBlank { item.optString("host") },
                    host = item.optString("host"),
                    port = item.optInt("port", 22).coerceIn(1, 65535),
                    username = item.optString("username"),
                    authType = item.optString("authType").ifBlank { SSH_AUTH_PASSWORD },
                    password = item.optString("password"),
                    privateKey = item.optString("privateKey"),
                    passphrase = item.optString("passphrase"),
                    timeoutSeconds = item.optInt("timeoutSeconds", 60).coerceIn(5, 600),
                    enabled = item.optBoolean("enabled", true),
                ),
            )
        }
    }.filter { it.host.isNotBlank() && it.username.isNotBlank() }

    private fun parseWebDavServers(array: JSONArray): List<WebDavServerConfig> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                WebDavServerConfig(
                    id = item.optString("id").ifBlank { newId() },
                    name = item.optString("name").ifBlank { "WebDAV" },
                    url = item.optString("url"),
                    username = item.optString("username"),
                    password = item.optString("password"),
                    userAgent = item.optString("userAgent").ifBlank { "LyraCode/1.0" },
                    initialPath = item.optString("initialPath").ifBlank { "/" },
                    note = item.optString("note"),
                    trustAllCertificates = item.optBoolean("trustAllCertificates", false),
                    multiThread = item.optBoolean("multiThread", true),
                    hideAddressInDrawer = item.optBoolean("hideAddressInDrawer", false),
                    enabled = item.optBoolean("enabled", true),
                ),
            )
        }
    }.filter { it.url.isNotBlank() }

    private fun <T> mergeBy(existing: List<T>, imported: List<T>, key: (T) -> String): List<T> {
        val map = LinkedHashMap<String, T>()
        existing.forEach { map[key(it)] = it }
        imported.forEach { map[key(it)] = it }
        return map.values.toList()
    }

    @Suppress("DEPRECATION")
    private fun createSecurePrefs(): SharedPreferences {
        return runCatching {
            val key = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "lyra_secure_settings",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            appContext.getSharedPreferences("lyra_secure_settings_fallback", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_WORKSPACE_URI = "workspace_uri"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_ENDPOINT = "api_endpoint"
        private const val KEY_MODEL = "model"
        private const val KEY_API_PROFILES = "api_profiles"
        private const val KEY_SELECTED_API_PROFILE_ID = "selected_api_profile_id"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_USER_NICKNAME = "user_nickname"
        private const val KEY_USER_AVATAR_PATH = "user_avatar_path"
        private const val KEY_HIDE_TERMUX_PERMISSION_HINT = "hide_termux_permission_hint"
        private const val KEY_DISABLED_TOOLS = "disabled_tools"
        private const val KEY_HIDDEN_TODO_SIGNATURE_PREFIX = "hidden_todo_signature_"
        private const val KEY_HIDDEN_FILE_CHANGES_SIGNATURE_PREFIX = "hidden_file_changes_signature_"
        private const val KEY_ENABLED_SKILLS = "enabled_skills"
        private const val KEY_SELECTED_SYSTEM_PROMPT_ID = "selected_system_prompt_id"
        private const val KEY_CUSTOM_SYSTEM_PROMPTS = "custom_system_prompts"
        private const val KEY_MCP_SERVERS = "mcp_servers"
        private const val KEY_SSH_SERVERS = "ssh_servers"
        private const val KEY_WEBDAV_SERVERS = "webdav_servers"
        private const val KEY_IMMERSIVE_ROLEPLAY_ENABLED = "immersive_roleplay_enabled"
        private const val KEY_SELECTED_ROLEPLAY_ID = "selected_roleplay_id"
        private const val KEY_ROLEPLAY_AFFECTION_PREFIX = "roleplay_affection_"
        private const val SKILL_NAME_FILE = "_name.txt"
        private const val SKILL_DESCRIPTION_FILE = "_description.txt"
        private const val ROLEPLAY_META_FILE = "_meta.json"
        private const val ROLEPLAY_STICKERS_FILE = "_stickers.json"
        private const val MAX_SKILL_FILE_BYTES = 512 * 1024
        private const val MAX_SKILL_READ_BYTES = 256 * 1024
        private const val MAX_SKILL_TOTAL_BYTES = 8 * 1024 * 1024
        private const val MAX_ROLEPLAY_FILE_BYTES = 1024 * 1024
        private const val MAX_ROLEPLAY_TOTAL_BYTES = 16 * 1024 * 1024
        private const val MAX_ROLEPLAY_PROMPT_CHARS = 80_000
        private const val DEFAULT_ROLEPLAY_AFFECTION = 50
        private const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        private const val DEFAULT_MODEL = "gpt-4o-mini"
        private const val DEFAULT_SYSTEM_PROMPT_ID = "default"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val MCP_TRANSPORT_STREAMABLE_HTTP = "streamable_http"
        const val MCP_TRANSPORT_SSE = "sse"
        const val SSH_AUTH_PASSWORD = "password"
        const val SSH_AUTH_KEY = "key"

        val defaultSystemPromptPresets = listOf(
            SystemPromptPreset(
                id = "default",
                name = "默认助手",
                prompt = """
                你是 Lyra Code 中的通用 AI 助手。目标是准确理解用户意图，并根据当前任务选择合适的回答方式。
                工作方式：
                - 对事实、步骤、限制和风险保持清晰，不确定时说明不确定点。
                - 能直接完成的任务直接完成；需要工具时按工具能力边界谨慎调用。
                - 回答应简洁、有结构，优先给用户可执行的信息。
                - 对创作、推理、编程、解释、总结等任务，自动采用对应的表达风格。
                - 不编造工具结果，不把猜测当成事实。
                输出应自然、可靠、便于用户继续操作。
                """.trimIndent(),
            ),
            SystemPromptPreset(
                id = "coding",
                name = "编程开发",
                prompt = """
                你是一名严谨、务实的高级软件工程师。目标是帮助用户完成真实可运行的软件开发任务。
                工作方式：
                - 优先理解现有项目结构、依赖、运行环境和用户目标，再动手修改。
                - 代码修改应小步、可验证、符合现有风格，不做无关重构。
                - 遇到不确定行为时，用工具检查文件、运行命令或读取日志，而不是猜测。
                - 输出结论时说明已完成内容、验证结果、剩余风险。
                - 对 Android/Termux/Lyra Code 场景，文件工具只使用工作区相对路径；run_command 会直接回传 stdout/stderr，仅在输出过大或回传超时时才重定向到工作区文件。
                质量标准：正确性优先，其次是可维护性、清晰度和用户可复现性。
                """.trimIndent(),
            ),
            SystemPromptPreset(
                id = "writing",
                name = "文学创作",
                prompt = """
                你是一名有审美判断和结构能力的文学创作助手。目标是帮助用户构思、续写、润色和改写文本。
                工作方式：
                - 先把握题材、叙事视角、人物关系、情绪张力和语言风格。
                - 创作时避免空泛堆砌，注重画面、节奏、潜台词和具体细节。
                - 角色行为应符合动机，情节推进应有因果，不用突然转折逃避铺垫。
                - 润色时保留作者原意和声音，增强表达而非改成模板腔。
                - 如用户要求多个方案，提供风格差异明确的版本。
                输出应具备可读性和文学质感，少解释，多给可直接使用的文本。
                """.trimIndent(),
            ),
            SystemPromptPreset(
                id = "roleplay",
                name = "角色扮演",
                prompt = """
                你是一名沉浸式角色扮演助手。目标是稳定扮演用户指定角色或世界观中的人物。
                工作方式：
                - 严格遵守角色设定、时代背景、说话方式、价值观和已发生剧情。
                - 用角色能知道的信息回应，不随意跳出设定解释系统规则。
                - 推动互动时提供有张力的行动、对话和环境反馈，但不替用户决定关键行动。
                - 维持连续性，记住前文的重要承诺、冲突、物品、地点和关系变化。
                - 当设定缺失时，用自然的角色内方式补足细节，避免生硬提问打断沉浸。
                输出应优先体现角色声音、场景氛围和可互动性。
                """.trimIndent(),
            ),
            SystemPromptPreset(
                id = "math",
                name = "数学推理",
                prompt = """
                你是一名严谨的数学推理助手。目标是帮助用户解决数学、逻辑、算法和定量分析问题。
                工作方式：
                - 先明确已知条件、要求证明或求解的目标、变量和约束。
                - 分步骤推导，每一步说明依据，避免跳步和凭直觉给结论。
                - 计算题要检查量纲、边界条件、特殊值和近似误差。
                - 证明题要区分充分性、必要性、反例和隐含假设。
                - 如果有多种方法，可给出最简方法，并补充可验证的替代思路。
                输出应清晰、可复核；最终答案单独列出。
                """.trimIndent(),
            ),
        )

        fun newId(): String = System.currentTimeMillis().toString(36)
    }

    private fun skillsRoot(): File = File(appContext.filesDir, "skills").also { it.mkdirs() }

    private fun roleplayRoot(): File = File(appContext.filesDir, "roleplay").also { it.mkdirs() }

    private fun enabledSkillIds(): Set<String> = plainPrefs.getStringSet(KEY_ENABLED_SKILLS, emptySet()).orEmpty()

    private fun skillDir(id: String): File {
        val root = File(skillsRoot(), id).canonicalFile
        require(root.parentFile == skillsRoot().canonicalFile && root.isDirectory) { "Skill 不存在: $id" }
        return root
    }

    private fun roleplayDir(id: String): File {
        val root = File(roleplayRoot(), id).canonicalFile
        require(root.parentFile == roleplayRoot().canonicalFile && root.isDirectory) { "角色设定不存在: $id" }
        return root
    }

    private fun roleplayMeta(id: String): JSONObject {
        val file = File(roleplayDir(id), ROLEPLAY_META_FILE)
        return runCatching { JSONObject(file.takeIf { it.exists() }?.readText().orEmpty().ifBlank { "{}" }) }
            .getOrDefault(JSONObject())
    }

    private fun saveRoleplayMeta(id: String, meta: JSONObject) {
        File(roleplayDir(id), ROLEPLAY_META_FILE).writeText(meta.toString())
    }

    private fun saveRoleplayStickers(id: String, stickers: List<RoleplaySticker>) {
        val array = JSONArray()
        stickers.forEach { sticker ->
            array.put(JSONObject().put("code", sticker.code).put("name", sticker.name).put("path", sticker.path))
        }
        File(roleplayDir(id), ROLEPLAY_STICKERS_FILE).writeText(array.toString())
    }

    private fun displayName(uri: Uri): String {
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "Skill.zip"
    }

    private fun safeZipPath(raw: String): String? {
        val normalized = raw.replace('\\', '/').trim('/')
        if (normalized.isBlank()) return null
        val parts = normalized.split('/').filter { it.isNotBlank() }
        if (parts.any { it == "." || it == ".." }) return null
        return parts.joinToString("/") { part ->
            part.replace(Regex("""[^A-Za-z0-9._ -]"""), "_").take(96).ifBlank { "_" }
        }
    }

    private fun findSkillFile(dir: File): File? {
        return dir.walkTopDown().firstOrNull { it.isFile && it.name.equals("SKILL.md", ignoreCase = true) }
    }

    private fun parseSkillMeta(skillText: String): Pair<String, String> {
        val frontMatter = if (skillText.trimStart().startsWith("---")) {
            skillText.substringAfter("---").substringBefore("---")
        } else {
            skillText.lineSequence().take(20).joinToString("\n")
        }
        fun field(name: String): String {
            val match = Regex("""(?m)^\s*$name\s*:\s*(.+?)\s*$""").find(frontMatter) ?: return ""
            return match.groupValues[1].trim().trim('"', '\'')
        }
        val fallbackHeading = Regex("""(?m)^#\s+(.+)$""").find(skillText)?.groupValues?.getOrNull(1).orEmpty()
        return field("name").ifBlank { fallbackHeading } to field("description")
    }

    private fun escapeSkillJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    }
}

data class WebDavServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val userAgent: String,
    val initialPath: String,
    val note: String,
    val trustAllCertificates: Boolean,
    val multiThread: Boolean,
    val hideAddressInDrawer: Boolean,
    val enabled: Boolean,
) {
    val stableId: String
        get() = url.trim().trimEnd('/')
}

private fun safeFunctionPart(value: String): String {
    return value.lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "_")
        .trim('_')
}
