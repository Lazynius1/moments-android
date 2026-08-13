package com.moments.android.views.story

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.adaptive.LocalAdaptiveWindowState
import com.moments.android.ad.AdMobConfiguration
import com.moments.android.ad.PlusStatusHelper
import com.moments.android.ad.StoryNativeAdView
import com.moments.android.ad.findActivity
import com.moments.android.models.Story
import com.moments.android.reportes.ReportBottomSheet
import com.moments.android.reportes.ReportTarget
import com.moments.android.services.auth.AuthService
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.views.story.storyviewer.GlassmorphicEmptyState
import com.moments.android.views.story.storyviewer.StoryDeckPageRole
import com.moments.android.views.story.storyviewer.StoryUserDeckPager
import com.moments.android.views.story.storyviewer.StoryViewerScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Image
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.min

/**
 * Port de `StoriesView.swift`.
 *
 * Inits iOS ↔ parámetros:
 * - `init(ringNavigationUserIds:)` → [ringNavigationUserIds]
 * - `init(startAtUserId:ringNavigationUserIds:)` → [startAtUserId] + ring
 * - `init(startWithUserId:)` → [startWithUserId] (usuario único; sin Deck Pass)
 * - `init(chainStories:startAtIndex:highlightTitle:)` → [explicitStories]
 */
