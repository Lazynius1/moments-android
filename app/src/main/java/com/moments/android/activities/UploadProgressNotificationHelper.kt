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
import com.moments.android.MainActivity
import com.moments.android.R

/**
 * Equivalente Android de ActivityKit Live Activities para progreso de subida.
 * Usa una notificación ongoing con progreso determinado.
 * N/A: widget de Live Activity en pantalla de bloqueo/Dynamic Island (iOS only).
 */
object UploadProgressNotificationHelper {
    private const val CHANNEL_ID = "moments_upload_progress"
    private const val CHANNEL_NAME = "Upload progress"

    fun showMomentUpload(
        context: Context,
        attributes: MomentUploadActivityAttributes,
        state: MomentUploadActivityAttributes.ContentState,
    ) {
        ensureChannel(context)
        val preview = attributes.previewImageFileName?.let { LiveActivityThumbnailStore.load(context, it) }
        val notification = buildNotification(
            context = context,
            notificationId = momentNotificationId(attributes.momentId),
            title = context.getString(R.string.upload_progress_moment_title),
            body = uploadStatusText(context, state.status, state.percentage),
            progress = state.percentage,
            indeterminate = state.status == MomentUploadActivityAttributes.ContentState.STATUS_PROCESSING,
            largeIcon = preview,
            ongoing = state.status != MomentUploadActivityAttributes.ContentState.STATUS_COMPLETED &&
                state.status != MomentUploadActivityAttributes.ContentState.STATUS_FAILED,
        )
        notify(context, momentNotificationId(attributes.momentId), notification)
        if (state.status == MomentUploadActivityAttributes.ContentState.STATUS_COMPLETED ||
            state.status == MomentUploadActivityAttributes.ContentState.STATUS_FAILED
        ) {
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
        val notification = buildNotification(
            context = context,
            notificationId = storyNotificationId(attributes.storyId),
            title = context.getString(R.string.upload_progress_story_title),
            body = uploadStatusText(context, state.status, state.percentage),
            progress = state.percentage,
            indeterminate = state.status == StoryUploadActivityAttributes.ContentState.STATUS_PROCESSING,
            largeIcon = preview,
            ongoing = state.status != StoryUploadActivityAttributes.ContentState.STATUS_COMPLETED &&
                state.status != StoryUploadActivityAttributes.ContentState.STATUS_FAILED,
        )
        notify(context, storyNotificationId(attributes.storyId), notification)
        if (state.status == StoryUploadActivityAttributes.ContentState.STATUS_COMPLETED ||
            state.status == StoryUploadActivityAttributes.ContentState.STATUS_FAILED
        ) {
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

    private fun uploadStatusText(context: Context, status: String, percentage: Int): String = when (status) {
        MomentUploadActivityAttributes.ContentState.STATUS_UPLOADING ->
            context.getString(R.string.upload_progress_status_uploading, percentage)
        MomentUploadActivityAttributes.ContentState.STATUS_PROCESSING ->
            context.getString(R.string.upload_progress_status_processing)
        MomentUploadActivityAttributes.ContentState.STATUS_COMPLETED ->
            context.getString(R.string.upload_progress_status_completed)
        MomentUploadActivityAttributes.ContentState.STATUS_FAILED ->
            context.getString(R.string.upload_progress_status_failed)
        else -> context.getString(R.string.upload_progress_status_uploading, percentage)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
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
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (indeterminate) builder.setProgress(0, 0, true)
        else builder.setProgress(100, progress.coerceIn(0, 100), false)
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
