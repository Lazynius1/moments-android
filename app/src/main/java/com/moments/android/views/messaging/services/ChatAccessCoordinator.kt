package com.moments.android.views.messaging.services

import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.ChatAccessState
import com.moments.android.services.messaging.EncryptionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Port 1:1 de `ChatAccessCoordinator.swift`.
 * Resuelve el acceso cripto al chat una vez por sesión vía
 * [EncryptionService.chatAccessState] (users/{uid}.chatKey + chatRecovery).
 */
object ChatAccessCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private val _accessState = MutableStateFlow<ChatAccessState?>(null)
    val accessState: StateFlow<ChatAccessState?> = _accessState.asStateFlow()

    @Volatile private var resolvedUserId: String? = null
    @Volatile private var resolveJob: Job? = null

    /** Paridad iOS `ensureAccess()` — estado cacheado o resolución por sesión. */
    suspend fun ensureAccess(): ChatAccessState = mutex.withLock {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            invalidateAll()
            return ChatAccessState.Unavailable("Chat unavailable")
        }

        if (resolvedUserId != userId) {
            invalidateAllUnlocked()
        }

        val cached = _accessState.value
        if (cached != null && resolvedUserId == userId) {
            return cached
        }

        val inFlight = resolveJob
        if (inFlight != null) {
            inFlight.join()
            val after = _accessState.value
            if (resolvedUserId == userId && after != null) return after
            return ChatAccessState.Unavailable("Chat unavailable")
        }

        val job = scope.launch {
            val state = withContext(Dispatchers.IO) {
                EncryptionService.chatAccessState()
            }
            if (FirebaseAuth.getInstance().currentUser?.uid != userId) return@launch
            resolvedUserId = userId
            _accessState.value = state
            resolveJob = null
        }
        resolveJob = job
        job.join()
        return _accessState.value
            ?: ChatAccessState.Unavailable("Chat unavailable")
    }

    /** Paridad iOS `refreshAccess()` — tras PIN setup/restore o retry. */
    suspend fun refreshAccess() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            invalidateAll()
            return
        }
        mutex.withLock {
            resolveJob?.cancel()
            resolveJob = null
        }
        val state = withContext(Dispatchers.IO) {
            EncryptionService.chatAccessState()
        }
        if (FirebaseAuth.getInstance().currentUser?.uid != userId) return
        resolvedUserId = userId
        _accessState.value = state
    }

    fun invalidate() {
        invalidateAll()
    }

    fun invalidate(userId: String?) {
        if (userId != null && userId != resolvedUserId) return
        invalidateAll()
    }

    fun invalidateAll() {
        resolveJob?.cancel()
        resolveJob = null
        _accessState.value = null
        resolvedUserId = null
    }

    private fun invalidateAllUnlocked() {
        resolveJob?.cancel()
        resolveJob = null
        _accessState.value = null
        resolvedUserId = null
    }

    val isAvailable: Boolean
        get() = _accessState.value is ChatAccessState.Available
}
