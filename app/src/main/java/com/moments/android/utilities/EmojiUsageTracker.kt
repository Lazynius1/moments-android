package com.moments.android.utilities

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Defaults de reacciones; story usa iconos de ReactionType iOS (no portado aún en Models). */
object EmojiReactionDefaults {
    val chat: List<String> = listOf("❤️", "😂", "😮", "😢", "😡", "👍")

    /** Iconos de ReactionType.allCases (reacciones.swift). */
    val story: List<String> = listOf(
        "✌🏻", "🔥", "✅", "😊", "✨", "❤️",
        "💕", "😮", "😂", "😢", "🙏🏻", "⚡",
        "🧠", "🎨", "😌", "🎉",
    )

    val emojiSlider: List<String> = listOf("😍", "🔥", "😂", "🥹", "❤️", "👏", "🙌", "💯")
}

object EmojiUsageStore {
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefs() = appContext?.getSharedPreferences("emoji_usage", Context.MODE_PRIVATE)
        ?: error("EmojiUsageStore.initialize(context) required")

    private fun storageKey(userId: String): String =
        "emojiUsage_${userId.ifEmpty { "guest" }}"

    private fun readCounts(userId: String): MutableMap<String, Int> {
        val json = prefs().getString(storageKey(userId), null) ?: return mutableMapOf()
        return runCatching {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key ->
                    put(key, obj.getInt(key))
                }
            }.toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun loadCounts(userId: String): MutableMap<String, Int> {
        migrateLegacySliderUsageIfNeeded(userId)
        return readCounts(userId)
    }

    private fun saveCounts(counts: Map<String, Int>, userId: String) {
        val obj = JSONObject()
        counts.forEach { (emoji, count) -> obj.put(emoji, count) }
        prefs().edit().putString(storageKey(userId), obj.toString()).apply()
    }

    fun increment(emoji: String, userId: String? = FirebaseAuth.getInstance().currentUser?.uid) {
        val resolvedUserId = userId.orEmpty()
        if (emoji.isEmpty()) return
        val counts = loadCounts(resolvedUserId)
        counts[emoji] = (counts[emoji] ?: 0) + 1
        saveCounts(counts, resolvedUserId)
    }

    fun recentlyUsed(
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
        limit: Int = 8,
    ): List<String> {
        val resolvedUserId = userId.orEmpty()
        return loadCounts(resolvedUserId)
            .filter { it.value > 0 }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }
    }

    fun ordered(
        defaults: List<String>,
        userId: String? = FirebaseAuth.getInstance().currentUser?.uid,
        limit: Int? = null,
    ): List<String> {
        val resolvedUserId = userId.orEmpty()
        val counts = loadCounts(resolvedUserId)
        val sorted = defaults.sortedWith { lhs, rhs ->
            val leftCount = counts[lhs] ?: 0
            val rightCount = counts[rhs] ?: 0
            if (leftCount != rightCount) return@sortedWith rightCount.compareTo(leftCount)
            defaults.indexOf(lhs).compareTo(defaults.indexOf(rhs))
        }
        return if (limit != null) sorted.take(limit) else sorted
    }

    private fun migrateLegacySliderUsageIfNeeded(userId: String) {
        if (userId.isEmpty()) return
        val legacyKey = "storyEditor.emojiSliderUsage.$userId"
        val legacyJson = prefs().getString(legacyKey, null) ?: return
        val legacy = runCatching {
            val obj = JSONObject(legacyJson)
            buildMap {
                obj.keys().forEach { key ->
                    put(key, obj.getInt(key))
                }
            }
        }.getOrNull() ?: return
        if (legacy.isEmpty()) return
        val counts = readCounts(userId)
        legacy.forEach { (emoji, count) ->
            counts[emoji] = (counts[emoji] ?: 0) + count
        }
        saveCounts(counts, userId)
        prefs().edit().remove(legacyKey).apply()
    }
}

class EmojiUsageTracker(userId: String? = null) {
    private val userId: String = userId ?: FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun increment(emoji: String) {
        EmojiUsageStore.increment(emoji, userId)
        _revision.value += 1
    }

    fun recentlyUsed(limit: Int = 8): List<String> {
        _revision.value // observe revision
        return EmojiUsageStore.recentlyUsed(userId, limit)
    }

    fun orderedEmojis(defaults: List<String>, limit: Int? = null): List<String> {
        _revision.value
        return EmojiUsageStore.ordered(defaults, userId, limit)
    }
}
