package com.moments.android.views.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.Moment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.privacy.canUserViewMomentEnhanced
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Mirror 1:1 de `SavedMomentsViewModel.swift` (384 líneas en iOS).
 */
class SavedMomentsViewModel {
    var moments by mutableStateOf<List<Moment>>(emptyList())
        private set

    var savedMomentIds by mutableStateOf<List<String>>(emptyList())
        private set

    val visibilityByMomentId = mutableStateMapOf<String, Boolean>()
    val mutedUserIds = mutableStateOf<Set<String>>(emptySet())

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<Throwable?>(null)
        private set

    private val firestoreService = FirestoreService()
    private val privacyService = PrivacyService
    private val scope = CoroutineScope(Dispatchers.IO)

    fun loadSavedMoments(completion: (Throwable?) -> Unit = {}) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            val err = Exception("Usuario no autenticado")
            error = err
            completion(err)
            return
        }

        isLoading = true
        error = null

        scope.launch {
            try {
                // Fetch saved moment IDs
                val snapshot = firestoreService.db.collection("users")
                    .document(userId)
                    .collection("savedMoments")
                    .get()
                    .await()

                val ids = snapshot.documents.mapNotNull { it.id }
                withContext(Dispatchers.Main) {
                    savedMomentIds = ids
                }

                if (ids.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        moments = emptyList()
                        visibilityByMomentId.clear()
                        isLoading = false
                    }
                    completion(null)
                    return@launch
                }

                // Fetch moments directly from active users
                val foundMoments = mutableListOf<Moment>()
                val activeUsersSnap = firestoreService.db.collection("users")
                    .limit(100)
                    .get()
                    .await()

                val activeUserIds = activeUsersSnap.documents.map { it.id }
                for (authorId in activeUserIds) {
                    try {
                        val momentsSnap = firestoreService.db.collection("users")
                            .document(authorId)
                            .collection("moments")
                            .get()
                            .await()

                        val userMoments = momentsSnap.documents.mapNotNull { Moment.from(it.id, it.data ?: emptyMap()) }
                        val matching = userMoments.filter { it.id != null && ids.contains(it.id) }
                        foundMoments.addAll(matching)
                    } catch (_: Exception) { }
                }

                val sorted = foundMoments.sortedByDescending { it.timestamp }

                withContext(Dispatchers.Main) {
                    moments = sorted
                    sorted.forEach { m ->
                        if (m.id != null) visibilityByMomentId[m.id] = true
                    }
                    isLoading = false
                }
                completion(null)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e
                    isLoading = false
                }
                completion(e)
            }
        }
    }

    fun isMomentSaved(momentId: String): Boolean {
        return savedMomentIds.contains(momentId)
    }

    fun isMomentFromMutedUser(moment: Moment): Boolean {
        return mutedUserIds.value.contains(moment.authorId)
    }

    fun removeMoment(momentId: String, completion: (Throwable?) -> Unit = {}) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        scope.launch {
            try {
                firestoreService.db.collection("users")
                    .document(userId)
                    .collection("savedMoments")
                    .document(momentId)
                    .delete()
                    .await()

                withContext(Dispatchers.Main) {
                    moments = moments.filter { it.id != momentId }
                    savedMomentIds = savedMomentIds.filter { it != momentId }
                    visibilityByMomentId.remove(momentId)
                }
                completion(null)
            } catch (e: Exception) {
                completion(e)
            }
        }
    }

    fun refreshVisibilityForMoment(moment: Moment, completion: ((Boolean) -> Unit)? = null) {
        val momentId = moment.id ?: run {
            completion?.invoke(false)
            return
        }
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            completion?.invoke(false)
            return
        }

        scope.launch {
            val canView = privacyService.canUserViewMomentEnhanced(moment, viewerId)
            withContext(Dispatchers.Main) {
                visibilityByMomentId[momentId] = canView
                completion?.invoke(canView)
            }
        }
    }

    fun forceRefresh() {
        moments = emptyList()
        savedMomentIds = emptyList()
        visibilityByMomentId.clear()
        loadSavedMoments()
    }
}
