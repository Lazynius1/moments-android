package com.moments.android.services.persistence

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Equivalente no bloqueante de `Conversation.unreadCount` en iOS.
 *
 * La conversación solo necesita un valor síncrono para dibujarse. El conteo real se carga desde
 * Room fuera del hilo principal y este mapa observable hace que Compose actualice la fila cuando
 * llega el resultado. Mientras carga se conserva el fallback de iOS: una conversación marcada
 * como no leída muestra al menos un mensaje.
 */
object UnreadMessageCountStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val counts = mutableStateMapOf<String, Int>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val conversationVersions = ConcurrentHashMap<String, Long>()
    private val globalVersion = AtomicLong(0)

    fun countFor(
        conversationId: String,
        currentUserId: String,
        lastReadAt: Date?,
    ): Int {
        val key = key(conversationId, currentUserId, lastReadAt)
        val cached = counts[key]
        if (cached == null && inFlight.add(key)) {
            val conversationVersion = conversationVersions[conversationId] ?: 0L
            val currentGlobalVersion = globalVersion.get()
            scope.launch {
                try {
                    val resolved = withContext(Dispatchers.IO) {
                        LocalPersistenceService.unreadMessageCountAsync(
                            conversationId = conversationId,
                            currentUserId = currentUserId,
                            lastReadAt = lastReadAt,
                        )
                    }
                    if (
                        (conversationVersions[conversationId] ?: 0L) == conversationVersion &&
                        globalVersion.get() == currentGlobalVersion
                    ) {
                        counts[key] = resolved
                    }
                } finally {
                    inFlight.remove(key)
                }
            }
        }
        return cached?.takeIf { it > 0 } ?: 1
    }

    fun invalidate(conversationId: String) {
        conversationVersions.merge(conversationId, 1L, Long::plus)
        scope.launch {
            val prefix = "$conversationId|"
            counts.keys.filter { it.startsWith(prefix) }.forEach(counts::remove)
            inFlight.filter { it.startsWith(prefix) }.forEach(inFlight::remove)
        }
    }

    fun invalidateAll() {
        globalVersion.incrementAndGet()
        conversationVersions.clear()
        scope.launch {
            counts.clear()
            inFlight.clear()
        }
    }

    private fun key(conversationId: String, currentUserId: String, lastReadAt: Date?): String =
        "$conversationId|$currentUserId|${lastReadAt?.time ?: 0L}"
}
