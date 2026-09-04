package com.moments.android.services.privacy

import com.google.firebase.auth.FirebaseAuth
import com.moments.android.MomentsApplication
import com.moments.android.services.persistence.LocalPersistenceService
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class FollowButtonState {
    OWN_PROFILE,
    BLOCKED,
    FOLLOWING,
    MUTUALS,
    CAN_FOLLOW,
    CAN_REQUEST_FOLLOW,
    REQUEST_PENDING,
    REQUEST_PENDING_CANCELLABLE;

    val isFollowingOrMutual: Boolean
        get() = this == FOLLOWING || this == MUTUALS

    val showsProspectFollow: Boolean
        get() = this == CAN_FOLLOW || this == CAN_REQUEST_FOLLOW || this == REQUEST_PENDING_CANCELLABLE

    val buttonText: String
        get() = when (this) {
            OWN_PROFILE -> "Own profile"
            BLOCKED -> "Blocked"
            FOLLOWING -> "Following"
            MUTUALS -> "Mutuals"
            CAN_FOLLOW -> "Follow"
            CAN_REQUEST_FOLLOW -> "Request follow"
            REQUEST_PENDING -> "Request sent"
            REQUEST_PENDING_CANCELLABLE -> "Cancel request"
        }

    val isActionable: Boolean
        get() = when (this) {
            OWN_PROFILE, BLOCKED, REQUEST_PENDING -> false
            FOLLOWING, MUTUALS, CAN_FOLLOW, CAN_REQUEST_FOLLOW, REQUEST_PENDING_CANCELLABLE -> true
        }

    val isPendingRequest: Boolean
        get() = this == REQUEST_PENDING || this == REQUEST_PENDING_CANCELLABLE

    val isProspect: Boolean
        get() = this == CAN_FOLLOW || this == CAN_REQUEST_FOLLOW

    val buttonColor: String
        get() = when (this) {
            OWN_PROFILE -> "gray"
            BLOCKED -> "red"
            FOLLOWING, MUTUALS -> "green"
            CAN_FOLLOW, CAN_REQUEST_FOLLOW -> "blue"
            REQUEST_PENDING, REQUEST_PENDING_CANCELLABLE -> "orange"
        }
}

/** Coordinador UI en memoria; LocalPersistenceService es el único snapshot persistente. */
object FollowStateStore {
    private const val CONFIRMED_FRESHNESS_MILLIS = 15_000L
    private const val OPTIMISTIC_REVALIDATION_DELAY_MILLIS = 3_000L
    private const val CONFIRMED_TTL_MILLIS = 5L * 60 * 1_000
    private const val OPTIMISTIC_TTL_MILLIS = 10L * 60 * 1_000
    private const val MAXIMUM_ENTRY_COUNT = 500

    private enum class EntrySource { LOCAL_SNAPSHOT, CONFIRMED, OPTIMISTIC }

    private data class Entry(
        val state: FollowButtonState,
        val updatedAt: Long,
        val revision: Long,
        val source: EntrySource,
    )

    private val lock = Any()
    private val entriesByRelationship = mutableMapOf<String, Entry>()
    private val listeners = mutableListOf<(String, FollowButtonState) -> Unit>()
    private val stateSnapshots = MutableStateFlow<Map<String, FollowButtonState>>(emptyMap())
    private val inFlightResolutions = ConcurrentHashMap<String, CompletableDeferred<FollowButtonState?>>()
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var removedObsoletePersistence = false

    private fun relationshipKey(viewerId: String, targetUserId: String) = "$viewerId|$targetUserId"

    private fun removeObsoletePersistenceOnce() {
        if (removedObsoletePersistence) return
        synchronized(lock) {
            if (removedObsoletePersistence) return
            MomentsApplication.instance
                ?.getSharedPreferences("follow_relationship_store", 0)
                ?.edit()
                ?.remove("relationship_states_v2")
                ?.remove("relationship_states_v3")
                ?.apply()
            removedObsoletePersistence = true
        }
    }

