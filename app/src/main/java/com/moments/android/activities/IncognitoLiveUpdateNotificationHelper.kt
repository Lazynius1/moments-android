package com.moments.android.activities

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.moments.android.MainActivity
import com.moments.android.R
import com.moments.android.services.incognito.PauseIncognitoReceiver

/**
 * Equivalente Android de [IncognitoLiveActivity] (ActivityKit).
 *
 * Sin glass / Dynamic Island: Live Update del sistema.
 * - API 37+: [NotificationCompat.MetricStyle] + timer countdown
 * - API 36: Standard + chronometer countdown + [setRequestPromotedOngoing]
 * - &lt; 36: notificación ongoing con texto MM:SS (fallback)
 *
 * Acción Pause ≡ [PauseIncognitoIntent].
 */
object IncognitoLiveUpdateNotificationHelper {
    private const val CHANNEL_ID = "moments_incognito_live"
    private const val CHANNEL_NAME = "Incognito"
    private const val NOTIFICATION_ID = 0x1C06170 // "incognito"
    private const val LIVE_UPDATE_API = 36
    private const val METRIC_STYLE_API = 37

    fun showOrUpdate(
        context: Context,
        userId: String,
        remainingSeconds: Int,
        isActive: Boolean,
        expectedEndTimeMillis: Long?,
    ) {
        if (!isActive || remainingSeconds <= 0) {
            cancel(context)
            return
        }
        ensureChannel(context)
        val endMillis = expectedEndTimeMillis
            ?: (System.currentTimeMillis() + remainingSeconds.coerceAtLeast(0) * 1000L)
        val notification = buildNotification(context, userId, remainingSeconds, endMillis)
        notificationManager(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        notificationManager(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager(context)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            if (existing.importance == NotificationManager.IMPORTANCE_MIN) {
                manager.deleteNotificationChannel(CHANNEL_ID)
            } else {
                return
            }
        }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                description = "Incognito countdown (Live Updates on Android 16+)"
            },
        )
    }

    private fun buildNotification(
        context: Context,
        userId: String,
        remainingSeconds: Int,
        endMillis: Long,
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = android.net.Uri.parse("moments://profile")
            putExtra("incognito_user_id", userId)
        }
        val openPending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val pauseIntent = Intent(context, PauseIncognitoReceiver::class.java).apply {
            action = PauseIncognitoReceiver.ACTION_PAUSE
        }
        val pausePending = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID + 1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = context.getString(R.string.incognito_activity_title)
        val subtitle = context.getString(R.string.incognito_activity_subtitle)
        val remainingLabel = context.getString(R.string.incognito_activity_remaining)
        val mmss = formatMmSs(remainingSeconds)
        val chip = mmss

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_moments)
            // iOS lock screen: fondo negro / texto blanco — accent ink (sin setColorized; prohibido en Live Updates).
            .setColor(context.getColor(R.color.widget_brand_ink))
            .setContentTitle(title)
            .setContentText("$mmss · $subtitle")
            .setSubText(remainingLabel)
            .setContentIntent(openPending)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setShortCriticalText(chip)
            .setRequestPromotedOngoing(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_stat_pause,
                    context.getString(R.string.incognito_activity_pause),
                    pausePending,
                ).build(),
            )

        if (Build.VERSION.SDK_INT >= METRIC_STYLE_API) {
            val timerMetric = NotificationCompat.Metric(
                NotificationCompat.Metric.TimeDifference.forTimer(
                    endMillis,
                    NotificationCompat.Metric.TimeDifference.FORMAT_CHRONOMETER,
                ),
                remainingLabel,
            )
            builder.setStyle(
                NotificationCompat.MetricStyle()
                    .addMetric(timerMetric)
                    .setCriticalMetric(0),
            )
            builder.setContentText(subtitle)
        } else {
            // Chronometer countdown nativo (también válido como Live Update en API 36).
            builder
                .setWhen(endMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        }

        if (Build.VERSION.SDK_INT < LIVE_UPDATE_API) {
            // Fallback pre-16: texto MM:SS sin promoción.
            builder.setRequestPromotedOngoing(false)
            builder.setContentText("$mmss · $subtitle")
            builder.setUsesChronometer(false)
            builder.setShowWhen(false)
        }

        return builder.build()
    }

    private fun formatMmSs(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
