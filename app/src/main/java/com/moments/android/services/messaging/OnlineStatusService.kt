package com.moments.android.services.messaging

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.OnlineStatus
import com.moments.android.models.PresenceDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.TimeUnit

/** Port de OnlineStatusService.swift. */
class OnlineStatusService private constructor() : DefaultLifecycleObserver {

    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentUserStatus = MutableStateFlow(OnlineStatus.OFFLINE)
    val currentUserStatus: StateFlow<OnlineStatus> = _currentUserStatus.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _lastSeen = MutableStateFlow(Date())
    val lastSeen: StateFlow<Date> = _lastSeen.asStateFlow()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            while (isActive) {
                delay(TimeUnit.SECONDS.toMillis(30))
                updateOnlineStatus()
            }
        }
        scope.launch {
            while (isActive) {
                delay(TimeUnit.MINUTES.toMillis(5))
                updateLastSeen()
            }
        }
        syncStatusWithFirestore()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (_currentUserStatus.value == OnlineStatus.ONLINE) updateLastSeen()
    }

    override fun onStart(owner: LifecycleOwner) {
        when (_currentUserStatus.value) {
            OnlineStatus.ONLINE, OnlineStatus.AWAY -> setStatus(OnlineStatus.ONLINE)
            OnlineStatus.BUSY -> updateLastSeen()
            OnlineStatus.INVISIBLE, OnlineStatus.OFFLINE -> Unit
        }
    }

    fun setStatus(status: OnlineStatus) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        _currentUserStatus.value = status
        _isOnline.value = status == OnlineStatus.ONLINE

        val data = mapOf(
            "onlineStatus" to status.raw,
            "isOnline" to (status == OnlineStatus.ONLINE),
            "lastSeen" to FieldValue.serverTimestamp(),
        )

        scope.launch {
            runCatching {
                db.collection("users").document(userId).update(data).await()
            }.onFailure {
                _currentUserStatus.value = OnlineStatus.OFFLINE
                _isOnline.value = false
            }
        }
    }

    fun setGlobalStatus(status: OnlineStatus) = setStatus(status)

    fun setConversationStatus(status: OnlineStatus, conversationId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val data = mapOf(
            "conversationStatus" to mapOf(
                conversationId to mapOf(
                    "status" to status.raw,
                    "timestamp" to FieldValue.serverTimestamp(),
                ),
            ),
        )
        scope.launch {
            runCatching { db.collection("users").document(userId).update(data).await() }
        }
    }

    suspend fun getConversationStatus(conversationId: String): OnlineStatus? {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val snap = runCatching { db.collection("users").document(userId).get().await() }.getOrNull()
            ?: return null
        @Suppress("UNCHECKED_CAST")
        val conversationStatus = snap.data?.get("conversationStatus") as? Map<String, Map<String, Any?>>
        val entry = conversationStatus?.get(conversationId) ?: return null
        return OnlineStatus.from(entry["status"] as? String)
    }

    fun observeUserStatus(userId: String, onUpdate: (OnlineStatus, Date?) -> Unit): () -> Unit {
        val registration = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val data = snapshot.data ?: return@addSnapshotListener
                val storedStatus = OnlineStatus.from(data["onlineStatus"] as? String)
                var lastSeen: Date? = (data["lastSeen"] as? Timestamp)?.toDate()
                var effective = storedStatus
                lastSeen?.let { seen ->
                    val elapsed = Date().time - seen.time
                    if (storedStatus == OnlineStatus.ONLINE) {
                        if (elapsed > TimeUnit.MINUTES.toMillis(5)) effective = OnlineStatus.AWAY
                        if (elapsed > TimeUnit.MINUTES.toMillis(30)) effective = OnlineStatus.OFFLINE
                    } else if (storedStatus == OnlineStatus.AWAY) {
                        if (elapsed > TimeUnit.MINUTES.toMillis(30)) effective = OnlineStatus.OFFLINE
                    }
                }
                onUpdate(effective, lastSeen)
            }
        return { registration.remove() }
    }

    fun formatLastSeen(date: Date?): String {
        if (date == null) return "Desconocido"
        val elapsed = Date().time - date.time
        return when {
            elapsed < TimeUnit.MINUTES.toMillis(1) -> "Ahora"
            elapsed < TimeUnit.HOURS.toMillis(1) -> "${elapsed / TimeUnit.MINUTES.toMillis(1)} min"
            elapsed < TimeUnit.DAYS.toMillis(1) -> "${elapsed / TimeUnit.HOURS.toMillis(1)} h"
            else -> "${elapsed / TimeUnit.DAYS.toMillis(1)} d"
        }
    }

    fun presenceDisplay(status: OnlineStatus, lastSeen: Date?): PresenceDisplay? {
        if (status == OnlineStatus.INVISIBLE) return null
        return PresenceDisplay(
            status = status,
            statusText = status.raw,
            supplementalText = supplementalLastSeenText(status, lastSeen),
        )
    }

    fun supplementalLastSeenText(status: OnlineStatus, lastSeen: Date?): String? {
        if (status != OnlineStatus.OFFLINE || lastSeen == null) return null
        if (Date().time - lastSeen.time < TimeUnit.MINUTES.toMillis(2)) return null
        return formatLastSeen(lastSeen)
    }

    private fun updateOnlineStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            runCatching {
                db.collection("users").document(userId).update(
                    mapOf(
                        "isOnline" to _isOnline.value,
                        "lastSeen" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
        }
    }

    private fun updateLastSeen() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            runCatching {
                db.collection("users").document(userId).update(
                    mapOf("lastSeen" to FieldValue.serverTimestamp()),
                ).await()
            }
        }
    }

    private fun syncStatusWithFirestore() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            val snap = runCatching { db.collection("users").document(userId).get().await() }.getOrNull()
                ?: return@launch
            val status = OnlineStatus.from(snap.data?.get("onlineStatus") as? String)
            _currentUserStatus.value = status
            _isOnline.value = status == OnlineStatus.ONLINE
        }
    }

    companion object {
        val shared: OnlineStatusService by lazy { OnlineStatusService() }

        fun initialize(@Suppress("UNUSED_PARAMETER") application: Application) {
            shared // touch lazy
        }
    }
}