    fun addListener(listener: (String, FollowButtonState) -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeListener(listener: (String, FollowButtonState) -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    fun state(userId: String): FollowButtonState? {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return state(viewerId, userId)
    }

    fun state(viewerId: String, targetUserId: String): FollowButtonState? {
        removeObsoletePersistenceOnce()
        val key = relationshipKey(viewerId, targetUserId)
        val result: FollowButtonState?
        synchronized(lock) {
            val entry = entriesByRelationship[key]
            val removed = entry != null && !isValid(entry, System.currentTimeMillis())
            if (removed) {
                entriesByRelationship.remove(key)
                stateSnapshots.value = entriesByRelationship.mapValues { it.value.state }
            }
            result = if (removed) null else entry?.state
        }
        if (result != null) return result

        val (isFollowing, isMutual) = LocalPersistenceService.cachedFollowRelationship(viewerId, targetUserId)
        val snapshotState = when {
            isMutual -> FollowButtonState.MUTUALS
            isFollowing -> FollowButtonState.FOLLOWING
            else -> null
        } ?: return null
        return write(
            snapshotState,
            EntrySource.LOCAL_SNAPSHOT,
            viewerId,
            targetUserId,
            expectedRevision = null,
            syncPersistentSnapshot = false,
        )
    }

    fun observe(viewerId: String, targetUserId: String): Flow<FollowButtonState?> {
        removeObsoletePersistenceOnce()
        val key = relationshipKey(viewerId, targetUserId)
        return stateSnapshots.map { it[key] }.distinctUntilChanged()
    }

    /** Las escrituras públicas representan acciones UI y avanzan la revisión. */
    fun setState(state: FollowButtonState, userId: String) {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        setState(state, viewerId, userId)
    }

    fun setState(state: FollowButtonState, viewerId: String, targetUserId: String) {
        write(state, EntrySource.OPTIMISTIC, viewerId, targetUserId, expectedRevision = null)
    }

    suspend fun resolve(viewerId: String, targetUserId: String): FollowButtonState? {
        if (viewerId == targetUserId) {
            setState(FollowButtonState.OWN_PROFILE, viewerId, targetUserId)
            return FollowButtonState.OWN_PROFILE
        }

        state(viewerId, targetUserId)
        val key = relationshipKey(viewerId, targetUserId)
        val now = System.currentTimeMillis()
        synchronized(lock) {
            entriesByRelationship[key]?.let { entry ->
                val age = now - entry.updatedAt
                val fresh = (entry.source == EntrySource.CONFIRMED && age < CONFIRMED_FRESHNESS_MILLIS) ||
                    (entry.source == EntrySource.OPTIMISTIC && age < OPTIMISTIC_REVALIDATION_DELAY_MILLIS)
                if (fresh) return entry.state
            }
        }
        val pending = CompletableDeferred<FollowButtonState?>()
        val existing = inFlightResolutions.putIfAbsent(key, pending)
        if (existing != null) return existing.await()
        val startingRevision = synchronized(lock) { entriesByRelationship[key]?.revision ?: 0L }

        storeScope.launch {
            try {
                val authoritative = PrivacyService.resolveFollowButtonState(viewerId, targetUserId)
                pending.complete(
                    write(
                        authoritative,
                        EntrySource.CONFIRMED,
                        viewerId,
                        targetUserId,
                        expectedRevision = startingRevision,
                    ),
                )
            } catch (error: Exception) {
                pending.complete(memoryState(viewerId, targetUserId) ?: state(viewerId, targetUserId))
                if (error is CancellationException) throw error
            } finally {
                inFlightResolutions.remove(key, pending)
            }
        }
        return pending.await()
    }

    fun reconciledState(authoritativeState: FollowButtonState, userId: String): FollowButtonState =
        authoritativeState

    private fun write(
        state: FollowButtonState,
        source: EntrySource,
        viewerId: String,
        targetUserId: String,
        expectedRevision: Long?,
        syncPersistentSnapshot: Boolean = true,
    ): FollowButtonState {
        val key = relationshipKey(viewerId, targetUserId)
        val previousState: FollowButtonState?
        val effectiveState: FollowButtonState
        val didWrite: Boolean
        synchronized(lock) {
            val current = entriesByRelationship[key]
            val currentRevision = current?.revision ?: 0L
            previousState = current?.state
            val rejectsStaleWrite = expectedRevision != null && expectedRevision != currentRevision
            val rejectsOptimisticDowngrade = source == EntrySource.CONFIRMED &&
                current != null &&
                !confirmedShouldReplace(state, current, System.currentTimeMillis())
            if (rejectsStaleWrite || rejectsOptimisticDowngrade) {
                effectiveState = current?.state ?: state
                didWrite = false
            } else {
                entriesByRelationship[key] = Entry(
                    state = state,
                    updatedAt = System.currentTimeMillis(),
                    revision = currentRevision + 1,
                    source = source,
                )
                pruneLocked(System.currentTimeMillis())
                effectiveState = state
                didWrite = true
            }
            if (didWrite) stateSnapshots.value = entriesByRelationship.mapValues { it.value.state }
        }

        if (!didWrite) return effectiveState
        if (syncPersistentSnapshot && state != FollowButtonState.OWN_PROFILE) {
            LocalPersistenceService.updateCachedFollowRelationship(
                viewerId = viewerId,
                targetUserId = targetUserId,
                isFollowing = state == FollowButtonState.FOLLOWING || state == FollowButtonState.MUTUALS,
                isMutual = state == FollowButtonState.MUTUALS,
            )
        }
        if (previousState != state && FirebaseAuth.getInstance().currentUser?.uid == viewerId) {
            val listenerSnapshot = synchronized(listeners) { listeners.toList() }
            listenerSnapshot.forEach { it(targetUserId, state) }
        }
        return state
    }

    private fun pruneLocked(now: Long) {
        entriesByRelationship.entries.removeAll { !isValid(it.value, now) }
        if (entriesByRelationship.size <= MAXIMUM_ENTRY_COUNT) return
        val keep = entriesByRelationship.entries
            .sortedByDescending { it.value.updatedAt }
            .take(MAXIMUM_ENTRY_COUNT)
            .mapTo(mutableSetOf()) { it.key }
        entriesByRelationship.keys.retainAll(keep)
    }

    private fun memoryState(viewerId: String, targetUserId: String): FollowButtonState? {
        val key = relationshipKey(viewerId, targetUserId)
        synchronized(lock) {
            val entry = entriesByRelationship[key] ?: return null
            return if (isValid(entry, System.currentTimeMillis())) entry.state else null
        }
    }

    private fun isValid(entry: Entry, now: Long): Boolean {
        val ttl = if (entry.source == EntrySource.OPTIMISTIC) {
            OPTIMISTIC_TTL_MILLIS
        } else {
            CONFIRMED_TTL_MILLIS
        }
        return now - entry.updatedAt <= ttl
    }

    private fun confirmedShouldReplace(
        confirmed: FollowButtonState,
        current: Entry,
        now: Long,
    ): Boolean {
        if (current.source != EntrySource.OPTIMISTIC) return true
        if (now - current.updatedAt > OPTIMISTIC_TTL_MILLIS) return true
        if (confirmed == FollowButtonState.BLOCKED || confirmed == FollowButtonState.OWN_PROFILE) return true
        if (confirmed == current.state) return true
        if (current.state.isFollowingOrMutual && confirmed.isFollowingOrMutual) return true
        if (current.state.isPendingRequest && confirmed.isPendingRequest) return true
        if (current.state.isProspect && confirmed.isProspect) return true
        return false
    }
}
