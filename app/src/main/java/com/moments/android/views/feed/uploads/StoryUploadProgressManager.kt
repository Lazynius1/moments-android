package com.moments.android.views.feed.uploads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Port de `StoryUploadProgressManager.swift` + estado de subidas del feed. */
object StoryUploadProgressManager {
    var isUploading by mutableStateOf(false)
    var progress by mutableStateOf(0.0)
    var activeUploads by mutableStateOf<List<UploadProgressItem>>(emptyList())

    fun startUpload() {
        isUploading = true
        progress = 0.0
    }

    fun updateProgress(value: Double) {
        progress = value.coerceIn(0.0, 1.0)
        activeUploads = activeUploads.map {
            if (it.kind == UploadKind.Story && it.status == UploadStatus.Uploading) {
                it.copy(progress = progress)
            } else {
                it
            }
        }
    }

    fun finishUpload() {
        isUploading = false
        progress = 1.0
    }

    fun cancelUpload() {
        isUploading = false
        progress = 0.0
        activeUploads = emptyList()
    }

    fun trackStoryUpload(id: String = java.util.UUID.randomUUID().toString(), initialProgress: Double = 0.0) {
        startUpload()
        activeUploads = activeUploads + UploadProgressItem(
            id = id,
            kind = UploadKind.Story,
            progress = initialProgress,
            status = UploadStatus.Uploading,
        )
    }

    fun complete(id: String) {
        finishUpload()
        activeUploads = activeUploads.map {
            if (it.id == id) it.copy(progress = 1.0, status = UploadStatus.Completed) else it
        }
    }

    fun remove(id: String) {
        activeUploads = activeUploads.filter { it.id != id }
        if (activeUploads.isEmpty()) cancelUpload()
    }
}

enum class UploadKind { Moment, Story }

enum class UploadStatus { Initializing, Uploading, Processing, Completed, Failed, Moderated }

data class UploadProgressItem(
    val id: String,
    val kind: UploadKind,
    val progress: Double,
    val status: UploadStatus = UploadStatus.Uploading,
    val fileCount: Int = 1,
    val content: String = "",
    val thumbnailUrl: String? = null,
)

object MomentUploadTracker {
    private val _items = mutableStateListOf<UploadProgressItem>()
    val items: List<UploadProgressItem> get() = _items

    fun upsert(item: UploadProgressItem) {
        val index = _items.indexOfFirst { it.id == item.id }
        if (index >= 0) _items[index] = item else _items.add(item)
    }

    fun remove(id: String) {
        _items.removeAll { it.id == id }
    }
}
