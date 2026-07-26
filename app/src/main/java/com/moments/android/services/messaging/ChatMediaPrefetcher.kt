package com.moments.android.services.messaging

import com.google.firebase.auth.FirebaseAuth
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.services.ChatService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Port de `ChatMediaPrefetcher.swift` — precarga proactiva de media de chat.
 * Reutiliza el resolver cifrado (`ChatMediaDownloadPolicy` + cuota); aquí solo se decide
 * *qué* precargar y se acota la concurrencia (`maxConcurrent = 3`), como en iOS.
 */
object ChatMediaPrefetcher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val inFlight = mutableSetOf<String>()
    private val pending = ArrayDeque<EnhancedMessage>()
    private var activeCount = 0
    private const val MAX_CONCURRENT = 3

    /**
     * Encola la media descargable de estos mensajes. No-op si la política no permite
     * descargar ahora (p. ej. wifi-only en celular). El trabajo corre en un scope propio
     * (equivalente al `Task {}` de iOS) para no bloquear al llamante.
     */
    fun prefetchIfNeeded(messages: List<EnhancedMessage>) {
        if (!ChatMediaDownloadPolicy.shouldDownloadAutomatically()) return
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
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

    /** Equivalente a `pump()` de iOS: hasta `MAX_CONCURRENT` Tasks en paralelo. */
    private suspend fun pump() {
        while (true) {
            val message = mutex.withLock {
                if (activeCount >= MAX_CONCURRENT || pending.isEmpty()) return@withLock null
                activeCount += 1
                pending.removeFirst()
            } ?: break
            scope.launch {
                // El resolver descarga, descifra, escribe a disco y aplica cuota.
                // Devuelve null sin efecto si la política bloquea ese fichero concreto.
                runCatching { ChatService.encryptedMediaResolver.resolveForMessage(message) }
                finish(message.id)
            }
        }
    }

    private suspend fun finish(messageId: String) {
        mutex.withLock {
            inFlight.remove(messageId)
            activeCount = maxOf(0, activeCount - 1)
        }
        pump()
    }
}
