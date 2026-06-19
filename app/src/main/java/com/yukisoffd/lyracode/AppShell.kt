package com.yukisoffd.lyracode

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.provider.MediaStore
import android.util.Base64
import android.widget.MediaController
import android.widget.VideoView
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.yukisoffd.lyracode.ai.ChatRecord
import com.yukisoffd.lyracode.ai.AiResponseCache
import com.yukisoffd.lyracode.ai.OpenAiAgent
import com.yukisoffd.lyracode.ai.TodoItem
import com.yukisoffd.lyracode.ai.WebViewWebAgent
import com.yukisoffd.lyracode.data.ApiProfile
import com.yukisoffd.lyracode.data.AppSettings
import com.yukisoffd.lyracode.data.AuditEntry
import com.yukisoffd.lyracode.data.AuditLogStore
import com.yukisoffd.lyracode.data.BackupManager
import com.yukisoffd.lyracode.data.BackupOptions
import com.yukisoffd.lyracode.data.Conversation
import com.yukisoffd.lyracode.data.ConversationStore
import com.yukisoffd.lyracode.data.McpServerConfig
import com.yukisoffd.lyracode.data.McpToolDefinition
import com.yukisoffd.lyracode.data.SkillPack
import com.yukisoffd.lyracode.data.SshServerConfig
import com.yukisoffd.lyracode.data.UpdateManager
import com.yukisoffd.lyracode.data.WebDavServerConfig
import com.yukisoffd.lyracode.filetransfer.FileTransferClient
import com.yukisoffd.lyracode.mcp.LocalMcpServerManager
import com.yukisoffd.lyracode.mcp.McpClientManager
import com.yukisoffd.lyracode.server.MiniServerManager
import com.yukisoffd.lyracode.ssh.SshExecutor
import com.yukisoffd.lyracode.system.SystemCommandExecutor
import com.yukisoffd.lyracode.tasks.DownloadTaskManager
import com.yukisoffd.lyracode.tasks.ScheduledTaskManager
import com.yukisoffd.lyracode.termux.TermuxExecutor
import com.yukisoffd.lyracode.webdav.TransferProgress
import com.yukisoffd.lyracode.webdav.WebDavClient
import com.yukisoffd.lyracode.workspace.GlobalFileManager
import com.yukisoffd.lyracode.workspace.NativeFileManager
import com.yukisoffd.lyracode.workspace.UploadedFile
import com.yukisoffd.lyracode.workspace.UploadedFileManager
import com.yukisoffd.lyracode.workspace.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.max
import kotlin.math.abs
import android.graphics.Canvas as AndroidCanvas

