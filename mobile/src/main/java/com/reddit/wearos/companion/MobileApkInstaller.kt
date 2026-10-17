package com.reddit.wearos.companion

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object MobileApkInstaller {

    suspend fun downloadAndInstallMobileApk(
        context: Context,
        apkUrl: String,
        onStatus: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onStatus("Descargando APK móvil...")
            val conn = URL(apkUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "RedForReddit-Mobile-OTA")

            val tempFile = File(context.cacheDir, "ota_update_mobile.apk")
            if (tempFile.exists()) tempFile.delete()

            conn.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.length() < 100_000L) {
                onStatus("Error: APK descargado incompleto")
                return@withContext false
            }

            onStatus("Abriendo instalador...")
            installApkFile(context, tempFile)
            true
        } catch (e: Exception) {
            onStatus("Error descarga móvil: ${e.localizedMessage ?: "red"}")
            false
        }
    }

    private fun installApkFile(context: Context, apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        session.openWrite("ota_mobile.apk", 0, apkFile.length()).use { out ->
            apkFile.inputStream().use { input ->
                input.copyTo(out)
            }
            session.fsync(out)
        }

        val intent = Intent(context, MobileInstallStatusReceiver::class.java).apply {
            action = "com.reddit.wearos.companion.ACTION_INSTALL_COMMIT"
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
        session.commit(pendingIntent.intentSender)
        session.close()
    }
}

class MobileInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmIntent)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "✅ Companion App actualizada", Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Cancelado o fallo"
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Instalación móvil: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
