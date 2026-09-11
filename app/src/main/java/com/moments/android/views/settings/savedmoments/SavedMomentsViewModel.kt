package com.moments.android.views.settings.savedmoments

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchMoments
import com.moments.android.services.firestore.toggleSaveMoment
import com.moments.android.services.privacy.PrivacyService
import java.util.Calendar
import java.util.UUID
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Mirror 1:1 de `SavedMomentsViewModel.swift` (384 líneas en iOS).
 */
class SavedMomentsViewModel : ViewModel() {
    var moments by mutableStateOf<List<Moment>>(emptyList())
        private set

    var savedMomentIds by mutableStateOf<List<String>>(emptyList())
        private set

    val visibilityByMomentId = mutableStateMapOf<String, Boolean>()

    var mutedUserIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<Throwable?>(null)
        private set

    private val firestoreService = FirestoreService()
    private val privacyService = PrivacyService
    private var visibilityValidationToken: String = UUID.randomUUID().toString()

    private var loadJob: Job? = null
    private var loadGeneration = 0
    private var dataOwnerId: String? = null

    fun loadSavedMoments(completion: (Throwable?) -> Unit = {}) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            val err = Exception("Usuario no autenticado")
            error = err
            completion(err)
            return
        }

        loadJob?.cancel()
        val generation = ++loadGeneration
        if (dataOwnerId != userId) {
            moments = emptyList()
            savedMomentIds = emptyList()
            visibilityByMomentId.clear()
            mutedUserIds = emptySet()
            dataOwnerId = userId
        }
        isLoading = true
        error = null

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            launch {
                runCatching { firestoreService.fetchMutedUserIds(userId) }
                    .onSuccess { muted ->
                        withContext(Dispatchers.Main) {
                            if (loadGeneration == generation && dataOwnerId == userId) mutedUserIds = muted
                        }
                    }
            }

            try {
                val snapshot = firestoreService.db.collection("users")
                    .document(userId)
                    .collection("savedMoments")
                    .get()
                    .await()

                if (generation != loadGeneration || FirebaseAuth.getInstance().currentUser?.uid != userId) return@launch
                val momentIds = snapshot.documents.map { it.id }
                withContext(Dispatchers.Main) {
                    savedMomentIds = momentIds
                }

                if (momentIds.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        moments = emptyList()
                        visibilityByMomentId.clear()
                        isLoading = false
                    }
                    completion(null)
                    return@launch
                }

                val cachedAuthors = moments.associate { it.id to it.authorId }
                val authors = snapshot.documents.mapNotNull { document ->
                    (document.getString("authorId") ?: cachedAuthors[document.id])
                        ?.takeIf { it.isNotBlank() }?.let { document.id to it }
                }.toMap()
                val loadError = fetchSavedMomentsDirectly(momentIds, authors, userId, generation)
                withContext(Dispatchers.Main) { completion(loadError) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (generation != loadGeneration || FirebaseAuth.getInstance().currentUser?.uid != userId) return@withContext
                    error = e
                    isLoading = false
                }
                completion(e)
            }
        }
    }

    /** Exact document paths for new bookmarks; bounded compatibility lookup for old ones. */
    private suspend fun fetchSavedMomentsDirectly(momentIds: List<String>, authorsById: Map<String, String>, viewerId: String, generation: Int): Throwable? {
        val foundMoments = mutableListOf<Moment>()
        var firstError: Throwable? = null
        for (batch in authorsById.entries.chunked(6)) {
            val results = coroutineScope {
                batch.map { (id, author) ->
                    async {
                        try {
                            val document = firestoreService.db.collection("users").document(author)
                                .collection("moments").document(id).get().await()
                            Result.success(if (!document.exists()) null else document.data?.let { Moment.from(document.id, it) }
                                ?.takeUnless { it.isArchived == true })
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { Result.failure<Moment?>(e) }
                    }
                }.awaitAll()
            }
            results.forEach { result ->
                result.onSuccess { it?.let(foundMoments::add) }
                    .onFailure { firstError = firstError ?: it }
            }
        }
        val legacyIds = momentIds.toSet() - authorsById.keys
        if (legacyIds.isNotEmpty()) {
            for (batch in fetchActiveUsers().chunked(6)) {
                foundMoments += coroutineScope {
                    batch.map { author -> async { fetchMomentsFromUser(author).filter { it.id in legacyIds } } }
                        .awaitAll().flatten()
                }
            }
        }
        // Unknown is not deleted: network failures, permissions and the legacy lookup
        // cannot prove that a saved publication no longer exists.
        firstError?.let { if (foundMoments.isEmpty()) throw it }
        if (generation != loadGeneration || FirebaseAuth.getInstance().currentUser?.uid != viewerId) return null
        val sortedMoments = foundMoments.sortedByDescending { it.timestamp }
        withContext(Dispatchers.Main) {
            if (generation != loadGeneration || FirebaseAuth.getInstance().currentUser?.uid != viewerId) return@withContext
            moments = sortedMoments
            error = firstError
            isLoading = false
        }
        validateVisibilityForLoadedMoments(sortedMoments)
        return firstError
    }

    private suspend fun fetchMomentsFromUser(userId: String): List<Moment> =
        try { firestoreService.fetchMoments(userId) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { emptyList() }

    private suspend fun fetchActiveUsers(): List<String> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -6)
        val recentDate = calendar.time

        return try {
            val snapshot = firestoreService.db.collection("users")
                .whereGreaterThan("lastActiveAt", Timestamp(recentDate))
                .limit(100)
                .get()
                .await()
            val userIds = snapshot.documents.map { it.id }
            if (userIds.isEmpty()) fetchAllUsers() else userIds
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            fetchAllUsers()
        }
    }

    private suspend fun fetchAllUsers(): List<String> =
        try {
            firestoreService.db.collection("users")
                .limit(200)
                .get()
                .await()
                .documents
                .map { it.id }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

    fun isMomentSaved(momentId: String): Boolean = savedMomentIds.contains(momentId)

    fun isMomentFromMutedUser(moment: Moment): Boolean = mutedUserIds.contains(moment.authorId)

    fun removeMoment(momentId: String, completion: (Throwable?) -> Unit = {}) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            completion(Exception("Usuario no autenticado"))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestoreService.toggleSaveMoment(userId, momentId)
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

    fun addSavedMoment(moment: Moment) {
        val momentId = moment.id ?: return
        if (!savedMomentIds.contains(momentId)) {
            savedMomentIds = savedMomentIds + momentId
            visibilityByMomentId[momentId] = true
            if (moments.none { it.id == momentId }) {
                moments = (moments + moment).sortedByDescending { it.timestamp }
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

        viewModelScope.launch(Dispatchers.IO) {
            val canView = privacyService.canUserViewMomentEnhanced(moment, viewerId)
            withContext(Dispatchers.Main) {
                visibilityByMomentId[momentId] = canView
                completion?.invoke(canView)
            }
        }
    }

    fun debugSavedMoments() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                firestoreService.db.collection("users").document(userId)
                    .collection("savedMoments")
                    .get()
                    .await()
            }
        }
    }

    fun forceRefresh() {
        loadSavedMoments()
    }

    private fun validateVisibilityForLoadedMoments(moments: List<Moment>) {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val token = UUID.randomUUID().toString()
        visibilityValidationToken = token

        viewModelScope.launch(Dispatchers.IO) {
            val result = coroutineScope {
                moments.mapNotNull { moment ->
                    val momentId = moment.id ?: return@mapNotNull null
                    async {
                        momentId to privacyService.canUserViewMomentEnhanced(moment, viewerId)
                    }
                }.awaitAll().toMap()
            }
            withContext(Dispatchers.Main) {
                if (visibilityValidationToken != token) return@withContext
                visibilityByMomentId.clear()
                visibilityByMomentId.putAll(result)
            }
        }
    }
}

// MARK: - SavedMomentsView Redesign 2026

enum class SavedMediaFilter(@StringRes val titleRes: Int) {
    ALL(R.string.saved_moments_filter_media_all),
    PHOTOS(R.string.saved_moments_filter_media_photos),
    VIDEOS(R.string.saved_moments_filter_media_videos),
}

enum class SavedCollectionFilter(@StringRes val titleRes: Int) {
    ALL(R.string.saved_moments_filter_collection_all),
    LOCATION(R.string.saved_moments_filter_collection_location),
    TEXT(R.string.saved_moments_filter_collection_text),
    MULTIPLE(R.string.saved_moments_filter_collection_multiple),
}

enum class SavedSortMode(@StringRes val titleRes: Int) {
    NEWEST(R.string.saved_moments_sort_newest),
    OLDEST(R.string.saved_moments_sort_oldest),
    AUTHOR(R.string.saved_moments_sort_author),
}

data class SavedMomentsDetailRoute(
    val id: String = UUID.randomUUID().toString(),
    val moments: List<Moment>,
    val initialIndex: Int,
)

data class SavedMomentCommentsRoute(
    val id: String = UUID.randomUUID().toString(),
    val moment: Moment,
)
