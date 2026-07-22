package com.moments.android.services.cache

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/** Port de UserCacheService.swift — memory warning vía [ComponentCallbacks2] (paridad `momentsDidReceiveMemoryWarning`). */
object UserCacheService {

    @Volatile private var memoryCallbacksRegistered = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val userCache = ConcurrentHashMap<String, AppUser>()
    private val lastFetchTimes = ConcurrentHashMap<String, Date>()
    private val pendingFetches = ConcurrentHashMap<String, MutableList<(AppUser?) -> Unit>>()
    private val mutex = Mutex()
    private const val CACHE_EXPIRATION_SECONDS = 300.0
    private const val MAX_CACHED_USERS = 500

    fun initialize(context: Context) {
        if (memoryCallbacksRegistered) return
        synchronized(this) {
            if (memoryCallbacksRegistered) return
            context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: Configuration) = Unit

                override fun onLowMemory() {
                    handleMemoryWarning()
                }

                override fun onTrimMemory(level: Int) {
                    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                        handleMemoryWarning()
                    }
                }
            })
            memoryCallbacksRegistered = true
        }
    }

    fun getUser(userId: String, completion: (AppUser?) -> Unit) {
        val lastFetch = lastFetchTimes[userId]
        val cached = userCache[userId]
        if (cached != null && lastFetch != null &&
            (Date().time - lastFetch.time) / 1000.0 < CACHE_EXPIRATION_SECONDS
        ) {
            completion(cached)
            return
        }

        scope.launch {
            mutex.withLock {
                val existing = pendingFetches[userId]
                if (existing != null) {
                    existing.add(completion)
                    return@launch
                }
                pendingFetches[userId] = mutableListOf(completion)
            }

            val result = runCatching { FirestoreService().fetchUser(userId) }.getOrNull()
            val callbacks: List<(AppUser?) -> Unit>
            mutex.withLock {
                callbacks = pendingFetches.remove(userId).orEmpty()
                if (result != null) {
                    userCache[userId] = result
                    lastFetchTimes[userId] = Date()
                    evictIfNeeded()
                }
            }
            val fallback = result ?: userCache[userId]
            callbacks.forEach { it(fallback) }
        }
    }

    fun getCachedUser(userId: String): AppUser? = userCache[userId]

    fun preloadUsers(userIds: List<String>) {
        for (userId in userIds) {
            if (userCache[userId] == null) getUser(userId) { }
        }
    }

    fun handleMemoryWarning() {
        userCache.clear()
        lastFetchTimes.clear()
    }

    fun clearCache() {
        userCache.clear()
        lastFetchTimes.clear()
        pendingFetches.clear()
    }

    fun refreshUser(userId: String, completion: (AppUser?) -> Unit) {
        lastFetchTimes.remove(userId)
        getUser(userId, completion)
    }

    private fun evictIfNeeded() {
        if (userCache.size <= MAX_CACHED_USERS) return
        val overflow = userCache.size - MAX_CACHED_USERS
        val sorted = lastFetchTimes.entries.sortedBy { it.value }
        for ((userId, _) in sorted.take(overflow)) {
            userCache.remove(userId)
            lastFetchTimes.remove(userId)
        }
    }
}
