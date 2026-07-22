package com.moments.android.services.social

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.UserAffinity
import org.json.JSONObject
import java.util.Calendar
import java.util.Date

enum class AffinityInteractionType(val raw: String, val scoreValue: Double) {
    DIRECT_MESSAGE("directMessage", 10.0),
    STORY_REACTION("storyReaction", 5.0),
    MOMENT_REACTION("momentReaction", 5.0),
    MOMENT_COMMENT("momentComment", 5.0),
    PROFILE_VISIT("profileVisit", 2.0),
    MOMENT_VIEW("momentView", 1.0),
}

/**
 * Port de AffinityTracker.swift.
 * SwiftData → SharedPreferences JSON (Room al completar Persistence).
 */
object AffinityTracker {
    private const val PREFS = "moments_affinity"

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefs() =
        (appContext ?: error("AffinityTracker.initialize required"))
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun trackInteraction(type: AffinityInteractionType, withTargetUserId: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotEmpty() } ?: return
        if (currentUserId == withTargetUserId) return
        val key = UserAffinity.makeAffinityKey(currentUserId, withTargetUserId)
        val existing = load(key)
        val counts = existing?.interactionCounts?.toMutableMap() ?: mutableMapOf()
        counts[type.raw] = (counts[type.raw] ?: 0) + 1
        val updated = UserAffinity(
            ownerUserId = currentUserId,
            targetUserId = withTargetUserId,
            score = (existing?.score ?: 0.0) + type.scoreValue,
            lastInteractionDate = Date(),
            interactionCounts = counts,
        )
        save(updated)
    }

    fun loadAffinity(ownerUserId: String, targetUserId: String): UserAffinity? =
        load(UserAffinity.makeAffinityKey(ownerUserId, targetUserId))

    fun getScore(forTargetUserId: String): Double {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotEmpty() } ?: return 0.0
        return loadAffinity(currentUserId, forTargetUserId)?.score ?: 0.0
    }

    /** Batch fetch (paridad getScores(for:in:) iOS). */
    fun getScores(forTargetUserIds: List<String>): Map<String, Double> {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotEmpty() } ?: return emptyMap()
        val uniqueIds = forTargetUserIds.filter { it.isNotEmpty() }.distinct()
        if (uniqueIds.isEmpty()) return emptyMap()
        return uniqueIds.associateWith { targetId ->
            loadAffinity(currentUserId, targetId)?.score ?: 0.0
        }
    }

    /** Reduce scores for interactions older than 3 days (15% decay). */
    fun applyTimeDecayIfNeeded() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -3)
        val decayThreshold = cal.time
        val all = loadAllForCurrentOwner()
        var didUpdate = false
        for (affinity in all) {
            if (affinity.lastInteractionDate.before(decayThreshold)) {
                save(
                    affinity.copy(
                        score = affinity.score * 0.85,
                        lastInteractionDate = Date(),
                    ),
                )
                didUpdate = true
            }
        }
        if (didUpdate) {
            android.util.Log.d("AffinityTracker", "Applied time-decay to stale affinities")
        }
    }

    /** Removes low-value affinities after decay (default minScore 0.5, olderThan 90 days). */
    fun cleanupVeryLowAffinities(minScore: Double = 0.5, olderThanDays: Int = 90) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -olderThanDays)
        val cutoff = cal.time
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val prefix = "$currentUserId|"
        val editor = prefs().edit()
        var removed = 0
        for ((key, value) in prefs().all) {
            if (!key.startsWith(prefix) || value !is String) continue
            val affinity = decode(value) ?: continue
            if (affinity.score < minScore && affinity.lastInteractionDate.before(cutoff)) {
                editor.remove(key)
                removed++
            }
        }
        if (removed > 0) {
            editor.apply()
            android.util.Log.d("AffinityTracker", "Cleaned $removed low-value affinities")
        }
    }

    fun topAffinities(ownerUserId: String, limit: Int = 20): List<UserAffinity> {
        val prefix = "$ownerUserId|"
        return prefs().all.mapNotNull { (k, v) ->
            if (!k.startsWith(prefix) || v !is String) return@mapNotNull null
            decode(v)
        }.sortedByDescending { it.score }.take(limit)
    }

    private fun loadAllForCurrentOwner(): List<UserAffinity> {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()
        val prefix = "$currentUserId|"
        return prefs().all.mapNotNull { (k, v) ->
            if (!k.startsWith(prefix) || v !is String) return@mapNotNull null
            decode(v)
        }
    }

    private fun load(key: String): UserAffinity? {
        val raw = prefs().getString(key, null) ?: return null
        return decode(raw)
    }

    private fun save(affinity: UserAffinity) {
        prefs().edit().putString(affinity.affinityKey, encode(affinity)).apply()
    }

    private fun encode(a: UserAffinity): String = JSONObject().apply {
        put("ownerUserId", a.ownerUserId)
        put("targetUserId", a.targetUserId)
        put("score", a.score)
        put("lastInteractionDate", a.lastInteractionDate.time)
        put("interactionCounts", JSONObject(a.interactionCounts.mapValues { it.value }))
    }.toString()

    private fun decode(raw: String): UserAffinity? = runCatching {
        val json = JSONObject(raw)
        val countsObj = json.optJSONObject("interactionCounts")
        val counts = mutableMapOf<String, Int>()
        countsObj?.keys()?.forEach { k -> counts[k] = countsObj.getInt(k) }
        UserAffinity(
            ownerUserId = json.getString("ownerUserId"),
            targetUserId = json.getString("targetUserId"),
            score = json.getDouble("score"),
            lastInteractionDate = Date(json.optLong("lastInteractionDate", System.currentTimeMillis())),
            interactionCounts = counts,
        )
    }.getOrNull()
}
