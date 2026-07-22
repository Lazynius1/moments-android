package com.moments.android.services.messaging

import java.util.Date
import java.util.concurrent.TimeUnit

/** Port de VanishMessageTimer.swift. */
enum class VanishMessageTimer(val raw: String) {
    ONCE_SEEN("onceSeen"),
    HOURS_24("hours24"),
    DAYS_7("days7");

    val id: String get() = raw

    companion object {
        val DEFAULT: VanishMessageTimer = HOURS_24

        fun fromStored(storedValue: String?): VanishMessageTimer =
            storedValue?.let { raw -> entries.firstOrNull { it.raw == raw } } ?: DEFAULT

        const val DISABLED_NOTICE_TOKEN = "disappearing:disabled"
        const val SCREENSHOT_NOTICE_TOKEN = "disappearing:screenshot"
        const val SCREEN_RECORDING_NOTICE_TOKEN = "disappearing:screenRecording"

        fun parseEnabledNotice(content: String): VanishMessageTimer? {
            val parts = content.split(":").map { it.trim() }
            if (parts.size != 3 || parts[0] != "disappearing" || parts[1] != "enabled") return null
            return entries.firstOrNull { it.raw == parts[2] }
        }

        fun isExpired(expiresAt: Date?): Boolean {
            if (expiresAt == null) return false
            return Date().time >= expiresAt.time
        }
    }

    val localizationKey: String
        get() = when (this) {
            ONCE_SEEN -> "chat.vanish.timer.onceSeen"
            HOURS_24 -> "chat.vanish.timer.24h"
            DAYS_7 -> "chat.vanish.timer.7d"
        }

    val enabledNoticeToken: String get() = "disappearing:enabled:$raw"

    /** Offset desde el ancla "everyone has seen" (no desde el envío). */
    fun expiresAt(from: Date = Date()): Date? = when (this) {
        ONCE_SEEN -> null
        HOURS_24 -> Date(from.time + TimeUnit.HOURS.toMillis(24))
        DAYS_7 -> Date(from.time + TimeUnit.DAYS.toMillis(7))
    }
}
