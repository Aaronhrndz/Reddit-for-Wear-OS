package com.reddit.wearos.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ApkSelfInstaller {

    suspend fun downloadAndInstallFromUrl(
        context: Context,
        apkUrl: String,
        onStatus: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        try {
            onStatus("Descargando APK en reloj...")
            var activeConn = URL(apkUrl).openConnection() as HttpURLConnection
            activeConn.instanceFollowRedirects = true
            activeConn.connectTimeout = 15000
            activeConn.readTimeout = 60000
            activeConn.setRequestProperty("User-Agent", "RedForReddit-WearOS-OTA")

            if (activeConn.responseCode in 300..399) {
                val redirectUrl = activeConn.getHeaderField("Location")
                if (!redirectUrl.isNullOrEmpty()) {
                    activeConn.disconnect()
                    activeConn = URL(redirectUrl).openConnection() as HttpURLConnection
                    activeConn.connectTimeout = 15000
                    activeConn.readTimeout = 60000
                    activeConn.setRequestProperty("User-Agent", "RedForReddit-WearOS-OTA")
                }
            }

            val updatesDir = File(appCtx.cacheDir, "updates").apply { mkdirs() }
            val tempFile = File(updatesDir, "reddit_wear_update.apk")
            if (tempFile.exists()) tempFile.delete()

            val contentLength = activeConn.contentLengthLong
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L
            var lastReportedPercent = -1

            activeConn.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val percent = ((totalRead * 100) / contentLength).toInt()
                            if (percent != lastReportedPercent && percent % 10 == 0) {
                                lastReportedPercent = percent
                                onStatus("Descargando: $percent%")
                            }
                        }
                    }
                }
            }
            activeConn.disconnect()

            if (tempFile.length() < 100_000L) {
                onStatus("Error: APK incompleto")
                return@withContext false
            }

            onStatus("Lanzando instalador...")

            // Intento 1: Lanzador nativo via FileProvider (estilo UltraRemapper)
            var intentStarted = false
            try {
                val apkUri: Uri = FileProvider.getUriForFile(
                    appCtx,
                    "${appCtx.packageName}.fileprovider",
                    tempFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appCtx.startActivity(installIntent)
                intentStarted = true
            } catch (_: Exception) {
                intentStarted = false
            }

            // Intento 2 / Fallback: PackageInstaller Session API
            if (!intentStarted) {
                installApkFile(appCtx, tempFile)
            }
            true
        } catch (e: Exception) {
            onStatus("Error OTA: ${e.localizedMessage ?: "red"}")
            false
        }
    }

    fun installApkFile(context: Context, apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        session.openWrite("ota_watch.apk", 0, apkFile.length()).use { out ->
            apkFile.inputStream().use { input ->
                input.copyTo(out)
            }
            session.fsync(out)
        }

        val intent = Intent(context, InstallStatusReceiver::class.java).apply {
            action = "com.reddit.wearos.ACTION_INSTALL_COMMIT"
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

class InstallStatusReceiver : BroadcastReceiver() {
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
                    Toast.makeText(context, "✅ App del reloj actualizada", Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Fallo al instalar"
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Instalación: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
