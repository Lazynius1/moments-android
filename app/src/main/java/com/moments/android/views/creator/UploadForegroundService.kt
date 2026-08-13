package com.moments.android.views.creator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.moments.android.MainActivity
import com.moments.android.R

/**
 * Equivalente Android de `UIApplication.beginBackgroundTask` en
 * `BackgroundStoryUploadService` / `BackgroundMomentUploadService` (iOS).
 *
 * Mantiene el proceso vivo con FGS `dataSync` mientras hay una publicación en curso.
 * No declara `mediaProcessing`: ese tipo no corresponde a la transferencia de red y
 * declarar ambos tipos para cada subida ampliaba innecesariamente la superficie de Play.
 * La recuperación tras kill sigue siendo el outbox + [com.moments.android.services.network.OfflineSyncService].
 */
class UploadForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground upload timed out; startId=$startId type=$fgsType")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.moments.android.action.UPLOAD_FGS_START"
        const val NOTIFICATION_ID = 0x4D4F5531 // "MOU1"
        private const val CHANNEL_ID = "moments_upload_live_v2"
        private const val TAG = "MomentsUploadFgs"

        fun buildNotification(context: Context): Notification {
            ensureChannel(context)
            val launch = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val accent = context.getColor(R.color.widget_brand_teal)
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_moments)
                .setColor(accent)
                .setContentTitle(context.getString(R.string.creator_upload_uploading))
                .setContentText(context.getString(R.string.upload_foreground_body))
                .setContentIntent(launch)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setProgress(0, 0, true)
                .build()
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Upload progress", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setShowBadge(false)
                    description = "Upload progress (Live Updates on Android 16+)"
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
    }
}

/**
 * Ref-count alrededor de prepare/upload — anida bien (prepare + publish).
 * Adquirir **antes** de lanzar la coroutine; liberar en `finally`.
 */
object UploadForegroundKeeper {
    private val lock = Any()
    private var refs = 0

    val isHeld: Boolean
        get() = synchronized(lock) { refs > 0 }

    fun acquire(context: Context) {
        val app = context.applicationContext
        synchronized(lock) {
            refs += 1
            if (refs == 1) {
                try {
                    val intent = Intent(app, UploadForegroundService::class.java).setAction(
                        UploadForegroundService.ACTION_START,
                    )
                    ContextCompat.startForegroundService(app, intent)
                } catch (error: Throwable) {
                    // Sin FGS el outbox sigue siendo la red de seguridad al reabrir.
                    Log.e("MomentsUploadFgs", "Unable to start upload foreground service", error)
                    refs = 0
                }
            }
        }
    }

    fun release(context: Context) {
        val app = context.applicationContext
        synchronized(lock) {
            refs = (refs - 1).coerceAtLeast(0)
            if (refs == 0) {
                app.stopService(Intent(app, UploadForegroundService::class.java))
            }
        }
    }
}
