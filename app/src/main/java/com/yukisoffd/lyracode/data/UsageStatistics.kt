package com.yukisoffd.lyracode.data

import android.content.Context
import java.util.Calendar

enum class UsageStatsPeriod(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年"),
    TOTAL("总共"),
}

data class UsageStatsSummary(
    val period: UsageStatsPeriod,
    val startAt: Long,
    val endAt: Long,
    val conversationCount: Int,
    val userInputTokens: Long,
    val aiOutputTokens: Long,
    val userMessageCount: Int,
    val assistantMessageCount: Int,
    val toolMessageCount: Int,
)

class UsageStatisticsRepository(
    context: Context,
    private val conversationStore: ConversationStore,
) {
    private val tokenizer = DeepSeekV3Tokenizer.get(context)

    fun calculate(period: UsageStatsPeriod, anchorAt: Long = System.currentTimeMillis()): UsageStatsSummary {
        val range = periodRange(period, anchorAt)
        val conversationsWithUserInput = linkedSetOf<Long>()
        var userInputTokens = 0L
        var aiOutputTokens = 0L
        var userMessageCount = 0
        var assistantMessageCount = 0
        var toolMessageCount = 0

        conversationStore.conversations().forEach { conversation ->
            conversationStore.messages(conversation.id).forEach { message ->
                if (message.createdAt < range.first || message.createdAt >= range.second) return@forEach
                when (message.role.lowercase()) {
                    "user" -> {
                        conversationsWithUserInput += message.conversationId
                        userMessageCount++
                        userInputTokens += tokenizer.count(message.content)
                    }
                    "tool" -> {
                        toolMessageCount++
                        userInputTokens += tokenizer.count(message.content)
                    }
                    "assistant" -> {
                        assistantMessageCount++
                        aiOutputTokens += tokenizer.count(message.content)
                        aiOutputTokens += tokenizer.count(message.thinking)
                    }
                }
            }
        }

        return UsageStatsSummary(
            period = period,
            startAt = range.first,
            endAt = range.second,
            conversationCount = conversationsWithUserInput.size,
            userInputTokens = userInputTokens,
            aiOutputTokens = aiOutputTokens,
            userMessageCount = userMessageCount,
            assistantMessageCount = assistantMessageCount,
            toolMessageCount = toolMessageCount,
        )
    }

    private fun periodRange(period: UsageStatsPeriod, anchorAt: Long): Pair<Long, Long> {
        if (period == UsageStatsPeriod.TOTAL) return Long.MIN_VALUE to Long.MAX_VALUE
        val start = Calendar.getInstance().apply {
            timeInMillis = anchorAt
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            UsageStatsPeriod.DAY -> Unit
            UsageStatsPeriod.WEEK -> start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            UsageStatsPeriod.MONTH -> start.set(Calendar.DAY_OF_MONTH, 1)
            UsageStatsPeriod.YEAR -> start.set(Calendar.DAY_OF_YEAR, 1)
            UsageStatsPeriod.TOTAL -> Unit
        }
        val end = start.clone() as Calendar
        when (period) {
            UsageStatsPeriod.DAY -> end.add(Calendar.DAY_OF_YEAR, 1)
            UsageStatsPeriod.WEEK -> end.add(Calendar.WEEK_OF_YEAR, 1)
            UsageStatsPeriod.MONTH -> end.add(Calendar.MONTH, 1)
            UsageStatsPeriod.YEAR -> end.add(Calendar.YEAR, 1)
            UsageStatsPeriod.TOTAL -> Unit
        }
        return start.timeInMillis to end.timeInMillis
    }
}
