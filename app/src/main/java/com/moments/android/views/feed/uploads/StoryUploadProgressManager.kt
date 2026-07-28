package com.moments.android.views.feed.uploads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Port 1:1 de `StoryUploadProgressManager.swift`.
 * Solo `isUploading` + `progress` + start/update/finish/cancel.
 */
object StoryUploadProgressManager {
    var isUploading by mutableStateOf(false)
        private set
    var progress by mutableDoubleStateOf(0.0)
        private set

    fun startUpload() {
        isUploading = true
        progress = 0.0
    }

    fun updateProgress(value: Double) {
        progress = value
    }

    fun finishUpload() {
        isUploading = false
        progress = 1.0
    }

    fun cancelUpload() {
        isUploading = false
        progress = 0.0
    }
}

// ---------------------------------------------------------------------------
// Tipos compartidos (iOS: `UploadStatus` vive en BackgroundMomentUploadService.swift).
// UploadProgressItem / MomentUploadTracker: helpers Android para filas de progreso;
// no existen en StoryUploadProgressManager.swift.
// ---------------------------------------------------------------------------

/** ≡ iOS `UploadStatus` (BackgroundMomentUploadService.swift). */
enum class UploadStatus {
    Initializing,
    Uploading,
    Processing,
    Completed,
    Failed,
    Moderated,
}

enum class UploadKind { Moment, Story }

data class UploadProgressItem(
    val id: String,
    val kind: UploadKind,
    val progress: Double,
    val status: UploadStatus = UploadStatus.Uploading,
    val fileCount: Int = 1,
    val content: String = "",
    val thumbnailUrl: String? = null,
)

/** Tracker Android para filas de momento (no existe en iOS como tipo aparte). */
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
