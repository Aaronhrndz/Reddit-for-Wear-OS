package com.reddit.wearos.util

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val apkUrl = intent.getStringExtra("apk_url") ?: return
        val tag = intent.getStringExtra("tag_name") ?: ""

        // Cancelar notificación activa
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(UpdateNotificationHelper.NOTIFICATION_ID)

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "Descargando actualización en reloj...", Toast.LENGTH_SHORT).show()
        }

        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            ApkSelfInstaller.downloadAndInstallFromUrl(appCtx, apkUrl) { status ->
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(appCtx, status, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
