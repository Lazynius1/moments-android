package com.moments.android.services.persistence

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Port de StorySeenStateService (final de LocalPersistenceService.swift).
 * Cache local del último timestamp de story vista por autor y viewer.
 */
object StorySeenStateService {

    private const val STORAGE_KEY = "story_last_seen_by_author_v1"
    private const val MAX_AGE_MS = 6L * 60 * 60 * 1000
    private const val REMOTE_CACHE_TTL_MS = 60_000L

    private var prefs: SharedPreferences? = null
    private var loaded = false
    private val lastSeenMap = mutableMapOf<String, Double>()
    private val remoteCache = ConcurrentHashMap<String, Pair<Date?, Long>>()
    private val inFlight = ConcurrentHashMap<String, MutableList<(Date?) -> Unit>>()
    private val lock = Any()

    fun initialize(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("moments_story_seen", Context.MODE_PRIVATE)
    }

    private fun compositeKey(viewerId: String, authorId: String) = "$viewerId|$authorId"

    private fun ensureLoaded() {
        if (loaded) return
        val stored = prefs?.all ?: emptyMap()
        for ((key, value) in stored) {
            when (value) {
                is Number -> lastSeenMap[key] = value.toDouble()
                is String -> value.toDoubleOrNull()?.let { lastSeenMap[key] = it }
            }
        }
        // Prefer dedicated map under STORAGE_KEY if present as encoded pairs — iOS uses dictionary forKey.
        // Android SharedPreferences stores flat; we use prefs file as the dictionary itself.
        loaded = true
    }

    private fun persistLocked() {
        val editor = prefs?.edit() ?: return
        editor.clear()
        for ((k, v) in lastSeenMap) {
            editor.putFloat(k, v.toFloat())
        }
        editor.apply()
    }

    private fun localLastSeenDateLocked(viewerId: String, authorId: String): Date? {
        ensureLoaded()
        val key = compositeKey(viewerId, authorId)
        val timestamp = lastSeenMap[key] ?: return null
        if (System.currentTimeMillis() / 1000.0 - timestamp > MAX_AGE_MS / 1000.0) {
            lastSeenMap.remove(key)
            persistLocked()
            return null
        }
        return Date((timestamp * 1000).toLong())
    }

    private fun maxDate(lhs: Date?, rhs: Date?): Date? = when {
        lhs != null && rhs != null -> if (lhs.after(rhs)) lhs else rhs
        lhs != null -> lhs
        else -> rhs
    }

    fun lastSeenDate(viewerId: String, authorId: String): Date? = synchronized(lock) {
        localLastSeenDateLocked(viewerId, authorId)
    }

    suspend fun fetchEffectiveLastSeen(viewerId: String, authorId: String): Date? =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                fetchEffectiveLastSeen(viewerId, authorId) { date ->
                    if (cont.isActive) cont.resume(date)
                }
            }
        }

    fun fetchEffectiveLastSeen(viewerId: String, authorId: String, completion: (Date?) -> Unit) {
        val key = compositeKey(viewerId, authorId)
        synchronized(lock) {
            ensureLoaded()
            val localDate = localLastSeenDateLocked(viewerId, authorId)
            val cached = remoteCache[key]
            if (cached != null && cached.second > System.currentTimeMillis()) {
                completion(maxDate(localDate, cached.first))
                return
            }
            val existing = inFlight[key]
            if (existing != null) {
                existing.add(completion)
                return
            }
            inFlight[key] = mutableListOf(completion)
        }

        FirebaseFirestore.getInstance()
            .collection("users").document(viewerId)
            .collection("storySeen").document(authorId)
            .get()
            .addOnCompleteListener { task ->
                val remoteDate = (task.result?.get("lastSeenAt") as? Timestamp)?.toDate()
                val callbacks: List<(Date?) -> Unit>
                val effective: Date?
                synchronized(lock) {
                    if (remoteDate != null) {
                        val currentValue = lastSeenMap[key] ?: 0.0
                        val remoteValue = remoteDate.time / 1000.0
                        if (remoteValue > currentValue) {
                            lastSeenMap[key] = remoteValue
                            persistLocked()
                        }
                    }
                    remoteCache[key] = remoteDate to (System.currentTimeMillis() + REMOTE_CACHE_TTL_MS)
                    val localAfter = localLastSeenDateLocked(viewerId, authorId)
                    effective = maxDate(localAfter, remoteDate)
                    callbacks = inFlight.remove(key).orEmpty()
                }
                callbacks.forEach { it(effective) }
            }
    }

    fun markSeen(viewerId: String, authorId: String, timestamp: Date, syncRemote: Boolean = false) {
        val key = compositeKey(viewerId, authorId)
        var shouldSync = false
        var timestampToSync = timestamp
        synchronized(lock) {
            ensureLoaded()
            val newValue = timestamp.time / 1000.0
            val currentValue = lastSeenMap[key] ?: 0.0
            val effectiveValue = maxOf(newValue, currentValue)
            if (effectiveValue > currentValue) {
                lastSeenMap[key] = effectiveValue
                persistLocked()
            }
            timestampToSync = Date((effectiveValue * 1000).toLong())
            remoteCache[key] = timestampToSync to (System.currentTimeMillis() + REMOTE_CACHE_TTL_MS)
            shouldSync = syncRemote
        }
        if (!shouldSync) return
        FirebaseFirestore.getInstance()
            .collection("users").document(viewerId)
            .collection("storySeen").document(authorId)
            .set(mapOf("lastSeenAt" to Timestamp(timestampToSync)), SetOptions.merge())
    }

    fun invalidate(viewerId: String, authorId: String) {
        synchronized(lock) {
            ensureLoaded()
            val key = compositeKey(viewerId, authorId)
            lastSeenMap.remove(key)
            remoteCache.remove(key)
            persistLocked()
        }
    }

    fun supportsShortcut(forAudience: String?): Boolean {
        val normalized = forAudience?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return true
        return when (normalized) {
            "everyone", "mutuals" -> true
            else -> false
        }
    }
}
