package com.moments.android.services.messaging

import android.content.Context
import com.moments.android.services.network.NetworkMonitor

/** Port de ChatMediaDownloadPolicy.swift — política fija, sin prefs de usuario. */
object ChatMediaDownloadPolicy {
    const val MAX_MEDIA_BYTES: Long = 1_610_612_736L // 1.5 GB
    const val RETENTION_DAYS: Int = 30

    val maxMediaBytes: Long get() = MAX_MEDIA_BYTES
    val retentionDays: Int get() = RETENTION_DAYS

    fun initialize(@Suppress("UNUSED_PARAMETER") context: Context) {}

    fun shouldDownloadAutomatically(force: Boolean = false): Boolean {
        if (force) return true
        return NetworkMonitor.isConnected
    }

    fun shouldDownloadThumbnailPreview(force: Boolean = false): Boolean {
        if (force) return true
        return NetworkMonitor.isConnected
    }
}