private const val PAGE_CHAT = 0
private const val PAGE_LOG = 1
private const val PAGE_STATS = 2
private const val PAGE_TASKS = 3
private const val PAGE_SETTINGS = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyraCodeApp(
    settings: AppSettings,
    auditLogStore: AuditLogStore,
    workspaceManager: WorkspaceManager,
    termuxExecutor: TermuxExecutor,
    mcpClientManager: McpClientManager,
    sshExecutor: SshExecutor,
    systemCommandExecutor: SystemCommandExecutor,
    webDavClient: WebDavClient,
    fileTransferClient: FileTransferClient,
    backupManager: BackupManager,
    miniServerManager: MiniServerManager,
    localMcpServerManager: LocalMcpServerManager,
    downloadTaskManager: DownloadTaskManager,
    scheduledTaskManager: ScheduledTaskManager,
    controller: ChatController,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    refreshRateMode: String,
    onRefreshRateModeChange: (String) -> Unit,
    fontScaleMode: String,
    customFontScale: Float,
    onFontScaleModeChange: (String) -> Unit,
    onCustomFontScaleChange: (Float) -> Unit,
) {
    val pages = listOf("AI 对话", "日志", "统计", "任务", "设置")
    val context = LocalContext.current
    var selectedPage by rememberSaveable { mutableIntStateOf(PAGE_CHAT) }
    val safeSelectedPage = selectedPage.coerceIn(0, pages.lastIndex)
    val controllerStatus = controller.status.value
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var workspaceName by remember { mutableStateOf(workspaceManager.displayName()) }
    var nickname by remember { mutableStateOf(settings.userNickname) }
    var avatarPath by remember { mutableStateOf(settings.userAvatarPath) }
    var skillsRevision by remember { mutableIntStateOf(0) }
    var skillStatus by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf("") }
    var appNotice by remember { mutableStateOf("") }
    var backupImportMode by remember { mutableStateOf("supplement") }
    val updateManager = remember(context) { UpdateManager(context) }
    var aboutUpdateAvailable by remember { mutableStateOf(updateManager.hasAvailableUpdate()) }
    val appSettingsRevision = controller.settingsRevision.intValue
    val skills = remember(skillsRevision, appSettingsRevision) { settings.installedSkills() }
    var settingsDetailTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsBackRequest by remember { mutableIntStateOf(0) }
    fun requestNewConversation() {
        if (controller.requestNewConversation()) {
            selectedPage = PAGE_CHAT
        } else {
            appNotice = "你已在新对话中，无需重复操作"
        }
    }

    fun updateBackupStatus(message: String) {
        backupStatus = message
        if (message.isNotBlank()) appNotice = message
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            updateManager.checkDailyForUpdateIfNeeded().getOrNull()
        }
        aboutUpdateAvailable = updateManager.hasAvailableUpdate()
    }
    LaunchedEffect(safeSelectedPage) {
        if (safeSelectedPage != PAGE_SETTINGS) settingsDetailTitle = null
    }
    BackHandler(enabled = safeSelectedPage == PAGE_SETTINGS && settingsDetailTitle != null && !drawerState.isOpen) {
        settingsBackRequest++
    }
    BackHandler(enabled = safeSelectedPage != PAGE_CHAT && !(safeSelectedPage == PAGE_SETTINGS && settingsDetailTitle != null) && !drawerState.isOpen) {
        selectedPage = PAGE_CHAT
    }
    BackHandler(enabled = drawerState.isOpen) {
        selectedPage = PAGE_CHAT
        scope.launch { drawerState.close() }
    }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            workspaceManager.persistWorkspace(uri)
            workspaceName = workspaceManager.displayName()
        }
    }
    fun updateSkillImportStatus(result: Result<SkillPack>) {
        result.fold(
            onSuccess = {
                skillStatus = "已导入 ${it.name}"
                skillsRevision++
            },
            onFailure = { skillStatus = it.message.orEmpty().ifBlank { "导入失败" } },
        )
    }
    val skillFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            updateSkillImportStatus(settings.importSkillFile(uri))
        }
    }
    val backupZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                backupStatus = "正在导入备份..."
                appNotice = "正在导入备份..."
                backupStatus = withContext(Dispatchers.IO) {
                    runCatching { backupManager.importFromUri(uri, backupImportMode) }
                        .fold({ "导入完成：$it" }, { "导入失败：${it.message}" })
                }
                appNotice = backupStatus
                controller.reloadConversations()
                skillsRevision++
                controller.settingsRevision.intValue++
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.86f),
                drawerContainerColor = MaterialTheme.colorScheme.background,
            ) {
                KimiDrawerContent(
                    settings = settings,
                    pages = pages,
                    selectedPage = safeSelectedPage,
                    controller = controller,
                    nickname = nickname,
                    avatarPath = avatarPath,
                    onProfileChanged = { newNickname, newAvatarPath ->
                        nickname = newNickname
                        avatarPath = newAvatarPath
                    },
                    onSelectPage = { index ->
                        selectedPage = index
                        scope.launch { drawerState.close() }
                    },
                    onNewConversation = {
                        requestNewConversation()
                        scope.launch { drawerState.close() }
                    },
                    onSelectConversation = { id ->
                        controller.selectConversation(id)
                        selectedPage = PAGE_CHAT
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.primary,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (safeSelectedPage == PAGE_SETTINGS && settingsDetailTitle != null) {
                                    settingsBackRequest++
                                } else {
                                    scope.launch { drawerState.open() }
                                }
                            },
                            modifier = Modifier.width(64.dp),
                        ) {
                            if (safeSelectedPage == PAGE_SETTINGS && settingsDetailTitle != null) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                            } else {
                                Icon(Icons.Default.Menu, contentDescription = "菜单")
                            }
                        }
                    },
                    title = {
                        if (safeSelectedPage == PAGE_CHAT) {
                            if (controller.isRoleplayMode()) {
                                val scenario = settings.roleplayScenario(controller.currentRoleplayId())
                                val roleplayBusy = controller.isActiveConversationRunning() || controllerStatus.startsWith("运行") || controllerStatus.startsWith("等待") || controllerStatus.contains("工具") || controllerStatus.contains("继续")
                                Text(
                                    if (roleplayBusy) "对方正在输入..." else scenario?.aiNickname.orEmpty().ifBlank { scenario?.name ?: "沉浸对话" },
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                val activeConversation = controller.conversations.firstOrNull { it.id == controller.activeConversationId.value }
                                val title = activeConversation?.title.orEmpty().ifBlank { "新聊天" }
                                    .let { if (it == "新对话") "新聊天" else it }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val activeProfile = controller.profiles.firstOrNull { it.id == controller.activeProfileId.value }
                                        val activeModelName = controller.activeModel.value.ifBlank { activeProfile?.selectedModel.orEmpty().ifBlank { "模型" } }
                                        Text(
                                            "$activeModelName / $workspaceName",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                if (safeSelectedPage == PAGE_SETTINGS) settingsDetailTitle ?: "设置" else pages[safeSelectedPage],
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        if (safeSelectedPage == PAGE_CHAT && !controller.isRoleplayMode()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { treeLauncher.launch(null) }) {
                                    PlusBadgeIcon(
                                        baseIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                    )
                                }
                                IconButton(onClick = {
                                    requestNewConversation()
                                }) {
                                    PlusBadgeIcon(
                                        baseIcon = { Icon(Icons.Default.ChatBubble, contentDescription = null) },
                                    )
                                }
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                AnimatedContent(
                    targetState = safeSelectedPage,
                    transitionSpec = {
                        val forward = targetState > initialState
                        slideInHorizontally(animationSpec = tween(260)) { fullWidth -> if (forward) fullWidth else -fullWidth } togetherWith
                            slideOutHorizontally(animationSpec = tween(260)) { fullWidth -> if (forward) -fullWidth else fullWidth }
                    },
                    label = "page-transition",
                ) { page ->
                    when (page) {
                        PAGE_CHAT -> ChatScreen(controller, settings, termuxExecutor)
                        PAGE_LOG -> LogScreen(auditLogStore)
                        PAGE_STATS -> UsageStatsScreen(controller)
                        PAGE_TASKS -> TaskScreen(settings, downloadTaskManager, scheduledTaskManager)
                        PAGE_SETTINGS -> SettingsScreen(
                            settings = settings,
                            controller = controller,
                            workspaceManager = workspaceManager,
                            termuxExecutor = termuxExecutor,
                            mcpClientManager = mcpClientManager,
                            sshExecutor = sshExecutor,
                            systemCommandExecutor = systemCommandExecutor,
                            webDavClient = webDavClient,
                            fileTransferClient = fileTransferClient,
                            backupManager = backupManager,
                            miniServerManager = miniServerManager,
                            localMcpServerManager = localMcpServerManager,
                            workspaceDisplayName = workspaceName,
                            skills = skills,
                            skillStatus = skillStatus,
                            backupStatus = backupStatus,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            dynamicColorEnabled = dynamicColorEnabled,
                            onDynamicColorChange = onDynamicColorChange,
                            refreshRateMode = refreshRateMode,
                            onRefreshRateModeChange = onRefreshRateModeChange,
                            fontScaleMode = fontScaleMode,
                            customFontScale = customFontScale,
                            onFontScaleModeChange = onFontScaleModeChange,
                            onCustomFontScaleChange = onCustomFontScaleChange,
                            onPickWorkspace = { treeLauncher.launch(null) },
                            onImportSkillFile = { skillFileLauncher.launch("*/*") },
                            onImportSkillRepository = { url ->
                                skillStatus = "正在下载 Skills 仓库..."
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { settings.importSkillRepository(url) }
                                    updateSkillImportStatus(result)
                                }
                            },
                            onImportSkillMarkdown = { text ->
                                updateSkillImportStatus(settings.importSkillMarkdown("manual_SKILL.md", text))
                            },
                            onImportBackup = { mode ->
                                backupImportMode = mode
                                backupZipLauncher.launch("application/zip")
                            },
                            onBackupStatusChange = ::updateBackupStatus,
                            updateAvailable = aboutUpdateAvailable,
                            onUpdateAvailabilityChange = { aboutUpdateAvailable = it },
                            settingsBackRequest = settingsBackRequest,
                            onDetailTitleChange = { settingsDetailTitle = it },
                            onToggleSkill = { id, enabled ->
                                settings.setSkillEnabled(id, enabled)
                                skillsRevision++
                            },
                            onDeleteSkill = { id ->
                                settings.deleteSkill(id)
                                skillsRevision++
                            },
                        )
                    }
                }
                TransientNotice(
                    message = appNotice,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    onDismiss = { appNotice = "" },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KimiDrawerContent(
    settings: AppSettings,
    pages: List<String>,
    selectedPage: Int,
    controller: ChatController,
    nickname: String,
    avatarPath: String?,
    onProfileChanged: (String, String?) -> Unit,
    onSelectPage: (Int) -> Unit,
    onNewConversation: () -> Unit,
    onSelectConversation: (Long) -> Unit,
) {
    val conversationSnapshot = controller.conversations.toList()
    var historyQuery by rememberSaveable { mutableStateOf("") }
    var selectedHistoryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var actionConversation by remember { mutableStateOf<Conversation?>(null) }
    val filteredConversations = remember(conversationSnapshot, historyQuery) {
        val query = historyQuery.trim()
        if (query.isBlank()) {
            conversationSnapshot
        } else {
            conversationSnapshot.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.model.contains(query, ignoreCase = true) ||
                    it.status.contains(query, ignoreCase = true)
            }
        }
    }
    val groupedConversations = remember(filteredConversations) {
        groupConversationsByTime(filteredConversations)
    }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    actionConversation?.let { conversation ->
        HistoryConversationActionsDialog(
            conversation = conversation,
            onDismiss = { actionConversation = null },
            onRename = { title ->
                controller.renameConversation(conversation.id, title)
                actionConversation = null
            },
            onPin = {
                controller.setConversationPinned(conversation.id, conversation.pinnedAt <= 0L)
                actionConversation = null
            },
            onDelete = {
                controller.deleteConversation(conversation.id)
                selectedHistoryIds = selectedHistoryIds - conversation.id
                actionConversation = null
            },
            onMultiSelect = {
                selectedHistoryIds = selectedHistoryIds + conversation.id
                actionConversation = null
            },
        )
    }
    if (editingProfile) {
        ProfileEditDialog(
            settings = settings,
            nickname = nickname,
            avatarPath = avatarPath,
            onDismiss = { editingProfile = false },
            onSaved = { newNickname, newAvatarPath ->
                onProfileChanged(newNickname, newAvatarPath)
                editingProfile = false
            },
        )
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        item {
            KimiCardBox {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { editingProfile = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(avatarPath = avatarPath, fallback = nickname.take(1).ifBlank { "L" }, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nickname, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            KimiCardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("功能", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                }
                KimiDivider()
                pages.forEachIndexed { index, page ->
                    KimiMenuRow(
                        icon = when (index) {
                            0 -> Icons.Default.Chat
                            1 -> Icons.Default.ReceiptLong
                            2 -> Icons.Default.Analytics
                            3 -> Icons.Default.TaskAlt
                            4 -> Icons.Default.Settings
                            5 -> Icons.Default.School
                            6 -> Icons.Default.Description
                            else -> Icons.Default.Info
                        },
                        title = page,
                        value = if (selectedPage == index) "当前" else "",
                        onClick = { onSelectPage(index) },
                    )
                    if (index != pages.lastIndex) KimiDivider()
                }
            }
        }
        item {
            KimiCardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("历史会话", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }
                CapsuleTextField(
                    value = historyQuery,
                    onValueChange = { historyQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "搜索历史对话",
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                )
                if (selectedHistoryIds.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("已选择 ${selectedHistoryIds.size}", modifier = Modifier.weight(1f), color = KimiMuted)
                        KimiChip("置顶", onClick = {
                            controller.setConversationsPinned(selectedHistoryIds, true)
                            selectedHistoryIds = emptySet()
                        })
                        KimiChip("删除", onClick = {
                            controller.deleteConversations(selectedHistoryIds)
                            selectedHistoryIds = emptySet()
                        })
                        IconButton(onClick = { selectedHistoryIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "取消选择")
                        }
                    }
                }
                KimiDivider()
                if (filteredConversations.isEmpty()) {
                    Text("暂无会话", color = KimiMuted)
                }
            }
        }
        groupedConversations.forEach { (label, conversations) ->
            item(key = "history-group-$label") {
                Text(
                    label,
                    color = KimiMuted,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .padding(top = 2.dp),
                )
            }
            items(conversations, key = { it.id }) { conversation ->
                KimiConversationRow(
                    conversation = conversation,
                    selected = controller.activeConversationId.value == conversation.id,
                    multiSelected = conversation.id in selectedHistoryIds,
                    selectionMode = selectedHistoryIds.isNotEmpty(),
                    onSelect = {
                        if (selectedHistoryIds.isEmpty()) {
                            onSelectConversation(conversation.id)
                        } else {
                            selectedHistoryIds = if (conversation.id in selectedHistoryIds) {
                                selectedHistoryIds - conversation.id
                            } else {
                                selectedHistoryIds + conversation.id
                            }
                        }
                    },
                    onLongPress = { actionConversation = conversation },
                )
            }
        }
    }
    }
}

private fun groupConversationsByTime(
    conversations: List<Conversation>,
    nowMillis: Long = System.currentTimeMillis(),
): List<Pair<String, List<Conversation>>> {
    if (conversations.isEmpty()) return emptyList()
    val calendar = Calendar.getInstance()
    fun startOfDay(time: Long): Long = calendar.run {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
    val todayStart = startOfDay(nowMillis)
    calendar.timeInMillis = todayStart
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayStart = calendar.timeInMillis
    calendar.timeInMillis = todayStart
    calendar.add(Calendar.DAY_OF_YEAR, -7)
    val weekStart = calendar.timeInMillis
    calendar.timeInMillis = todayStart
    calendar.add(Calendar.MONTH, -1)
    val monthStart = calendar.timeInMillis

    val groups = linkedMapOf<String, MutableList<Conversation>>()
    conversations.forEach { conversation ->
        val label = when {
            conversation.pinnedAt > 0L -> "置顶"
            conversation.updatedAt >= todayStart -> "今天"
            conversation.updatedAt >= yesterdayStart -> "昨天"
            conversation.updatedAt >= weekStart -> "一周内"
            conversation.updatedAt >= monthStart -> "一月内"
            else -> SimpleDateFormat("yyyy年M月", Locale.getDefault()).format(Date(conversation.updatedAt))
        }
        groups.getOrPut(label) { mutableListOf() }.add(conversation)
    }
    return groups.map { it.key to it.value }
}

@Composable
internal fun CapsuleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
    enabled: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        decorationBox = { innerTextField ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(KimiPillShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leadingIcon?.invoke()
                Box(Modifier.weight(1f)) {
                    if (value.isBlank()) {
                        Text(placeholder, color = KimiMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable
internal fun UserAvatar(avatarPath: String?, fallback: String, modifier: Modifier = Modifier) {
    val bitmap = remember(avatarPath) {
        avatarPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    Box(
        modifier
            .clip(CircleShape)
            .background(Color(0xFFC6A990)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(fallback, color = Color(0xFF121212), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
internal fun ProfileEditDialog(
    settings: AppSettings,
    nickname: String,
    avatarPath: String?,
    onDismiss: () -> Unit,
    onSaved: (String, String?) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var draftName by rememberSaveable(nickname) { mutableStateOf(nickname) }
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var offsetX by rememberSaveable { mutableStateOf(0f) }
    var offsetY by rememberSaveable { mutableStateOf(0f) }
    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            zoom = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }
    val previewBitmap = remember(selectedUri, avatarPath) {
        selectedUri?.let { decodeBitmap(context, it) }
            ?: avatarPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("个人资料") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (previewBitmap != null) {
                        val frameSize = 128.dp
                        val aspect = previewBitmap.width.toFloat() / previewBitmap.height.toFloat()
                        val baseWidth = if (aspect >= 1f) frameSize * aspect else frameSize
                        val baseHeight = if (aspect >= 1f) frameSize else frameSize / aspect
                        val maxShiftX = with(density) { ((baseWidth * zoom - frameSize) / 2f).coerceAtLeast(0.dp).toPx() }
                        val maxShiftY = with(density) { ((baseHeight * zoom - frameSize) / 2f).coerceAtLeast(0.dp).toPx() }
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .requiredWidth(baseWidth)
                                .requiredHeight(baseHeight)
                                .graphicsLayer(
                                    scaleX = zoom,
                                    scaleY = zoom,
                                    translationX = -offsetX * maxShiftX,
                                    translationY = -offsetY * maxShiftY,
                                )
                                .pointerInput(zoom) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        if (maxShiftX > 0f) {
                                            offsetX = (offsetX - dragAmount.x / maxShiftX).coerceIn(-1f, 1f)
                                        }
                                        if (maxShiftY > 0f) {
                                            offsetY = (offsetY - dragAmount.y / maxShiftY).coerceIn(-1f, 1f)
                                        }
                                    }
                                },
                            contentScale = ContentScale.FillBounds,
                        )
                    } else {
                        Text(draftName.take(1).ifBlank { "L" }, style = MaterialTheme.typography.headlineLarge)
                    }
                }
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("昵称") },
                    singleLine = true,
                )
                OutlinedButton(onClick = { avatarLauncher.launch("image/*") }, shape = KimiPillShape) {
                    Text("选择头像图片")
                }
                if (previewBitmap != null) {
                    Text("裁剪缩放", color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                    Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..3f)
                    Text("水平位置", color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                    Slider(value = offsetX, onValueChange = { offsetX = it }, valueRange = -1f..1f)
                    Text("垂直位置", color = KimiMuted, style = MaterialTheme.typography.labelMedium)
                    Slider(value = offsetY, onValueChange = { offsetY = it }, valueRange = -1f..1f)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newAvatarPath = selectedUri?.let { saveCroppedAvatar(context, it, zoom, offsetX, offsetY) } ?: avatarPath
                    settings.userNickname = draftName
                    settings.userAvatarPath = newAvatarPath
                    onSaved(settings.userNickname, newAvatarPath)
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun SkillDrawerRow(skill: SkillPack, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(skill.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text("${skill.fileCount} 个文件 · ${if (skill.enabled) "已启用" else "已禁用"}", color = KimiMuted, style = MaterialTheme.typography.labelSmall)
        }
        KimiChip(if (skill.enabled) "禁用" else "启用", onClick = onToggle)
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除 Skill")
        }
    }
}

@Composable
internal fun SkillsScreen(
    skills: List<SkillPack>,
    status: String,
    onImportSkillFile: () -> Unit,
    onImportSkillRepository: (String) -> Unit,
    onImportSkillMarkdown: (String) -> Unit,
    onToggleSkill: (String, Boolean) -> Unit,
    onDeleteSkill: (String) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<SkillPack?>(null) }
    var importModeVisible by remember { mutableStateOf(false) }
    var manualImportVisible by remember { mutableStateOf(false) }
    var repositoryImportVisible by remember { mutableStateOf(false) }
    deleteTarget?.let { skill ->
        ConfirmDeleteDialog(
            title = "删除 Skill",
            message = "该操作会删除此 Skill 包及其本地文件。",
            targetName = skill.name,
            onDismiss = { deleteTarget = null },
            onConfirm = { onDeleteSkill(skill.id) },
        )
    }
    if (importModeVisible) {
        SkillImportModeDialog(
            onDismiss = { importModeVisible = false },
            onImportFile = {
                importModeVisible = false
                onImportSkillFile()
            },
            onImportRepository = {
                importModeVisible = false
                repositoryImportVisible = true
            },
            onImportMarkdown = {
                importModeVisible = false
                manualImportVisible = true
            },
        )
    }
    if (manualImportVisible) {
        SkillManualImportDialog(
            onDismiss = { manualImportVisible = false },
            onImportMarkdown = { text ->
                manualImportVisible = false
                onImportSkillMarkdown(text)
            },
        )
    }
    if (repositoryImportVisible) {
        SkillRepositoryImportDialog(
            onDismiss = { repositoryImportVisible = false },
            onImportRepository = { url ->
                repositoryImportVisible = false
                onImportSkillRepository(url)
            },
        )
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Skills 能力包", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "支持从文件、仓库链接或手动编辑 SKILL.md 导入。AI 会先查看 name/description 判断是否需要，再按需读取包内文件。",
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            KimiDivider()
            KimiMenuRow(
                icon = Icons.Default.UploadFile,
                title = "导入 Skills",
                value = "${skills.size} 个已安装",
                onClick = { importModeVisible = true },
            )
        }
        if (status.isNotBlank()) {
            Text(status, color = KimiMuted, style = MaterialTheme.typography.labelMedium)
        }
        if (skills.isEmpty()) {
            KimiCardBox {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("暂无 Skills", style = MaterialTheme.typography.titleMedium)
                            Text("导入 zip、单个 SKILL.md 或仓库后会在这里显示，可启用、禁用或删除。", color = KimiMuted)
                        }
                    }
                }
            } else {
            KimiSectionLabel("已安装")
        }
        if (skills.isNotEmpty()) {
            KimiCardBox {
                skills.forEachIndexed { index, skill ->
                    SkillSettingsRow(
                        skill = skill,
                        onToggle = { onToggleSkill(skill.id, !skill.enabled) },
                        onDelete = { deleteTarget = skill },
                    )
                    if (index != skills.lastIndex) KimiDivider()
                }
            }
        }
    }
}

@Composable
internal fun SkillImportModeDialog(
    onDismiss: () -> Unit,
    onImportFile: () -> Unit,
    onImportRepository: () -> Unit,
    onImportMarkdown: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SkillImportModeRow(
                    icon = Icons.Default.UploadFile,
                    title = "从文件导入",
                    subtitle = "选择 zip 或单个 SKILL.md",
                    onClick = onImportFile,
                )
                KimiDivider()
                SkillImportModeRow(
                    icon = Icons.Default.CloudDownload,
                    title = "从仓库导入",
                    subtitle = "GitHub / Gitee / GitLab",
                    onClick = onImportRepository,
                )
                KimiDivider()
                SkillImportModeRow(
                    icon = Icons.Default.Add,
                    title = "手动添加",
                    subtitle = "直接编辑 SKILL.md",
                    onClick = onImportMarkdown,
                )
            }
        }
    }
}

@Composable
internal fun SkillImportModeRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = KimiMuted)
    }
}