@Composable
fun StoriesView(
    startAtUserId: String? = null,
    ringNavigationUserIds: List<String> = emptyList(),
    startWithUserId: String? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    explicitStories: List<Story>? = null,
    startAtIndex: Int = 0,
    highlightTitle: String? = null,
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val configuration = LocalConfiguration.current
    val adaptiveWindow = LocalAdaptiveWindowState.current
    val viewModel = remember { StoryViewModel() }
    val firestore = remember { FirestoreService() }
    val scope = rememberCoroutineScope()
    val deckGestureGate = remember { StoryDeckGestureGate() }

    // El visor adaptado aprovecha el espacio extra ocultando solo la status bar.
    // En móvil conservamos la barra y su safe area, igual que el visor original.
    DisposableEffect(hostView, adaptiveWindow.usesLargeStoryLayout) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, hostView) }
        if (adaptiveWindow.usesLargeStoryLayout) {
            controller?.hide(WindowInsetsCompat.Type.statusBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            if (adaptiveWindow.usesLargeStoryLayout) {
                controller?.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    // ≡ @State locales de StoriesView.swift (no solo el VM)
    var hostUserIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var hostIsLoading by remember { mutableStateOf(true) }
    var userIndex by remember { mutableIntStateOf(0) }
    var storyIndex by remember { mutableIntStateOf(0) }
    var showingReportSheet by remember { mutableStateOf(false) }
    var showingBlockConfirmation by remember { mutableStateOf(false) }
    var reportStory by remember { mutableStateOf<Story?>(null) }
    var showAd by remember { mutableStateOf(false) }
    var otherUsersStoryCount by remember { mutableIntStateOf(0) }
    var adStoryCount by remember { mutableIntStateOf(1) }
    var adStoryIndex by remember { mutableIntStateOf(0) }
    var totalStoriesViewed by remember { mutableIntStateOf(0) }
    var loadingOverlayState by remember { mutableStateOf<StoryLoadingOverlayState>(StoryLoadingOverlayState.Loading) }
    var pendingUnseenResolveUserId by remember { mutableStateOf<String?>(null) }
    var hasResolvedInitialViewerPosition by remember { mutableStateOf(false) }
    var initialTargetUserId by remember {
        mutableStateOf(
            if (startWithUserId.isNullOrBlank()) {
                startAtUserId?.takeIf { it.isNotBlank() }.orEmpty()
            } else {
                ""
            },
        )
    }
    var hostErrorMessage by remember { mutableStateOf<String?>(null) }
    var currentChainIndex by remember { mutableIntStateOf(startAtIndex) }
    // ≡ @State isInChainMode / chainStories (puede activarse mid-session vía NavigateToChainStory)
    var isInChainMode by remember { mutableStateOf(explicitStories != null) }
    var hostChainStories by remember { mutableStateOf(explicitStories.orEmpty()) }

    val lockedRingNavigationUserIds = remember(ringNavigationUserIds) {
        ringNavigationUserIds.filter { it.isNotEmpty() }
    }

    // ≡ isMultiUserRingMode: startWithUserId == nil || empty
    val isMultiUserRingMode = !isInChainMode && startWithUserId.isNullOrBlank()
    val isSingleUserEntry = !isInChainMode && !startWithUserId.isNullOrBlank()
    val shouldIncludeConnections = isMultiUserRingMode
    val shouldUseDeckPass = !isInChainMode && isMultiUserRingMode && hostUserIds.size > 1

    val chainRailId = StoryViewModel.CHAIN_RAIL_ID
    val userIds = hostUserIds
    val currentUserId = userIds.getOrNull(userIndex)
    val userStories = currentUserId?.let { viewModel.storiesFor(it) }.orEmpty()
    val currentStory = userStories.getOrNull(storyIndex)

    fun shouldShowLoadingState(forUserId: String): Boolean {
        if (hostErrorMessage != null || viewModel.errorMessage != null) return false
        val stories = viewModel.stories[forUserId]
        return stories == null || stories.isEmpty()
    }

    // ≡ currentLoadingMode
    val currentLoadingMode: StoryLoadingMode? = run {
        if (isInChainMode || hostErrorMessage != null || showAd) return@run null
        if (hostIsLoading) return@run StoryLoadingMode.Initial
        val uid = userIds.getOrNull(userIndex) ?: return@run null
        if (shouldShowLoadingState(uid)) StoryLoadingMode.Author(uid) else null
    }

    val canRenderStoryViewer: Boolean = run {
        val uid = userIds.getOrNull(userIndex) ?: return@run false
        val stories = viewModel.stories[uid].orEmpty()
        stories.isNotEmpty() && storyIndex in stories.indices
    }

    fun resolvedNavigationUserIds(from: Map<String, List<Story>>): List<String> {
        if (lockedRingNavigationUserIds.isNotEmpty()) return lockedRingNavigationUserIds
        if (viewModel.ringOrderedStoryUserIds.isNotEmpty()) return viewModel.ringOrderedStoryUserIds
        return from.keys.filter { !(from[it].isNullOrEmpty()) }.sorted()
    }

    suspend fun firstUnseenStoryIndex(stories: List<Story>, authorId: String): Int {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return 0
        if (stories.isEmpty()) return 0
        return coroutineScope {
            val flags = stories.mapIndexed { index, story ->
                async {
                    val storyId = story.id ?: return@async index to true
                    // ≡ iOS: document nil / error → no contado como visto
                    val viewed = runCatching {
                        firestore.db.collection("users").document(authorId)
                            .collection("stories").document(storyId)
                            .collection("viewers").document(viewerId)
                            .get().await().exists()
                    }.getOrDefault(false)
                    index to viewed
                }
            }.awaitAll()
            flags.filter { !it.second }.minByOrNull { it.first }?.first ?: 0
        }
    }

    fun prefetchNeighborStories(around: Int) {
        if (!isMultiUserRingMode) return
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        listOf(around, around + 1, around - 1).forEach { idx ->
            val uid = userIds.getOrNull(idx) ?: return@forEach
            viewModel.loadAuthorReelIfNeeded(uid, viewerId)
        }
    }

    fun applyStoryIndexForUser(at: Int) {
        val uid = userIds.getOrNull(at) ?: return
        prefetchNeighborStories(around = at)
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid
        if (viewerId == null) {
            storyIndex = 0
            return
        }
        viewModel.mergeStoriesForUserIfNeeded(uid, viewerId)
        val stories = viewModel.storiesFor(uid)
        if (stories.isNotEmpty()) {
            pendingUnseenResolveUserId = null
            scope.launch {
                storyIndex = firstUnseenStoryIndex(stories, uid)
            }
        } else {
            pendingUnseenResolveUserId = uid
            storyIndex = 0
        }
    }

    fun moveToNextUser() {
        // ≡ startWithUserId != nil → dismiss
        if (isSingleUserEntry) {
            onDismiss()
        } else if (userIndex < userIds.lastIndex) {
            userIndex += 1
            applyStoryIndexForUser(userIndex)
        } else {
            onDismiss()
        }
    }

    fun moveToPreviousUser() {
        if (isSingleUserEntry) {
            storyIndex = 0
        } else if (userIndex > 0) {
            userIndex -= 1
            applyStoryIndexForUser(userIndex)
        } else {
            storyIndex = 0
        }
    }

    fun moveToNextStoryOrUser() {
        val uid = userIds.getOrNull(userIndex)
        val stories = uid?.let { viewModel.storiesFor(it) }.orEmpty()
        if (stories.isNotEmpty()) {
            if (storyIndex < stories.lastIndex) {
                storyIndex += 1
            } else {
                moveToNextUser()
            }
        } else {
            onDismiss()
        }
    }

    fun shouldShowStoryAd(): Boolean {
        val user = AuthService.currentUser.value
        return otherUsersStoryCount > 0 &&
            otherUsersStoryCount % 4 == 0 &&
            PlusStatusHelper.shouldShowAds(user)
    }

    fun handleStoryNext(viewedUserId: String) {
        val me = FirebaseAuth.getInstance().currentUser?.uid
        if (me == null) {
            moveToNextStoryOrUser()
            return
        }
        totalStoriesViewed += 1
        if (viewedUserId != me) {
            otherUsersStoryCount += 1
            if (shouldShowStoryAd()) {
                adStoryCount = 1
                adStoryIndex = 0
                showAd = true
                return
            }
        }
        if (isInChainMode) {
            val rail = viewModel.storiesFor(chainRailId)
            if (currentChainIndex < rail.lastIndex) {
                currentChainIndex += 1
                userIndex = 0
                storyIndex = currentChainIndex
            } else {
                onDismiss()
            }
            return
        }
        moveToNextStoryOrUser()
    }

    fun handleStoryDeleted(deletedStory: Story, fallbackUserId: String) {
        val activeUserId = if (isInChainMode) chainRailId else fallbackUserId
        if (isInChainMode) {
            val remaining = viewModel.storiesFor(activeUserId).filter { it.id != deletedStory.id }
            viewModel.replaceExplicitRail(remaining)
            currentChainIndex = min(currentChainIndex, (remaining.size - 1).coerceAtLeast(0))
            if (remaining.isNotEmpty()) {
                storyIndex = min(storyIndex, remaining.lastIndex)
                return
            }
            onDismiss()
            return
        }
        val remainingForUser = viewModel.storiesFor(activeUserId)
        if (remainingForUser.isNotEmpty()) {
            storyIndex = min(storyIndex, remainingForUser.lastIndex)
            return
        }
        val navigationOrder =
            if (lockedRingNavigationUserIds.isEmpty()) userIds else lockedRingNavigationUserIds
        val remainingUserIds = navigationOrder.filter { viewModel.storiesFor(it).isNotEmpty() }
        if (remainingUserIds.isEmpty()) {
            onDismiss()
            return
        }
        val previousActive = userIds.getOrNull(userIndex)
        hostUserIds = remainingUserIds
        userIndex = previousActive?.let { remainingUserIds.indexOf(it).takeIf { i -> i >= 0 } }
            ?: min(userIndex, remainingUserIds.lastIndex)
        storyIndex = 0
    }

    fun attemptAdvanceToNextUserFromTimeout(): Boolean {
        if (!shouldUseDeckPass && !isMultiUserRingMode) return false
        if (userIds.size <= 1) return false
        val nextWithStories = ((userIndex + 1) until userIds.size).firstOrNull { idx ->
            viewModel.storiesFor(userIds[idx]).isNotEmpty()
        }
        val nextAny = ((userIndex + 1) until userIds.size).firstOrNull()
        val nextIndex = nextWithStories ?: nextAny ?: return false
        hostErrorMessage = null
        loadingOverlayState = StoryLoadingOverlayState.Loading
        hostIsLoading = false
        userIndex = nextIndex
        storyIndex = 0
        applyStoryIndexForUser(nextIndex)
        return true
    }

    fun blockUserConfirmed() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val story = reportStory ?: currentStory ?: return
        scope.launch {
            val ok = runCatching { firestore.blockUser(uid, story.authorId) }.isSuccess
            showingBlockConfirmation = false
            if (ok) onDismiss()
        }
    }

    /** ≡ iOS `loadStories` chain branch + install `__chain__` rail. */
    fun enterChainMode(stories: List<Story>, index: Int) {
        val ordered = stories.sortedWith(
            compareBy({ it.chainPosition ?: Int.MAX_VALUE }, { it.timestamp }),
        )
        hostChainStories = ordered
        isInChainMode = true
        viewModel.loadExplicitStories(ordered)
        hostUserIds = listOf(chainRailId)
        userIndex = 0
        currentChainIndex = index.coerceIn(0, (ordered.size - 1).coerceAtLeast(0))
        storyIndex = currentChainIndex
        hostIsLoading = false
        hasResolvedInitialViewerPosition = true
    }

    /** ≡ iOS `loadChainStories(for:)`. */
    fun loadChainStories(forStory: Story, onLoaded: (List<Story>) -> Unit = {}) {
        val chainId = forStory.chainId ?: return
        scope.launch {
            val loaded = runCatching {
                firestore.db.collectionGroup("stories")
                    .whereEqualTo("chainId", chainId)
                    .orderBy("chainPosition")
                    .get().await()
                    .documents.mapNotNull { doc ->
                        @Suppress("UNCHECKED_CAST")
                        Story.from(doc.id, doc.data as? Map<String, Any?> ?: return@mapNotNull null)
                    }
            }.getOrDefault(emptyList())
            if (loaded.isNotEmpty()) {
                hostChainStories = loaded
                onLoaded(loaded)
            }
        }
    }

    /**
     * ≡ iOS `navigateToChainStory(storyId:chainIndex:)` + Notification NavigateToChainStory.
     * [loadedChain] viene del viewer (ya hidratado); si el autor no está en el ring, instala el carril.
     */
    fun navigateToChainStory(storyId: String, chainIndex: Int, loadedChain: List<Story> = emptyList()) {
        // Ya en carril `__chain__`: solo avanzar índice (prev/next chrome)
        if (isInChainMode || hostUserIds.singleOrNull() == chainRailId) {
            val rail = when {
                loadedChain.isNotEmpty() -> loadedChain
                hostChainStories.isNotEmpty() -> hostChainStories
                else -> viewModel.storiesFor(chainRailId)
            }
            if (rail.isNotEmpty()) {
                if (loadedChain.isNotEmpty()) hostChainStories = loadedChain
                currentChainIndex = chainIndex.coerceIn(0, rail.lastIndex)
                storyIndex = currentChainIndex
                userIndex = 0
                return
            }
        }
        for ((authorId, stories) in viewModel.stories) {
            val sIdx = stories.indexOfFirst { it.id == storyId }
            if (sIdx < 0) continue
            val uIdx = hostUserIds.indexOf(authorId)
            if (uIdx < 0) continue
            userIndex = uIdx
            storyIndex = sIdx
            currentChainIndex = chainIndex
            isInChainMode = true
            val seed = when {
                loadedChain.isNotEmpty() -> loadedChain
                hostChainStories.isNotEmpty() -> hostChainStories
                else -> emptyList()
            }
            if (seed.isNotEmpty()) {
                enterChainMode(seed, chainIndex)
            } else {
                val anchor = stories[sIdx]
                loadChainStories(anchor) { enterChainMode(it, chainIndex) }
            }
            return
        }
        // Fallback: partes de cadena fuera del ring actual
        if (loadedChain.isNotEmpty()) {
            enterChainMode(loadedChain, chainIndex)
        }
    }

    // MARK: - loadStories()
    LaunchedEffect(Unit) {
        // ≡ onAppear GlobalVideoManager.shared.pauseAllVideos()
        GlobalVideoManager.pauseAllVideos()
    }

    LaunchedEffect(startAtUserId, startWithUserId, ringNavigationUserIds, explicitStories) {
        hostErrorMessage = null
        loadingOverlayState = StoryLoadingOverlayState.Loading
        hasResolvedInitialViewerPosition = false

        when {
            isInChainMode -> {
                val ordered = (explicitStories ?: hostChainStories).sortedWith(
                    compareBy({ it.chainPosition ?: Int.MAX_VALUE }, { it.timestamp }),
                )
                hostChainStories = ordered
                viewModel.loadExplicitStories(ordered)
                hostUserIds = listOf(chainRailId)
                userIndex = 0
                currentChainIndex = startAtIndex.coerceIn(0, (ordered.size - 1).coerceAtLeast(0))
                storyIndex = currentChainIndex
                hostIsLoading = false
                hasResolvedInitialViewerPosition = true
            }
            isSingleUserEntry -> {
                val target = startWithUserId!!
                val viewerId = FirebaseAuth.getInstance().currentUser?.uid
                if (viewerId == null) {
                    hostIsLoading = false
                    hostErrorMessage = context.getString(R.string.stories_error_auth_required)
                    return@LaunchedEffect
                }
                hostUserIds = listOf(target)
                userIndex = 0
                viewModel.loadAuthorReelIfNeeded(target, viewerId)
                hostIsLoading = false
            }
            shouldIncludeConnections && lockedRingNavigationUserIds.isNotEmpty() -> {
                val viewerId = FirebaseAuth.getInstance().currentUser?.uid
                if (viewerId == null) {
                    hostIsLoading = false
                    hostErrorMessage = context.getString(R.string.stories_error_auth_required)
                    return@LaunchedEffect
                }
                viewModel.setRingNavigationOrder(lockedRingNavigationUserIds)
                hostUserIds = lockedRingNavigationUserIds
                val targetId = initialTargetUserId.ifEmpty {
                    lockedRingNavigationUserIds.firstOrNull() ?: viewerId
                }
                userIndex = hostUserIds.indexOf(targetId).takeIf { it >= 0 } ?: 0
                viewModel.fetchStories(forUserId = viewerId, includeConnections = true)
                // `userIds` is the value captured by the current Compose composition.
                // Unlike SwiftUI's @State, it still points to the old list until the
                // next recomposition, so calling `applyStoryIndexForUser` here can
                // fetch the previous/current user instead of the ring item tapped.
                // Seed the selected reel explicitly; the stories observer resolves
                // the first unseen index once that reel reaches the state map.
                pendingUnseenResolveUserId = targetId
                storyIndex = 0
                viewModel.loadAuthorReelIfNeeded(targetId, viewerId)
                listOf(userIndex - 1, userIndex + 1).forEach { neighborIndex ->
                    lockedRingNavigationUserIds.getOrNull(neighborIndex)?.let { neighborId ->
                        viewModel.loadAuthorReelIfNeeded(neighborId, viewerId)
                    }
                }
                hostIsLoading = false
            }
            else -> {
                val viewerId = FirebaseAuth.getInstance().currentUser?.uid
                if (viewerId == null) {
                    hostIsLoading = false
                    hostErrorMessage = context.getString(R.string.stories_error_auth_required)
                    return@LaunchedEffect
                }
                hostIsLoading = true
                viewModel.load(lockedRingNavigationUserIds, startAtUserId)
            }
        }

        if (PlusStatusHelper.shouldShowAds(AuthService.currentUser.value)) {
            delay(1_000)
            AdMobConfiguration.preloadNativeAd(context.findActivity())
        }
    }

    // ≡ onReceive(storyViewModel.$stories) → updateUserIds (iOS no pausa por showAd)
    LaunchedEffect(viewModel.stories, viewModel.ringOrderedStoryUserIds, viewModel.isLoading) {
        if (isInChainMode) return@LaunchedEffect
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val storiesMap = viewModel.stories
        loadingOverlayState = StoryLoadingOverlayState.Loading

        if (isSingleUserEntry) {
            val target = startWithUserId ?: return@LaunchedEffect
            hostUserIds = listOf(target)
            userIndex = 0
            val userStoriesList = storiesMap[target]
            if (userStoriesList != null) {
                if (userStoriesList.isNotEmpty()) {
                    storyIndex = firstUnseenStoryIndex(userStoriesList, target)
                } else {
                    storyIndex = 0
                }
                hostIsLoading = false
                loadingOverlayState = StoryLoadingOverlayState.Loading
                return@LaunchedEffect
            }
            hostIsLoading = !storiesMap.containsKey(target)
            return@LaunchedEffect
        }

        val newUserIds = resolvedNavigationUserIds(storiesMap)
        val previousActiveUserId = hostUserIds.getOrNull(userIndex)

        if (initialTargetUserId.isNotEmpty()) {
            val targetUserId = initialTargetUserId
            val targetIndex = newUserIds.indexOf(targetUserId)
            val targetStories = storiesMap[targetUserId]
            if (targetIndex < 0 || targetStories.isNullOrEmpty()) {
                hostUserIds = newUserIds
                hostIsLoading = true
                loadingOverlayState = StoryLoadingOverlayState.Loading
                return@LaunchedEffect
            }
            initialTargetUserId = ""
            hostUserIds = newUserIds
            userIndex = targetIndex
            hasResolvedInitialViewerPosition = true
            prefetchNeighborStories(around = targetIndex)
            storyIndex = firstUnseenStoryIndex(targetStories, targetUserId)
            hostIsLoading = false
            loadingOverlayState = StoryLoadingOverlayState.Loading
            otherUsersStoryCount = 0
            totalStoriesViewed = 0
            return@LaunchedEffect
        }

        hostUserIds = newUserIds

        if (hasResolvedInitialViewerPosition) {
            if (previousActiveUserId != null) {
                val preserved = newUserIds.indexOf(previousActiveUserId)
                userIndex = if (preserved >= 0) preserved else min(userIndex, (newUserIds.size - 1).coerceAtLeast(0))
            }
            return@LaunchedEffect
        }

        if (newUserIds.isEmpty()) {
            hostIsLoading = false
            return@LaunchedEffect
        }

        userIndex = newUserIds.indexOf(me).takeIf { it >= 0 } ?: 0
        storyIndex = 0
        hasResolvedInitialViewerPosition = true
        prefetchNeighborStories(around = userIndex)
        hostIsLoading = false
        loadingOverlayState = StoryLoadingOverlayState.Loading
        otherUsersStoryCount = 0
        totalStoriesViewed = 0
    }

    LaunchedEffect(pendingUnseenResolveUserId, viewModel.stories) {
        val pending = pendingUnseenResolveUserId ?: return@LaunchedEffect
        if (pending != userIds.getOrNull(userIndex)) return@LaunchedEffect
        val stories = viewModel.stories[pending].orEmpty()
        if (stories.isEmpty()) return@LaunchedEffect
        pendingUnseenResolveUserId = null
        storyIndex = firstUnseenStoryIndex(stories, pending)
    }

    // ≡ loading timeout 3s
    LaunchedEffect(currentLoadingMode) {
        loadingOverlayState = StoryLoadingOverlayState.Loading
        val mode = currentLoadingMode ?: return@LaunchedEffect
        delay(3_000)
        if (currentLoadingMode != mode) return@LaunchedEffect
        when (mode) {
            StoryLoadingMode.Initial -> {
                if (attemptAdvanceToNextUserFromTimeout()) return@LaunchedEffect
                hostIsLoading = false
                loadingOverlayState = StoryLoadingOverlayState.Error(
                    context.getString(R.string.stories_error_loading_story),
                )
            }
            is StoryLoadingMode.Author -> {
                if (userIds.getOrNull(userIndex) != mode.userId) return@LaunchedEffect
                if (attemptAdvanceToNextUserFromTimeout()) return@LaunchedEffect
                loadingOverlayState = StoryLoadingOverlayState.Error(
                    context.getString(R.string.stories_error_loading_story),
                )
            }
        }
    }

    val goPrevious: () -> Unit = {
        if (storyIndex > 0) {
            storyIndex -= 1
        } else {
            moveToPreviousUser()
        }
    }
    val goPreviousState = rememberUpdatedState(goPrevious)

    val storySurface = if (isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    CompositionLocalProvider(LocalStoryDeckGestureGate provides deckGestureGate) {
        Box(modifier.fillMaxSize().background(storySurface)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(storySurface),
            ) {
            val errorText = hostErrorMessage ?: viewModel.errorMessage
            // Orden ≡ body de StoriesView.swift
            when {
                hostIsLoading -> {
                    StoryViewerLoadingState(
                        state = loadingOverlayState,
                        onClose = onDismiss,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                currentLoadingMode != null -> {
                    StoryViewerLoadingState(
                        state = loadingOverlayState,
                        onClose = onDismiss,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                errorText != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlassmorphicEmptyState(
                            icon = Icons.Filled.Warning,
                            message = errorText,
                            showCloseButton = true,
                            onClose = onDismiss,
                        )
                    }
                }
                userIds.isEmpty() || (!shouldUseDeckPass && viewModel.stories.isEmpty()) -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlassmorphicEmptyState(
                            icon = Icons.Outlined.Image,
                            message = stringResource(R.string.stories_no_stories_available),
                            showCloseButton = true,
                            onClose = onDismiss,
                        )
                    }
                }
                showAd -> {
                    StoryNativeAdView(
                        onNext = {
                            showAd = false
                            moveToNextStoryOrUser()
                        },
                        onPrevious = {
                            showAd = false
                            if (storyIndex > 0) storyIndex -= 1 else moveToPreviousUser()
                        },
                        onClose = onDismiss,
                        storyCount = adStoryCount,
                        storyIndex = adStoryIndex,
                        screenWidthDp = configuration.screenWidthDp.dp,
                        screenHeightDp = configuration.screenHeightDp.dp,
                    )
                }
                shouldUseDeckPass -> {
                    StoryUserDeckPager(
                        userIds = userIds,
                        currentUserIndex = userIndex,
                        onCurrentUserIndexChange = {
                            userIndex = it
                            applyStoryIndexForUser(it)
                        },
                        // Foundation's pager arbitrates horizontal drags itself. The
                        // old manual gate can remain latched by a neighboring page
                        // and would disable the whole deck.
                        isDeckGestureEnabled = userIds.size > 1,
                        gestureGate = deckGestureGate,
                        onUserChanged = { applyStoryIndexForUser(it) },
                        modifier = Modifier.fillMaxSize(),
                    ) { pageUserId, role, isDraggingDeck ->
                        StoryViewerPageContent(
                            pageUserId = pageUserId,
                            currentUserId = currentUserId,
                            storyIndex = storyIndex,
                            viewModel = viewModel,
                            isDeckPageActive = role == StoryDeckPageRole.CENTER && !isDraggingDeck,
                            highlightTitle = if (role == StoryDeckPageRole.CENTER) highlightTitle else null,
                            showingReportSheet = showingReportSheet,
                            showingBlockConfirmation = showingBlockConfirmation,
                            gestureGate = deckGestureGate,
                            onNext = {
                                val stories = viewModel.storiesFor(pageUserId)
                                val idx = if (pageUserId == currentUserId) {
                                    min(storyIndex, (stories.size - 1).coerceAtLeast(0))
                                } else {
                                    0
                                }
                                val story = stories.getOrNull(idx)
                                // ≡ isInChainMode ? story.authorId : userId
                                handleStoryNext(
                                    if (isInChainMode) story?.authorId ?: pageUserId else pageUserId,
                                )
                            },
                            onPrevious = {
                                if (pageUserId == currentUserId && storyIndex > 0) {
                                    storyIndex -= 1
                                } else if (pageUserId == currentUserId) {
                                    moveToPreviousUser()
                                }
                            },
                            onDismiss = onDismiss,
                            onReportStory = { story ->
                                reportStory = story
                                showingReportSheet = true
                            },
                            onBlockUser = { story ->
                                reportStory = story
                                showingBlockConfirmation = true
                            },
                            onStoryDeleted = { story -> handleStoryDeleted(story, pageUserId) },
                            onOpenChainStory = { stories, index ->
                                val target = stories.getOrNull(index) ?: return@StoryViewerPageContent
                                val storyId = target.id ?: return@StoryViewerPageContent
                                // ≡ NotificationCenter NavigateToChainStory
                                navigateToChainStory(storyId, index, loadedChain = stories)
                            },
                            loadingOverlayState = loadingOverlayState,
                        )
                    }
                }
                canRenderStoryViewer && currentUserId != null -> {
                    StoryViewerPageContent(
                        pageUserId = currentUserId,
                        currentUserId = currentUserId,
                        storyIndex = storyIndex,
                        viewModel = viewModel,
                        isDeckPageActive = true,
                        highlightTitle = highlightTitle,
                        showingReportSheet = showingReportSheet,
                        showingBlockConfirmation = showingBlockConfirmation,
                        gestureGate = deckGestureGate,
                        onNext = {
                            // ≡ isInChainMode ? story.authorId : userId
                            handleStoryNext(
                                if (isInChainMode) currentStory?.authorId ?: currentUserId else currentUserId,
                            )
                        },
                        onPrevious = goPreviousState.value,
                        onDismiss = onDismiss,
                        onReportStory = { story ->
                            reportStory = story
                            showingReportSheet = true
                        },
                        onBlockUser = { story ->
                            reportStory = story
                            showingBlockConfirmation = true
                        },
                        onStoryDeleted = { story -> handleStoryDeleted(story, currentUserId) },
                        onOpenChainStory = { stories, index ->
                            val target = stories.getOrNull(index) ?: return@StoryViewerPageContent
                            val storyId = target.id ?: return@StoryViewerPageContent
                            // ≡ NotificationCenter NavigateToChainStory
                            navigateToChainStory(storyId, index, loadedChain = stories)
                        },
                        loadingOverlayState = loadingOverlayState,
                    )
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GlassmorphicEmptyState(
                            icon = Icons.Filled.Warning,
                            message = stringResource(R.string.stories_error_loading_story),
                            showCloseButton = true,
                            onClose = onDismiss,
                        )
                    }
                }
            }
            }
        }
    }

    val storyForReport = reportStory ?: currentStory
    if (showingReportSheet && storyForReport != null) {
        ReportBottomSheet(
            target = ReportTarget.StoryTarget(storyForReport),
            onDismiss = {
                showingReportSheet = false
                reportStory = null
            },
        )
    }
    LaunchedEffect(showingReportSheet, storyForReport) {
        if (showingReportSheet && storyForReport == null) showingReportSheet = false
    }

    if (showingBlockConfirmation) {
        AlertDialog(
            onDismissRequest = { showingBlockConfirmation = false },
            title = { Text(stringResource(R.string.stories_block_user_title)) },
            text = { Text(stringResource(R.string.stories_block_user_message)) },
            confirmButton = {
                TextButton(onClick = { blockUserConfirmed() }) {
                    Text(stringResource(R.string.stories_block_user_action), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showingBlockConfirmation = false }) {
                    Text(stringResource(R.string.stories_block_user_cancel))
                }
            },
        )
    }
}

