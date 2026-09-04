package com.moments.android.views.nova.core

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Port de `Views/Nova/Core/NovaWelcomeSparkStore.swift`. Cache local diaria de la chispa. */
object NovaWelcomeSparkStore {
    data class Cached(val day: String, val text: String)

    fun todayKey(now: Date = Date()): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(now)
    }

    fun load(context: Context, userId: String): Cached? {
        val prefs = prefs(context)
        val day = prefs.getString(dayKey(userId), null) ?: return null
        val text = prefs.getString(textKey(userId), null)?.trim().orEmpty()
        if (text.isEmpty()) return null
        return Cached(day, text)
    }

    fun previousText(context: Context, userId: String): String? =
        prefs(context).getString(previousKey(userId), null)?.trim()?.takeIf { it.isNotEmpty() }

    fun save(context: Context, userId: String, day: String, text: String) {
        val prefs = prefs(context)
        val oldDay = prefs.getString(dayKey(userId), null)
        val oldText = prefs.getString(textKey(userId), null)?.trim().orEmpty()
        prefs.edit().apply {
            if (oldDay != null && oldDay != day && oldText.isNotEmpty()) {
                putString(previousKey(userId), oldText)
            }
            putString(dayKey(userId), day)
            putString(textKey(userId), text)
            apply()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dayKey(userId: String) = "novaWelcomeSpark.day-$userId"
    private fun textKey(userId: String) = "novaWelcomeSpark.text-$userId"
    private fun previousKey(userId: String) = "novaWelcomeSpark.previous-$userId"

    private const val PREFS = "nova_welcome_spark"
}