@Composable
internal fun SkillRepositoryImportDialog(
    onDismiss: () -> Unit,
    onImportRepository: (String) -> Unit,
) {
    var repoUrl by rememberSaveable { mutableStateOf("") }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("从仓库导入", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "输入 GitHub、Gitee 或 GitLab 仓库链接，Lyra Code 会自动下载仓库文件并识别 SKILL.md。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("GitHub / Gitee / GitLab 链接") },
                    placeholder = { Text("https://github.com/owner/repo") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onImportRepository(repoUrl.trim()) },
                        enabled = repoUrl.trim().isNotBlank(),
                        shape = KimiPillShape,
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("导入")
                    }
                }
            }
        }
    }
}

@Composable
internal fun SkillManualImportDialog(
    onDismiss: () -> Unit,
    onImportMarkdown: (String) -> Unit,
) {
    var manualText by rememberSaveable {
        mutableStateOf(
            """
            ---
            name: 自定义 Skill
            description: 简要说明这个 Skill 的用途
            ---

            # 自定义 Skill

            在这里写给 AI 的能力说明、适用场景、使用步骤和约束。
            """.trimIndent(),
        )
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("手动添加", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "直接编辑 SKILL.md 内容，保存后会作为一个独立 Skill 安装。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    label = { Text("SKILL.md") },
                    minLines = 8,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onImportMarkdown(manualText) },
                        enabled = manualText.isNotBlank(),
                        shape = KimiPillShape,
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("保存 Skill")
                    }
                }
            }
        }
    }
}