@Composable
private fun StoryViewerPageContent(
    pageUserId: String,
    currentUserId: String?,
    storyIndex: Int,
    viewModel: StoryViewModel,
    isDeckPageActive: Boolean,
    highlightTitle: String?,
    showingReportSheet: Boolean,
    showingBlockConfirmation: Boolean,
    gestureGate: StoryDeckGestureGate,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
    onReportStory: (Story) -> Unit,
    onBlockUser: (Story) -> Unit,
    onStoryDeleted: (Story) -> Unit,
    onOpenChainStory: (List<Story>, Int) -> Unit,
    loadingOverlayState: StoryLoadingOverlayState,
) {
    val pageStories = viewModel.storiesFor(pageUserId)
    if (pageStories.isEmpty()) {
        StoryViewerLoadingState(
            state = loadingOverlayState,
            onClose = onDismiss,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val pageStoryIndex =
        if (pageUserId == currentUserId) min(storyIndex, pageStories.lastIndex) else 0
    val pageStory = pageStories.getOrNull(pageStoryIndex) ?: return
    StoryViewerScreen(
        story = pageStory,
        segmentCount = pageStories.size,
        segmentIndex = pageStoryIndex,
        onNext = onNext,
        onPrevious = onPrevious,
        onDismiss = onDismiss,
        storyViewModel = viewModel,
        gestureGate = gestureGate,
        isDeckPageActive = isDeckPageActive,
        viewers = viewModel.storyViewers[pageStory.id.orEmpty()].orEmpty(),
        reactions = viewModel.storyReactions[pageStory.id.orEmpty()].orEmpty(),
        showingReportSheet = showingReportSheet,
        showingBlockConfirmation = showingBlockConfirmation,
        onReportStory = { onReportStory(pageStory) },
        onBlockUser = { onBlockUser(pageStory) },
        onStoryDeleted = { onStoryDeleted(pageStory) },
        onOpenChainStory = onOpenChainStory,
        highlightTitle = highlightTitle,
        modifier = Modifier.fillMaxSize(),
    )
}

private sealed class StoryLoadingMode {
    data object Initial : StoryLoadingMode()
    data class Author(val userId: String) : StoryLoadingMode()
}

private sealed class StoryLoadingOverlayState {
    data object Loading : StoryLoadingOverlayState()
    data class Error(val message: String) : StoryLoadingOverlayState()
}

/** Port de `StoryViewerLoadingState` en StoriesView.swift. */
@Composable
private fun StoryViewerLoadingState(
    state: StoryLoadingOverlayState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val background = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primaryChrome = if (isDark) Color.White.copy(0.96f) else Color.Black.copy(0.82f)
    val secondaryChrome = if (isDark) Color.White.copy(0.16f) else Color.Black.copy(0.10f)
    val tertiaryChrome = if (isDark) Color.White.copy(0.28f) else Color.Black.copy(0.18f)
    val spinnerTint = if (isDark) Color.White else Color.Black.copy(0.82f)

    Box(
        modifier
            .background(background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, start = 12.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(4) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .background(
                                    if (index == 0) tertiaryChrome else secondaryChrome,
                                    RoundedCornerShape(percent = 50),
                                ),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).background(secondaryChrome, CircleShape))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier
                                .width(108.dp)
                                .height(11.dp)
                                .background(tertiaryChrome, RoundedCornerShape(percent = 50)),
                        )
                        Box(
                            Modifier
                                .width(58.dp)
                                .height(9.dp)
                                .background(secondaryChrome, RoundedCornerShape(percent = 50)),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(
                                if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f),
                                CircleShape,
                            )
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.common_close),
                            tint = primaryChrome,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (state) {
                    StoryLoadingOverlayState.Loading -> {
                        CircularProgressIndicator(
                            color = spinnerTint,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    is StoryLoadingOverlayState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(horizontal = 28.dp),
                        ) {
                            Text("⚠️", fontSize = 26.sp)
                            Text(
                                state.message,
                                color = primaryChrome.copy(0.88f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                            )
                            TextButton(onClick = onClose) {
                                Text(
                                    stringResource(R.string.common_close),
                                    color = primaryChrome,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(46.dp)
                    .background(
                        if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f),
                        RoundedCornerShape(22.dp),
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .width(128.dp)
                        .height(11.dp)
                        .background(secondaryChrome, RoundedCornerShape(percent = 50)),
                )
            }
        }
    }
}
