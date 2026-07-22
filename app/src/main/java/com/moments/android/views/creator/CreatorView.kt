package com.moments.android.views.creator

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.moments.android.models.StickerData
import com.moments.android.views.creator.creatorscreens.CaptionAndDetailsView
import com.moments.android.views.creator.creatorscreens.ContentTypeSelectionView
import com.moments.android.views.creator.creatorscreens.CreatorFlowPendingScreen
import com.moments.android.views.creator.creatorscreens.MediaEditingView
import com.moments.android.views.creator.creatorscreens.MediaSelectionView
import com.moments.android.views.creator.creatorscreens.StoryCameraView
import com.moments.android.views.creator.creatorscreens.StoryEditingView

/**
 * Port de `CreatorView.swift` — orquestador de flujos.
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

/** Espejo de `CreatorMedia.AspectRatio` (CreatorSharedModels.swift). */
enum class CreatorAspectRatio(val displayName: String, val ratio: Float) {
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 0.8f),
    LANDSCAPE("16:9", 16f / 9f),
    NINE_BY_SIXTEEN("9:16", 9f / 16f);

    companion object {
        fun fromRatio(imageRatio: Float): CreatorAspectRatio {
            val tolerance = 0.15f
            return when {
                kotlin.math.abs(imageRatio - 0.5625f) < tolerance -> NINE_BY_SIXTEEN
                kotlin.math.abs(imageRatio - 0.8f) < tolerance -> PORTRAIT
                kotlin.math.abs(imageRatio - 1f) < tolerance -> SQUARE
                kotlin.math.abs(imageRatio - 1.777f) < tolerance -> LANDSCAPE
                imageRatio < 0.65f -> NINE_BY_SIXTEEN
                imageRatio < 0.85f -> PORTRAIT
                imageRatio < 1.15f -> SQUARE
                else -> LANDSCAPE
            }
        }
    }
}

/** Espejo de `CreatorMedia` (CreatorSharedModels.swift) para el flujo. */
data class CreatorMedia(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: Uri,
    val isVideo: Boolean = false,
    val durationSeconds: Double? = null,
    val aspectRatio: CreatorAspectRatio = CreatorAspectRatio.SQUARE,
    val recommendedAspectRatio: CreatorAspectRatio? = null,
    val hasEdits: Boolean = false,
    /** Etiquetas espaciales — iOS `CreatorMedia.tags`. */
    val tags: List<com.moments.android.models.PhotoTag> = emptyList(),
) {
    companion object {
        /** iOS `CreatorMedia.maxMomentVideoDuration` = 5 min */
        const val MAX_MOMENT_VIDEO_DURATION_SECONDS = 5.0 * 60.0
    }
}

/** Álbumes MediaStore — espejo de `AlbumInfo` sin PHAssetCollection. */
data class CreatorAlbumInfo(
    val id: String,
    val title: String,
    val bucketId: String?,
    val assetCount: Int,
)

@Composable
fun CreatorView(
    showCreatorView: Boolean,
    onShowCreatorViewChange: (Boolean) -> Unit,
    isCreatingStory: Boolean,
    onIsCreatingStoryChange: (Boolean) -> Unit,
    openInStoryMode: Boolean = false,
    /** Hasta portar `StickerItem` UI de stickerview.swift; usamos StickerData. */
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
    var selectedMediaItems by remember {
        mutableStateOf(initialMedia.orEmpty())
    }
    var storyStartsInTextMode by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        if (openInStoryMode || initialSticker != null || initialMedia != null) {
            onIsCreatingStoryChange(true)
        }
        onDispose {
            // iOS cleanupVideoAndAudio — se cablea al portar StoryCamera / editors
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (currentFlow) {
            CreatorFlow.TYPE_SELECTION -> ContentTypeSelectionView(
                contentType = contentType,
                onContentTypeChange = { contentType = it },
                currentFlow = currentFlow,
                onCurrentFlowChange = { flow ->
                    currentFlow = flow
                    onIsCreatingStoryChange(flow == CreatorFlow.STORY_CAMERA || flow == CreatorFlow.STORY_EDITING)
                },
                onDismiss = { onShowCreatorViewChange(false) },
            )
            CreatorFlow.MEDIA_SELECTION -> MediaSelectionView(
                selectedMediaItems = selectedMediaItems,
                onSelectedMediaItemsChange = { selectedMediaItems = it },
                onCurrentFlowChange = { flow ->
                    currentFlow = flow
                    onIsCreatingStoryChange(false)
                },
                onDismiss = { onShowCreatorViewChange(false) },
            )
            CreatorFlow.MEDIA_EDITING -> MediaEditingView(
                selectedMediaItems = selectedMediaItems,
                onSelectedMediaItemsChange = { selectedMediaItems = it },
                onCurrentFlowChange = { currentFlow = it },
                onDismiss = { onShowCreatorViewChange(false) },
            )
            CreatorFlow.VIDEO_EDITING -> CreatorFlowPendingScreen(
                iosSource = "VideoEditor.swift / SocialVideoEditorView",
                onBack = { currentFlow = CreatorFlow.MEDIA_SELECTION },
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
                onCurrentFlowChange = { flow ->
                    currentFlow = flow
                    onIsCreatingStoryChange(flow == CreatorFlow.STORY_CAMERA || flow == CreatorFlow.STORY_EDITING)
                },
                onStoryStartsInTextModeChange = { storyStartsInTextMode = it },
                onDismiss = { onShowCreatorViewChange(false) },
            )
            CreatorFlow.STORY_EDITING -> StoryEditingView(
                selectedMediaItems = selectedMediaItems,
                onSelectedMediaItemsChange = { selectedMediaItems = it },
                onCurrentFlowChange = { flow ->
                    currentFlow = flow
                    onIsCreatingStoryChange(flow == CreatorFlow.STORY_CAMERA || flow == CreatorFlow.STORY_EDITING)
                },
                startInTextMode = storyStartsInTextMode,
                onStartInTextModeChange = { storyStartsInTextMode = it },
                onDismiss = { onShowCreatorViewChange(false) },
            )
        }
    }

    // Suppress unused until sticker/chain entry consume them (paridad iOS state).
    @Suppress("UNUSED_VARIABLE")
    val keep = Pair(initialSticker, isCreatingStory)
}