@Composable
internal fun SkillSettingsRow(skill: SkillPack, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Extension,
            contentDescription = null,
            tint = if (skill.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(skill.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (skill.enabled) "已启用" else "已禁用",
                    color = if (skill.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (skill.description.isNotBlank()) {
                Text(
                    skill.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("${skill.fileCount} 个文件 · ${skill.id}", color = KimiMuted, style = MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Switch(checked = skill.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除 Skill", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

internal fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

internal fun saveCroppedAvatar(context: Context, uri: Uri, zoom: Float, offsetX: Float, offsetY: Float): String? {
    val source = decodeBitmap(context, uri) ?: return null
    val side = min(source.width, source.height)
    val cropSide = (side / zoom.coerceIn(1f, 3f)).toInt().coerceAtLeast(1)
    val maxLeft = (source.width - cropSide).coerceAtLeast(0)
    val maxTop = (source.height - cropSide).coerceAtLeast(0)
    val centerLeft = maxLeft / 2f
    val centerTop = maxTop / 2f
    val left = (centerLeft + offsetX.coerceIn(-1f, 1f) * centerLeft).toInt().coerceIn(0, maxLeft)
    val top = (centerTop + offsetY.coerceIn(-1f, 1f) * centerTop).toInt().coerceIn(0, maxTop)
    val cropped = Bitmap.createBitmap(source, left, top, cropSide, cropSide)
    val scaled = Bitmap.createScaledBitmap(cropped, 512, 512, true)
    val file = File(context.filesDir, "avatar.png")
    file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
    if (cropped !== source) cropped.recycle()
    if (scaled !== cropped) scaled.recycle()
    return file.absolutePath
}

@Composable
internal fun ImageCropUploadDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onUseOriginal: () -> Unit,
    onCropped: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val original = remember(uri) { decodeBitmap(context, uri) }
    var bitmap by remember(uri) { mutableStateOf(original) }
    var mode by remember { mutableStateOf(ImageEditMode.Crop) }
    var cropLeft by rememberSaveable(uri.toString()) { mutableStateOf(0.08f) }
    var cropTop by rememberSaveable(uri.toString()) { mutableStateOf(0.08f) }
    var cropWidth by rememberSaveable(uri.toString()) { mutableStateOf(0.84f) }
    var cropHeight by rememberSaveable(uri.toString()) { mutableStateOf(0.84f) }
    var brushColor by remember { mutableStateOf(Color(0xFFE53935)) }
    var brushWidth by remember { mutableStateOf(0.014f) }
    var strokes by remember(uri) { mutableStateOf<List<ImageEditStroke>>(emptyList()) }
    var redoStack by remember(uri) { mutableStateOf<List<ImageEditStroke>>(emptyList()) }
    var activeStroke by remember { mutableStateOf<ImageEditStroke?>(null) }
    var cropDragMode by remember { mutableStateOf("move") }
    val currentCropState = rememberUpdatedState(CropRectState(cropLeft, cropTop, cropWidth, cropHeight))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("取消", color = Color.White) }
                Spacer(Modifier.weight(1f))
                IconButton(
                    enabled = strokes.isNotEmpty(),
                    onClick = {
                        strokes.lastOrNull()?.let {
                            strokes = strokes.dropLast(1)
                            redoStack = redoStack + it
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.Undo,
                        contentDescription = "撤销",
                        tint = if (strokes.isNotEmpty()) Color.White else Color.Gray,
                    )
                }
                IconButton(
                    enabled = redoStack.isNotEmpty(),
                    onClick = {
                        redoStack.lastOrNull()?.let {
                            strokes = strokes + it
                            redoStack = redoStack.dropLast(1)
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.Redo,
                        contentDescription = "重做",
                        tint = if (redoStack.isNotEmpty()) Color.White else Color.Gray,
                    )
                }
            }
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val currentBitmap = bitmap
                if (currentBitmap != null) {
                    val aspect = currentBitmap.width.toFloat() / currentBitmap.height.toFloat()
                    val availableAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
                    val frameWidth = if (availableAspect > aspect) maxHeight * aspect else maxWidth
                    val frameHeight = if (availableAspect > aspect) maxHeight else maxWidth / aspect
                    Box(
                        Modifier
                            .requiredWidth(frameWidth)
                            .requiredHeight(frameHeight)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.DarkGray),
                    ) {
                        Image(
                            bitmap = currentBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                        )
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(mode, brushColor, brushWidth) {
                                    var dragStartRect = CropRectState(0.08f, 0.08f, 0.84f, 0.84f)
                                    var totalDx = 0f
                                    var totalDy = 0f
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            totalDx = 0f
                                            totalDy = 0f
                                            if (mode == ImageEditMode.Crop) {
                                                dragStartRect = currentCropState.value
                                                cropDragMode = cropHitMode(start, dragStartRect.left, dragStartRect.top, dragStartRect.width, dragStartRect.height, size.width, size.height)
                                            } else {
                                                val point = normalizedEditPoint(start, size.width.toFloat(), size.height.toFloat())
                                                activeStroke = ImageEditStroke(mode, brushColor.toArgb(), brushWidth, listOf(point))
                                            }
                                        },
                                        onDragEnd = {
                                            activeStroke?.let {
                                                strokes = strokes + it
                                                redoStack = emptyList()
                                            }
                                            activeStroke = null
                                            cropDragMode = "move"
                                        },
                                        onDragCancel = {
                                            activeStroke = null
                                            cropDragMode = "move"
                                        },
                                    ) { change, dragAmount ->
                                        change.consume()
                                        if (mode == ImageEditMode.Crop) {
                                            totalDx += dragAmount.x / size.width.toFloat().coerceAtLeast(1f)
                                            totalDy += dragAmount.y / size.height.toFloat().coerceAtLeast(1f)
                                            val updated = updateCropRect(
                                                dragStartRect.left,
                                                dragStartRect.top,
                                                dragStartRect.width,
                                                dragStartRect.height,
                                                totalDx,
                                                totalDy,
                                                cropDragMode,
                                            )
                                            cropLeft = updated.left
                                            cropTop = updated.top
                                            cropWidth = updated.width
                                            cropHeight = updated.height
                                        } else {
                                            val point = normalizedEditPoint(change.position, size.width.toFloat(), size.height.toFloat())
                                            activeStroke = activeStroke?.copy(points = activeStroke!!.points + point)
                                        }
                                    }
                                },
                        ) {
                            val allStrokes = activeStroke?.let { strokes + it } ?: strokes
                            allStrokes.forEach { drawEditStroke(it, size) }
                            drawCropOverlay(cropLeft, cropTop, cropWidth, cropHeight, size)
                        }
                    }
                } else {
                    Text("无法预览图片", color = Color.White)
                }
            }
            ImageEditToolbar(
                mode = mode,
                onModeChange = { mode = it },
                brushColor = brushColor,
                onBrushColorChange = { brushColor = it },
                brushWidth = brushWidth,
                onBrushWidthChange = { brushWidth = it },
                onRotate = {
                    bitmap = bitmap?.let { rotateBitmap90(it) }
                    cropLeft = 0.08f
                    cropTop = 0.08f
                    cropWidth = 0.84f
                    cropHeight = 0.84f
                    strokes = emptyList()
                    redoStack = emptyList()
                },
                onReset = {
                    cropLeft = 0.08f
                    cropTop = 0.08f
                    cropWidth = 0.84f
                    cropHeight = 0.84f
                    strokes = emptyList()
                    redoStack = emptyList()
                    bitmap = original
                },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onUseOriginal, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("原图上传")
                }
                Button(
                    onClick = {
                        val edited = bitmap?.let {
                            saveEditedUploadImage(context, it, cropLeft, cropTop, cropWidth, cropHeight, strokes)
                        }
                        edited?.let(onCropped) ?: onUseOriginal()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("完成")
                }
            }
        }
    }
}

