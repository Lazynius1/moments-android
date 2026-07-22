package com.moments.android.viewmodels

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.models.Echo
import com.moments.android.models.EchoMomentRef
import com.moments.android.models.EchoParticipantStatus
import com.moments.android.models.Moment
import com.moments.android.services.cache.ImagePrefetchManager
import com.moments.android.services.cache.VideoPreloader
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.privacy.canUserViewMomentEnhanced
import com.moments.android.services.privacy.checkIfBestFriend
import com.moments.android.services.social.EchoService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

data class GroupedPerspective(
    val authorId: String,
    val username: String,
    val profileImagePath: String?,
    val moments: List<EchoMomentRef>,
) {
    val id: String get() = authorId
}

/** Port de EchoViewModel.swift → StateFlow */
class EchoViewModel(
    private val echoId: String,
    initialEcho: Echo? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    private val _echo = MutableStateFlow<Echo?>(initialEcho)
    val echo: StateFlow<Echo?> = _echo.asStateFlow()

    private val _isLoading = MutableStateFlow(initialEcho == null)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentPerspectiveIndex = MutableStateFlow(0)
    val currentPerspectiveIndex: StateFlow<Int> = _currentPerspectiveIndex.asStateFlow()

    private val _currentVerticalIndex = MutableStateFlow(0)
    val currentVerticalIndex: StateFlow<Int> = _currentVerticalIndex.asStateFlow()

    private val _isVideoPlaying = MutableStateFlow(true)
    val isVideoPlaying: StateFlow<Boolean> = _isVideoPlaying.asStateFlow()

    private val _ripplePhase = MutableStateFlow(0.0)
    val ripplePhase: StateFlow<Double> = _ripplePhase.asStateFlow()

    private val _groupedPerspectives = MutableStateFlow<List<GroupedPerspective>>(emptyList())
    val groupedPerspectives: StateFlow<List<GroupedPerspective>> = _groupedPerspectives.asStateFlow()

    private val _momentAvailability = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val momentAvailability: StateFlow<Map<String, Boolean>> = _momentAvailability.asStateFlow()

    val acceptedCount: Int
        get() = acceptedParticipantMomentCount

    val acceptedParticipantMomentCount: Int
        get() {
            val echoValue = _echo.value ?: return 0
            val acceptedIds = echoValue.participants
                .filter { it.status == EchoParticipantStatus.ACCEPTED }
                .map { it.userId }
                .toSet()
            val momentAuthorIds = echoValue.moments.map { it.authorId }.toSet()
            return acceptedIds.intersect(momentAuthorIds).size
        }

    val isEchoActive: Boolean
        get() = acceptedCount >= 2 && (_echo.value?.hasMinimumMomentParticipants == true)

    val hasExpired: Boolean
        get() {
            val echoValue = _echo.value ?: return false
            return echoValue.expiresAt <= Date()
        }

    val isHistoricalIncomplete: Boolean
        get() = hasExpired && (_echo.value?.hasMinimumMomentParticipants != true)

    val canOpenLocationMap: Boolean get() = !isHistoricalIncomplete
    val canBrowseMedia: Boolean get() = isEchoActive || isHistoricalIncomplete

    val allMoments: List<EchoMomentRef>
        get() = _groupedPerspectives.value.flatMap { it.moments }

    val currentMoment: EchoMomentRef?
        get() {
            if (!canBrowseMedia) return null
            val perspectives = _groupedPerspectives.value
            val perspectiveIndex = _currentPerspectiveIndex.value
            val verticalIndex = _currentVerticalIndex.value
            if (perspectiveIndex >= perspectives.size) return null
            val moments = perspectives[perspectiveIndex].moments
            if (verticalIndex >= moments.size) return null
            return moments[verticalIndex]
        }

    init {
        if (initialEcho != null) {
            updateDisplayedMoments()
            validateMomentsLive()
            preloadMedia()
        }
    }

    fun loadEcho() {
        _isLoading.value = true
        listener?.remove()
        listener = db.collection("echoes").document(echoId).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val data = snapshot.data as Map<String, Any?>? ?: emptyMap()
                    var fetchedEcho = Echo.from(snapshot.id, data)
                    if (fetchedEcho == null) {
                        _isLoading.value = false
                        return@addSnapshotListener
                    }
                    if (fetchedEcho.id == null) fetchedEcho = fetchedEcho.copy(id = snapshot.id)
                    _echo.value = fetchedEcho
                    updateDisplayedMoments()
                    validateMomentsLive()
                    _isLoading.value = false
                    preloadMedia()
                    if (fetchedEcho.participantIds.isEmpty()) {
                        EchoService.repairEcho(fetchedEcho)
                    }
                } catch (_: Exception) {
                    _isLoading.value = false
                }
            } else {
                _isLoading.value = false
            }
        }
    }

    fun switchPerspective(index: Int) {
        val perspectives = _groupedPerspectives.value
        if (index !in perspectives.indices) return
        _isVideoPlaying.value = false
        scope.launch {
            kotlinx.coroutines.delay(30)
            _currentPerspectiveIndex.value = index
            _currentVerticalIndex.value = 0
            _ripplePhase.value = 0.0
            val firstMoment = perspectives[index].moments.firstOrNull()
            _isVideoPlaying.value = firstMoment?.mediaType == "video"
            kotlinx.coroutines.delay(320)
            _ripplePhase.value = 0.0
        }
    }

    fun switchVerticalIndex(index: Int) {
        val perspectiveIndex = _currentPerspectiveIndex.value
        val perspectives = _groupedPerspectives.value
        if (perspectiveIndex >= perspectives.size) return
        val moments = perspectives[perspectiveIndex].moments
        if (index !in moments.indices) return
        _isVideoPlaying.value = false
        scope.launch {
            kotlinx.coroutines.delay(30)
            _currentVerticalIndex.value = index
            _isVideoPlaying.value = moments[index].mediaType == "video"
        }
    }

    fun clear() {
        listener?.remove()
        listener = null
    }

    private fun updateDisplayedMoments() {
        val echoValue = _echo.value ?: return
        if (!isEchoActive && !isHistoricalIncomplete) {
            _groupedPerspectives.value = emptyList()
            return
        }
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val rawMoments = echoValue.moments.filter { moment ->
            if (isHistoricalIncomplete) {
                echoValue.participantIds.contains(moment.authorId)
            } else if (moment.authorId == currentUserId) {
                true
            } else {
                echoValue.participants.any { it.userId == moment.authorId && it.status == EchoParticipantStatus.ACCEPTED }
            }
        }
        val grouped = rawMoments.groupBy { it.authorId }
        var perspectives = grouped.map { (authorId, moments) ->
            val first = moments.first()
            val participant = echoValue.participants.firstOrNull { it.userId == authorId }
            GroupedPerspective(
                authorId = authorId,
                username = participant?.username ?: first.username,
                profileImagePath = participant?.profileImagePath,
                moments = moments.sortedBy { it.timestamp },
            )
        }
        perspectives = perspectives.sortedWith { p1, p2 ->
            when {
                p1.authorId == currentUserId -> -1
                p2.authorId == currentUserId -> 1
                else -> {
                    val t1 = p1.moments.firstOrNull()?.timestamp ?: Date()
                    val t2 = p2.moments.firstOrNull()?.timestamp ?: Date()
                    t1.compareTo(t2)
                }
            }
        }
        _groupedPerspectives.value = perspectives
        if (_currentPerspectiveIndex.value >= perspectives.size) {
            _currentPerspectiveIndex.value = maxOf(0, perspectives.size - 1)
        }
        val visibleMoments = perspectives.getOrNull(_currentPerspectiveIndex.value)?.moments.orEmpty()
        if (_currentVerticalIndex.value >= visibleMoments.size) {
            _currentVerticalIndex.value = maxOf(0, visibleMoments.size - 1)
        } else if (perspectives.isEmpty()) {
            _currentVerticalIndex.value = 0
        }
    }

    private fun preloadMedia() {
        val moments = allMoments
        if (moments.isEmpty()) return
        val mediaWindow = 6
        val thumbWindow = 12
        val mediaSlice = moments.take(mediaWindow)
        val thumbSlice = moments.take(thumbWindow)
        val thumbUrls = thumbSlice.mapNotNull { it.thumbnailUrl }
        val imageUrls = mediaSlice.filter { it.mediaType != "video" }.map { it.mediaUrl }
        ImagePrefetchManager.prefetch(thumbUrls + imageUrls)
        val videoUrls = mediaSlice.filter { it.mediaType == "video" }.map { it.mediaUrl }
        VideoPreloader.preloadAssets(videoUrls)
    }

    private fun validateMomentsLive() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val privacyService = PrivacyService
        for (momentRef in allMoments) {
            if (momentRef.authorId == currentUserId) {
                _momentAvailability.value = _momentAvailability.value + (momentRef.momentId to true)
                continue
            }
            validateSingleMoment(momentRef, currentUserId, privacyService)
        }
    }

    private fun validateSingleMoment(momentRef: EchoMomentRef, viewerId: String, privacyService: PrivacyService) {
        scope.launch(Dispatchers.IO) {
            try {
                val snapshot = db.collection("users").document(momentRef.authorId)
                    .collection("moments").document(momentRef.momentId).get().await()
                if (!snapshot.exists()) {
                    _momentAvailability.value = _momentAvailability.value + (momentRef.momentId to false)
                    return@launch
                }
                @Suppress("UNCHECKED_CAST")
                val data = snapshot.data as Map<String, Any?>? ?: emptyMap()
                val moment = Moment.from(snapshot.id, data)
                if (moment.isArchived == true) {
                    _momentAvailability.value = _momentAvailability.value + (momentRef.momentId to false)
                    return@launch
                }
                val audience = momentRef.audience ?: "everyone"
                val available = when (audience) {
                    "everyone", "mutuals" -> true
                    "bestFriends" -> privacyService.checkIfBestFriend(momentRef.authorId, viewerId)
                    "custom", "customList" -> privacyService.canUserViewMomentEnhanced(moment, viewerId)
                    else -> false
                }
                _momentAvailability.value = _momentAvailability.value + (momentRef.momentId to available)
            } catch (_: Exception) {
                _momentAvailability.value = _momentAvailability.value + (momentRef.momentId to false)
            }
        }
    }
}
