package com.yukisoffd.lyracode

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yukisoffd.lyracode.data.UsageStatisticsRepository
import com.yukisoffd.lyracode.data.UsageStatsPeriod
import com.yukisoffd.lyracode.data.UsageStatsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsageStatsScreen(controller: ChatController) {
    val context = LocalContext.current
    var selectedPeriodName by rememberSaveable { mutableStateOf(UsageStatsPeriod.DAY.name) }
    var anchorAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf<UsageStatsSummary?>(null) }
    var compactNumbers by rememberSaveable { mutableStateOf(true) }
    val selectedPeriod = UsageStatsPeriod.valueOf(selectedPeriodName)
    val conversationRevision = controller.conversations.size
    val currentMessageRevision = controller.messages.value.size

    LaunchedEffect(selectedPeriodName, anchorAt, refreshKey, conversationRevision, currentMessageRevision) {
        loading = true
        error = ""
        val result = withContext(Dispatchers.IO) {
            runCatching {
                UsageStatisticsRepository(context, controller.usageStore()).calculate(selectedPeriod, anchorAt)
            }
        }
        result.fold(
            onSuccess = { summary = it },
            onFailure = {
                summary = null
                error = it.message.orEmpty().ifBlank { "统计失败" }
            },
        )
        loading = false
    }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = anchorAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        anchorAt = datePickerState.selectedDateMillis ?: anchorAt
                        showDatePicker = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("使用统计", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "按模型请求时间估算；会重复计入上下文、工具结果和静态提示词成本。",
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { refreshKey++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UsageStatsPeriod.entries.forEach { period ->
                FilterChip(
                    selected = period == selectedPeriod,
                    onClick = { selectedPeriodName = period.name },
                    label = { Text(period.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = compactNumbers,
                onClick = { compactNumbers = true },
                label = { Text("粗略显示") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
            FilterChip(
                selected = !compactNumbers,
                onClick = { compactNumbers = false },
                label = { Text("精确显示") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }

        KimiCardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("统计时间", style = MaterialTheme.typography.titleMedium)
                    Text(
                        summary?.let { formatStatsRange(it) } ?: if (selectedPeriod == UsageStatsPeriod.TOTAL) "全部历史" else formatAnchorDate(anchorAt),
                        color = KimiMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (selectedPeriod != UsageStatsPeriod.TOTAL) {
                    IconButton(onClick = { anchorAt = shiftAnchor(anchorAt, selectedPeriod, -1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "上一段")
                    }
                    IconButton(onClick = { anchorAt = shiftAnchor(anchorAt, selectedPeriod, 1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "下一段")
                    }
                }
            }
            if (selectedPeriod != UsageStatsPeriod.TOTAL) {
                KimiDivider()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text("选择日期")
                    }
                    TextButton(onClick = { anchorAt = System.currentTimeMillis() }) {
                        Text("回到今天")
                    }
                    Text(formatAnchorDate(anchorAt), color = KimiMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        when {
            loading -> KimiCardBox {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("正在读取本地 tokenizer 并统计...", color = KimiMuted)
                }
            }
            error.isNotBlank() -> KimiCardBox {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            summary != null -> UsageStatsContent(summary!!, compactNumbers)
        }
    }
}

@Composable
private fun UsageStatsContent(summary: UsageStatsSummary, compactNumbers: Boolean) {
    KimiSectionLabel("${summary.period.label}统计")
    UsageMetricCard(
        icon = Icons.Default.Forum,
        title = "对话次数",
        value = formatStatsNumber(summary.conversationCount.toLong(), compactNumbers),
        description = "该时间段内有用户输入的会话数",
    )
    UsageMetricCard(
        icon = Icons.AutoMirrored.Filled.Input,
        title = "请求输入 Tokens",
        value = formatStatsNumber(summary.userInputTokens, compactNumbers),
        description = "每次请求的上下文 + 工具结果 + 固定提示词估算",
    )
    UsageMetricCard(
        icon = Icons.Default.Output,
        title = "模型输出 Tokens",
        value = formatStatsNumber(summary.aiOutputTokens, compactNumbers),
        description = "AI 正文 + thinking + 工具调用参数",
    )

    KimiCardBox {
        Text("明细", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        KimiDivider()
        UsageDetailRow(Icons.Default.Analytics, "模型请求", "${formatStatsNumber(summary.modelRequestCount.toLong(), compactNumbers)} 次")
        KimiDivider()
        UsageDetailRow(Icons.Default.Forum, "用户消息", "${formatStatsNumber(summary.userMessageCount.toLong(), compactNumbers)} 条")
        KimiDivider()
        UsageDetailRow(Icons.Default.SmartToy, "AI 消息", "${formatStatsNumber(summary.assistantMessageCount.toLong(), compactNumbers)} 条")
        KimiDivider()
        UsageDetailRow(Icons.Default.Build, "工具结果", "${formatStatsNumber(summary.toolMessageCount.toLong(), compactNumbers)} 条")
    }

    Text(
        "Token 数由本地 DeepSeek V3 tokenizer 估算，不访问网络。当前口径按每次模型请求重复计算上下文，并为系统提示词、工具 schema 和消息模板加入固定开销；不同服务商的 tokenizer、图片计费、缓存折扣和实际 usage 仍可能不同。",
        color = KimiMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun UsageMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    description: String,
) {
    KimiCardBox {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Text(
                value,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Clip,
            )
            Column(Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    color = KimiMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UsageDetailRow(icon: ImageVector, title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(value) },
            colors = AssistChipDefaults.assistChipColors(
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

private fun formatStatsNumber(value: Long, compact: Boolean): String {
    if (!compact) return NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)
    return formatCompactChineseNumber(value)
}

private fun formatCompactChineseNumber(value: Long): String {
    val absValue = kotlin.math.abs(value)
    if (absValue < 10_000L) return NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)
    val units = listOf(
        10_000.0 to "万",
        100_000_000.0 to "亿",
        1_000_000_000_000.0 to "万亿",
        10_000_000_000_000_000.0 to "亿亿",
    )
    val (divisor, unit) = units.lastOrNull { absValue >= it.first } ?: units.first()
    val scaled = absValue / divisor
    val rounded = if (scaled < 100.0) kotlin.math.round(scaled * 10.0) / 10.0 else kotlin.math.round(scaled)
    val numberText = if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", rounded)
    }
    return "${if (value < 0) "-" else ""}$numberText$unit"
}

private fun shiftAnchor(anchorAt: Long, period: UsageStatsPeriod, amount: Int): Long {
    return Calendar.getInstance().apply {
        timeInMillis = anchorAt
        when (period) {
            UsageStatsPeriod.DAY -> add(Calendar.DAY_OF_YEAR, amount)
            UsageStatsPeriod.WEEK -> add(Calendar.WEEK_OF_YEAR, amount)
            UsageStatsPeriod.MONTH -> add(Calendar.MONTH, amount)
            UsageStatsPeriod.YEAR -> add(Calendar.YEAR, amount)
            UsageStatsPeriod.TOTAL -> Unit
        }
    }.timeInMillis
}

private fun formatAnchorDate(anchorAt: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(anchorAt))
}

private fun formatStatsRange(summary: UsageStatsSummary): String {
    if (summary.period == UsageStatsPeriod.TOTAL) return "全部历史"
    val start = Date(summary.startAt)
    val endInclusive = Date((summary.endAt - 1L).coerceAtLeast(summary.startAt))
    return when (summary.period) {
        UsageStatsPeriod.DAY -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(start)
        UsageStatsPeriod.WEEK -> {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            "${formatter.format(start)} 至 ${formatter.format(endInclusive)}"
        }
        UsageStatsPeriod.MONTH -> SimpleDateFormat("yyyy 年 M 月", Locale.getDefault()).format(start)
        UsageStatsPeriod.YEAR -> SimpleDateFormat("yyyy 年", Locale.getDefault()).format(start)
        UsageStatsPeriod.TOTAL -> "全部历史"
    }
}
