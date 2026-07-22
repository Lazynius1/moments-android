package com.moments.android.views.messaging.services

import com.moments.android.services.persistence.LocalPersistenceService

/**
 * Stub mínimo de ChatSessionEngine.swift para supresión de banners y fallback listeners.
 * Sesiones ViewModel completas viven en Messaging/Views.
 */
object ChatSessionEngine {
    @Volatile
    var activeConversationId: String? = null

    @Volatile
    private var ownerUserId: String? = null

    private val fallbackConversationIds = mutableSetOf<String>()

    fun setActiveConversation(conversationId: String?) {
        reconcileCurrentUser()
        activeConversationId = conversationId?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun activate(conversationId: String) {
        reconcileCurrentUser()
        activeConversationId = conversationId
    }

    fun deactivate(conversationId: String) {
        reconcileCurrentUser()
        if (activeConversationId == conversationId) {
            activeConversationId = null
        }
    }

    fun registerFallbackConversation(conversationId: String) {
        if (conversationId.isBlank()) return
        synchronized(fallbackConversationIds) {
            fallbackConversationIds.add(conversationId)
            while (fallbackConversationIds.size > 5) {
                fallbackConversationIds.remove(fallbackConversationIds.first())
            }
        }
    }

    fun notificationConversationIdsForFallback(): List<String> {
        val ids = linkedSetOf<String>()
        activeConversationId?.let { ids.add(it) }
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val cachedConversations = LocalPersistenceService.loadConversations()
        cachedConversations
            .filter { !(it.readStatus[currentUserId] ?: true) }
            .mapNotNull { it.id }
            .forEach { ids.add(it) }
        if (ids.isEmpty()) {
            cachedConversations.take(5).mapNotNull { it.id }.forEach { ids.add(it) }
        }
        synchronized(fallbackConversationIds) {
            fallbackConversationIds.forEach { ids.add(it) }
        }
        return ids.take(5)
    }

    /** Paridad con ChatSessionEngine.invalidateAll() en iOS (sign-out / cambio de UID). */
    fun resetOnSignOut() {
        activeConversationId = null
        ownerUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        synchronized(fallbackConversationIds) {
            fallbackConversationIds.clear()
        }
    }

    fun invalidateSession(conversationId: String) {
        if (activeConversationId == conversationId) {
            activeConversationId = null
        }
        synchronized(fallbackConversationIds) {
            fallbackConversationIds.remove(conversationId)
        }
    }

    private fun reconcileCurrentUser() {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (ownerUserId == currentUserId) return
        if (ownerUserId == null) {
            ownerUserId = currentUserId
            return
        }
        resetOnSignOut()
    }
}
