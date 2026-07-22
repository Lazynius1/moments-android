package com.moments.android.services.messaging

import android.content.Context
import com.moments.android.services.network.NetworkMonitor

/** Port de ChatMediaDownloadPolicy.swift. */
enum class ChatMediaAutoDownload(val raw: String) {
    WIFI_ONLY("wifiOnly"), ALWAYS("always"), NEVER("never");

    val titleKey: String
        get() = when (this) {
            WIFI_ONLY -> "settings.chatStorage.autoDownload.wifi"
            ALWAYS -> "settings.chatStorage.autoDownload.always"
            NEVER -> "settings.chatStorage.autoDownload.never"
        }

    companion object {
        fun from(raw: String?): ChatMediaAutoDownload? = entries.firstOrNull { it.raw == raw }
    }
}

enum class ChatMediaRetention(val days: Int) {
    DAYS_7(7), DAYS_30(30), DAYS_90(90), FOREVER(0);

    val titleKey: String
        get() = when (this) {
            DAYS_7 -> "settings.chatStorage.retention.7days"
            DAYS_30 -> "settings.chatStorage.retention.30days"
            DAYS_90 -> "settings.chatStorage.retention.90days"
            FOREVER -> "settings.chatStorage.retention.forever"
        }

    companion object {
        fun from(days: Int): ChatMediaRetention = entries.firstOrNull { it.days == days } ?: DAYS_30
    }
}

object ChatMediaDownloadPolicy {
    private const val PREFS = "chat_media_download_policy"
    private const val AUTO_DOWNLOAD_KEY = "chat_media_auto_download"
    private const val RETENTION_DAYS_KEY = "chat_media_retention_days"
    private const val MAX_BYTES_KEY = "chat_media_max_bytes"

    const val DEFAULT_MAX_BYTES: Long = 1_610_612_736L // 1.5 GB

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefs() =
        (appContext ?: error("ChatMediaDownloadPolicy.initialize required"))
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var autoDownload: ChatMediaAutoDownload
        get() {
            val raw = prefs().getString(AUTO_DOWNLOAD_KEY, null) ?: return ChatMediaAutoDownload.WIFI_ONLY
            return ChatMediaAutoDownload.from(raw) ?: ChatMediaAutoDownload.WIFI_ONLY
        }
        set(value) { prefs().edit().putString(AUTO_DOWNLOAD_KEY, value.raw).apply() }

    var retention: ChatMediaRetention
        get() {
            val p = prefs()
            if (!p.contains(RETENTION_DAYS_KEY)) return ChatMediaRetention.DAYS_30
            return ChatMediaRetention.from(p.getInt(RETENTION_DAYS_KEY, 30))
        }
        set(value) { prefs().edit().putInt(RETENTION_DAYS_KEY, value.days).apply() }

    val retentionDays: Int get() = retention.days

    var maxMediaBytes: Long
        get() = prefs().getLong(MAX_BYTES_KEY, DEFAULT_MAX_BYTES)
        set(value) { prefs().edit().putLong(MAX_BYTES_KEY, value).apply() }

    fun shouldDownloadAutomatically(force: Boolean = false): Boolean {
        if (force) return true
        return when (autoDownload) {
            ChatMediaAutoDownload.NEVER -> false
            ChatMediaAutoDownload.ALWAYS -> NetworkMonitor.isConnected
            ChatMediaAutoDownload.WIFI_ONLY -> {
                if (!NetworkMonitor.isConnected) return false
                when (NetworkMonitor.connectionType) {
                    NetworkMonitor.ConnectionType.WIFI,
                    NetworkMonitor.ConnectionType.ETHERNET -> true
                    NetworkMonitor.ConnectionType.CELLULAR,
                    NetworkMonitor.ConnectionType.UNKNOWN -> false
                }
            }
        }
    }

    fun shouldDownloadThumbnailPreview(force: Boolean = false): Boolean {
        if (force) return true
        return NetworkMonitor.isConnected
    }
}
