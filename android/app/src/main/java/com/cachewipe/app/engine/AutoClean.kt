package com.cachewipe.app.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cachewipe.app.MainActivity
import com.cachewipe.app.R
import com.cachewipe.app.data.Prefs
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

enum class AutoMode { OFF, DAILY, WEEKLY }

/** Pro: scheduled clean. Runs headless through Shizuku when possible, otherwise posts a tap-to-clean reminder. */
object AutoClean {
    /** Widget, quick tile and reminder notification open MainActivity with this action. */
    const val ACTION_CLEAN_ALL = "com.cachewipe.app.action.CLEAN_ALL"
    private const val WORK = "auto_clean"
    private const val CHANNEL = "auto_clean"
    private const val NOTIFICATION_ID = 1

    fun schedule(context: Context, mode: AutoMode) {
        val work = WorkManager.getInstance(context)
        val days = when (mode) {
            AutoMode.OFF -> return work.cancelUniqueWork(WORK).let { }
            AutoMode.DAILY -> 1L
            AutoMode.WEEKLY -> 7L
        }
        val request = PeriodicWorkRequestBuilder<AutoCleanWorker>(days, TimeUnit.DAYS)
            .setInitialDelay(days, TimeUnit.DAYS)
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Device-test hook: run the scheduled job once, now. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<AutoCleanWorker>().build())
    }

    fun cleanAllIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_CLEAN_ALL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun cleanAllPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0, cleanAllIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    internal fun notify(context: Context, title: String, text: String?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(cleanAllPendingIntent(context))
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}

class AutoCleanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!Prefs(context).hasPro) return Result.success()

        // The Shizuku binder arrives shortly after our process starts
        for (i in 1..10) {
            if (ShizukuEngine.state() == ShizukuEngine.State.READY) break
            delay(500)
        }
        val freed = if (Prefs(context).fastMode && ShizukuEngine.state() == ShizukuEngine.State.READY) {
            ShizukuEngine.trimCaches()
        } else null
        Log.i("CacheWipe", "auto clean: fast=${freed != null} freed=$freed")

        if (freed != null) {
            AutoClean.notify(context, context.getString(R.string.notif_auto_done, Formatter.formatShortFileSize(context, freed)), null)
        } else {
            // The accessibility engine needs an unlocked screen, so ask the user to tap
            AutoClean.notify(context, context.getString(R.string.notif_reminder_title), context.getString(R.string.notif_reminder_text))
        }
        return Result.success()
    }
}
