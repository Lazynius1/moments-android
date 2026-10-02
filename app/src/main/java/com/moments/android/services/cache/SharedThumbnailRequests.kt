package com.moments.android.services.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shares work without making its lifetime depend on any one visible cell. */
internal class SharedThumbnailRequests<T>(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<T>>()

    suspend fun get(key: String, generate: suspend () -> T): T {
        val request = mutex.withLock {
            inFlight[key] ?: scope.async {
                try {
                    generate()
                } finally {
                    mutex.withLock { inFlight.remove(key) }
                }
            }.also { inFlight[key] = it }
        }
        return request.await()
    }
}
