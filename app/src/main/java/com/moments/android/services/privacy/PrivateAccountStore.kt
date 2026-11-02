package com.moments.android.services.privacy

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.utilities.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Datos privados del usuario actual: `users/{uid}/private/account` (bloqueos, mejores amigos, email)
 * y `users/{uid}/private/relations` (quién me bloquea / quién me tiene como mejor amigo).
 * Solo el propietario puede leerlos; los escribe el servidor salvo las claves permitidas al cliente.
 */
object PrivateAccountStore {
    private const val TAG = "PrivateAccountStore"

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val lock = Any()

    private val _blockedUsers = MutableStateFlow<Set<String>>(emptySet())
    val blockedUsers: StateFlow<Set<String>> = _blockedUsers.asStateFlow()

    private val _bestFriends = MutableStateFlow<Set<String>>(emptySet())
    val bestFriends: StateFlow<Set<String>> = _bestFriends.asStateFlow()

    private val _blockedBy = MutableStateFlow<Set<String>>(emptySet())
    val blockedBy: StateFlow<Set<String>> = _blockedBy.asStateFlow()

    private val _bestFriendOf = MutableStateFlow<Set<String>>(emptySet())
    val bestFriendOf: StateFlow<Set<String>> = _bestFriendOf.asStateFlow()

    private val _email = MutableStateFlow<String?>(null)
    val email: StateFlow<String?> = _email.asStateFlow()

    @Volatile private var currentUserId: String? = null
    private var accountRegistration: ListenerRegistration? = null
    private var relationsRegistration: ListenerRegistration? = null
    // Sin sesión no hay nada que esperar: arrancan completados.
    @Volatile private var accountLoaded = CompletableDeferred(Unit)
    @Volatile private var relationsLoaded = CompletableDeferred(Unit)

    fun start(userId: String) {
        synchronized(lock) {
            if (currentUserId == userId && accountRegistration != null && relationsRegistration != null) return
            removeListenersLocked()
            clearStateLocked()
            currentUserId = userId
            val accountReady = CompletableDeferred<Unit>().also { accountLoaded = it }
            val relationsReady = CompletableDeferred<Unit>().also { relationsLoaded = it }
            val privateCollection = db.collection("users").document(userId).collection("private")

            accountRegistration = privateCollection.document("account")
                .addSnapshotListener { snap, error ->
                    if (currentUserId != userId) return@addSnapshotListener
                    if (error != null) {
                        AppLog.debug { "$TAG account listener error: ${error.message}" }
                        // Un listener con error queda muerto: permite re-adjuntarlo en el próximo start.
                        synchronized(lock) { accountRegistration = null }
                        accountReady.complete(Unit)
                        return@addSnapshotListener
                    }
                    applyAccount(snap)
                    accountReady.complete(Unit)
                }

            relationsRegistration = privateCollection.document("relations")
                .addSnapshotListener { snap, error ->
                    if (currentUserId != userId) return@addSnapshotListener
                    if (error != null) {
                        AppLog.debug { "$TAG relations listener error: ${error.message}" }
                        synchronized(lock) { relationsRegistration = null }
                        relationsReady.complete(Unit)
                        return@addSnapshotListener
                    }
                    applyRelations(snap)
                    relationsReady.complete(Unit)
                }
        }
    }

    fun stop() {
        synchronized(lock) {
            removeListenersLocked()
            clearStateLocked()
            currentUserId = null
            // Libera a quien espere en awaitLoaded tras cerrar sesión.
            accountLoaded.complete(Unit)
            relationsLoaded.complete(Unit)
        }
    }

    /** Espera a los primeros snapshots de ambos documentos (o al timeout). */
    suspend fun awaitLoaded(timeoutMs: Long = 3_000) {
        ensureStartedForSignedInUser()
        val account = accountLoaded
        val relations = relationsLoaded
        withTimeoutOrNull(timeoutMs) {
            account.await()
            relations.await()
        }
    }

    /** True si `userId` me tiene en su lista de bloqueados. */
    fun hasBlockedMe(userId: String): Boolean = _blockedBy.value.contains(userId)

    /** True si `authorId` me tiene en sus mejores amigos. */
    fun isBestFriendOf(authorId: String): Boolean = _bestFriendOf.value.contains(authorId)

    /** Unión de los bloqueados del doc público con los del doc privado. */
    suspend fun mergedBlockedUsers(publicValue: Collection<String>): Set<String> {
        awaitLoaded()
        return publicValue.toSet() + _blockedUsers.value
    }

    /** Unión de los mejores amigos del doc público con los del doc privado. */
    suspend fun mergedBestFriends(publicValue: Collection<String>): Set<String> {
        awaitLoaded()
        return publicValue.toSet() + _bestFriends.value
    }

    /** True si el usuario dado es el que tiene la sesión iniciada. */
    fun isSignedInUser(userId: String): Boolean =
        userId.isNotEmpty() && userId == FirebaseAuth.getInstance().currentUser?.uid

    // Por si se consulta antes de que AuthService arranque la sesión.
    private fun ensureStartedForSignedInUser() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (currentUserId == uid) return
        start(uid)
    }

    private fun applyAccount(snap: DocumentSnapshot?) {
        if (snap == null || !snap.exists()) {
            _blockedUsers.value = emptySet()
            _bestFriends.value = emptySet()
            _email.value = null
            return
        }
        _blockedUsers.value = snap.stringSet("blockedUsers")
        _bestFriends.value = snap.stringSet("bestFriends")
        _email.value = snap.getString("email")?.takeIf { it.isNotBlank() }
    }

    private fun applyRelations(snap: DocumentSnapshot?) {
        if (snap == null || !snap.exists()) {
            _blockedBy.value = emptySet()
            _bestFriendOf.value = emptySet()
            return
        }
        _blockedBy.value = snap.stringSet("blockedBy")
        _bestFriendOf.value = snap.stringSet("bestFriendOf")
    }

    private fun DocumentSnapshot.stringSet(field: String): Set<String> =
        (get(field) as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()

    private fun removeListenersLocked() {
        accountRegistration?.remove()
        accountRegistration = null
        relationsRegistration?.remove()
        relationsRegistration = null
    }

    private fun clearStateLocked() {
        _blockedUsers.value = emptySet()
        _bestFriends.value = emptySet()
        _blockedBy.value = emptySet()
        _bestFriendOf.value = emptySet()
        _email.value = null
    }
}
