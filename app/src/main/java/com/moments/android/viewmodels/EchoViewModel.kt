package com.moments.android.viewmodels

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.models.Echo
import com.moments.android.models.EchoMomentRef
import com.moments.android.models.EchoParticipantStatus
import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.services.cache.ImagePrefetchManager
import com.moments.android.services.cache.VideoPreloader
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.social.EchoService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

data class EchoDeckPost(
    val momentId: String,
    val authorId: String,
    val username: String,
    val timestamp: Date,
    val aspectRatio: String?,
    val slides: List<EchoMomentRef>,
) {
    val id: String get() = momentId
}

data class GroupedPerspective(
    val authorId: String,
    val username: String,
    val profileImagePath: String?,
    val moments: List<EchoMomentRef>,
    val posts: List<EchoDeckPost>,
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

    val currentPost: EchoDeckPost?
        get() {
            if (!canBrowseMedia) return null
            val perspectives = _groupedPerspectives.value
            val perspectiveIndex = _currentPerspectiveIndex.value
            val verticalIndex = _currentVerticalIndex.value
            if (perspectiveIndex >= perspectives.size) return null
            val posts = perspectives[perspectiveIndex].posts
            if (verticalIndex >= posts.size) return null
            return posts[verticalIndex]
        }

    val currentMoment: EchoMomentRef?
        get() {
            val post = currentPost ?: return null
            return visibleSlides(post).firstOrNull() ?: post.slides.firstOrNull()
        }

    /** Paridad feed `visibleMediaItems`: saca del carrusel las slides ocultas por moderación. */
    fun visibleSlides(post: EchoDeckPost): List<EchoMomentRef> {
        val moment = _postMoments.value[post.momentId] ?: return post.slides
        val visible = moment.visibleMediaItems
        if (moment.mediaItems.isNullOrEmpty() && visible.isEmpty()) return post.slides
        val urls = visible.map { it.url }.toSet()
        return post.slides.filter { it.mediaUrl in urls }
    }

    private val _postCaptions = MutableStateFlow<Map<String, String>>(emptyMap())
    val postCaptions: StateFlow<Map<String, String>> = _postCaptions.asStateFlow()

    private val _postAspectRatios = MutableStateFlow<Map<String, String>>(emptyMap())
    val postAspectRatios: StateFlow<Map<String, String>> = _postAspectRatios.asStateFlow()

    private val _postMoments = MutableStateFlow<Map<String, Moment>>(emptyMap())
    val postMoments: StateFlow<Map<String, Moment>> = _postMoments.asStateFlow()

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
        // ≡ switchPerspective(to:) — apagar video, delay 0.03s, reset vertical
        _isVideoPlaying.value = false
        scope.launch {
            kotlinx.coroutines.delay(30)
            _currentPerspectiveIndex.value = index
            _currentVerticalIndex.value = 0
            _ripplePhase.value = 0.0
            val firstPost = perspectives[index].posts.firstOrNull()
            if (firstPost != null) {
                loadPostDetailsIfNeeded(firstPost)
                val firstSlide = firstPost.slides.firstOrNull()
                _isVideoPlaying.value = firstSlide?.mediaType == "video" &&
                    _momentAvailability.value[firstPost.momentId] != false
            } else {
                _isVideoPlaying.value = false
            }
            kotlinx.coroutines.delay(350)
            _ripplePhase.value = 0.0
        }
    }

    fun switchVerticalIndex(index: Int) {
        val perspectiveIndex = _currentPerspectiveIndex.value
        val perspectives = _groupedPerspectives.value
        if (perspectiveIndex >= perspectives.size) return
        val posts = perspectives[perspectiveIndex].posts
        if (index !in posts.indices) return
        _isVideoPlaying.value = false
        scope.launch {
            kotlinx.coroutines.delay(30)
            _currentVerticalIndex.value = index
            val post = posts[index]
            loadPostDetailsIfNeeded(post)
            val firstSlide = post.slides.firstOrNull()
            _isVideoPlaying.value = firstSlide?.mediaType == "video" &&
                _momentAvailability.value[post.momentId] != false
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
            val ordered = moments.sortedBy { it.timestamp }
            GroupedPerspective(
                authorId = authorId,
                username = participant?.username ?: first.username,
                profileImagePath = participant?.profileImagePath,
                moments = ordered,
                posts = postsFrom(ordered),
            )
        }
        perspectives = perspectives.sortedWith { p1, p2 ->
            when {
                p1.authorId == currentUserId -> -1
                p2.authorId == currentUserId -> 1
                else -> {
                    val t1 = p1.posts.firstOrNull()?.timestamp ?: Date()
                    val t2 = p2.posts.firstOrNull()?.timestamp ?: Date()
                    t1.compareTo(t2)
                }
            }
        }
        _groupedPerspectives.value = perspectives
        if (_currentPerspectiveIndex.value >= perspectives.size) {
            _currentPerspectiveIndex.value = maxOf(0, perspectives.size - 1)
        }
        if (_currentPerspectiveIndex.value < perspectives.size) {
            val visiblePosts = perspectives[_currentPerspectiveIndex.value].posts
            if (_currentVerticalIndex.value >= visiblePosts.size) {
                _currentVerticalIndex.value = maxOf(0, visiblePosts.size - 1)
            }
            currentPost?.let { loadPostDetailsIfNeeded(it) }
        } else {
            _currentVerticalIndex.value = 0
        }
    }

    fun loadPostDetailsIfNeeded(post: EchoDeckPost) {
        val cached = _postMoments.value[post.momentId]
        if (cached != null) {
            if (_postCaptions.value[post.momentId] == null) {
                _postCaptions.value = _postCaptions.value + (post.momentId to cached.content)
            }
            return
        }
        scope.launch(Dispatchers.IO) {
            val snapshot = runCatching {
                db.collection("users").document(post.authorId)
                    .collection("moments").document(post.momentId).get().await()
            }.getOrNull()
            @Suppress("UNCHECKED_CAST")
            val data = snapshot?.data as Map<String, Any?>?
            val moment = if (snapshot != null && snapshot.exists() && data != null) {
                Moment.from(snapshot.id, data)
            } else {
                null
            }
            withContext(Dispatchers.Main) {
                applyLoadedMoment(moment, post)
            }
        }
    }

    fun playbackMoment(forPost: EchoDeckPost): Moment {
        return _postMoments.value[forPost.momentId]
            ?: stubMoment(forPost, _postCaptions.value[forPost.momentId].orEmpty())
    }

    private fun applyLoadedMoment(moment: Moment?, fallbackPost: EchoDeckPost) {
        if (moment != null) {
            _postMoments.value = _postMoments.value + (fallbackPost.momentId to moment)
            _postCaptions.value = _postCaptions.value + (fallbackPost.momentId to moment.content)
            val ratio = moment.aspectRatio?.takeIf { it.isNotEmpty() } ?: fallbackPost.aspectRatio
            if (!ratio.isNullOrEmpty()) {
                _postAspectRatios.value = _postAspectRatios.value + (fallbackPost.momentId to ratio)
            }
        } else {
            if (_postCaptions.value[fallbackPost.momentId] == null) {
                _postCaptions.value = _postCaptions.value + (fallbackPost.momentId to "")
            }
            fallbackPost.aspectRatio?.let {
                _postAspectRatios.value = _postAspectRatios.value + (fallbackPost.momentId to it)
            }
            _momentAvailability.value = _momentAvailability.value + (fallbackPost.momentId to false)
            if (currentMoment?.momentId == fallbackPost.momentId) {
                _isVideoPlaying.value = false
            }
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
            validateSingleMoment(momentRef, currentUserId, privacyService)
        }
    }

    private fun validateSingleMoment(momentRef: EchoMomentRef, viewerId: String, privacyService: PrivacyService) {
        scope.launch(Dispatchers.IO) {
            fun setAvailable(value: Boolean) {
                _momentAvailability.value = _momentAvailability.value + (momentRef.momentId to value)
                if (!value && currentMoment?.momentId == momentRef.momentId) {
                    _isVideoPlaying.value = false
                }
            }
            try {
                val snapshot = db.collection("users").document(momentRef.authorId)
                    .collection("moments").document(momentRef.momentId).get().await()
                if (!snapshot.exists()) {
                    setAvailable(false)
                    return@launch
                }
                @Suppress("UNCHECKED_CAST")
                val data = snapshot.data as Map<String, Any?>? ?: emptyMap()
                val moment = Moment.from(snapshot.id, data)
                if (moment.isArchived == true) {
                    setAvailable(false)
                    return@launch
                }
                if (momentRef.authorId == viewerId) {
                    setAvailable(true)
                    return@launch
                }
                val audience = momentRef.audience ?: "everyone"
                when (audience) {
                    "everyone", "mutuals" -> setAvailable(true)
                    "bestFriends" -> setAvailable(privacyService.checkIfBestFriend(momentRef.authorId, viewerId))
                    "custom", "customList" -> setAvailable(privacyService.canUserViewMomentEnhanced(moment, viewerId))
                    // iOS: ramas desconocidas no escriben availability
                }
            } catch (_: Exception) {
                setAvailable(false)
            }
        }
    }

    companion object {
        private fun postsFrom(refs: List<EchoMomentRef>): List<EchoDeckPost> {
            val order = mutableListOf<String>()
            val buckets = linkedMapOf<String, MutableList<EchoMomentRef>>()
            for (ref in refs) {
                if (ref.momentId !in buckets) {
                    order.add(ref.momentId)
                    buckets[ref.momentId] = mutableListOf()
                }
                buckets[ref.momentId]?.add(ref)
            }
            return order.mapNotNull { momentId ->
                val slides = buckets[momentId] ?: return@mapNotNull null
                val first = slides.firstOrNull() ?: return@mapNotNull null
                EchoDeckPost(
                    momentId = momentId,
                    authorId = first.authorId,
                    username = first.username,
                    timestamp = first.timestamp,
                    aspectRatio = first.aspectRatio,
                    slides = slides,
                )
            }
        }

        private fun stubMoment(post: EchoDeckPost, caption: String): Moment {
            val mediaItems = post.slides.map { slide ->
                MediaItem(
                    type = if (slide.mediaType == "video") MediaItem.MediaType.VIDEO else MediaItem.MediaType.IMAGE,
                    url = slide.mediaUrl,
                    aspectRatio = slide.aspectRatio ?: post.aspectRatio,
                    thumbnailUrl = slide.thumbnailUrl,
                )
            }
            val firstVideo = post.slides.firstOrNull { it.mediaType == "video" }
            val firstImage = post.slides.firstOrNull { it.mediaType != "video" }
            return Moment(
                id = post.momentId,
                authorId = post.authorId,
                username = post.username,
                content = caption,
                imagePath = firstImage?.mediaUrl,
                videoUrl = firstVideo?.mediaUrl,
                timestamp = post.timestamp,
                audience = post.slides.firstOrNull()?.audience,
                mediaItems = mediaItems,
                aspectRatio = post.aspectRatio,
                customListId = post.slides.firstOrNull()?.customListId,
                thumbnailUrl = post.slides.firstOrNull()?.thumbnailUrl,
            )
        }
    }
}
