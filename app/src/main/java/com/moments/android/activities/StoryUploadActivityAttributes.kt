package com.moments.android.activities

/**
 * Port de StoryUploadActivityAttributes.swift (ActivityKit).
 * Android: datos compartidos con [UploadProgressNotificationHelper] para notificación ongoing.
 * N/A: Live Activity widget UI en iOS.
 */
data class StoryUploadActivityAttributes(
    val storyId: String,
    val mediaType: String,
    val previewImageFileName: String? = null,
) {
    data class ContentState(
        val progress: Double,
        val status: String,
    ) {
        val percentage: Int get() = (progress * 100).toInt()

        companion object {
            const val STATUS_UPLOADING = "uploading"
            const val STATUS_PROCESSING = "processing"
            const val STATUS_COMPLETED = "completed"
            const val STATUS_FAILED = "failed"
        }
    }
}
