package com.moments.android.views.messaging.services

import com.google.firebase.auth.FirebaseAuth
import com.moments.android.MomentsApplication
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.notifications.services.InAppNotificationService
import com.moments.android.services.messaging.LocalFirstMessagingSettings
import com.moments.android.services.messaging.MessageCatchUpService
import com.moments.android.services.messaging.MessageIngestService
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.views.messaging.screens.chat.MomentsChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Port de `ChatSessionEngine.swift` — caché de sesiones de chat.
 *
 * [session] devuelve el ViewModel ya existente de una conversación, de modo que reabrirla reutiliza
 * los mensajes cargados, los listeners y el scroll en vez de reconstruirlo todo. Antes era un stub
 * que sólo guardaba el id activo, y `GlassmorphicChatView` creaba el VM con `remember`: cada entrada
 * al chat empezaba de cero.
 *
 * Diferencia consciente con iOS: `activeConversationId` no se publica como observable porque en
 * Android nadie lo observa; se lee directamente.
 */
object ChatSessionEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()
    private val sessions = mutableMapOf<String, MomentsChatViewModel>()
    private val conversationById = mutableMapOf<String, Conversation>()
    private const val MAX_CACHED_SESSIONS = 10

    @Volatile private var ownerUserId: String? = null

    @Volatile
    var activeConversationId: String? = null
        private set

    private val currentUserId: String get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    /**
     * Sesión cacheada de la conversación; se crea si no existía. Las conversaciones en borrador
     * (sin id) no se cachean: devuelven un VM nuevo hasta que se materializan.
     */
    fun session(conversation: Conversation): MomentsChatViewModel {
        val conversationId = conversation.id
        if (conversationId.isNullOrEmpty()) {
            return MomentsChatViewModel(conversation, currentUserId)
        }
        reconcileCurrentUser()

        synchronized(lock) {
            conversationById[conversationId] = conversation
            sessions[conversationId]?.let { existing ->
                existing.mergeConversationReadMetadata(conversation)
                return existing
            }
            trimSessionCache(conversationId)
        }

        val session = MomentsChatViewModel(conversation, currentUserId)
        synchronized(lock) { sessions[conversationId] = session }
        session.loadCachedMessagesIfNeeded()
        return session
    }

    /** Sesión ya cacheada (sin crear). Para sync desde settings/inbox. */
    fun cachedSession(conversationId: String): MomentsChatViewModel? {
        if (conversationId.isBlank()) return null
        reconcileCurrentUser()
        return synchronized(lock) { sessions[conversationId] }
    }

    /**
     * Port de `registerMaterializedSession`: una sesión abierta como borrador (sin id) entra al
     * caché al materializarse su conversación, para que las siguientes aperturas la reutilicen.
     */
    fun registerMaterializedSession(session: MomentsChatViewModel, conversationId: String) {
        if (conversationId.isEmpty()) return
        reconcileCurrentUser()
        synchronized(lock) {
            conversationById[conversationId] = session.conversation
            if (sessions[conversationId] == null) {
                trimSessionCache(conversationId)
                sessions[conversationId] = session
            }
        }
    }

    /** Calienta las sesiones de las conversaciones más recientes. */
    fun preloadRecentSessions(conversations: List<Conversation>, limit: Int = 5) {
        reconcileCurrentUser()
        conversations.take(limit).forEach { conversation ->
            val conversationId = conversation.id?.takeIf { it.isNotEmpty() } ?: return@forEach
            val created = synchronized(lock) {
                conversationById[conversationId] = conversation
                if (sessions.containsKey(conversationId)) {
                    null
                } else {
                    trimSessionCache(conversationId)
                    MomentsChatViewModel(conversation, currentUserId).also { sessions[conversationId] = it }
                }
            }
            created?.loadCachedMessagesIfNeeded()
        }
    }

    fun activate(conversationId: String) {
        reconcileCurrentUser()
        activeConversationId = conversationId
        val session = synchronized(lock) { sessions[conversationId] }
        if (session == null) {
            syncInAppFallbackListeners()
            return
        }
        session.activateChatSession()
        syncInAppFallbackListeners()

        if (LocalFirstMessagingSettings.isEnabled) {
            scope.launch { MessageCatchUpService.sync(conversationId) }
        }
    }

    fun deactivate(conversationId: String) {
        reconcileCurrentUser()
        if (activeConversationId == conversationId) activeConversationId = null
        synchronized(lock) { sessions[conversationId] }?.deactivateChatSession()
        syncInAppFallbackListeners()
    }

    /** Fija la conversación activa sin tocar sesiones (lo usaban las notificaciones). */
    fun setActiveConversation(conversationId: String?) {
        reconcileCurrentUser()
        activeConversationId = conversationId?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun invalidateAll() {
        activeConversationId = null
        val cached = synchronized(lock) {
            val values = sessions.values.toList()
            sessions.clear()
            conversationById.clear()
            values
        }
        cached.forEach { it.stopListening() }
        MomentsApplication.instance?.let { ChatScrollStateStore.clearAll(it) }
        ownerUserId = FirebaseAuth.getInstance().currentUser?.uid
        syncInAppFallbackListeners()
    }

    /** Descarta la sesión en memoria de una conversación (por ejemplo, tras borrarla del inbox). */
    fun invalidateSession(conversationId: String) {
        if (conversationId.isEmpty()) return
        if (activeConversationId == conversationId) activeConversationId = null
        val session = synchronized(lock) {
            conversationById.remove(conversationId)
            sessions.remove(conversationId)
        }
        session?.stopListening()
        syncInAppFallbackListeners()
    }

    /** Paridad con `invalidateAll()` de iOS al cerrar sesión o cambiar de UID. */
    fun resetOnSignOut() = invalidateAll()

    fun notificationConversationIdsForFallback(): List<String> {
        val ids = linkedSetOf<String>()
        activeConversationId?.let { ids.add(it) }

        val userId = currentUserId
        val cachedConversations = LocalPersistenceService.loadConversations()
        cachedConversations
            .filter { !(it.readStatus[userId] ?: true) }
            .mapNotNull { it.id }
            .forEach { ids.add(it) }

        if (ids.isEmpty()) {
            cachedConversations.take(5).mapNotNull { it.id }.forEach { ids.add(it) }
        }
        return ids.take(5)
    }

    private fun syncInAppFallbackListeners() {
        InAppNotificationService.syncFallbackListeners(notificationConversationIdsForFallback())
    }

    /** Debe llamarse con [lock] tomado. Expulsa las sesiones más antiguas por fecha. */
    private fun trimSessionCache(excluding: String) {
        if (sessions.size < MAX_CACHED_SESSIONS) return
        val evictable = sessions.values
            .filter { it.conversation.id != excluding && it.conversation.id != activeConversationId }
            // ≡ iOS: timestamp ?? .distantPast
            .sortedBy { it.conversation.timestamp?.time ?: Long.MIN_VALUE }
        evictable.take((sessions.size - MAX_CACHED_SESSIONS + 1).coerceAtLeast(0)).forEach { session ->
            val id = session.conversation.id ?: return@forEach
            session.stopListening()
            sessions.remove(id)
            conversationById.remove(id)
        }
    }

    /** Cambio de usuario: tirar sesiones y todo el estado atado a la identidad anterior. */
    private fun reconcileCurrentUser() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (ownerUserId == userId) return
        if (ownerUserId == null) {
            ownerUserId = userId
            return
        }
        activeConversationId = null
        val cached = synchronized(lock) {
            val values = sessions.values.toList()
            sessions.clear()
            conversationById.clear()
            values
        }
        cached.forEach { it.stopListening() }
        MomentsApplication.instance?.let { ChatScrollStateStore.clearAll(it) }
        ChatAccessCoordinator.invalidateAll()
        MessageIngestService.resetOnSignOut()
        MessageCatchUpService.resetOnSignOut()
        ownerUserId = userId
        syncInAppFallbackListeners()
    }
}