internal fun saveTemporaryUploadImage(context: Context, bitmap: Bitmap): Uri? = runCatching {
    val dir = File(context.cacheDir, "upload_crop").apply { mkdirs() }
    val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    Uri.fromFile(file)
}.getOrNull()

internal enum class ImageEditMode {
    Crop,
    Brush,
    Mosaic,
}

internal data class ImageEditPoint(val x: Float, val y: Float)

internal data class ImageEditStroke(
    val mode: ImageEditMode,
    val color: Int,
    val width: Float,
    val points: List<ImageEditPoint>,
)

internal data class CropRectState(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

@Composable
internal fun ImageEditToolbar(
    mode: ImageEditMode,
    onModeChange: (ImageEditMode) -> Unit,
    brushColor: Color,
    onBrushColorChange: (Color) -> Unit,
    brushWidth: Float,
    onBrushWidthChange: (Float) -> Unit,
    onRotate: () -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ImageModeChip("裁剪", mode == ImageEditMode.Crop) { onModeChange(ImageEditMode.Crop) }
            ImageModeChip("画笔", mode == ImageEditMode.Brush) { onModeChange(ImageEditMode.Brush) }
            ImageModeChip("马赛克", mode == ImageEditMode.Mosaic) { onModeChange(ImageEditMode.Mosaic) }
            Spacer(Modifier.weight(1f))
            KimiChip("旋转", onClick = onRotate)
            KimiChip("还原", onClick = onReset)
        }
        if (mode == ImageEditMode.Brush || mode == ImageEditMode.Mosaic) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (mode == ImageEditMode.Brush) {
                    listOf(Color.White, Color.Black, Color(0xFFE53935), Color(0xFFFFB300), Color(0xFF43A047), Color(0xFF039BE5)).forEach { color ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(if (color == brushColor) 3.dp else 1.dp, Color.White, CircleShape)
                                .clickable { onBrushColorChange(color) },
                        )
                    }
                }
                Text("粗细", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = brushWidth,
                    onValueChange = onBrushWidthChange,
                    valueRange = 0.006f..0.04f,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun ImageModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(KimiPillShape)
            .background(if (selected) KimiBlue else Color(0xFF242424))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
    )
}

