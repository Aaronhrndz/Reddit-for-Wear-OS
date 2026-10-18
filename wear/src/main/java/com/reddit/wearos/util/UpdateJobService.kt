package com.reddit.wearos.util

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UpdateJobService : JobService() {

    companion object {
        const val JOB_ID = 7788
        private const val PREFS_NAME = "ota_job_prefs"
        private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return

            // Evitar reprogramar si ya está activo
            if (scheduler.getPendingJob(JOB_ID) != null) return

            val component = ComponentName(context, UpdateJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, component)
                .setPeriodic(24 * 60 * 60 * 1000L) // Una vez cada 24 horas
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // Solo cuando el reloj tiene conexión
                .setRequiresBatteryNotLow(true) // No despierta si la batería es baja (<15-20%)
                .setRequiresCharging(true) // Battery-friendly óptimo: corre en la base de carga inalámbrica
                .setPersisted(true) // Persistente tras reiniciar el reloj

            val result = scheduler.schedule(builder.build())
            Log.d("UpdateJobService", "OTA daily background job scheduled: $result")
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentVersion = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "1.6.0"
                } catch (_: Exception) {
                    "1.6.0"
                }

                val info = UpdateChecker.checkLatestRelease(currentVersion)
                if (info != null && info.isUpdateAvailable) {
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val lastNotified = prefs.getString(KEY_LAST_NOTIFIED_TAG, null)

                    // Solo notificar una vez por cada versión nueva
                    if (lastNotified != info.latestTag) {
                        UpdateNotificationHelper.showUpdateNotification(applicationContext, info)
                        prefs.edit().putString(KEY_LAST_NOTIFIED_TAG, info.latestTag).apply()
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateJobService", "Fallo al comprobar OTA periódica: ${e.message}")
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return false
    }
}
