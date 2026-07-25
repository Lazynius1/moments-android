package com.moments.android.services.messaging

import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageType
import com.moments.android.views.messaging.services.ChatService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Port de `ChatMediaPrefetcher.swift` — precarga proactiva de media de chat: cuando llegan mensajes
 * con media, se descarga y descifra en segundo plano (según la política de auto-descarga y la cuota)
 * para que esté lista antes de abrir la conversación.
 *
 * Reutiliza el resolver cifrado, que ya aplica `ChatMediaDownloadPolicy` y la cuota de caché; aquí
 * sólo se decide *qué* precargar y se acota la concurrencia para no saturar red/CPU.
 *
 * **Ojo al portar:** en iOS `pump()` lanza `Task {}`, o sea hasta `maxConcurrent` descargas **en
 * paralelo** y retorno inmediato. La versión anterior en Kotlin esperaba cada descarga en serie
 * dentro de `pump()` y volvía a llamarse desde `finish()`: `prefetchIfNeeded` no retornaba hasta
 * vaciar la cola entera —bloqueando la ingesta de mensajes que la invoca— y recursaba una vez por
 * elemento (500 niveles en un catch-up grande).
 */
object ChatMediaPrefetcher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val inFlight = mutableSetOf<String>()
    private val pending = ArrayDeque<EnhancedMessage>()
    private var activeCount = 0
    private const val MAX_CONCURRENT = 3

    /**
     * Encola la media descargable de estos mensajes. No bloquea al llamante: el trabajo va a un
     * scope propio, como el `Task {}` de iOS. No hace nada si la política no permite descargar
     * ahora (por ejemplo, sólo-wifi estando en datos móviles).
     */
    fun prefetchIfNeeded(messages: List<EnhancedMessage>) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            if (!ChatMediaDownloadPolicy.shouldDownloadAutomatically()) return@launch
            mutex.withLock {
                for (message in messages) {
                    if (!shouldPrefetch(message, currentUserId)) continue
                    if (!inFlight.add(message.id)) continue
                    pending.addLast(message)
                }
            }
            pump()
        }
    }

    private fun shouldPrefetch(message: EnhancedMessage, currentUserId: String): Boolean {
        if (message.isDeleted) return false
        // Los mensajes propios ya se cachean localmente al enviarse.
        if (message.senderId == currentUserId) return false
        // View-once y efímeros se abren deliberadamente: no se precachean en silencio.
        if (message.type != MessageType.IMAGE && message.type != MessageType.VIDEO) return false
        val path = message.mediaObjectPath
        if (path.isNullOrEmpty() || message.mediaEncryption == null) return false
        return true
    }

    /** Arranca tantos workers como permita el tope; cada uno vacía la cola por su cuenta. */
    private suspend fun pump() {
        val slots = mutex.withLock {
            val free = MAX_CONCURRENT - activeCount
            val take = minOf(free, pending.size).coerceAtLeast(0)
            activeCount += take
            take
        }
        repeat(slots) {
            scope.launch { drain() }
        }
    }

    private suspend fun drain() {
        while (true) {
            val message = mutex.withLock { pending.removeFirstOrNull() } ?: break
            // El resolver descarga, descifra, escribe a disco y aplica cuota.
            runCatching { ChatService.encryptedMediaResolver.resolveForMessage(message) }
            mutex.withLock { inFlight.remove(message.id) }
        }
        mutex.withLock { activeCount = maxOf(0, activeCount - 1) }
    }
}
