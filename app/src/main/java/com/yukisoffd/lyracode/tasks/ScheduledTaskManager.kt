package com.yukisoffd.lyracode.tasks

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ScheduledTaskType {
    ONCE,
    DAILY,
    WEEKLY,
    MONTHLY,
}

enum class ScheduledTaskStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED,
}

data class ScheduledTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val prompt: String,
    val type: ScheduledTaskType,
    val hour: Int,
    val minute: Int,
    val runAtMillis: Long = 0L,
    val dayOfWeek: Int = 1,
    val dayOfMonth: Int = 1,
    val profileId: String,
    val model: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val nextRunAt: Long = 0L,
    val lastRunAt: Long = 0L,
    val finishedAt: Long = 0L,
    val status: ScheduledTaskStatus = ScheduledTaskStatus.IDLE,
    val result: String = "",
    val error: String = "",
)

class ScheduledTaskManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _tasks = MutableStateFlow(load())
    val tasks: StateFlow<List<ScheduledTask>> = _tasks.asStateFlow()

    init {
        _tasks.value.filter { it.enabled }.forEach(::schedule)
    }

    fun task(id: String): ScheduledTask? = _tasks.value.firstOrNull { it.id == id }

    fun save(task: ScheduledTask): ScheduledTask {
        val normalized = task.copy(
            title = task.title.trim().ifBlank { "定时任务" },
            prompt = task.prompt.trim(),
            hour = task.hour.coerceIn(0, 23),
            minute = task.minute.coerceIn(0, 59),
            dayOfWeek = task.dayOfWeek.coerceIn(1, 7),
            dayOfMonth = task.dayOfMonth.coerceIn(1, 31),
        ).let {
            it.copy(nextRunAt = if (it.enabled) calculateNextRun(it) else 0L)
        }
        synchronized(lock) {
            val updated = _tasks.value.toMutableList()
            val index = updated.indexOfFirst { it.id == normalized.id }
            if (index >= 0) updated[index] = normalized else updated.add(0, normalized)
            _tasks.value = updated.sortedByDescending { it.createdAt }
            persist()
        }
        if (normalized.enabled) schedule(normalized) else cancel(normalized.id)
        return normalized
    }

    fun setEnabled(id: String, enabled: Boolean): ScheduledTask? {
        val current = task(id) ?: return null
        return save(current.copy(enabled = enabled, status = ScheduledTaskStatus.IDLE, error = ""))
    }

    fun delete(id: String) {
        synchronized(lock) {
            _tasks.value = _tasks.value.filterNot { it.id == id }
            persist()
        }
        cancel(id)
    }

    fun markRunning(id: String): ScheduledTask? = update(id) {
        it.copy(status = ScheduledTaskStatus.RUNNING, lastRunAt = System.currentTimeMillis(), error = "")
    }

    fun markWaitingForNetwork(id: String, error: String): ScheduledTask? = update(id) {
        it.copy(
            status = ScheduledTaskStatus.RUNNING,
            error = error.take(MAX_RESULT_CHARS),
        )
    }

    fun markFinished(id: String, result: String, error: String = ""): ScheduledTask? {
        val current = task(id) ?: return null
        val recurring = current.type != ScheduledTaskType.ONCE
        val finished = current.copy(
            enabled = current.enabled && recurring,
            status = if (error.isBlank()) ScheduledTaskStatus.COMPLETED else ScheduledTaskStatus.FAILED,
            result = result.take(MAX_RESULT_CHARS),
            error = error.take(MAX_RESULT_CHARS),
            finishedAt = System.currentTimeMillis(),
        ).let {
            it.copy(nextRunAt = if (it.enabled) calculateNextRun(it, afterMillis = System.currentTimeMillis() + 1_000L) else 0L)
        }
        synchronized(lock) {
            _tasks.value = _tasks.value.map { if (it.id == id) finished else it }
            persist()
        }
        if (finished.enabled) schedule(finished) else cancel(id)
        return finished
    }

    fun describe(): JSONArray = JSONArray().also { array ->
        _tasks.value.forEach { task ->
            array.put(
                JSONObject()
                    .put("id", task.id)
                    .put("title", task.title)
                    .put("prompt", task.prompt)
                    .put("schedule_type", task.type.name.lowercase())
                    .put("hour", task.hour)
                    .put("minute", task.minute)
                    .put("run_at", task.runAtMillis)
                    .put("day_of_week", task.dayOfWeek)
                    .put("day_of_month", task.dayOfMonth)
                    .put("profile_id", task.profileId)
                    .put("model", task.model)
                    .put("enabled", task.enabled)
                    .put("next_run_at", task.nextRunAt)
                    .put("last_run_at", task.lastRunAt)
                    .put("status", task.status.name.lowercase())
                    .put("error", task.error),
            )
        }
    }

    private fun update(id: String, transform: (ScheduledTask) -> ScheduledTask): ScheduledTask? {
        var updatedTask: ScheduledTask? = null
        synchronized(lock) {
            _tasks.value = _tasks.value.map {
                if (it.id == id) transform(it).also { task -> updatedTask = task } else it
            }
            persist()
        }
        return updatedTask
    }

    private fun schedule(task: ScheduledTask) {
        if (!task.enabled || task.nextRunAt <= 0L) return
        val delay = (task.nextRunAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInputData(Data.Builder().putString(ScheduledTaskWorker.KEY_TASK_ID, task.id).build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, NETWORK_RETRY_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(workName(task.id))
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            workName(task.id),
            androidx.work.ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun cancel(id: String) {
        WorkManager.getInstance(appContext).cancelUniqueWork(workName(id))
    }

    private fun calculateNextRun(task: ScheduledTask, afterMillis: Long = System.currentTimeMillis()): Long {
        if (task.type == ScheduledTaskType.ONCE) return task.runAtMillis.takeIf { it > afterMillis } ?: 0L
        val zone = ZoneId.systemDefault()
        val after = Instant.ofEpochMilli(afterMillis).atZone(zone).toLocalDateTime()
        var candidate = after.withSecond(0).withNano(0).withHour(task.hour).withMinute(task.minute)
        candidate = when (task.type) {
            ScheduledTaskType.DAILY -> if (candidate.isAfter(after)) candidate else candidate.plusDays(1)
            ScheduledTaskType.WEEKLY -> {
                val targetDay = DayOfWeek.of(task.dayOfWeek)
                var next = candidate.with(TemporalAdjusters.nextOrSame(targetDay))
                if (!next.isAfter(after)) next = next.plusWeeks(1)
                next
            }
            ScheduledTaskType.MONTHLY -> {
                fun monthly(base: LocalDateTime): LocalDateTime {
                    val day = task.dayOfMonth.coerceAtMost(base.toLocalDate().lengthOfMonth())
                    return base.withDayOfMonth(day).withHour(task.hour).withMinute(task.minute).withSecond(0).withNano(0)
                }
                var next = monthly(candidate)
                if (!next.isAfter(after)) next = monthly(candidate.plusMonths(1).withDayOfMonth(1))
                next
            }
            ScheduledTaskType.ONCE -> candidate
        }
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }

    private fun persist() {
        prefs.edit().putString(
            KEY_TASKS,
            JSONArray().also { array -> _tasks.value.forEach { array.put(it.toJson()) } }.toString(),
        ).apply()
    }

    private fun load(): List<ScheduledTask> {
        val raw = prefs.getString(KEY_TASKS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toScheduledTask()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun ScheduledTask.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("prompt", prompt)
        .put("type", type.name)
        .put("hour", hour)
        .put("minute", minute)
        .put("runAtMillis", runAtMillis)
        .put("dayOfWeek", dayOfWeek)
        .put("dayOfMonth", dayOfMonth)
        .put("profileId", profileId)
        .put("model", model)
        .put("enabled", enabled)
        .put("createdAt", createdAt)
        .put("nextRunAt", nextRunAt)
        .put("lastRunAt", lastRunAt)
        .put("finishedAt", finishedAt)
        .put("status", status.name)
        .put("result", result)
        .put("error", error)

    private fun JSONObject.toScheduledTask(): ScheduledTask = ScheduledTask(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        title = optString("title").ifBlank { "定时任务" },
        prompt = optString("prompt"),
        type = runCatching { ScheduledTaskType.valueOf(optString("type")) }.getOrDefault(ScheduledTaskType.ONCE),
        hour = optInt("hour"),
        minute = optInt("minute"),
        runAtMillis = optLong("runAtMillis"),
        dayOfWeek = optInt("dayOfWeek", 1),
        dayOfMonth = optInt("dayOfMonth", 1),
        profileId = optString("profileId"),
        model = optString("model"),
        enabled = optBoolean("enabled", true),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        nextRunAt = optLong("nextRunAt"),
        lastRunAt = optLong("lastRunAt"),
        finishedAt = optLong("finishedAt"),
        status = runCatching { ScheduledTaskStatus.valueOf(optString("status")) }.getOrDefault(ScheduledTaskStatus.IDLE),
        result = optString("result"),
        error = optString("error"),
    )

    companion object {
        private const val PREFS_NAME = "lyra_scheduled_tasks"
        private const val KEY_TASKS = "tasks"
        private const val MAX_RESULT_CHARS = 20_000
        private const val NETWORK_RETRY_BACKOFF_SECONDS = 15L

        @Volatile
        private var instance: ScheduledTaskManager? = null

        fun getInstance(context: Context): ScheduledTaskManager =
            instance ?: synchronized(this) {
                instance ?: ScheduledTaskManager(context).also { instance = it }
            }

        private fun workName(id: String) = "lyra_scheduled_task_$id"
    }
}
