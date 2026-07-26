package com.moments.android.services.messaging

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.models.OnlineStatus
import com.moments.android.views.messaging.core.PresenceDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

/** Port de `OnlineStatusService.swift`. */
class OnlineStatusService private constructor() : DefaultLifecycleObserver {

    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appContext: Context? = null
    @Volatile private var trackingStarted = false

    /** Timers automáticos iOS (`awayTimer` / `offlineTimer`) — hoy no se programan en Swift; se cancelan en set/bg/fg. */
    private var awayTimerJob: Job? = null
    private var offlineTimerJob: Job? = null

    private val _currentUserStatus = MutableStateFlow(OnlineStatus.OFFLINE)
    val currentUserStatus: StateFlow<OnlineStatus> = _currentUserStatus.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _lastSeen = MutableStateFlow(Date())
    val lastSeen: StateFlow<Date> = _lastSeen.asStateFlow()

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun ctx(): Context = appContext ?: error("OnlineStatusService.initialize required")

    private fun startTracking() {
        if (trackingStarted) return
        trackingStarted = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        // Actualizar estado cada 30 segundos
        scope.launch {
            while (isActive) {
                delay(30_000L)
                updateOnlineStatus()
            }
        }
        // Actualizar lastSeen cada 5 minutos
        scope.launch {
            while (isActive) {
                delay(300_000L)
                updateLastSeen()
            }
        }
        syncStatusWithFirestore()
    }

    /** ≡ `handleAppDidEnterBackground` */
    override fun onStop(owner: LifecycleOwner) {
        if (_currentUserStatus.value == OnlineStatus.ONLINE) updateLastSeen()
        cancelAutoTimers()
    }

    /** ≡ `handleAppWillEnterForeground` */
    override fun onStart(owner: LifecycleOwner) {
        cancelAutoTimers()
        when (_currentUserStatus.value) {
            OnlineStatus.ONLINE, OnlineStatus.AWAY -> setStatus(OnlineStatus.ONLINE)
            OnlineStatus.BUSY -> updateLastSeen()
            OnlineStatus.INVISIBLE, OnlineStatus.OFFLINE -> Unit
        }
    }

    fun setStatus(status: OnlineStatus) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        cancelAutoTimers()

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
        if (!snap.exists()) return null
        @Suppress("UNCHECKED_CAST")
        val conversationStatus = snap.data?.get("conversationStatus") as? Map<String, Map<String, Any?>>
        val entry = conversationStatus?.get(conversationId) ?: return null
        return OnlineStatus.from(entry["status"] as? String)
    }

    fun observeUserStatus(userId: String, onUpdate: (OnlineStatus, Date?) -> Unit): () -> Unit {
        val registration = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val data = snapshot.data
                val storedStatus = OnlineStatus.from(data?.get("onlineStatus") as? String)
                val lastSeenDate = (data?.get("lastSeen") as? Timestamp)?.toDate()
                var effective = storedStatus

                // Lógica pasiva: degradar online/away si lastSeen es viejo (5 min / 30 min).
                lastSeenDate?.let { seen ->
                    val elapsedSec = (Date().time - seen.time) / 1000.0
                    if (storedStatus == OnlineStatus.ONLINE) {
                        if (elapsedSec > 300) effective = OnlineStatus.AWAY
                        if (elapsedSec > 1800) effective = OnlineStatus.OFFLINE
                    } else if (storedStatus == OnlineStatus.AWAY) {
                        if (elapsedSec > 1800) effective = OnlineStatus.OFFLINE
                    }
                }
                onUpdate(effective, lastSeenDate)
            }
        return { registration.remove() }
    }

    fun formatLastSeen(date: Date?): String {
        val context = appContext ?: return ""
        if (date == null) return context.getString(R.string.online_status_unknown)

        val elapsedSec = (Date().time - date.time) / 1000.0
        return when {
            elapsedSec < 60 -> context.getString(R.string.online_status_now)
            elapsedSec < 3600 -> {
                val minutes = (elapsedSec / 60).toInt()
                context.getString(R.string.online_status_minutes_ago, minutes)
            }
            elapsedSec < 86400 -> {
                val hours = (elapsedSec / 3600).toInt()
                context.getString(R.string.online_status_hours_ago, hours)
            }
            else -> {
                val days = (elapsedSec / 86400).toInt()
                context.getString(R.string.online_status_days_ago, days)
            }
        }
    }

    fun presenceDisplay(status: OnlineStatus, lastSeen: Date?): PresenceDisplay? {
        if (status == OnlineStatus.INVISIBLE) return null
        return PresenceDisplay(
            status = status,
            statusText = status.displayName(ctx()),
            supplementalText = supplementalLastSeenText(status, lastSeen),
        )
    }

    fun supplementalLastSeenText(status: OnlineStatus, lastSeen: Date?): String? {
        if (status != OnlineStatus.OFFLINE || lastSeen == null) return null
        // "Offline · now" es contradictorio; solo tras ≥120 s de utilidad.
        if (Date().time - lastSeen.time < 120_000L) return null
        return formatLastSeen(lastSeen)
    }

    fun hasPendingAutoChanges(): Boolean = awayTimerJob != null || offlineTimerJob != null

    fun getNextAutoChangeInfo(): Pair<OnlineStatus, String>? {
        val context = appContext ?: return null
        return when {
            awayTimerJob != null ->
                OnlineStatus.AWAY to context.getString(R.string.online_status_auto_change_away)
            offlineTimerJob != null ->
                OnlineStatus.OFFLINE to context.getString(R.string.online_status_auto_change_offline)
            else -> null
        }
    }

    private fun cancelAutoTimers() {
        awayTimerJob?.cancel()
        offlineTimerJob?.cancel()
        awayTimerJob = null
        offlineTimerJob = null
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
            if (!snap.exists()) return@launch
            val statusString = snap.data?.get("onlineStatus") as? String ?: return@launch
            val status = OnlineStatus.from(statusString)
            _currentUserStatus.value = status
            _isOnline.value = status == OnlineStatus.ONLINE
        }
    }

    companion object {
        val shared: OnlineStatusService by lazy { OnlineStatusService() }

        fun initialize(application: Application) {
            shared.initialize(application)
            shared.startTracking()
        }
    }
}

/** ≡ `OnlineStatus.displayName` (NSLocalizedString onlineStatus.*). */
fun OnlineStatus.displayName(context: Context): String = context.getString(
    when (this) {
        OnlineStatus.ONLINE -> R.string.messaging_status_online
        OnlineStatus.AWAY -> R.string.messaging_status_away
        OnlineStatus.BUSY -> R.string.messaging_status_busy
        OnlineStatus.OFFLINE -> R.string.messaging_status_offline
        OnlineStatus.INVISIBLE -> R.string.messaging_status_invisible
    },
)
