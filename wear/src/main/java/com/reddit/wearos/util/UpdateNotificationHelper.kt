package com.reddit.wearos.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.reddit.wearos.MainActivity
import com.reddit.wearos.R

object UpdateNotificationHelper {

    const val CHANNEL_ID = "reddit_wear_updates"
    const val NOTIFICATION_ID = 7788

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Actualizaciones de Reddit",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de nuevas versiones de Red for Reddit"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showUpdateNotification(context: Context, info: GitHubReleaseInfo) {
        createNotificationChannel(context)

        val targetApkUrl = info.wearApkUrl ?: return

        // Acción principal del reloj: descargar e instalar directamente
        val installIntent = Intent(context, UpdateActionReceiver::class.java).apply {
            action = "com.reddit.wearos.ACTION_TRIGGER_OTA_INSTALL"
            putExtra("apk_url", targetApkUrl)
            putExtra("tag_name", info.latestTag)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val installPendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            installIntent,
            flags
        )

        // Acción al tocar cuerpo de notificación: abrir la app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("trigger_update", true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            1002,
            openIntent,
            flags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Actualización ${info.latestTag}")
            .setContentText("Nueva versión disponible. Toca para actualizar.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.stat_sys_download,
                "Actualizar ahora",
                installPendingIntent
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
