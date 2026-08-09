package com.moments.android.activities

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.moments.android.MainActivity
import com.moments.android.R
import kotlin.random.Random

/**
 * Equivalente Android de ActivityKit Live Activities de subida
 * (MomentUploadLiveActivity / GlowsyWidgetExtensionLiveActivity).
 *
 * Sin glass / Dynamic Island custom: Live Updates del sistema.
 * - API 36+: ProgressStyle + promoted ongoing + largeIcon (miniatura)
 * - &lt; 36: barra de progreso classic
 *
 * Diseño iOS → Android: thumb + título + % (teal marca) / emoji al completar.
 */
object UploadProgressNotificationHelper {
    // Canal nuevo: el anterior `moments_upload_progress` quedó en IMPORTANCE_LOW y OEMs
    // (p.ej. Oppo) lo marcan unimportant → no se ve Live Update / banner.
    private const val CHANNEL_ID = "moments_upload_live_v2"
    private const val CHANNEL_NAME = "Upload progress"
    private const val LEGACY_CHANNEL_ID = "moments_upload_progress"
    private const val LIVE_UPDATE_API = 36

    private val completionEmojis = listOf("😊", "😄", "✨", "🎉", "👍", "💫", "❤️", "🥳", "😎", "🔥")

    fun showMomentUpload(
        context: Context,
        attributes: MomentUploadActivityAttributes,
        state: MomentUploadActivityAttributes.ContentState,
    ) {
        ensureChannel(context)
        val preview = attributes.previewImageFileName?.let { LiveActivityThumbnailStore.load(context, it) }
        val ongoing = state.status != MomentUploadActivityAttributes.ContentState.STATUS_COMPLETED &&
            state.status != MomentUploadActivityAttributes.ContentState.STATUS_FAILED
        val completedEmoji = if (state.status == MomentUploadActivityAttributes.ContentState.STATUS_COMPLETED) {
            completionEmojis[Random.nextInt(completionEmojis.size)]
        } else {
            null
        }
        val notification = buildNotification(
            context = context,
            notificationId = momentNotificationId(attributes.momentId),
            title = context.getString(R.string.upload_progress_moment_title),
            body = uploadStatusText(context, state.status, state.percentage, completedEmoji),
            progress = state.percentage,
            indeterminate = state.status == MomentUploadActivityAttributes.ContentState.STATUS_PROCESSING,
            largeIcon = preview,
            ongoing = ongoing,
            chipText = chipText(context, state.status, state.percentage, completedEmoji),
            completed = state.status == MomentUploadActivityAttributes.ContentState.STATUS_COMPLETED,
        )
        notify(context, momentNotificationId(attributes.momentId), notification)
        if (!ongoing) {
            attributes.previewImageFileName?.let { LiveActivityThumbnailStore.remove(context, attributes.momentId) }
        }
    }

    fun showStoryUpload(
        context: Context,
        attributes: StoryUploadActivityAttributes,
        state: StoryUploadActivityAttributes.ContentState,
    ) {
        ensureChannel(context)
        val preview = attributes.previewImageFileName?.let { LiveActivityThumbnailStore.load(context, it) }
        val ongoing = state.status != StoryUploadActivityAttributes.ContentState.STATUS_COMPLETED &&
            state.status != StoryUploadActivityAttributes.ContentState.STATUS_FAILED
        val completedEmoji = if (state.status == StoryUploadActivityAttributes.ContentState.STATUS_COMPLETED) {
            completionEmojis[Random.nextInt(completionEmojis.size)]
        } else {
            null
        }
        val notification = buildNotification(
            context = context,
            notificationId = storyNotificationId(attributes.storyId),
            title = context.getString(R.string.upload_progress_story_title),
            body = uploadStatusText(context, state.status, state.percentage, completedEmoji),
            progress = state.percentage,
            indeterminate = state.status == StoryUploadActivityAttributes.ContentState.STATUS_PROCESSING,
            largeIcon = preview,
            ongoing = ongoing,
            chipText = chipText(context, state.status, state.percentage, completedEmoji),
            completed = state.status == StoryUploadActivityAttributes.ContentState.STATUS_COMPLETED,
        )
        notify(context, storyNotificationId(attributes.storyId), notification)
        if (!ongoing) {
            attributes.previewImageFileName?.let { LiveActivityThumbnailStore.remove(context, attributes.storyId) }
        }
    }

