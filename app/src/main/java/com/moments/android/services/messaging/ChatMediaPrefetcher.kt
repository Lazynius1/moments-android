package com.moments.android.services.messaging

import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.moments.android.views.messaging.services.ChatService

/**
 * Port de ChatMediaPrefetcher.swift — precarga proactiva de media según política de auto-descarga.
 */
object ChatMediaPrefetcher {
    private val mutex = Mutex()
    private val inFlight = mutableSetOf<String>()
    private val pending = ArrayDeque<EnhancedMessage>()
    private var activeCount = 0
    private const val MAX_CONCURRENT = 3

    suspend fun prefetchIfNeeded(messages: List<EnhancedMessage>) {
        if (!ChatMediaDownloadPolicy.shouldDownloadAutomatically()) return
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        mutex.withLock {
            for (message in messages) {
                if (!shouldPrefetch(message, currentUserId)) continue
                if (message.id in inFlight) continue
                inFlight.add(message.id)
                pending.addLast(message)
            }
        }
        pump()
    }

    private fun shouldPrefetch(message: EnhancedMessage, currentUserId: String): Boolean {
        if (message.isDeleted) return false
        if (message.senderId == currentUserId) return false
        if (message.type != MessageType.IMAGE && message.type != MessageType.VIDEO) return false
        val path = message.mediaObjectPath
        if (path.isNullOrEmpty() || message.mediaEncryption == null) return false
        return true
    }

    private suspend fun pump() {
        while (true) {
            val message = mutex.withLock {
                if (activeCount >= MAX_CONCURRENT || pending.isEmpty()) return
                activeCount++
                pending.removeFirst()
            }

            runCatching {
                ChatService.encryptedMediaResolver.resolveForMessage(message)
            }
            finish(message.id)
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
