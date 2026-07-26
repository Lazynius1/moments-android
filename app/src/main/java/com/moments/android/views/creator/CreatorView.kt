package com.moments.android.views.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.models.StickerData
import com.moments.android.utilities.MomentsAudioSession
import com.moments.android.views.creator.creatorscreens.CaptionAndDetailsView
import com.moments.android.views.creator.creatorscreens.ContentTypeSelectionView
import com.moments.android.views.creator.creatorscreens.MediaEditingView
import com.moments.android.views.creator.creatorscreens.MediaSelectionView
import com.moments.android.views.creator.creatorscreens.StoryCameraView

/**
 * Port de `CreatorView.swift` — orquestador de flujos (MARK Main Creator View).
 * Reveal sticker editor → [RevealStickerEditor.kt].
 * Modelos compartidos → [CreatorSharedModels].
 */
enum class CreatorFlow {
    TYPE_SELECTION,
    MEDIA_SELECTION,
    MEDIA_EDITING,
    VIDEO_EDITING,
    CAPTION_AND_DETAILS,
    STORY_CAMERA,
    STORY_EDITING,
}

enum class CreatorContentType {
    MOMENT,
    STORY,
}

@Composable
fun CreatorView(
    showCreatorView: Boolean,
    onShowCreatorViewChange: (Boolean) -> Unit,
    isCreatingStory: Boolean,
    onIsCreatingStoryChange: (Boolean) -> Unit,
    openInStoryMode: Boolean = false,
    /** iOS `StickerItem`; Android [StickerData] hasta paridad total de stickerview. */
    initialSticker: StickerData? = null,
    initialMedia: List<CreatorMedia>? = null,
    startInCameraWhenOnlySticker: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (!showCreatorView) return

    var currentFlow by remember {
        mutableStateOf(
            when {
                initialMedia != null -> CreatorFlow.STORY_EDITING
                initialSticker != null && startInCameraWhenOnlySticker -> CreatorFlow.STORY_CAMERA
                initialSticker != null -> CreatorFlow.STORY_EDITING
                openInStoryMode -> CreatorFlow.STORY_CAMERA
                else -> CreatorFlow.TYPE_SELECTION
            },
        )
    }
    var contentType by remember {
        mutableStateOf(
            when {
                initialMedia != null || initialSticker != null || openInStoryMode -> CreatorContentType.STORY
                else -> CreatorContentType.MOMENT
            },
        )
    }
    var selectedMediaItems by remember { mutableStateOf(initialMedia.orEmpty()) }
    var storyStartsInTextMode by remember { mutableStateOf(false) }
    // ≡ responseSticker
    var responseSticker by remember { mutableStateOf(initialSticker) }
    // ≡ pendingChain*
    var pendingChainId by remember { mutableStateOf<String?>(null) }
    var pendingChainTitle by remember { mutableStateOf<String?>(null) }
    var pendingChainPosition by remember { mutableStateOf<Int?>(null) }
    var skipContentTypeEffect by remember { mutableStateOf(true) }

    fun applyFlow(flow: CreatorFlow) {
        currentFlow = flow
        onIsCreatingStoryChange(
            flow == CreatorFlow.STORY_CAMERA || flow == CreatorFlow.STORY_EDITING,
        )
    }

    fun cleanupVideoAndAudio() {
        selectedMediaItems = emptyList()
        MomentsAudioSession.deactivate()
        NavigationEventBus.emit(CoordinatorNavigationEvent.CleanupVideoPlayer)
    }

    // ≡ onAppear setupResponseStickerListener + setupContinueChainListener
    LaunchedEffect(Unit) {
        if (openInStoryMode || initialSticker != null || initialMedia != null) {
            onIsCreatingStoryChange(true)
        }
        if (initialSticker != null && responseSticker == null) {
            responseSticker = initialSticker
        }
        NavigationEventBus.events.collect { event ->
            when (event) {
                is CoordinatorNavigationEvent.AddResponseStickerToCreator -> {
                    responseSticker = event.sticker
                    contentType = CreatorContentType.STORY
                    applyFlow(CreatorFlow.STORY_EDITING)
                }
                is CoordinatorNavigationEvent.ContinueStoryChain -> {
                    contentType = CreatorContentType.STORY
                    applyFlow(CreatorFlow.STORY_CAMERA)
                    onIsCreatingStoryChange(true)
                    pendingChainId = event.chainId
                    pendingChainTitle = event.chainTitle
                    pendingChainPosition = event.chainPosition
                    NavigationEventBus.emit(
                        CoordinatorNavigationEvent.SetChainContext(
                            event.chainId,
                            event.chainTitle,
                            event.chainPosition,
                        ),
                    )
                }
                is CoordinatorNavigationEvent.SetContentType -> {
                    if (event.contentType == "story") {
                        contentType = CreatorContentType.STORY
                        applyFlow(CreatorFlow.STORY_CAMERA)
                        onIsCreatingStoryChange(true)
                    }
                }
                is CoordinatorNavigationEvent.SetChainContext -> {
                    pendingChainId = event.chainId
                    pendingChainTitle = event.chainTitle
                    pendingChainPosition = event.chainPosition
                }
                else -> Unit
            }
        }
    }

    // ≡ onChange(of: contentType) con guards iOS (no en el primer composition)
    LaunchedEffect(contentType) {
        if (skipContentTypeEffect) {
            skipContentTypeEffect = false
            return@LaunchedEffect
        }
        if (initialSticker != null || responseSticker != null) {
            if (currentFlow == CreatorFlow.STORY_EDITING) return@LaunchedEffect
        }
        if (currentFlow != CreatorFlow.TYPE_SELECTION) return@LaunchedEffect
        if (contentType == CreatorContentType.STORY) {
            applyFlow(
                if (initialMedia != null) CreatorFlow.STORY_EDITING else CreatorFlow.STORY_CAMERA,
            )
            onIsCreatingStoryChange(true)
        } else {
            applyFlow(CreatorFlow.MEDIA_SELECTION)
            onIsCreatingStoryChange(false)
        }
    }

    // ≡ onDisappear cleanup
    DisposableEffect(Unit) {
        onDispose { cleanupVideoAndAudio() }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (currentFlow) {
            CreatorFlow.TYPE_SELECTION -> ContentTypeSelectionView(
                contentType = contentType,
                onContentTypeChange = { contentType = it },
                currentFlow = currentFlow,
                onCurrentFlowChange = { applyFlow(it) },
                onDismiss = { onShowCreatorViewChange(false) },
            )
            CreatorFlow.MEDIA_SELECTION -> MediaSelectionView(
                selectedMediaItems = selectedMediaItems,
                onSelectedMediaItemsChange = { selectedMediaItems = it },
                onCurrentFlowChange = { currentFlow = it },
                onDismiss = { onShowCreatorViewChange(false) },
            )
            CreatorFlow.MEDIA_EDITING -> MediaEditingView(
                selectedMediaItems = selectedMediaItems,
                onSelectedMediaItemsChange = { selectedMediaItems = it },
                onCurrentFlowChange = { currentFlow = it },
                onDismiss = { onShowCreatorViewChange(false) },
            )
            CreatorFlow.VIDEO_EDITING -> MediaEditingView(
                selectedMediaItems = selectedMediaItems,
                onSelectedMediaItemsChange = { selectedMediaItems = it },
                onCurrentFlowChange = { currentFlow = it },
                onDismiss = { onShowCreatorViewChange(false) },
            )
            CreatorFlow.CAPTION_AND_DETAILS -> CaptionAndDetailsView(
                selectedMediaItems = selectedMediaItems,
                onSelectedMediaItemsChange = { selectedMediaItems = it },
                onCurrentFlowChange = { currentFlow = it },
                onDismiss = { onShowCreatorViewChange(false) },
            )
            CreatorFlow.STORY_CAMERA -> StoryCameraView(
                selectedMediaItems = selectedMediaItems,
                onSelectedMediaItemsChange = { selectedMediaItems = it },
                onCurrentFlowChange = { applyFlow(it) },
                onStoryStartsInTextModeChange = { storyStartsInTextMode = it },
                onDismiss = { onShowCreatorViewChange(false) },
            )
            CreatorFlow.STORY_EDITING -> StoryEditingView(
                selectedMediaItems = selectedMediaItems,
                onSelectedMediaItemsChange = { selectedMediaItems = it },
                onCurrentFlowChange = { applyFlow(it) },
                startInTextMode = storyStartsInTextMode,
                onStartInTextModeChange = { storyStartsInTextMode = it },
                initialSticker = responseSticker,
                initialChainId = pendingChainId,
                initialChainTitle = pendingChainTitle,
                initialChainPosition = pendingChainPosition,
                onDismiss = { onShowCreatorViewChange(false) },
            )
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val keepCreatingFlag = isCreatingStory
}

// Reveal sticker editor → `RevealStickerEditor.kt` (MARK en CreatorView.swift).
// ModernSelectionInterface / CornerBorders / RotationControl / ScaleControl:
// 0 call sites en iOS (código muerto en el mismo archivo) — no portados.