    fun cancelMomentUpload(context: Context, momentId: String) {
        cancel(context, momentNotificationId(momentId))
        LiveActivityThumbnailStore.remove(context, momentId)
    }

    fun cancelStoryUpload(context: Context, storyId: String) {
        cancel(context, storyNotificationId(storyId))
        LiveActivityThumbnailStore.remove(context, storyId)
    }

    private fun momentNotificationId(momentId: String): Int = "moment_upload_$momentId".hashCode()
    private fun storyNotificationId(storyId: String): Int = "story_upload_$storyId".hashCode()

    private fun uploadStatusText(
        context: Context,
        status: String,
        percentage: Int,
        completedEmoji: String?,
    ): String = when (status) {
        MomentUploadActivityAttributes.ContentState.STATUS_UPLOADING ->
            context.getString(R.string.upload_progress_status_uploading, percentage)
        MomentUploadActivityAttributes.ContentState.STATUS_PROCESSING ->
            context.getString(R.string.upload_progress_status_processing)
        MomentUploadActivityAttributes.ContentState.STATUS_COMPLETED ->
            listOfNotNull(
                completedEmoji,
                context.getString(R.string.upload_progress_status_completed),
            ).joinToString(" ")
        MomentUploadActivityAttributes.ContentState.STATUS_FAILED ->
            context.getString(R.string.upload_progress_status_failed)
        else -> context.getString(R.string.upload_progress_status_uploading, percentage)
    }

    /** Chip status bar ≡ compact trailing iOS (`42%` / emoji). */
    private fun chipText(
        context: Context,
        status: String,
        percentage: Int,
        completedEmoji: String?,
    ): String = when (status) {
        MomentUploadActivityAttributes.ContentState.STATUS_PROCESSING ->
            context.getString(R.string.upload_progress_status_processing)
        MomentUploadActivityAttributes.ContentState.STATUS_COMPLETED ->
            completedEmoji ?: context.getString(R.string.upload_progress_status_completed)
        MomentUploadActivityAttributes.ContentState.STATUS_FAILED ->
            context.getString(R.string.upload_progress_status_failed)
        else -> "${percentage.coerceIn(0, 100)}%"
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Limpiar canal legacy silenciado.
        if (manager.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        }
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            if (existing.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                manager.deleteNotificationChannel(CHANNEL_ID)
            } else {
                return
            }
        }
        // DEFAULT (no LOW): requisito práctico para Live Updates / chip en OEM Android 16.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                setShowBadge(false)
                description = "Upload progress (Live Updates on Android 16+)"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun buildNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        progress: Int,
        indeterminate: Boolean,
        largeIcon: Bitmap?,
        ongoing: Boolean,
        chipText: String,
        completed: Boolean,
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val clamped = progress.coerceIn(0, 100)
        // Teal marca ≡ MomentsBrand.teal del anillo iOS (sin gradiente glass).
        val accent = context.getColor(R.color.widget_brand_teal)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_moments)
            .setColor(accent)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShortCriticalText(chipText)
            .setRequestPromotedOngoing(ongoing)

        if (indeterminate) builder.setProgress(0, 0, true)
        else if (!completed) builder.setProgress(100, clamped, false)
        else builder.setProgress(0, 0, false)

        if (Build.VERSION.SDK_INT >= LIVE_UPDATE_API && ongoing) {
            val style = NotificationCompat.ProgressStyle()
                .setStyledByProgress(true)
                .addProgressSegment(
                    NotificationCompat.ProgressStyle.Segment(100).setColor(accent),
                )
                .setProgressTrackerIcon(
                    IconCompat.createWithResource(context, R.drawable.ic_stat_moments),
                )
            if (indeterminate) {
                style.setProgressIndeterminate(true)
            } else {
                style.setProgress(clamped)
            }
            builder.setStyle(style)
        }

        largeIcon?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }

    private fun cancel(context: Context, id: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
    }
}