internal fun normalizedEditPoint(offset: Offset, width: Float, height: Float): ImageEditPoint {
    return ImageEditPoint(
        x = (offset.x / width.coerceAtLeast(1f)).coerceIn(0f, 1f),
        y = (offset.y / height.coerceAtLeast(1f)).coerceIn(0f, 1f),
    )
}

internal fun cropHitMode(point: Offset, left: Float, top: Float, width: Float, height: Float, frameWidth: Int, frameHeight: Int): String {
    val x = point.x / frameWidth.toFloat().coerceAtLeast(1f)
    val y = point.y / frameHeight.toFloat().coerceAtLeast(1f)
    val right = left + width
    val bottom = top + height
    val edge = 0.06f
    val nearLeft = abs(x - left) < edge
    val nearRight = abs(x - right) < edge
    val nearTop = abs(y - top) < edge
    val nearBottom = abs(y - bottom) < edge
    return when {
        nearLeft && nearTop -> "top_left"
        nearRight && nearTop -> "top_right"
        nearLeft && nearBottom -> "bottom_left"
        nearRight && nearBottom -> "bottom_right"
        nearLeft -> "left"
        nearRight -> "right"
        nearTop -> "top"
        nearBottom -> "bottom"
        else -> "move"
    }
}

internal fun updateCropRect(left: Float, top: Float, width: Float, height: Float, dx: Float, dy: Float, mode: String): CropRectState {
    val minSize = 0.12f
    var l = left
    var t = top
    var w = width
    var h = height
    fun move() {
        l = (l + dx).coerceIn(0f, 1f - w)
        t = (t + dy).coerceIn(0f, 1f - h)
    }
    fun leftEdge() {
        val newLeft = (l + dx).coerceIn(0f, l + w - minSize)
        w += l - newLeft
        l = newLeft
    }
    fun rightEdge() {
        w = (w + dx).coerceIn(minSize, 1f - l)
    }
    fun topEdge() {
        val newTop = (t + dy).coerceIn(0f, t + h - minSize)
        h += t - newTop
        t = newTop
    }
    fun bottomEdge() {
        h = (h + dy).coerceIn(minSize, 1f - t)
    }
    when (mode) {
        "left" -> leftEdge()
        "right" -> rightEdge()
        "top" -> topEdge()
        "bottom" -> bottomEdge()
        "top_left" -> { topEdge(); leftEdge() }
        "top_right" -> { topEdge(); rightEdge() }
        "bottom_left" -> { bottomEdge(); leftEdge() }
        "bottom_right" -> { bottomEdge(); rightEdge() }
        else -> move()
    }
    return CropRectState(l.coerceIn(0f, 1f - w), t.coerceIn(0f, 1f - h), w.coerceIn(minSize, 1f), h.coerceIn(minSize, 1f))
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCropOverlay(left: Float, top: Float, width: Float, height: Float, canvasSize: Size) {
    val x = canvasSize.width * left
    val y = canvasSize.height * top
    val w = canvasSize.width * width
    val h = canvasSize.height * height
    drawRect(Color.Black.copy(alpha = 0.42f), topLeft = Offset.Zero, size = Size(canvasSize.width, y))
    drawRect(Color.Black.copy(alpha = 0.42f), topLeft = Offset(0f, y + h), size = Size(canvasSize.width, canvasSize.height - y - h))
    drawRect(Color.Black.copy(alpha = 0.42f), topLeft = Offset(0f, y), size = Size(x, h))
    drawRect(Color.Black.copy(alpha = 0.42f), topLeft = Offset(x + w, y), size = Size(canvasSize.width - x - w, h))
    drawRect(Color.White, topLeft = Offset(x, y), size = Size(w, h), style = Stroke(width = 3f))
    val corner = min(w, h).coerceAtMost(46f)
    listOf(
        Offset(x, y) to Pair(1f, 1f),
        Offset(x + w, y) to Pair(-1f, 1f),
        Offset(x, y + h) to Pair(1f, -1f),
        Offset(x + w, y + h) to Pair(-1f, -1f),
    ).forEach { (origin, dir) ->
        drawLine(Color.White, origin, origin + Offset(corner * dir.first, 0f), strokeWidth = 7f)
        drawLine(Color.White, origin, origin + Offset(0f, corner * dir.second), strokeWidth = 7f)
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEditStroke(stroke: ImageEditStroke, canvasSize: Size) {
    if (stroke.points.isEmpty()) return
    val color = Color(stroke.color)
    val pxWidth = stroke.width * min(canvasSize.width, canvasSize.height)
    if (stroke.mode == ImageEditMode.Mosaic) {
        stroke.points.forEach { point ->
            val side = pxWidth * 3.5f
            drawRect(
                Color.Gray.copy(alpha = 0.7f),
                topLeft = Offset(point.x * canvasSize.width - side / 2f, point.y * canvasSize.height - side / 2f),
                size = Size(side, side),
            )
        }
        return
    }
    stroke.points.zipWithNext().forEach { (a, b) ->
        drawLine(
            color = color,
            start = Offset(a.x * canvasSize.width, a.y * canvasSize.height),
            end = Offset(b.x * canvasSize.width, b.y * canvasSize.height),
            strokeWidth = pxWidth,
        )
    }
}

internal fun rotateBitmap90(source: Bitmap): Bitmap {
    val matrix = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

internal fun saveEditedUploadImage(
    context: Context,
    source: Bitmap,
    cropLeft: Float,
    cropTop: Float,
    cropWidthFraction: Float,
    cropHeightFraction: Float,
    strokes: List<ImageEditStroke>,
): Uri? {
    val editable = source.copy(Bitmap.Config.ARGB_8888, true)
    strokes.forEach { stroke ->
        if (stroke.mode == ImageEditMode.Mosaic) {
            val side = (stroke.width * min(editable.width, editable.height) * 3.5f).toInt().coerceAtLeast(8)
            stroke.points.forEach { point ->
                applyMosaicBlock(editable, (point.x * editable.width).toInt(), (point.y * editable.height).toInt(), side)
            }
        }
    }
    val canvas = AndroidCanvas(editable)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.filter { it.mode == ImageEditMode.Brush }.forEach { stroke ->
        paint.color = stroke.color
        paint.strokeWidth = stroke.width * min(editable.width, editable.height)
        stroke.points.zipWithNext().forEach { (a, b) ->
            canvas.drawLine(a.x * editable.width, a.y * editable.height, b.x * editable.width, b.y * editable.height, paint)
        }
    }
    val cropWidth = (editable.width * cropWidthFraction.coerceIn(0.05f, 1f)).toInt().coerceIn(1, editable.width)
    val cropHeight = (editable.height * cropHeightFraction.coerceIn(0.05f, 1f)).toInt().coerceIn(1, editable.height)
    val left = (editable.width * cropLeft.coerceIn(0f, 1f)).toInt().coerceIn(0, (editable.width - cropWidth).coerceAtLeast(0))
    val top = (editable.height * cropTop.coerceIn(0f, 1f)).toInt().coerceIn(0, (editable.height - cropHeight).coerceAtLeast(0))
    val cropped = Bitmap.createBitmap(editable, left, top, cropWidth, cropHeight)
    val dir = File(context.cacheDir, "uploads").apply { mkdirs() }
    val file = File(dir, "image_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    if (cropped !== editable) cropped.recycle()
    if (editable !== source) editable.recycle()
    return Uri.fromFile(file)
}

internal fun applyMosaicBlock(bitmap: Bitmap, centerX: Int, centerY: Int, side: Int) {
    val half = side / 2
    val left = (centerX - half).coerceIn(0, bitmap.width - 1)
    val top = (centerY - half).coerceIn(0, bitmap.height - 1)
    val right = (centerX + half).coerceIn(left + 1, bitmap.width)
    val bottom = (centerY + half).coerceIn(top + 1, bitmap.height)
    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0L
    var y = top
    while (y < bottom) {
        var x = left
        while (x < right) {
            val color = bitmap.getPixel(x, y)
            r += android.graphics.Color.red(color)
            g += android.graphics.Color.green(color)
            b += android.graphics.Color.blue(color)
            count++
            x += 2
        }
        y += 2
    }
    if (count == 0L) return
    val avg = android.graphics.Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    y = top
    while (y < bottom) {
        var x = left
        while (x < right) {
            bitmap.setPixel(x, y, avg)
            x++
        }
        y++
    }
}

@Composable
internal fun KimiDrawerShortcut(icon: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
        Text(label, color = KimiMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun KimiConversationRow(
    conversation: Conversation,
    selected: Boolean,
    multiSelected: Boolean,
    selectionMode: Boolean,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
) {
    val rowShape = RoundedCornerShape(18.dp)
    val borderWidth = if (selected) 2.dp else if (multiSelected) 1.5.dp else 0.dp
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        multiSelected -> KimiBlue
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0f)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(borderWidth, borderColor), rowShape)
            .combinedClickable(onClick = onSelect, onLongClick = onLongPress),
        shape = rowShape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected || multiSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = multiSelected, onCheckedChange = { onSelect() })
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.pinnedAt > 0L) {
                        Icon(Icons.Default.PushPin, contentDescription = "已置顶", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    listOf(conversation.status, conversation.model).filter { it.isNotBlank() }.joinToString(" · "),
                    color = KimiMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
internal fun HistoryConversationActionsDialog(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onMultiSelect: () -> Unit,
) {
    var title by rememberSaveable(conversation.id) { mutableStateOf(conversation.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("会话操作") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("对话标题") },
                )
                Button(onClick = { onRename(title.trim().ifBlank { conversation.title }) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("保存标题")
                }
                OutlinedButton(onClick = onPin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PushPin, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (conversation.pinnedAt > 0L) "取消置顶" else "置顶对话")
                }
                OutlinedButton(onClick = onMultiSelect, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("进入多选")
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("删除对话")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

