package com.yukisoffd.lyracode.tasks

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yukisoffd.lyracode.R
import com.yukisoffd.lyracode.data.AppSettings

internal object TaskCompletionNotifier {
    private const val CHANNEL_ID = "lyra_background_tasks"

    fun notify(
        context: Context,
        settings: AppSettings,
        title: String,
        message: String,
        notificationId: Int,
        success: Boolean = true,
    ) {
        if (!settings.taskCompletionNotificationsEnabled) return
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "任务完成通知",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        manager.notify(
            notificationId,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setCategory(if (success) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_ERROR)
                .build(),
        )
    }
}
