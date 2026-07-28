package com.moments.android.views.creator

import com.moments.android.views.creator.components.ActiveEditorMode
import com.moments.android.services.social.StoryChainLimitError
import com.moments.android.services.social.StoryChainLimitsService
import com.moments.android.views.creator.components.StoryBackgroundPreset
import com.moments.android.views.creator.components.StoryMediaBackgroundView
import com.moments.android.views.creator.components.EditableImageView
import com.moments.android.views.creator.components.StoryEditableMediaContainer
import com.moments.android.views.creator.components.storyShouldShowGeneratedBackground
import com.moments.android.views.creator.components.storyDominantBackgroundColors
import com.moments.android.views.creator.components.StoryDrawingEditorOverlay
import com.moments.android.views.creator.components.StoryFilterSelectorView
import com.moments.android.views.creator.components.StoryTextOverlayDraft
import com.moments.android.views.creator.components.StoryTextStyle
import com.moments.android.views.creator.components.StoryTextGradientSettings
import com.moments.android.views.creator.components.StoryVideoGravity
import com.moments.android.views.creator.components.StoryVideoPlayerView
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.requiredSize
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.media.MediaMetadataRetriever
import com.moments.android.views.components.AnimatedStickerView
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.components.InteractiveAudioStickerView
import com.moments.android.views.components.StickerHashtagCardView
import com.moments.android.views.components.StickerLinkCardView
import com.moments.android.views.components.StickerLocationCardView
import com.moments.android.views.components.StickerMentionCardView
import com.moments.android.views.components.StickerPolaroidFrameView
import com.moments.android.views.components.StickerTimeCardView
import com.moments.android.views.components.StoryPolaroidFrameStyle
import com.moments.android.views.story.QuestionResponseStoryStickerCardView
import com.moments.android.views.creator.components.StoryTextOverlayLabel
import com.moments.android.views.creator.creatorscreens.CreatorFlowPendingScreen
import com.moments.android.views.creator.creatorscreens.SelfieStickerLiveCameraView
import com.moments.android.views.creator.creatorscreens.StoryDrawingCanvasOverlay
import com.moments.android.views.creator.creatorscreens.StoryOverlayDragState
import com.moments.android.views.creator.creatorscreens.StoryOverlayTrashZone
import com.moments.android.views.creator.creatorscreens.StoryOverlayToast
import com.moments.android.views.creator.creatorscreens.StoryOverlayToastHost
import com.moments.android.views.creator.creatorscreens.StoryPolaroidCaptionField
import com.moments.android.views.creator.creatorscreens.StoryRevealStatusBadge
import com.moments.android.views.creator.creatorscreens.StickerOverlayView
import com.moments.android.views.creator.creatorscreens.StoryTextEditor
import com.moments.android.views.creator.creatorscreens.StoryTextOverlayItem
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.moments.android.views.creator.creatoruikit.creatorMomentsCaptureRect
import com.moments.android.views.creator.creatoruikit.creatorNormalizedUp
import com.moments.android.views.creator.creatoruikit.storyViewerCanvasCornerRadius
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.CachedSticker
import com.moments.android.models.CachedStickerInteractionData
import com.moments.android.models.Point
import com.moments.android.models.StickerData
import com.moments.android.services.content.FilterService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.searchUsers
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.utilities.HapticManager
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.views.creator.BackgroundStoryUploadService
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorFlow
import com.moments.android.views.creator.CreatorMedia
import com.moments.android.views.creator.audienceselector.AudienceSelectionView
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.feed.maps.LocationMapView
import com.moments.android.views.permission.shared.PermissionPrimerGate
import com.moments.android.views.permission.shared.PermissionPrimerGateHost
import com.moments.android.views.creator.EmojiPickerView
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.Layers
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moments.android.views.messaging.media.CameraPickerMediaType
import com.moments.android.views.messaging.media.ChatMediaOverlayPayload
import com.moments.android.views.messaging.media.ChatMediaSendMode
import com.moments.android.views.messaging.media.ChatMediaSendModeIcon
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.creator.stickerHostLabel
import kotlinx.coroutines.Dispatchers
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/** Equivalente local de la edición inline que `StickerOverlayView.swift` habilita por tipo. */
private fun stickerSupportsInlineEdit(sticker: StoryStickerDraft): Boolean = when (sticker.type) {
    "poll", "question", "countdown", "quiz", "emojiSlider" -> true
    "hashtag" -> sticker.hashtag.isNullOrBlank()
    else -> false
}

/** Representación de cuenta atrás para el card del editor: HH:MM:SS, igual que el desglose Swift. */
private fun formatCountdownRemaining(targetAtMs: Double): String {
    val totalSeconds = ((targetAtMs - System.currentTimeMillis()) / 1000.0).toLong().coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

/**
 * Port de `storyeditor.swift` / `StoryEditingView`.
 * Chunk 1: canvas + Share.
 * Chunk 2: text overlays (crear/editar/arrastrar/borrar) + StoryTextEditor mínimo.
 * Chunk 3: fonts + color swatches en texto.
 * Chunk 4: dibujo (StoryDrawingEditorOverlay).
 * Chunk 5: filtros (StoryFilterSelectorView + FilterService).
 * Chunk 6: stickers emoji (StickerPickerView + overlay + upload).
 * Stickers interactivos / GIF / motion: chunks siguientes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryEditingView(
    selectedMediaItems: List<CreatorMedia>,
    onSelectedMediaItemsChange: (List<CreatorMedia>) -> Unit,
    onCurrentFlowChange: (CreatorFlow) -> Unit,
    startInTextMode: Boolean,
    onStartInTextModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    /** iOS `initialSticker` / response sticker. */
    initialSticker: StickerData? = null,
    initialChainId: String? = null,
    initialChainTitle: String? = null,
    initialChainPosition: Int? = null,
    /** ≡ iOS `chatRecipientUserId` — avatar en barra de envío chat. */
    chatRecipientUserId: String? = null,
    /** ≡ iOS `onChatSend` — si no es null, editor en modo chat (sin publicar historia). */
    onChatSend: ((ByteArray, CameraPickerMediaType, ChatMediaSendMode, ChatMediaOverlayPayload?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val canvas = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val controlFg = if (isDark) Color.White else Color.Black.copy(0.82f)
    val controlStroke = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)
    val shareBg = if (isDark) Color(0xFFFAF9F6) else Color(0xFF0B1215)
    val shareFg = if (isDark) Color.Black.copy(0.9f) else Color.White

    var audience by remember { mutableStateOf(ContentAudience.EVERYONE) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var selectedListName by remember { mutableStateOf<String?>(null) }
    var customSelectedUsers by remember { mutableStateOf<List<String>>(emptyList()) }
    var expirationHours by remember { mutableIntStateOf(24) }
    var isPublishing by remember { mutableStateOf(false) }
    var showingAudience by remember { mutableStateOf(false) }
    var pendingTool by remember { mutableStateOf<String?>(null) }

    var activeEditorMode by remember { mutableStateOf(ActiveEditorMode.IDLE) }
    var textOverlays by remember { mutableStateOf<List<StoryTextOverlayDraft>>(emptyList()) }
    var activeTextOverlayId by remember { mutableStateOf<String?>(null) }
    var editorBuffer by remember { mutableStateOf("") }
    var editorStyle by remember { mutableStateOf(StoryTextStyle.MODERN) }
    var editorColorHex by remember { mutableStateOf(StoryTextStyle.MODERN.defaultColorHex) }
    var editorTextAlignmentRaw by remember { mutableStateOf("center") }
    var editorTextBackgroundFillRaw by remember { mutableStateOf("none") }
    var editorTextFontSize by remember { mutableStateOf(30f) }
    var editorTextStrokeRaw by remember { mutableStateOf("none") }
    var editorTextMotionRaw by remember { mutableStateOf("none") }
    var editorVisualEffectRaw by remember { mutableStateOf("none") }
    var editorGradientStops by remember { mutableStateOf<List<Color>>(emptyList()) }
    var editorGradientAngle by remember { mutableIntStateOf(0) }
    var editorSelectedGradientStopIndex by remember { mutableIntStateOf(0) }
    var editorForcesAllCaps by remember { mutableStateOf(false) }
    var nextLayerOrder by remember { mutableIntStateOf(0) }
    var deleteArmedId by remember { mutableStateOf<String?>(null) }
    var overlayDragState by remember { mutableStateOf(StoryOverlayDragState()) }
    var drawingImage by remember { mutableStateOf<Bitmap?>(null) }
    var drawingOffsetX by remember { mutableFloatStateOf(0f) }
    var drawingOffsetY by remember { mutableFloatStateOf(0f) }
    var drawingScale by remember { mutableFloatStateOf(1f) }
    var selectedFilter by remember { mutableStateOf(FilterService.FilterType.NORMAL) }
    var filterIntensity by remember { mutableDoubleStateOf(1.0) }
    var filteredImage by remember { mutableStateOf<Bitmap?>(null) }
    var filterJob by remember { mutableStateOf<Job?>(null) }
    var showingIntensitySlider by remember { mutableStateOf(false) }
    var isVideoPreviewMuted by remember { mutableStateOf(false) }
    var stickers by remember { mutableStateOf<List<StoryStickerDraft>>(emptyList()) }
    var showingStickerPicker by remember { mutableStateOf(false) }
    var nextStickerZ by remember { mutableIntStateOf(0) }
    // ≡ iOS chain context (storyeditor.swift)
    var chainId by remember { mutableStateOf<String?>(null) }
    var chainTitle by remember { mutableStateOf("") }
    var chainPosition by remember { mutableStateOf<Int?>(null) }
    var originalChainTitle by remember { mutableStateOf("") }
    var isContinuingChain by remember { mutableStateOf(false) }
    // ≡ iOS selectedBackgroundPresetIndex / discard / gallery alert
    var selectedBackgroundPresetIndex by remember { mutableIntStateOf(0) }
    var showDiscardChangesAlert by remember { mutableStateOf(false) }
    // ≡ iOS isCreatingChain / allowOthersToContinue / continuationAudience / showingChainConfiguration
    var isCreatingChain by remember { mutableStateOf(false) }
    var allowOthersToContinue by remember { mutableStateOf(true) }
    var continuationAudience by remember { mutableStateOf(ChainContinuationSetting.EVERYONE) }
    var showingChainConfiguration by remember { mutableStateOf(false) }
    // ≡ iOS chat send mode
    var chatSendMode by remember { mutableStateOf(ChatMediaSendMode.VIEW_ONCE) }
    var mediaCanvasWidthPx by remember { mutableFloatStateOf(1080f) }
    var mediaCanvasHeightPx by remember { mutableFloatStateOf(1920f) }
    // Rect del capture en coords de pantalla completa (para drawing overlay fuera del clip).
    var mediaCaptureRect by remember { mutableStateOf(Rect.Zero) }
    val isChatSendMode = onChatSend != null
    // ≡ iOS imageScale / imageOffset / imageRotation + EditableImageView
    var imageScale by remember { mutableFloatStateOf(1f) }
    var imageOffset by remember { mutableStateOf(Offset.Zero) }
    var imageRotationRadians by remember { mutableFloatStateOf(0f) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingUserSettings by remember { mutableStateOf(true) }
    // ≡ iOS autoBackgroundPalette / autoBackgroundPaletteMediaId
    var autoBackgroundPalette by remember {
        mutableStateOf(
            listOf(Color(0xFF0B1215), Color(0xFFFAF9F6)),
        )
    }
    var autoBackgroundPaletteMediaId by remember { mutableStateOf<String?>(null) }
    // ≡ iOS showingLocationMap / selectedLocationName / selectedCoordinate
    var showingLocationMap by remember { mutableStateOf(false) }
    var selectedLocationName by remember { mutableStateOf("") }
    var selectedLocationLat by remember { mutableStateOf<Double?>(null) }
    var selectedLocationLng by remember { mutableStateOf<Double?>(null) }
    var showingExpirationInfoOverlay by remember { mutableStateOf(false) }
    var showingEmojiPicker by remember { mutableStateOf(false) }
    val emojiUsageTracker = remember { com.moments.android.utilities.EmojiUsageTracker() }
    val photosSaveGate = remember { PermissionPrimerGate(PermissionPrimerGate.Kind.PHOTOS_SAVE) }

    val selectedBackgroundPreset =
        StoryBackgroundPreset.presets[selectedBackgroundPresetIndex % StoryBackgroundPreset.presets.size]

    val showsStoryExpirationSelector = !isChatSendMode && !isCreatingChain && !isContinuingChain
    val showsGeneratedBackground = storyShouldShowGeneratedBackground(
        imageScale,
        imageOffset,
        imageRotationRadians,
    )

    fun resolvedStoryBackgroundPalette(): List<Color> {
        return if (selectedBackgroundPreset.usesAutoPalette) {
            autoBackgroundPalette.ifEmpty {
                listOf(Color(0xFF0B1215), Color(0xFF203A43), Color(0xFFFAF9F6))
            }
        } else {
            selectedBackgroundPreset.colors.ifEmpty {
                listOf(Color(0xFF0B1215), Color(0xFF203A43), Color(0xFFFAF9F6))
            }
        }
    }

    fun setChainContext(id: String, title: String, position: Int) {
        chainId = id
        chainTitle = title
        chainPosition = position
        isContinuingChain = true
        originalChainTitle = title
    }

    fun stickerDataToDraft(data: StickerData): StoryStickerDraft = StoryStickerDraft(
        id = data.stickerId ?: UUID.randomUUID().toString(),
        type = data.type,
        content = data.content,
        normalizedX = data.position.x,
        normalizedY = data.position.y,
        scale = data.scale,
        rotationRadians = data.rotation,
        zIndex = data.zIndex ?: nextStickerZ++,
        gifURL = data.gifURL,
        videoURL = data.videoURL,
        isAnimated = data.isAnimated,
        hashtag = data.hashtag,
        weatherSymbol = data.weatherSymbol,
        username = data.username,
        userId = data.userId,
        profileImagePath = data.profileImagePath,
        questionText = data.questionText,
        caption = data.caption,
        pollOptions = data.pollOptions,
        linkURL = data.linkURL,
        linkTitle = data.linkTitle,
        location = data.location,
        latitude = data.latitude,
        longitude = data.longitude,
        countdownTitle = data.countdownTitle,
        countdownTargetAtMs = data.countdownTargetAtMs,
        quizQuestion = data.quizQuestion,
        quizOptions = data.quizOptions,
        quizCorrectIndex = data.quizCorrectIndex,
        sliderEmoji = data.sliderEmoji,
        sliderPrompt = data.sliderPrompt,
        // shareMoment / image stickers: content es JPEG Base64 (≡ StickerData.from(StickerItem))
        image = decodeStickerContentBitmap(data.type, data.content),
        frameStyle = data.frameStyle,
        contentScale = data.contentScale,
        contentOffsetX = data.contentOffsetX,
        contentOffsetY = data.contentOffsetY,
        styleVariant = data.styleVariant,
        revealType = data.revealType,
        revealPattern = data.revealPattern,
        revealPrimaryColor = data.revealPrimaryColor,
        revealSecondaryColor = data.revealSecondaryColor,
        revealEffectColor = data.revealEffectColor,
        audioURL = data.audioURL,
        audioDuration = data.audioDuration,
    )

    // ≡ onAppear: initialSticker + initialChain* + SetChainContext listener
    LaunchedEffect(Unit) {
        initialSticker?.let { stickers = stickers + stickerDataToDraft(it) }
        val cid = initialChainId
        val ctitle = initialChainTitle
        val cpos = initialChainPosition
        if (cid != null && ctitle != null && cpos != null) {
            setChainContext(cid, ctitle, cpos)
        }
        NavigationEventBus.events.collect { event ->
            if (event is CoordinatorNavigationEvent.SetChainContext) {
                setChainContext(event.chainId, event.chainTitle, event.chainPosition)
            }
        }
    }
    var activeEditingStickerId by remember { mutableStateOf<String?>(null) }
    var deleteArmedStickerId by remember { mutableStateOf<String?>(null) }
    var focusedInlineStickerOriginal by remember { mutableStateOf<StoryStickerDraft?>(null) }
    var editingPolaroidId by remember { mutableStateOf<String?>(null) }
    var editingPolaroidOriginal by remember { mutableStateOf<StoryStickerDraft?>(null) }
    var polaroidCaptionBuffer by remember { mutableStateOf("") }
    var polaroidSwipeOffsetX by remember { mutableStateOf(0f) }
    var editingRevealId by remember { mutableStateOf<String?>(null) }
    val isEditingStickerChrome = activeEditingStickerId != null || editingPolaroidId != null
    var storyOverlayToast by remember { mutableStateOf<StoryOverlayToast?>(null) }
    var draggingStickerId by remember { mutableStateOf<String?>(null) }
    var selectedStickerId by remember { mutableStateOf<String?>(null) }

    fun cycleStickerStyle(stickerId: String) {
        stickers = stickers.map { item ->
            if (item.id != stickerId) return@map item
            val count = if (item.type == "questionResponse") 6 else 4
            val next = ((item.styleVariant ?: 0) + 1) % count
            item.copy(styleVariant = next)
        }
        HapticManager.shared.lightImpact()
    }

    /** iOS `cycleSelectedStickerColor` — poll/question/quiz/countdown/emojiSlider → styleVariant % 6. */
    fun cycleSelectedStickerColor() {
        val selectedId = activeEditingStickerId ?: selectedStickerId ?: return
        stickers = stickers.map { item ->
            if (item.id != selectedId) return@map item
            val next = ((item.styleVariant ?: 0) + 1) % 6
            item.copy(styleVariant = next)
        }
        HapticManager.shared.lightImpact()
    }

    fun cycleBackgroundPreset() {
        selectedBackgroundPresetIndex =
            (selectedBackgroundPresetIndex + 1) % StoryBackgroundPreset.presets.size
        HapticManager.shared.lightImpact()
    }

    fun stickerPalettePreviewColors(): List<Color> = listOf(
        Color(0xFFFF5F6D),
        Color(0xFF9D4EDD),
        Color(0xFF4A00E0),
    )

    fun backgroundPalettePreviewColors(): List<Color> =
        resolvedStoryBackgroundPalette().take(3)

    fun showsStickerPaletteButton(): Boolean {
        val active = stickers.firstOrNull { it.id == activeEditingStickerId } ?: return false
        return active.type in setOf("poll", "question", "quiz", "countdown", "emojiSlider")
    }

    /** Sin transforms aún: solo text-only (media vacío), ≡ iOS `selectedMediaItems.isEmpty`. */
    fun showsBackgroundPaletteButton(): Boolean =
        activeEditorMode == ActiveEditorMode.IDLE &&
            editingRevealId == null &&
            activeEditingStickerId == null &&
            (selectedMediaItems.isEmpty() || showsGeneratedBackground)

    fun updateActiveSliderEmoji(emoji: String) {
        val activeId = activeEditingStickerId ?: return
        emojiUsageTracker.increment(emoji)
        stickers = stickers.map { item ->
            if (item.id != activeId) item
            else item.copy(sliderEmoji = emoji, content = emoji)
        }
        HapticManager.shared.lightImpact()
    }

    fun saveToGalleryAuthorized() {
        val current = selectedMediaItems.firstOrNull()
        val palette = resolvedStoryBackgroundPalette()
        val canvasW = mediaCanvasWidthPx
        val canvasH = mediaCanvasHeightPx
        val scale = imageScale
        val offset = imageOffset
        val rotation = imageRotationRadians
        val drawing = drawingImage
        val drawingS = drawingScale
        val drawingOx = drawingOffsetX
        val drawingOy = drawingOffsetY
        val filterBmp = filteredImage
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (current != null && current.isVideo) {
                        // iOS: vídeo fuente sin bake; overlays van en metadata al publicar.
                        saveUriToGallery(context, current.uri, isVideo = true)
                    } else {
                        val mediaBmp = when {
                            current == null -> null
                            filterBmp != null &&
                                selectedFilter != FilterService.FilterType.NORMAL &&
                                !filterBmp.isRecycled -> filterBmp
                            else ->
                                context.contentResolver.openInputStream(current.uri)
                                    ?.use(BitmapFactory::decodeStream)
                        }
                        val composed = renderStoryWithOverlays(
                            mediaImage = mediaBmp,
                            backgroundPalette = palette,
                            drawing = drawing,
                            drawingScale = drawingS,
                            drawingOffsetX = drawingOx,
                            drawingOffsetY = drawingOy,
                            imageScale = scale,
                            imageOffsetX = offset.x,
                            imageOffsetY = offset.y,
                            imageRotationRadians = rotation,
                            editorCanvasWidth = canvasW,
                            editorCanvasHeight = canvasH,
                        )
                        saveBitmapToGallery(context, composed).also {
                            composed.recycle()
                            if (mediaBmp != null && mediaBmp !== filterBmp && !mediaBmp.isRecycled) {
                                mediaBmp.recycle()
                            }
                        }
                    }
                }.getOrDefault(false)
            }
            Toast.makeText(
                context,
                context.getString(R.string.story_editor_saved_to_gallery),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun saveToGallery() {
        photosSaveGate.requestAccess(context) {
            saveToGalleryAuthorized()
        }
    }

    fun tapCyclesStickerStyle(type: String): Boolean =
        type == "location" || type == "mention" || type == "link" ||
            type == "hashtag" || type == "time" || type == "questionResponse"

    fun restoreFocusedInlineSticker() {
        val original = focusedInlineStickerOriginal ?: return
        stickers = stickers.map { item -> if (item.id == original.id) original else item }
        focusedInlineStickerOriginal = null
    }

    fun focusInlineSticker(sticker: StoryStickerDraft) {
        val existingFocus = focusedInlineStickerOriginal
        if (existingFocus?.id != sticker.id) {
            restoreFocusedInlineSticker()
            focusedInlineStickerOriginal = sticker
            val focusScale = when (sticker.type) {
                "poll", "quiz" -> 1.12
                "question", "countdown", "emojiSlider", "hashtag" -> 1.18
                else -> 1.14
            }
            stickers = stickers.map { item ->
                if (item.id == sticker.id) {
                    item.copy(
                        normalizedX = .5,
                        normalizedY = .33,
                        scale = maxOf(item.scale, focusScale),
                        rotationRadians = 0.0,
                    )
                } else {
                    item
                }
            }
        }
        activeEditingStickerId = sticker.id
        deleteArmedStickerId = null
        HapticManager.shared.mediumImpact()
    }

    fun beginPolaroidEditing(sticker: StoryStickerDraft) {
        restoreFocusedInlineSticker()
        editingPolaroidId = sticker.id
        editingPolaroidOriginal = sticker
        polaroidCaptionBuffer = sticker.caption.orEmpty()
        stickers = stickers.map { item ->
            if (item.id == sticker.id) {
                item.copy(normalizedX = .5, normalizedY = .33, scale = 1.4, rotationRadians = 0.0)
            } else {
                item
            }
        }
        deleteArmedStickerId = null
        HapticManager.shared.mediumImpact()
    }

    fun handleProfileNavigation(userId: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid != null && currentUid == userId) return
        HapticManager.shared.mediumImpact()
        NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToProfile(userId))
        onDismiss()
    }

    fun handleLocationNavigation(locationName: String, latitude: Double?, longitude: Double?) {
        HapticManager.shared.mediumImpact()
        selectedLocationName = locationName
        selectedLocationLat = latitude
        selectedLocationLng = longitude
        showingLocationMap = true
    }

    /**
     * ≡ `handleStickerTap` de `StoryOverlaysView.swift`.
     * En el editor, mention/location/hashtag/link/time/questionResponse suelen ciclar estilo
     * antes de llegar aquí; poll/question suelen ir a edición inline.
     */
    fun handleStickerTap(sticker: StoryStickerDraft) {
        selectedStickerId = sticker.id
        activeEditingStickerId = null
        when (sticker.type) {
            "frame" -> beginPolaroidEditing(sticker)
            "poll" -> storyOverlayToast = StoryOverlayToast.Poll
            "question" -> storyOverlayToast = StoryOverlayToast.Question
            "questionResponse" -> storyOverlayToast = StoryOverlayToast.QuestionResponse
            "hashtag" -> {
                val tag = sticker.hashtag?.takeIf { it.isNotBlank() }
                    ?: sticker.content.removePrefix("#").takeIf { it.isNotBlank() }
                if (tag != null) storyOverlayToast = StoryOverlayToast.Hashtag(tag)
            }
            "mention" -> {
                val knownId = sticker.userId?.takeIf { it.isNotBlank() }
                if (knownId != null) {
                    handleProfileNavigation(knownId)
                    return
                }
                val username = sticker.username?.takeIf { it.isNotBlank() }
                    ?: sticker.content.removePrefix("@").takeIf { it.isNotBlank() }
                    ?: return
                // ≡ findUserIdByUsername + onNavigateToProfile / userNotFound toast
                scope.launch {
                    val users = runCatching {
                        FirestoreService().searchUsers(username, limit = 10)
                    }.getOrDefault(emptyList())
                    val match = users.firstOrNull {
                        it.username.equals(username, ignoreCase = true)
                    }
                    if (match == null) {
                        storyOverlayToast = StoryOverlayToast.UserNotFound(username)
                    } else {
                        handleProfileNavigation(match.id)
                    }
                }
            }
            "location" -> {
                val loc = sticker.location?.takeIf { it.isNotBlank() }
                    ?: sticker.content.takeIf { it.isNotBlank() }
                if (loc != null) {
                    handleLocationNavigation(loc, sticker.latitude, sticker.longitude)
                }
            }
            else -> {
                // Android: segundo tap armado para borrar (iOS usa trash zone).
                deleteArmedStickerId = sticker.id
            }
        }
    }

    fun savePolaroidEditing() {
        val original = editingPolaroidOriginal
        val id = editingPolaroidId
        if (original != null && id != null) {
            stickers = stickers.map { item ->
                if (item.id == id) {
                    // Mantiene caption, estilo y crop editados, restaurando solo el transform de entrada.
                    item.copy(
                        normalizedX = original.normalizedX,
                        normalizedY = original.normalizedY,
                        scale = original.scale,
                        rotationRadians = original.rotationRadians,
                    )
                } else {
                    item
                }
            }
        }
        editingPolaroidId = null
        editingPolaroidOriginal = null
        polaroidCaptionBuffer = ""
    }

    fun cyclePolaroidFrameStyle(direction: Int) {
        val id = editingPolaroidId ?: return
        stickers = stickers.map { item ->
            if (item.id != id) return@map item
            val styles = StoryPolaroidFrameStyle.entries
            val current = StoryPolaroidFrameStyle.fromRawOrDefault(item.frameStyle)
            val index = styles.indexOf(current)
            val next = styles[(index + direction + styles.size) % styles.size]
            item.copy(frameStyle = next.raw)
        }
        HapticManager.shared.lightImpact()
    }

    fun appendLiveSelfieSticker() {
        val placed = StoryStickerDraft(
            type = "selfie",
            caption = "selfie_live",
            image = makeLiveSelfiePlaceholderImage(),
            zIndex = nextStickerZ++,
        )
        stickers = stickers + placed
        activeEditingStickerId = null
        deleteArmedStickerId = null
        HapticManager.shared.mediumImpact()
    }
    val selfiePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) appendLiveSelfieSticker() else HapticManager.shared.warning()
    }
    fun requestSelfieSticker() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            appendLiveSelfieSticker()
        } else {
            selfiePermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun applySelectedFilter() {
        filterJob?.cancel()
        val current = selectedMediaItems.firstOrNull()
        if (current == null || current.isVideo || selectedFilter == FilterService.FilterType.NORMAL) {
            filteredImage = null
            return
        }
        val uri = current.uri
        val type = selectedFilter
        val intensity = filterIntensity
        filterJob = scope.launch {
            delay(45)
            val processed = withContext(Dispatchers.Default) {
                val base = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return@withContext null
                FilterService.applyFilter(type, base, intensity).also {
                    if (it !== base) base.recycle()
                }
            }
            filteredImage = processed
        }
    }

    fun commitActiveTextOverlay() {
        val id = activeTextOverlayId ?: return
        val trimmed = editorBuffer.trim()
        textOverlays = if (trimmed.isEmpty()) {
            textOverlays.filterNot { it.id == id }
        } else {
            textOverlays.map {
                if (it.id != id) it
                else it.copy(
                    text = trimmed,
                    styleRaw = editorStyle.raw,
                    colorHex = editorColorHex,
                    alignmentRaw = editorTextAlignmentRaw,
                    backgroundFillRaw = editorTextBackgroundFillRaw,
                    fontSize = editorTextFontSize.toDouble(),
                    strokeRaw = editorTextStrokeRaw,
                    motionRaw = editorTextMotionRaw,
                    visualEffectRaw = editorVisualEffectRaw,
                    forcesAllCaps = editorForcesAllCaps,
                    gradientStopHexes = StoryTextGradientSettings.encodeStops(editorGradientStops),
                    gradientAngle = editorGradientAngle,
                )
            }
        }
    }

    fun finishTextEditing() {
        commitActiveTextOverlay()
        activeTextOverlayId = null
        editorBuffer = ""
        editorStyle = StoryTextStyle.MODERN
        editorColorHex = StoryTextStyle.MODERN.defaultColorHex
        editorTextAlignmentRaw = "center"
        editorTextBackgroundFillRaw = "none"
        editorTextFontSize = 30f
        editorTextStrokeRaw = "none"
        editorTextMotionRaw = "none"
        editorVisualEffectRaw = "none"
        editorGradientStops = emptyList()
        editorGradientAngle = 0
        editorSelectedGradientStopIndex = 0
        editorForcesAllCaps = false
        activeEditorMode = ActiveEditorMode.IDLE
        deleteArmedId = null
    }

    fun beginCreatingTextOverlay() {
        commitActiveTextOverlay()
        val style = StoryTextStyle.MODERN
        val draft = StoryTextOverlayDraft.defaultPlacement().copy(
            layerOrder = nextLayerOrder++,
            styleRaw = style.raw,
            colorHex = style.defaultColorHex,
            forcesAllCaps = style.usesAllCaps,
        )
        textOverlays = textOverlays + draft
        activeTextOverlayId = draft.id
        editorBuffer = ""
        editorStyle = style
        editorColorHex = style.defaultColorHex
        editorTextAlignmentRaw = "center"
        editorTextBackgroundFillRaw = "none"
        editorTextFontSize = 30f
        editorTextStrokeRaw = "none"
        editorTextMotionRaw = "none"
        editorVisualEffectRaw = "none"
        editorGradientStops = emptyList()
        editorGradientAngle = 0
        editorSelectedGradientStopIndex = 0
        editorForcesAllCaps = style.usesAllCaps
        activeEditorMode = ActiveEditorMode.TEXT
    }

    fun beginEditingTextOverlay(id: String) {
        val existing = textOverlays.firstOrNull { it.id == id } ?: return
        commitActiveTextOverlay()
        val brought = existing.copy(layerOrder = nextLayerOrder++)
        textOverlays = textOverlays.map { if (it.id == id) brought else it }
        activeTextOverlayId = id
        editorBuffer = brought.text
        editorStyle = StoryTextStyle.fromRaw(brought.styleRaw)
        editorColorHex = brought.colorHex.ifBlank { editorStyle.defaultColorHex }
        editorTextAlignmentRaw = brought.alignmentRaw
        editorTextBackgroundFillRaw = brought.backgroundFillRaw
        editorTextFontSize = brought.fontSize.toFloat()
        editorTextStrokeRaw = brought.strokeRaw
        editorTextMotionRaw = brought.motionRaw
        editorVisualEffectRaw = brought.visualEffectRaw
        editorGradientStops = brought.gradientColors
        editorGradientAngle = brought.gradientAngle
        editorSelectedGradientStopIndex = 0
        editorForcesAllCaps = brought.forcesAllCaps
        activeEditorMode = ActiveEditorMode.TEXT
        deleteArmedId = null
    }

    // ≡ iOS applyRandomBackgroundPresetIfNeeded (solo text-only)
    LaunchedEffect(selectedMediaItems.isEmpty()) {
        if (selectedMediaItems.isEmpty()) {
            selectedBackgroundPresetIndex =
                (0 until StoryBackgroundPreset.presets.size).random()
        } else {
            // ≡ resetBaseMediaTransform → preset index 0 al tener media
            selectedBackgroundPresetIndex = 0
        }
    }

    LaunchedEffect(startInTextMode) {
        if (startInTextMode) {
            beginCreatingTextOverlay()
            onStartInTextModeChange(false)
        }
    }

    // ≡ iOS loadUserDefaultAudienceSettings
    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            isLoadingUserSettings = false
            return@LaunchedEffect
        }
        runCatching {
            val snap = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
            val visibility = snap.get("contentVisibilitySettings") as? Map<*, *> ?: return@runCatching
            val raw = visibility["storyAudience"] as? String ?: return@runCatching
            val content = ContentAudience.entries.firstOrNull { it.raw == raw } ?: return@runCatching
            when (content) {
                ContentAudience.CUSTOM -> {
                    audience = ContentAudience.CUSTOM
                    customSelectedUsers =
                        (visibility["storyCustomUsers"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                }
                ContentAudience.CUSTOM_LIST -> {
                    audience = ContentAudience.CUSTOM_LIST
                    selectedListId = visibility["storyCustomListId"] as? String
                    selectedListName = visibility["storyCustomListName"] as? String
                }
                else -> audience = content
            }
        }
        isLoadingUserSettings = false
    }

    val media = selectedMediaItems.firstOrNull()

    // Bitmap fuente para EditableImageView / paleta vídeo
    LaunchedEffect(media?.id, media?.uri, media?.isVideo) {
        val current = media
        if (current == null) {
            sourceBitmap = null
            imageScale = 1f
            imageOffset = Offset.Zero
            imageRotationRadians = 0f
            autoBackgroundPaletteMediaId = null
            return@LaunchedEffect
        }
        imageScale = 1f
        imageOffset = Offset.Zero
        imageRotationRadians = 0f
        sourceBitmap = withContext(Dispatchers.IO) {
            if (current.isVideo) {
                current.thumbnailUri?.let { thumb ->
                    context.contentResolver.openInputStream(thumb)?.use(BitmapFactory::decodeStream)
                } ?: runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, current.uri)
                        retriever.getFrameAtTime(100_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        runCatching { retriever.release() }
                    }
                }.getOrNull()
            } else {
                context.contentResolver.openInputStream(current.uri)
                    ?.use(BitmapFactory::decodeStream)
                    ?.creatorNormalizedUp(context, current.uri)
            }
        }
    }

    // ≡ iOS resolveAutoBackgroundPaletteIfNeeded
    LaunchedEffect(sourceBitmap, media?.id) {
        val bmp = sourceBitmap ?: return@LaunchedEffect
        val id = media?.id ?: return@LaunchedEffect
        if (autoBackgroundPaletteMediaId == id) return@LaunchedEffect
        autoBackgroundPalette = withContext(Dispatchers.Default) {
            storyDominantBackgroundColors(bmp)
        }
        autoBackgroundPaletteMediaId = id
    }

    val hasTextOverlays = textOverlays.any { it.isReady } ||
        (activeEditorMode == ActiveEditorMode.TEXT && editorBuffer.trim().isNotEmpty())
    val hasDrawing = drawingImage != null
    val hasStickers = stickers.isNotEmpty()
    val hasContent = media != null || hasTextOverlays || hasDrawing || hasStickers

    if (showDiscardChangesAlert) {
        AlertDialog(
            onDismissRequest = { showDiscardChangesAlert = false },
            title = { Text(stringResource(R.string.story_editor_discard_title)) },
            text = { Text(stringResource(R.string.story_editor_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardChangesAlert = false
                        onCurrentFlowChange(CreatorFlow.STORY_CAMERA)
                    },
                ) {
                    Text(stringResource(R.string.story_editor_discard_confirm), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardChangesAlert = false }) {
                    Text(stringResource(R.string.story_editor_discard_cancel))
                }
            },
        )
    }

    if (pendingTool != null) {
        CreatorFlowPendingScreen(
            iosSource = pendingTool!!,
            onBack = { pendingTool = null },
            onDismiss = onDismiss,
            modifier = modifier,
        )
        return
    }

    fun handleChainLimitError(error: Throwable) {
        HapticManager.shared.warning()
        val msg = when (error) {
            is StoryChainLimitError.InvalidChainData -> context.getString(R.string.story_chains_error_invalid_title)
            is StoryChainLimitError.MaxPartsReached -> context.getString(R.string.story_chains_error_max_parts)
            is StoryChainLimitError.ChainExpired -> context.getString(R.string.story_chains_error_expired)
            is StoryChainLimitError.TooSoonBetweenParts -> context.getString(R.string.story_chains_error_too_soon)
            is StoryChainLimitError.ChainNotFound -> context.getString(R.string.story_chains_error_not_found)
            is StoryChainLimitError.UserNotAuthorized -> context.getString(R.string.story_chains_error_user_not_authorized)
            else -> context.getString(R.string.story_chains_error_validation, error.message.orEmpty())
        }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        if (isContinuingChain) {
            isContinuingChain = false
            chainId = null
            chainPosition = null
            originalChainTitle = ""
        }
        isPublishing = false
    }

    fun chatOverlayPayload(): ChatMediaOverlayPayload? {
        val prepared = textOverlays.filter { it.isReady }
            .sortedBy { it.layerOrder }
            .mapNotNull { it.toMetadata() }
        val stickerPayload = stickers.sortedBy { it.zIndex }.mapIndexed { index, draft ->
            draft.toChatStickerData(index)
        }
        val payload = ChatMediaOverlayPayload(
            textOverlayLive = if (prepared.isEmpty()) null else true,
            textOverlays = prepared.takeIf { it.isNotEmpty() },
            stickers = stickerPayload.takeIf { it.isNotEmpty() },
            drawingData = null,
        )
        return if (payload.isEmpty) null else payload
    }

    fun chatSend() {
        val send = onChatSend ?: return
        if (isPublishing) return
        finishTextEditing()
        isPublishing = true
        val overlayPayload = chatOverlayPayload()
        val current = selectedMediaItems.firstOrNull()
        scope.launch {
            try {
                if (current != null && current.isVideo) {
                    val (tw, th) = storyRenderTargetSize()
                    val overlay = renderStoryOverlayImage(
                        drawing = drawingImage,
                        drawingScale = drawingScale,
                        drawingOffsetX = drawingOffsetX,
                        drawingOffsetY = drawingOffsetY,
                        targetWidth = tw,
                        targetHeight = th,
                        screenWidth = mediaCanvasWidthPx,
                        screenHeight = mediaCanvasHeightPx,
                    )
                    val shouldBake = shouldBakeCurrentOverlaysIntoVideo(
                        media = current,
                        drawingImage = drawingImage,
                        hasAnyTextOverlays = textOverlays.any { it.isReady },
                        imageScale = imageScale,
                        imageOffsetX = imageOffset.x,
                        imageOffsetY = imageOffset.y,
                        imageRotationRadians = imageRotationRadians,
                    )
                    val prepared = withContext(Dispatchers.IO) {
                        prepareMediaForStoryUpload(
                            context = context,
                            media = current,
                            shouldBake = shouldBake,
                            overlay = overlay,
                            backgroundPalette = resolvedStoryBackgroundPalette(),
                            targetWidth = tw,
                            targetHeight = th,
                            imageScale = imageScale,
                            imageOffsetX = imageOffset.x,
                            imageOffsetY = imageOffset.y,
                            imageRotationRadians = imageRotationRadians,
                            editorCanvasWidth = mediaCanvasWidthPx,
                            editorCanvasHeight = mediaCanvasHeightPx,
                        )
                    }
                    overlay?.recycle()
                    val data = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(prepared.uri)?.use { it.readBytes() }
                            ?: error("Missing video bytes")
                    }
                    isPublishing = false
                    HapticManager.shared.mediumImpact()
                    send(data, CameraPickerMediaType.VIDEO, chatSendMode, overlayPayload)
                } else {
                    val data = withContext(Dispatchers.IO) {
                        renderChatImageJpeg(
                            context = context,
                            media = current,
                            filteredImage = filteredImage,
                            drawingImage = drawingImage,
                            drawingScale = drawingScale,
                            drawingOffsetX = drawingOffsetX,
                            drawingOffsetY = drawingOffsetY,
                            backgroundPalette = resolvedStoryBackgroundPalette(),
                            imageScale = imageScale,
                            imageOffsetX = imageOffset.x,
                            imageOffsetY = imageOffset.y,
                            imageRotationRadians = imageRotationRadians,
                            editorCanvasWidth = mediaCanvasWidthPx,
                            editorCanvasHeight = mediaCanvasHeightPx,
                        )
                    }
                    isPublishing = false
                    if (data == null) return@launch
                    HapticManager.shared.mediumImpact()
                    send(data, CameraPickerMediaType.IMAGE, chatSendMode, overlayPayload)
                }
            } catch (_: Throwable) {
                isPublishing = false
                Toast.makeText(
                    context,
                    context.getString(R.string.story_editor_error_publish_start),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun resetStoryForm() {
        // ≡ iOS resetStoryForm
        textOverlays = emptyList()
        stickers = emptyList()
        drawingImage?.recycle()
        drawingImage = null
        drawingOffsetX = 0f
        drawingOffsetY = 0f
        drawingScale = 1f
        expirationHours = 24
        selectedFilter = FilterService.FilterType.NORMAL
        filterIntensity = 1.0
        filteredImage = null
        imageScale = 1f
        imageOffset = Offset.Zero
        imageRotationRadians = 0f
        isCreatingChain = false
        chainTitle = ""
        activeEditorMode = ActiveEditorMode.IDLE
        activeTextOverlayId = null
        activeEditingStickerId = null
        selectedStickerId = null
        editorBuffer = ""
        editorStyle = StoryTextStyle.MODERN
        editorColorHex = StoryTextStyle.MODERN.defaultColorHex
        editorTextAlignmentRaw = "center"
        editorTextBackgroundFillRaw = "none"
        editorTextFontSize = 30f
        editorTextStrokeRaw = "none"
        editorTextMotionRaw = "none"
        editorVisualEffectRaw = "none"
        editorGradientStops = emptyList()
        editorGradientAngle = 0
        editorSelectedGradientStopIndex = 0
        editorForcesAllCaps = false
    }

    fun publishStory() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (isPublishing || userId == null || !hasContent) return
        finishTextEditing()
        val prepared = textOverlays.filter { it.isReady }
            .sortedBy { it.layerOrder }
            .mapNotNull { it.toMetadata() }
        if (media == null && prepared.isEmpty() && drawingImage == null && stickers.isEmpty()) return
        isPublishing = true
        scope.launch {
            try {
                if (isCreatingChain) {
                    StoryChainLimitsService.validateChainTitle(chainTitle)
                } else if (isContinuingChain) {
                    val existing = chainId ?: throw StoryChainLimitError.ChainNotFound
                    StoryChainLimitsService.canContinueChain(existing, userId)
                }
            } catch (e: Throwable) {
                handleChainLimitError(e)
                return@launch
            }

            // Capturar estado en Main antes de dismiss (≡ iOS publishStoryAfterValidation)
            val baseMedia = media
            val capturedFilter = selectedFilter
            val capturedFilterBmp = filteredImage?.let { src ->
                if (src.isRecycled) null else src.copy(Bitmap.Config.ARGB_8888, false)
            }
            val capturedSourceBmp = sourceBitmap?.let { src ->
                if (src.isRecycled) null else src.copy(Bitmap.Config.ARGB_8888, false)
            }
            val capturedDrawing = drawingImage?.let { src ->
                if (src.isRecycled) null else src.copy(Bitmap.Config.ARGB_8888, false)
            }
            val capturedDrawingScale = drawingScale
            val capturedDrawingOffsetX = drawingOffsetX
            val capturedDrawingOffsetY = drawingOffsetY
            val capturedScale = imageScale
            val capturedOffset = imageOffset
            val capturedRotation = imageRotationRadians
            val capturedCanvasW = mediaCanvasWidthPx
            val capturedCanvasH = mediaCanvasHeightPx
            val capturedPalette = resolvedStoryBackgroundPalette()
            val capturedStickers = stickers.toList()
            val primary = prepared.firstOrNull()
            val drawingBytes = capturedDrawing?.let { bmp ->
                ByteArrayOutputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                }
            }
            val publishChainId: String?
            val publishChainTitle: String?
            val publishChainPosition: Int?
            when {
                isCreatingChain && chainTitle.isNotBlank() -> {
                    publishChainId = UUID.randomUUID().toString()
                    publishChainTitle = chainTitle.trim()
                    publishChainPosition = 1
                }
                isContinuingChain -> {
                    publishChainId = chainId
                    publishChainTitle = originalChainTitle
                    publishChainPosition = (chainPosition ?: 0) + 1
                }
                else -> {
                    publishChainId = null
                    publishChainTitle = null
                    publishChainPosition = null
                }
            }
            val chainActive = isCreatingChain || isContinuingChain
            val publishAudience = if (chainActive) ContentAudience.EVERYONE else audience
            val resolvedExpiration = if (chainActive) 48 else expirationHours
            val capturedCustomUsers = customSelectedUsers.toList()
            val capturedListId = selectedListId
            val capturedListName = selectedListName
            val capturedAllow = allowOthersToContinue
            val capturedContinuation = continuationAudience.raw
            val appCtx = context.applicationContext
            val pendingDir = File(appCtx.filesDir, "pending_uploads").also { it.mkdirs() }
            val cachedStickers = withContext(Dispatchers.Default) {
                capturedStickers.sortedBy { it.zIndex }.map { draft ->
                    val stickerBitmap = when {
                        draft.type == "emoji" && draft.content.isNotBlank() -> renderEmojiStickerBitmap(draft.content)
                        draft.type == "selfie" || draft.type == "frame" || draft.type == "shareMoment" -> draft.image
                        else -> null
                    }
                    val localName = if (stickerBitmap != null) {
                        val name = "sticker_${draft.id}.png"
                        FileOutputStream(File(pendingDir, name)).use { out ->
                            stickerBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        if (stickerBitmap !== draft.image) stickerBitmap.recycle()
                        name
                    } else {
                        null
                    }
                    CachedSticker(
                        id = draft.id,
                        localImageName = localName,
                        position = Point(draft.normalizedX, draft.normalizedY),
                        scale = draft.scale,
                        rotationRadians = draft.rotationRadians,
                        gifURL = draft.gifURL,
                        videoURL = draft.videoURL,
                        isAnimated = draft.isAnimated,
                        type = draft.type,
                        interactionData = CachedStickerInteractionData(
                            username = draft.username,
                            userId = draft.userId,
                            profileImagePath = draft.profileImagePath,
                            hashtag = draft.hashtag,
                            weatherSymbol = draft.weatherSymbol,
                            questionText = draft.questionText,
                            caption = draft.caption ?: draft.content.takeIf { draft.type == "emoji" },
                            pollData = draft.pollOptions,
                            linkURL = draft.linkURL,
                            linkTitle = draft.linkTitle,
                            location = draft.location,
                            latitude = draft.latitude,
                            longitude = draft.longitude,
                            countdownTitle = draft.countdownTitle,
                            countdownTargetAtMs = draft.countdownTargetAtMs,
                            quizQuestion = draft.quizQuestion,
                            quizOptions = draft.quizOptions,
                            quizCorrectIndex = draft.quizCorrectIndex,
                            sliderEmoji = draft.sliderEmoji,
                            sliderPrompt = draft.sliderPrompt,
                            frameStyle = draft.frameStyle,
                            contentScale = draft.contentScale,
                            contentOffsetX = draft.contentOffsetX,
                            contentOffsetY = draft.contentOffsetY,
                            revealType = draft.revealType,
                            revealPattern = draft.revealPattern,
                            revealPrimaryColor = draft.revealPrimaryColor,
                            revealSecondaryColor = draft.revealSecondaryColor,
                            revealEffectColor = draft.revealEffectColor,
                            audioURL = draft.audioURL,
                            audioDuration = draft.audioDuration,
                        ),
                    )
                }
            }

            val actionId = BackgroundStoryUploadService.uploadStoryWithPreparation(
                prepareMedia = {
                    var publishMedia = baseMedia
                        ?: CreatorMedia(
                            uri = Uri.EMPTY,
                            isVideo = false,
                            aspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
                            recommendedAspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
                        )
                    if (publishMedia.isVideo && baseMedia != null) {
                        val (tw, th) = storyRenderTargetSize()
                        val overlay = renderStoryOverlayImage(
                            drawing = capturedDrawing,
                            drawingScale = capturedDrawingScale,
                            drawingOffsetX = capturedDrawingOffsetX,
                            drawingOffsetY = capturedDrawingOffsetY,
                            targetWidth = tw,
                            targetHeight = th,
                            screenWidth = capturedCanvasW,
                            screenHeight = capturedCanvasH,
                        )
                        val shouldBake = shouldBakeCurrentOverlaysIntoVideo(
                            media = publishMedia,
                            drawingImage = capturedDrawing,
                            hasAnyTextOverlays = prepared.isNotEmpty(),
                            imageScale = capturedScale,
                            imageOffsetX = capturedOffset.x,
                            imageOffsetY = capturedOffset.y,
                            imageRotationRadians = capturedRotation,
                        )
                        try {
                            publishMedia = prepareMediaForStoryUpload(
                                context = appCtx,
                                media = publishMedia,
                                shouldBake = shouldBake,
                                overlay = overlay,
                                backgroundPalette = capturedPalette,
                                targetWidth = tw,
                                targetHeight = th,
                                imageScale = capturedScale,
                                imageOffsetX = capturedOffset.x,
                                imageOffsetY = capturedOffset.y,
                                imageRotationRadians = capturedRotation,
                                editorCanvasWidth = capturedCanvasW,
                                editorCanvasHeight = capturedCanvasH,
                            )
                        } finally {
                            overlay?.recycle()
                            capturedDrawing?.recycle()
                            capturedFilterBmp?.recycle()
                            capturedSourceBmp?.recycle()
                        }
                    } else {
                        // ≡ iOS finalRenderedImage = renderStoryWithOverlays()
                        val mediaBmp = when {
                            capturedFilterBmp != null &&
                                capturedFilter != FilterService.FilterType.NORMAL -> capturedFilterBmp
                            capturedSourceBmp != null -> capturedSourceBmp
                            baseMedia != null ->
                                appCtx.contentResolver.openInputStream(baseMedia.uri)
                                    ?.use(BitmapFactory::decodeStream)
                            else -> null
                        }
                        val finalBmp = renderStoryWithOverlays(
                            mediaImage = mediaBmp,
                            backgroundPalette = capturedPalette,
                            drawing = capturedDrawing,
                            drawingScale = capturedDrawingScale,
                            drawingOffsetX = capturedDrawingOffsetX,
                            drawingOffsetY = capturedDrawingOffsetY,
                            imageScale = capturedScale,
                            imageOffsetX = capturedOffset.x,
                            imageOffsetY = capturedOffset.y,
                            imageRotationRadians = capturedRotation,
                            editorCanvasWidth = capturedCanvasW,
                            editorCanvasHeight = capturedCanvasH,
                        )
                        val dir = File(appCtx.cacheDir, "story_rendered").also { it.mkdirs() }
                        val file = File(dir, "story_${UUID.randomUUID()}.jpg")
                        FileOutputStream(file).use { out ->
                            finalBmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                        finalBmp.recycle()
                        if (mediaBmp != null &&
                            mediaBmp !== capturedFilterBmp &&
                            mediaBmp !== capturedSourceBmp
                        ) {
                            mediaBmp.recycle()
                        }
                        capturedDrawing?.recycle()
                        capturedFilterBmp?.recycle()
                        capturedSourceBmp?.recycle()
                        publishMedia = CreatorMedia(
                            uri = Uri.fromFile(file),
                            isVideo = false,
                            aspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
                            recommendedAspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
                            hasEdits = true,
                        )
                    }
                    publishMedia
                },
                storyText = primary?.text,
                textPosition = primary?.normalizedPosition,
                selectedTextStyle = primary?.styleRaw,
                textOverlayMetadata = primary,
                textOverlays = prepared.takeIf { it.isNotEmpty() },
                drawingData = drawingBytes,
                stickers = cachedStickers.takeIf { it.isNotEmpty() },
                audienceSetting = publishAudience.raw,
                customViewers = capturedCustomUsers.takeIf { it.isNotEmpty() && !chainActive },
                customListId = capturedListId.takeUnless { chainActive },
                selectedListName = capturedListName.takeUnless { chainActive },
                expirationHours = resolvedExpiration,
                chainId = publishChainId,
                chainPosition = publishChainPosition,
                chainTitle = publishChainTitle,
                allowOthersToContinue = if (chainActive) capturedAllow else null,
                continuationAudience = if (chainActive) capturedContinuation else null,
                continuationCustomViewers = if (chainActive) capturedCustomUsers.takeIf { it.isNotEmpty() } else null,
                continuationCustomListId = if (chainActive) capturedListId else null,
                continuationCustomListName = if (chainActive) capturedListName else null,
                onPrepareFailed = {
                    // Toast desde main — el editor ya puede estar cerrado
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(
                            appCtx,
                            appCtx.getString(R.string.story_editor_error_publish_start),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
            )

            if (actionId != null) {
                HapticManager.shared.success()
                onSelectedMediaItemsChange(emptyList())
                resetStoryForm()
                onDismiss()
            } else {
                HapticManager.shared.warning()
                isPublishing = false
                capturedDrawing?.recycle()
                capturedFilterBmp?.recycle()
                capturedSourceBmp?.recycle()
            }
        }
    }

    Box(modifier.fillMaxSize().background(canvas)) {
        // ≡ iOS GeometryReader + creatorMomentsCaptureRect (inset 4 / top 8 / radius 12)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val navBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
            // ≡ iOS: solo safe-area bottom (no restar chrome publish/filtros)
            val captureRect = creatorMomentsCaptureRect(
                inSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
                topInsetPx = 0f,
                bottomInsetPx = navBottomPx,
                density = density,
            )
            val corner = storyViewerCanvasCornerRadius
            val boxW = captureRect.width.coerceAtLeast(1f)
            val boxH = captureRect.height.coerceAtLeast(1f)
            mediaCanvasWidthPx = boxW
            mediaCanvasHeightPx = boxH
            mediaCaptureRect = captureRect

            Box(
                Modifier
                    .offset {
                        IntOffset(captureRect.left.roundToInt(), captureRect.top.roundToInt())
                    }
                    .size(
                        width = with(density) { captureRect.width.toDp() },
                        height = with(density) { captureRect.height.toDp() },
                    )
                    .clip(RoundedCornerShape(corner))
                    .background(Color.Black),
            ) {

                when {
                    media != null && !media.isVideo -> {
                        val bmp = sourceBitmap
                        if (bmp != null) {
                            EditableImageView(
                                image = bmp,
                                scale = imageScale,
                                onScaleChange = { imageScale = it },
                                offset = imageOffset,
                                onOffsetChange = { imageOffset = it },
                                rotationRadians = imageRotationRadians,
                                onRotationChange = { imageRotationRadians = it },
                                filteredImage = filteredImage.takeIf {
                                    selectedFilter != FilterService.FilterType.NORMAL
                                },
                                canvasSize = Size(boxW, boxH),
                                paletteIdentity = media.id,
                                paletteOverride = resolvedStoryBackgroundPalette(),
                                isInteractionEnabled = activeEditorMode == ActiveEditorMode.IDLE &&
                                    activeEditingStickerId == null &&
                                    editingPolaroidId == null &&
                                    editingRevealId == null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            AsyncImage(
                                model = media.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    media != null && media.isVideo -> {
                        val paletteBmp = sourceBitmap
                        val mediaSize = paletteBmp?.let {
                            Size(it.width.toFloat().coerceAtLeast(1f), it.height.toFloat().coerceAtLeast(1f))
                        } ?: Size(boxW, boxH)
                        Box(Modifier.fillMaxSize()) {
                            if (paletteBmp != null) {
                                StoryEditableMediaContainer(
                                    mediaSize = mediaSize,
                                    scale = imageScale,
                                    onScaleChange = { imageScale = it },
                                    offset = imageOffset,
                                    onOffsetChange = { imageOffset = it },
                                    rotationRadians = imageRotationRadians,
                                    onRotationChange = { imageRotationRadians = it },
                                    canvasSize = Size(boxW, boxH),
                                    paletteIdentity = "${media.id}-video",
                                    paletteSourceImage = paletteBmp,
                                    paletteOverride = resolvedStoryBackgroundPalette(),
                                    isInteractionEnabled = activeEditorMode == ActiveEditorMode.IDLE &&
                                        activeEditingStickerId == null &&
                                        editingPolaroidId == null &&
                                        editingRevealId == null,
                                    modifier = Modifier.fillMaxSize(),
                                ) { _ ->
                                    StoryVideoPlayerView(
                                        videoUri = media.uri,
                                        videoGravity = StoryVideoGravity.RESIZE_ASPECT_FILL,
                                        isMuted = isVideoPreviewMuted,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            } else {
                                StoryVideoPlayerView(
                                    videoUri = media.uri,
                                    videoGravity = StoryVideoGravity.RESIZE_ASPECT_FILL,
                                    isMuted = isVideoPreviewMuted,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            // Mute va en sideToolbar (≡ iOS editingToolButtons), no sobre el canvas.
                        }
                    }
                    else -> {
                        val palette = resolvedStoryBackgroundPalette()
                        StoryMediaBackgroundView(
                            palette = palette,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // ≡ iOS canvasAutoSplitNotice — sobre el media, bottom del canvas
                if (
                    activeEditorMode == ActiveEditorMode.IDLE &&
                    media != null &&
                    media.isVideo &&
                    media.storyVideoMode == StoryVideoMode.AUTO_SPLIT
                ) {
                    val duration = media.durationSeconds ?: 0.0
                    val partCount = maxOf(
                        2,
                        kotlin.math.ceil(duration / StoryVideoProcessingService.maxStorySegmentDuration).toInt(),
                    )
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                            .momentsChromeGlass(RoundedCornerShape(14.dp), interactive = false)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Layers,
                            contentDescription = null,
                            tint = controlFg.copy(0.86f),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            stringResource(R.string.story_video_editor_auto_split_notice, partCount),
                            color = controlFg.copy(0.86f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Drawing layer under text overlays — ≡ StoryOverlaysView drawing + trash
                if (activeEditorMode != ActiveEditorMode.DRAWING) {
                    drawingImage?.let { bmp ->
                        StoryDrawingCanvasOverlay(
                            bitmap = bmp,
                            offsetX = drawingOffsetX,
                            offsetY = drawingOffsetY,
                            scale = drawingScale,
                            canvasWidthPx = boxW,
                            canvasHeightPx = boxH,
                            hasTextOverlays = textOverlays.any { it.isReady },
                            onOffsetChange = { x, y ->
                                drawingOffsetX = x
                                drawingOffsetY = y
                            },
                            onScaleChange = { drawingScale = it },
                            onClear = {
                                drawingImage?.recycle()
                                drawingImage = null
                                drawingOffsetX = 0f
                                drawingOffsetY = 0f
                                drawingScale = 1f
                                HapticManager.shared.warning()
                            },
                            onDragStateChange = { state ->
                                if (!overlayDragState.isOverTrash && state.isOverTrash) {
                                    HapticManager.shared.mediumImpact()
                                }
                                overlayDragState = state
                            },
                            onBackgroundTap = { selectedStickerId = null },
                        )
                    }
                }

                // Equivalente al fondo de foco de StoryOverlaysView.swift: queda detrás del
                // sticker editado y permite volver a su transformación original al tocar fuera.
                if (activeEditingStickerId != null || editingPolaroidId != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .zIndex(1500f)
                            .background(
                                Color.Black.copy(alpha = if (editingPolaroidId != null) .82f else .65f),
                            )
                            .pointerInput(editingPolaroidId) {
                                if (editingPolaroidId != null) {
                                    detectDragGestures(
                                        onDragStart = { polaroidSwipeOffsetX = 0f },
                                        onDragEnd = {
                                            if (abs(polaroidSwipeOffsetX) >= 36f) {
                                                cyclePolaroidFrameStyle(if (polaroidSwipeOffsetX < 0f) 1 else -1)
                                            }
                                            polaroidSwipeOffsetX = 0f
                                        },
                                        onDrag = { change, drag ->
                                            if (abs(drag.x) > abs(drag.y)) {
                                                change.consume()
                                                polaroidSwipeOffsetX += drag.x
                                            }
                                        },
                                    )
                                }
                            }
                            .clickable {
                                if (editingPolaroidId != null) {
                                    savePolaroidEditing()
                                } else {
                                    activeEditingStickerId = null
                                    restoreFocusedInlineSticker()
                                }
                            },
                    )
                }

                // Idle overlays on canvas
                if (activeEditorMode == ActiveEditorMode.IDLE) {
                    stickers.sortedBy { it.zIndex }.forEach { sticker ->
                        if (sticker.type == "reveal") return@forEach
                        val armed = deleteArmedStickerId == sticker.id
                        val editing = activeEditingStickerId == sticker.id
                        val polaroidEditing = editingPolaroidId == sticker.id
                        val selected = selectedStickerId == sticker.id || armed || editing
                        val effectiveZ = when {
                            editing -> 3000f
                            polaroidEditing -> 2000f
                            selected -> 500f
                            else -> sticker.zIndex.toFloat()
                        }
                        StickerOverlayView(
                            sticker = sticker,
                            canvasWidthPx = boxW.roundToInt(),
                            canvasHeightPx = boxH.roundToInt(),
                            isSelected = selected,
                            isDragging = draggingStickerId == sticker.id,
                            isContentEditing = polaroidEditing,
                            isEditingInline = editing,
                            onUpdate = { updated ->
                                stickers = stickers.map { if (it.id == updated.id) updated else it }
                            },
                            onDelete = {
                                stickers = stickers.filterNot { it.id == sticker.id }
                                deleteArmedStickerId = null
                                activeEditingStickerId = null
                                selectedStickerId = null
                                HapticManager.shared.warning()
                            },
                            onDragChanged = { updated, overTrash ->
                                draggingStickerId = updated.id
                                selectedStickerId = updated.id
                                if (!overlayDragState.isOverTrash && overTrash) {
                                    HapticManager.shared.mediumImpact()
                                }
                                overlayDragState = StoryOverlayDragState(isDragging = true, isOverTrash = overTrash)
                                deleteArmedStickerId = null
                                stickers = stickers.map { if (it.id == updated.id) updated else it }
                            },
                            onDragEnded = { updated, overTrash ->
                                draggingStickerId = null
                                if (overTrash) {
                                    stickers = stickers.filterNot { it.id == updated.id }
                                    activeEditingStickerId = null
                                    selectedStickerId = null
                                    HapticManager.shared.warning()
                                } else {
                                    stickers = stickers.map { if (it.id == updated.id) updated else it }
                                }
                                overlayDragState = StoryOverlayDragState()
                            },
                            onStickerTapped = {
                                when {
                                    armed -> {
                                        stickers = stickers.filterNot { item -> item.id == sticker.id }
                                        deleteArmedStickerId = null
                                        activeEditingStickerId = null
                                        selectedStickerId = null
                                        restoreFocusedInlineSticker()
                                        HapticManager.shared.warning()
                                    }
                                    // ≡ isInlineEditableSticker → focusInlineEditableSticker
                                    stickerSupportsInlineEdit(sticker) -> {
                                        if (editing) {
                                            activeEditingStickerId = null
                                            restoreFocusedInlineSticker()
                                        } else {
                                            focusInlineSticker(sticker)
                                        }
                                    }
                                    // ≡ tapCyclesStickerStyle → cycle on second tap
                                    tapCyclesStickerStyle(sticker.type) -> {
                                        val wasSelected = selectedStickerId == sticker.id
                                        selectedStickerId = sticker.id
                                        if (wasSelected) cycleStickerStyle(sticker.id)
                                    }
                                    // ≡ handleStickerTap (frame / toasts / select)
                                    else -> handleStickerTap(sticker)
                                }
                            },
                            modifier = Modifier.zIndex(effectiveZ),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StoryStickerChip(
                                    sticker = sticker,
                                    isEditingInline = editing,
                                    onUpdate = { updated ->
                                        stickers = stickers.map { if (it.id == updated.id) updated else it }
                                    },
                                    modifier = Modifier,
                                )
                                if (armed || editing) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        null,
                                        tint = Color(0xFFE91E63),
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .size(18.dp)
                                            .clickable {
                                                stickers = stickers.filterNot { it.id == sticker.id }
                                                deleteArmedStickerId = null
                                                activeEditingStickerId = null
                                                HapticManager.shared.warning()
                                            },
                                    )
                                }
                            }
                        }
                    }

                    textOverlays.filter { it.isReady }.sortedBy { it.layerOrder }.forEach { overlay ->
                        StoryTextOverlayItem(
                            overlay = overlay,
                            canvasWidthPx = boxW,
                            canvasHeightPx = boxH,
                            isEditorPresented = activeEditorMode != ActiveEditorMode.IDLE,
                            onUpdate = { updated ->
                                deleteArmedId = null
                                textOverlays = textOverlays.map { item ->
                                    if (item.id == updated.id) updated else item
                                }
                            },
                            onEdit = {
                                deleteArmedId = null
                                beginEditingTextOverlay(overlay.id)
                            },
                            onDelete = {
                                textOverlays = textOverlays.filterNot { it.id == overlay.id }
                                deleteArmedId = null
                                HapticManager.shared.warning()
                            },
                            onDragStateChange = { state ->
                                if (!overlayDragState.isOverTrash && state.isOverTrash) {
                                    HapticManager.shared.mediumImpact()
                                }
                                overlayDragState = state
                            },
                        ) {
                            StoryCanvasTextLabel(
                                overlay = overlay,
                                modifier = Modifier
                                    .background(Color.Black.copy(0.25f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }

                    if (stickers.any { it.type == "reveal" } && editingRevealId == null) {
                        StoryRevealStatusBadge(
                            onCustomize = {
                                editingRevealId = stickers.firstOrNull { it.type == "reveal" }?.id
                                HapticManager.shared.mediumImpact()
                            },
                            onRemove = {
                                stickers = stickers.filterNot { it.type == "reveal" }
                                editingRevealId = null
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 100.dp),
                        )
                    }

                    StoryOverlayTrashZone(overlayDragState)

                    // Pie de foto vivo del frame, sobre el fondo de foco y bajo el sticker hero.
                    if (editingPolaroidId != null) {
                        StoryPolaroidCaptionField(
                            value = polaroidCaptionBuffer,
                            onValueChange = { caption ->
                                polaroidCaptionBuffer = caption
                                val id = editingPolaroidId
                                if (id != null) {
                                    stickers = stickers.map { item ->
                                        if (item.id == id) item.copy(caption = caption) else item
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp),
                        )
                    }
                }

                if (editingRevealId != null) {
                    RevealStickerEditorView(
                        stickers = stickers,
                        editingId = editingRevealId,
                        onEditingIdChange = { editingRevealId = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                StoryOverlayToastHost(
                    toast = storyOverlayToast,
                    onDismiss = { storyOverlayToast = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ≡ iOS topBarView overlay (oculto en reveal; en sticker edit → Done + palette)
            if (
                activeEditorMode != ActiveEditorMode.TEXT &&
                activeEditorMode != ActiveEditorMode.DRAWING &&
                editingRevealId == null
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    if (isEditingStickerChrome) {
                        // ≡ iOS topBarView when activeEditingStickerId != nil
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.size(44.dp))
                            Spacer(Modifier.weight(1f))
                            Text(
                                stringResource(R.string.story_text_editor_done),
                                color = controlFg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                                    .clickable {
                                        activeEditingStickerId = null
                                        editingPolaroidId = null
                                        HapticManager.shared.lightImpact()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        if (showsStickerPaletteButton()) {
                            val paletteOffsetY = if (isChatSendMode) 56.dp else 0.dp
                            StoryEditorPaletteChip(
                                iconTint = controlFg,
                                previewColors = stickerPalettePreviewColors(),
                                onClick = { cycleSelectedStickerColor() },
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(top = paletteOffsetY),
                            )
                        }
                    } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChromeTool(
                            onClick = {
                                when (activeEditorMode) {
                                    ActiveEditorMode.FILTERS -> activeEditorMode = ActiveEditorMode.IDLE
                                    else -> showDiscardChangesAlert = true
                                }
                            },
                            stroke = controlStroke,
                        ) {
                            Icon(
                                if (activeEditorMode == ActiveEditorMode.FILTERS) {
                                    Icons.AutoMirrored.Filled.ArrowBack
                                } else {
                                    Icons.Filled.Close
                                },
                                null,
                                tint = controlFg,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (activeEditorMode == ActiveEditorMode.FILTERS) {
                            // Done handled by back → idle on left button style; keep save hidden like iOS filters
                            Text(
                                stringResource(R.string.common_done),
                                color = controlFg,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                                    .clickable { activeEditorMode = ActiveEditorMode.IDLE }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // ≡ iOS chatTopToolbarView
                                if (isChatSendMode && activeEditorMode == ActiveEditorMode.IDLE && editingRevealId == null) {
                                    SideTool(Icons.Filled.TextFields, controlFg, controlStroke) {
                                        beginCreatingTextOverlay()
                                    }
                                    SideTool(
                                        iconRes = R.drawable.moments_sticker_tool,
                                        tint = controlFg,
                                        stroke = controlStroke,
                                    ) {
                                        showingStickerPicker = true
                                    }
                                    SideTool(Icons.Filled.Brush, controlFg, controlStroke) {
                                        activeEditorMode = ActiveEditorMode.DRAWING
                                    }
                                    SideTool(Icons.Filled.PhotoFilter, controlFg, controlStroke) {
                                        activeEditorMode = ActiveEditorMode.FILTERS
                                        if (media != null && !media.isVideo) {
                                            showingIntensitySlider = selectedFilter != FilterService.FilterType.NORMAL
                                            applySelectedFilter()
                                        }
                                    }
                                    if (media != null && media.isVideo) {
                                        SideTool(
                                            if (isVideoPreviewMuted) Icons.AutoMirrored.Filled.VolumeOff
                                            else Icons.AutoMirrored.Filled.VolumeUp,
                                            controlFg,
                                            controlStroke,
                                        ) {
                                            isVideoPreviewMuted = !isVideoPreviewMuted
                                        }
                                    }
                                }
                                ChromeTool(
                                    onClick = { saveToGallery() },
                                    stroke = controlStroke,
                                ) {
                                    Icon(Icons.Filled.Download, null, tint = controlFg, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    // iOS center swatch: sticker palette > background palette
                    // chatPaletteTopOffset ≡ tool row height + gap when chat mode
                    val paletteOffsetY = if (isChatSendMode) 56.dp else 0.dp
                    when {
                        showsStickerPaletteButton() -> {
                            StoryEditorPaletteChip(
                                iconTint = controlFg,
                                previewColors = stickerPalettePreviewColors(),
                                onClick = { cycleSelectedStickerColor() },
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(top = paletteOffsetY),
                            )
                        }
                        showsBackgroundPaletteButton() -> {
                            StoryEditorPaletteChip(
                                iconTint = controlFg,
                                previewColors = backgroundPalettePreviewColors(),
                                onClick = { cycleBackgroundPreset() },
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(top = paletteOffsetY),
                            )
                        }
                    }
                    } // end else normal top bar
                }
            }

            // ≡ iOS sideToolbarView: debajo del topBar, trailing (fuera del captureRect).
            if (
                !isChatSendMode &&
                activeEditorMode == ActiveEditorMode.IDLE &&
                activeEditingStickerId == null &&
                editingPolaroidId == null &&
                editingRevealId == null
            ) {
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 62.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SideTool(Icons.Filled.TextFields, controlFg, controlStroke) {
                        beginCreatingTextOverlay()
                    }
                    SideTool(
                        iconRes = R.drawable.moments_sticker_tool,
                        tint = controlFg,
                        stroke = controlStroke,
                    ) {
                        showingStickerPicker = true
                    }
                    SideTool(Icons.Filled.Brush, controlFg, controlStroke) {
                        activeEditorMode = ActiveEditorMode.DRAWING
                    }
                    SideTool(Icons.Filled.PhotoFilter, controlFg, controlStroke) {
                        activeEditorMode = ActiveEditorMode.FILTERS
                        if (media != null && !media.isVideo) {
                            showingIntensitySlider = selectedFilter != FilterService.FilterType.NORMAL
                            applySelectedFilter()
                        }
                    }
                    if (media != null && media.isVideo) {
                        SideTool(
                            if (isVideoPreviewMuted) Icons.AutoMirrored.Filled.VolumeOff
                            else Icons.AutoMirrored.Filled.VolumeUp,
                            controlFg,
                            controlStroke,
                        ) {
                            isVideoPreviewMuted = !isVideoPreviewMuted
                        }
                    }
                    if (!isContinuingChain) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .momentsChromeGlass(CircleShape, interactive = true)
                                .border(
                                    1.dp,
                                    if (isCreatingChain) Color(0xFF007AFF) else controlStroke,
                                    CircleShape,
                                )
                                .clickable {
                                    isCreatingChain = !isCreatingChain
                                    HapticManager.shared.selection()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Link,
                                null,
                                tint = if (isCreatingChain) Color(0xFF007AFF) else controlFg,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (showsStoryExpirationSelector) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .momentsChromeGlass(CircleShape, interactive = true)
                                .border(1.dp, controlStroke, CircleShape)
                                .combinedClickable(
                                    onClick = { expirationHours = if (expirationHours == 24) 48 else 24 },
                                    onLongClick = {
                                        showingExpirationInfoOverlay = true
                                        HapticManager.shared.lightImpact()
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.story_editor_expiration_hours, expirationHours),
                                color = controlFg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            // ≡ iOS bottomPublishingInset + filter chrome bajo el canvas
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            if (activeEditorMode == ActiveEditorMode.FILTERS && media != null && !media.isVideo) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (selectedFilter != FilterService.FilterType.NORMAL && showingIntensitySlider) {
                        Text(
                            "${(filterIntensity * 100).toInt()}%",
                            color = controlFg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        Slider(
                            value = filterIntensity.toFloat(),
                            onValueChange = {
                                filterIntensity = it.toDouble()
                                applySelectedFilter()
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = controlFg,
                                activeTrackColor = controlFg,
                                inactiveTrackColor = controlFg.copy(0.25f),
                            ),
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                    StoryFilterSelectorView(
                        selectedFilter = selectedFilter,
                        onFilterChange = { filter ->
                            selectedFilter = filter
                            showingIntensitySlider = filter != FilterService.FilterType.NORMAL
                            if (filter == FilterService.FilterType.NORMAL) {
                                filterIntensity = 1.0
                            }
                            applySelectedFilter()
                        },
                        baseUri = media.uri,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (editingRevealId != null) {
                RevealStickerBottomControlsInset(
                    stickers = stickers,
                    onStickersChange = { stickers = it },
                    editingId = editingRevealId,
                    onEditingIdChange = { editingRevealId = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                )
            } else if (isChatSendMode && activeEditorMode == ActiveEditorMode.IDLE && activeEditingStickerId == null && editingPolaroidId == null) {
                // ≡ iOS chatSendBottomBar
                val isEditingSticker = activeEditingStickerId != null
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                            .clickable(enabled = !isPublishing) {
                                chatSendMode = chatSendMode.next()
                                HapticManager.shared.selection()
                            }
                            .padding(start = 10.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .border(
                                    width = 1.6.dp,
                                    color = controlFg,
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (chatSendMode.innerIcon) {
                                ChatMediaSendModeIcon.PLAY ->
                                    Icon(Icons.Filled.PlayArrow, null, tint = controlFg, modifier = Modifier.size(14.dp))
                                ChatMediaSendModeIcon.SAVE ->
                                    Icon(Icons.Filled.Download, null, tint = controlFg, modifier = Modifier.size(14.dp))
                                null ->
                                    Text("1", color = controlFg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        Text(
                            stringResource(chatSendMode.labelRes),
                            color = controlFg,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(shareBg.copy(if (!isPublishing && !isEditingSticker) 1f else 0.55f))
                            .clickable(enabled = !isPublishing && !isEditingSticker) { chatSend() }
                            .padding(start = 9.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (chatRecipientUserId != null) {
                            AsyncProfileImageView(
                                userId = chatRecipientUserId,
                                modifier = Modifier.size(30.dp).clip(CircleShape),
                            )
                        }
                        if (isPublishing) {
                            CircularProgressIndicator(color = shareFg, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Text(
                                stringResource(R.string.camera_preview_send),
                                color = shareFg,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                null,
                                tint = shareFg,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            } else if (activeEditorMode == ActiveEditorMode.IDLE && activeEditingStickerId == null && editingPolaroidId == null) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        // iOS: chain title input while creating
                        isCreatingChain -> {
                            val keyboardController = LocalSoftwareKeyboardController.current
                            var chainTitleFocused by remember { mutableStateOf(false) }
                            Row(
                                Modifier
                                    .weight(1f)
                                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Filled.Link, null, tint = controlFg.copy(0.72f), modifier = Modifier.size(15.dp))
                                BasicTextField(
                                    value = chainTitle,
                                    onValueChange = { chainTitle = it.take(50) },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = controlFg,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(controlFg),
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusChanged { chainTitleFocused = it.isFocused },
                                    decorationBox = { inner ->
                                        if (chainTitle.isBlank()) {
                                            Text(
                                                stringResource(R.string.story_chains_title_placeholder),
                                                color = controlFg.copy(0.45f),
                                                fontSize = 15.sp,
                                            )
                                        }
                                        inner()
                                    },
                                )
                                if (chainTitleFocused) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = controlFg.copy(0.72f),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable {
                                                keyboardController?.hide()
                                                chainTitleFocused = false
                                            },
                                    )
                                }
                            }
                        }
                        // iOS: continuing capsule
                        isContinuingChain -> {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Filled.Link, null, tint = controlFg, modifier = Modifier.size(15.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.story_chains_continuing, originalChainTitle),
                                        color = controlFg,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                    )
                                    Text(
                                        stringResource(R.string.story_chains_part_short, (chainPosition ?: 0) + 1),
                                        color = controlFg.copy(0.72f),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        else -> {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                                    .clickable { showingAudience = true }
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AudienceIconView(audience, AudienceIconMetrics.storyCapsule, tintColor = controlFg.copy(0.72f))
                                Text(
                                    when {
                                        audience == ContentAudience.CUSTOM_LIST && !selectedListName.isNullOrBlank() ->
                                            selectedListName!!
                                        else -> audienceLabel(audience)
                                    },
                                    color = controlFg,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    // ≡ principalActionButton
                    when {
                        isContinuingChain -> {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(shareBg.copy(if (hasContent && !isPublishing && !isLoadingUserSettings) 1f else 0.55f))
                                    .clickable(enabled = hasContent && !isPublishing && !isLoadingUserSettings) { publishStory() }
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isPublishing) {
                                    CircularProgressIndicator(color = shareFg, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = shareFg, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        isCreatingChain -> {
                            Box(
                                Modifier
                                    .size(width = 54.dp, height = 48.dp)
                                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                                    .clickable(enabled = hasContent && !isPublishing && !isLoadingUserSettings) {
                                        showingChainConfiguration = true
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Settings, null, tint = controlFg, modifier = Modifier.size(18.dp))
                            }
                        }
                        else -> {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(shareBg.copy(if (hasContent && !isPublishing && !isLoadingUserSettings) 1f else 0.55f))
                                    .clickable(enabled = hasContent && !isPublishing && !isLoadingUserSettings) { publishStory() }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (isPublishing) {
                                    CircularProgressIndicator(
                                        color = shareFg,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp),
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.story_editor_share),
                                        color = shareFg,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        null,
                                        tint = shareFg,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            } // bottom chrome Column
        } // BoxWithConstraints capture geometry

        // ≡ StoryTextEditor / StoryDrawingEditorOverlay a pantalla completa (fuera del
        // captureRect clip), mismo contenedor que el ZStack iOS.
        if (activeEditorMode == ActiveEditorMode.DRAWING) {
            StoryDrawingEditorOverlay(
                baseDrawing = drawingImage,
                canvasRect = mediaCaptureRect.takeIf { it.width > 1f && it.height > 1f },
                onCancel = { activeEditorMode = ActiveEditorMode.IDLE },
                onDone = { result ->
                    val previous = drawingImage
                    drawingImage = result
                    drawingOffsetX = 0f
                    drawingOffsetY = 0f
                    drawingScale = 1f
                    if (previous != null && previous !== result && !previous.isRecycled) {
                        previous.recycle()
                    }
                    activeEditorMode = ActiveEditorMode.IDLE
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(45f),
            )
        }

        if (activeEditorMode == ActiveEditorMode.TEXT) {
            StoryTextEditor(
                text = editorBuffer,
                onTextChange = { editorBuffer = it },
                selectedStyle = editorStyle,
                onStyleChange = { editorStyle = it },
                colorHex = editorColorHex,
                onColorHexChange = { editorColorHex = it },
                textAlignmentRaw = editorTextAlignmentRaw,
                onTextAlignmentRawChange = { editorTextAlignmentRaw = it },
                textBackgroundFillRaw = editorTextBackgroundFillRaw,
                onTextBackgroundFillRawChange = { editorTextBackgroundFillRaw = it },
                textFontSize = editorTextFontSize,
                onTextFontSizeChange = { editorTextFontSize = it },
                textStrokeRaw = editorTextStrokeRaw,
                onTextStrokeRawChange = { editorTextStrokeRaw = it },
                textMotionRaw = editorTextMotionRaw,
                onTextMotionRawChange = { editorTextMotionRaw = it },
                visualEffectRaw = editorVisualEffectRaw,
                onVisualEffectRawChange = { editorVisualEffectRaw = it },
                gradientStops = editorGradientStops,
                onGradientStopsChange = { editorGradientStops = it },
                gradientAngle = editorGradientAngle,
                onGradientAngleChange = { editorGradientAngle = it },
                selectedGradientStopIndex = editorSelectedGradientStopIndex,
                onSelectedGradientStopIndexChange = { editorSelectedGradientStopIndex = it },
                forcesAllCaps = editorForcesAllCaps,
                onForcesAllCapsChange = { editorForcesAllCaps = it },
                mediaSampleImage = filteredImage,
                onDone = { finishTextEditing() },
                onCancel = {
                    val id = activeTextOverlayId
                    activeTextOverlayId = null
                    editorBuffer = ""
                    editorStyle = StoryTextStyle.MODERN
                    editorColorHex = StoryTextStyle.MODERN.defaultColorHex
                    editorTextAlignmentRaw = "center"
                    editorTextBackgroundFillRaw = "none"
                    editorTextFontSize = 30f
                    editorTextStrokeRaw = "none"
                    editorTextMotionRaw = "none"
                    editorVisualEffectRaw = "none"
                    editorGradientStops = emptyList()
                    editorGradientAngle = 0
                    editorSelectedGradientStopIndex = 0
                    editorForcesAllCaps = false
                    if (id != null) {
                        textOverlays = textOverlays.filterNot { it.id == id && it.text.isBlank() }
                    }
                    activeEditorMode = ActiveEditorMode.IDLE
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(5000f),
            )
        }


        val editingSlider = stickers.firstOrNull {
            it.id == activeEditingStickerId && it.type == "emojiSlider"
        }
        if (editingSlider != null) {
            EmojiSliderPresetBar(
                selectedEmoji = editingSlider.sliderEmoji ?: "😍",
                onSelect = { emoji -> updateActiveSliderEmoji(emoji) },
                onMore = {
                    showingEmojiPicker = true
                    HapticManager.shared.lightImpact()
                },
                emojiUsageTracker = emojiUsageTracker,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 88.dp),
            )
        }

        if (showingStickerPicker) {
            StickerPickerView(
                onStickerCreated = { draft ->
                    val placed = draft.copy(zIndex = nextStickerZ++)
                    stickers = stickers + placed
                    showingStickerPicker = false
                    activeEditingStickerId =
                        if (stickerSupportsInlineEdit(placed)) placed.id else null
                    deleteArmedStickerId = null
                },
                onSelfieRequested = ::requestSelfieSticker,
                hasRevealSticker = stickers.any { it.type == "reveal" },
                isVideo = media?.isVideo == true,
                hasAudioSticker = stickers.any { it.type == "audio" },
                onDismiss = { showingStickerPicker = false },
            )
        }

        // ≡ iOS `.sheet` LocationMapView
        if (showingLocationMap) {
            Dialog(
                onDismissRequest = { showingLocationMap = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                LocationMapView(
                    locationName = selectedLocationName,
                    latitude = selectedLocationLat,
                    longitude = selectedLocationLng,
                    onDismiss = { showingLocationMap = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ≡ iOS showingEmojiPicker → EmojiPickerView
        if (showingEmojiPicker) {
            MomentsModalSheet(
                onDismissRequest = { showingEmojiPicker = false },
                largeOnly = false,
                containerColor = rememberAdaptiveColors().surfaceBackground,
            ) {
                EmojiPickerView(
                    onDismiss = { showingEmojiPicker = false },
                    onSelect = { emoji ->
                        updateActiveSliderEmoji(emoji)
                        showingEmojiPicker = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ≡ iOS storyExpirationInfoOverlay
        if (showingExpirationInfoOverlay) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable { showingExpirationInfoOverlay = false },
            ) {
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 84.dp, end = 68.dp)
                        .widthIn(max = 260.dp)
                        .momentsChromeGlass(RoundedCornerShape(22.dp), interactive = false)
                        .clickable { /* consume */ }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        stringResource(R.string.story_editor_expiration_info_title),
                        color = controlFg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.story_editor_expiration_info_message),
                        color = controlFg.copy(0.78f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // ≡ iOS overlay storyEditor.sharing (chat bake / publish brief)
        if (isPublishing && isChatSendMode) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.story_editor_sharing),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        PermissionPrimerGateHost(gate = photosSaveGate)

        // ≡ iOS `.sheet` AudienceSelectionView (detents medium/large)
        if (showingAudience) {
            MomentsModalSheet(
                onDismissRequest = { showingAudience = false },
                largeOnly = false,
                containerColor = rememberAdaptiveColors().surfaceBackground,
            ) {
                AudienceSelectionView(
                    selectedAudience = audience,
                    selectedListId = selectedListId,
                    selectedListName = selectedListName,
                    customSelectedUsers = customSelectedUsers,
                    onSelectedAudienceChange = { audience = it },
                    onSelectedListIdChange = { selectedListId = it },
                    onSelectedListNameChange = { selectedListName = it },
                    onCustomSelectedUsersChange = { customSelectedUsers = it },
                    onDismiss = { showingAudience = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }

        // ≡ iOS `.sheet` ChainConfigurationView
        if (showingChainConfiguration) {
            MomentsModalSheet(
                onDismissRequest = { showingChainConfiguration = false },
                largeOnly = false,
                containerColor = rememberAdaptiveColors().surfaceBackground,
            ) {
                ChainConfigurationView(
                    allowOthersToContinue = allowOthersToContinue,
                    onAllowOthersToContinueChange = { allowOthersToContinue = it },
                    continuationAudience = continuationAudience,
                    onContinuationAudienceChange = { continuationAudience = it },
                    selectedListId = selectedListId,
                    onSelectedListIdChange = { selectedListId = it },
                    selectedListName = selectedListName,
                    onSelectedListNameChange = { selectedListName = it },
                    customSelectedUsers = customSelectedUsers,
                    onCustomSelectedUsersChange = { customSelectedUsers = it },
                    chainTitleSummary = if (isContinuingChain) originalChainTitle else chainTitle,
                    isContinuing = isContinuingChain,
                    onConfirm = { publishStory() },
                    onDismiss = { showingChainConfiguration = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StoryStickerChip(
    sticker: StoryStickerDraft,
    modifier: Modifier = Modifier,
    isEditingInline: Boolean = false,
    onUpdate: (StoryStickerDraft) -> Unit = {},
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isEditingInline, sticker.id) {
        if (isEditingInline) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    fun openCountdownDatePicker() {
        val current = sticker.countdownTargetAtMs?.toLong()
            ?: (System.currentTimeMillis() + 86_400_000L)
        val cal = Calendar.getInstance().apply { timeInMillis = current }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, minute)
                        cal.set(Calendar.SECOND, 0)
                        val minMs = System.currentTimeMillis() + 60_000L
                        val ms = maxOf(cal.timeInMillis, minMs).toDouble()
                        onUpdate(sticker.copy(countdownTargetAtMs = ms))
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).apply { datePicker.minDate = System.currentTimeMillis() }.show()
    }

    if (sticker.isAnimated && !sticker.gifURL.isNullOrBlank()) {
        AnimatedStickerView(
            sticker = sticker,
            size = androidx.compose.ui.unit.DpSize(128.dp, 128.dp),
            modifier = modifier
                .size(128.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
        return
    }

    if (sticker.type == "frame") {
        StickerPolaroidFrameView(
            image = sticker.image,
            caption = sticker.caption,
            frameStyle = StoryPolaroidFrameStyle.fromRawOrDefault(sticker.frameStyle),
            contentScale = sticker.contentScale?.toFloat() ?: 1f,
            contentOffsetX = sticker.contentOffsetX?.toFloat() ?: 0f,
            contentOffsetY = sticker.contentOffsetY?.toFloat() ?: 0f,
            modifier = modifier,
        )
        return
    }

    if (sticker.type == "reveal") {
        Box(modifier.size(width = 240.dp, height = 150.dp).background(Color.Black.copy(0.82f), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        return
    }

    if (sticker.type == "audio") {
        val url = sticker.audioURL
        if (!url.isNullOrBlank()) {
            InteractiveAudioStickerView(
                audioURL = url,
                duration = sticker.audioDuration ?: 0.0,
                modifier = modifier,
            )
        } else {
            Box(modifier.size(72.dp).background(Color.White.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Mic, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        return
    }

    if (sticker.type == "selfie") {
        if (sticker.caption == "selfie_live") {
            SelfieStickerLiveCameraView(
                onPhotoCaptured = { captured -> onUpdate(sticker.copy(image = captured, caption = null)) },
                modifier = modifier.size(80.dp),
            )
        } else if (sticker.image != null) {
            Image(
                bitmap = sticker.image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier.size(80.dp).clip(CircleShape),
            )
        }
        return
    }

    if (sticker.type == "shareMoment" && sticker.image != null) {
        Image(
            bitmap = sticker.image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .width(160.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        return
    }

    when (sticker.type) {
        "emoji" -> {
            // La escala se aplica una sola vez por StickerOverlayView, igual que `scaleEffect`
            // en el contenedor Swift; multiplicarla aquí duplicaba el pellizco del emoji.
            val fontSp = 42f
            Text(sticker.content, fontSize = fontSp.sp, modifier = modifier)
        }
        "weather" -> {
            Text(
                sticker.weatherSymbol ?: sticker.content,
                fontSize = 28.sp,
                modifier = modifier
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color(0xFF2196F3), Color(0xFF00BCD4)),
                        ),
                        shape = RoundedCornerShape(25.dp),
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
        "time" -> {
            val timeText = sticker.questionText
                ?: sticker.content.substringBefore("·").trim().ifBlank { sticker.content }
            val dateText = sticker.caption
                ?: sticker.content.substringAfter("·", "").trim()
            StickerTimeCardView(
                timeText = timeText,
                dateText = dateText,
                styleVariant = sticker.styleVariant ?: 0,
                modifier = modifier,
            )
        }
        "hashtag" -> {
            StickerHashtagCardView(
                hashtag = sticker.hashtag?.takeIf { it.isNotBlank() }
                    ?: sticker.content.removePrefix("#"),
                onHashtagChange = if (isEditingInline) {
                    { raw ->
                        val cleaned = raw.removePrefix("#").filterNot { it.isWhitespace() }.take(24)
                        onUpdate(
                            sticker.copy(
                                hashtag = cleaned,
                                content = if (cleaned.isBlank()) "#" else "#$cleaned",
                            ),
                        )
                    }
                } else {
                    null
                },
                styleVariant = sticker.styleVariant ?: 0,
                isEditingInline = isEditingInline,
                modifier = modifier,
            )
        }
        "mention" -> {
            val username = sticker.username?.takeIf { it.isNotBlank() }
                ?: sticker.content.removePrefix("@").trim()
            StickerMentionCardView(
                username = username.ifBlank { "user" },
                styleVariant = sticker.styleVariant ?: 0,
                modifier = modifier,
            )
        }
        "questionResponse" -> {
            QuestionResponseStoryStickerCardView(
                questionText = sticker.questionText?.takeIf { it.isNotBlank() } ?: sticker.content,
                styleVariant = sticker.styleVariant ?: 0,
                modifier = modifier,
            )
        }
        "poll" -> {
            val poll = (sticker.pollOptions ?: listOf("", "", "")).let {
                when {
                    it.size >= 3 -> it.take(3)
                    else -> it + List(3 - it.size) { "" }
                }
            }
            Column(
                modifier
                    .background(Color.White.copy(0.94f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .widthIn(min = 200.dp, max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isEditingInline) {
                    InlineStickerField(
                        value = poll[0],
                        placeholder = stringResource(R.string.sticker_poll_question),
                        onValueChange = {
                            val next = poll.toMutableList().also { list -> list[0] = it.take(44) }
                            onUpdate(
                                sticker.copy(
                                    pollOptions = next,
                                    questionText = next[0],
                                    content = next[0].ifBlank { "Poll" },
                                ),
                            )
                        },
                        focusRequester = focusRequester,
                        bold = true,
                    )
                    listOf(1, 2).forEach { idx ->
                        InlineStickerField(
                            value = poll[idx],
                            placeholder = stringResource(
                                if (idx == 1) R.string.sticker_poll_option_a else R.string.sticker_poll_option_b,
                            ),
                            onValueChange = {
                                val next = poll.toMutableList().also { list -> list[idx] = it.take(28) }
                                onUpdate(sticker.copy(pollOptions = next))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(0.045f), RoundedCornerShape(17.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    Text(
                        poll[0].ifBlank { "…" },
                        color = Color.Black.copy(0.92f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 2,
                    )
                    listOf(1, 2).forEach { idx ->
                        Text(
                            poll[idx].ifBlank { "…" },
                            color = Color.Black.copy(0.88f),
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(0.045f), RoundedCornerShape(17.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        "question" -> {
            Column(
                modifier
                    .background(Color.White.copy(0.94f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .widthIn(min = 200.dp, max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isEditingInline) {
                    InlineStickerField(
                        value = sticker.questionText.orEmpty(),
                        placeholder = stringResource(R.string.sticker_question_placeholder),
                        onValueChange = {
                            val q = it.take(48)
                            onUpdate(sticker.copy(questionText = q, content = q.ifBlank { "?" }))
                        },
                        focusRequester = focusRequester,
                        bold = true,
                    )
                } else {
                    Text(
                        sticker.questionText?.ifBlank { "…" } ?: sticker.content.ifBlank { "…" },
                        color = Color.Black.copy(0.92f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 2,
                    )
                }
                Text(
                    stringResource(R.string.sticker_question_tap),
                    color = Color(0xFF3D75E0),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(0.05f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        "link" -> {
            val title = sticker.linkTitle?.takeIf { it.isNotBlank() }
                ?: sticker.linkURL?.let { stickerHostLabel(it) }
                ?: sticker.content.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.sticker_link_fallback)
            StickerLinkCardView(
                title = title,
                styleVariant = sticker.styleVariant ?: 0,
                modifier = modifier,
            )
        }
        "location" -> {
            val label = (sticker.location ?: sticker.content).ifBlank { "Location" }
            StickerLocationCardView(
                locationName = label,
                styleVariant = sticker.styleVariant ?: 0,
                modifier = modifier,
            )
        }
        "countdown" -> {
            val title = (sticker.countdownTitle ?: sticker.content).take(26)
            val remaining = sticker.countdownTargetAtMs?.let { formatCountdownRemaining(it) }
                ?: stringResource(R.string.sticker_countdown_finished)
            val parts = remaining.split(":")
            Column(
                modifier
                    .background(Color.White.copy(0.94f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .widthIn(min = 180.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isEditingInline) {
                    InlineStickerField(
                        value = title,
                        placeholder = stringResource(R.string.sticker_countdown_title_placeholder),
                        onValueChange = {
                            val t = it.take(26)
                            onUpdate(sticker.copy(countdownTitle = t, content = t))
                        },
                        focusRequester = focusRequester,
                        bold = true,
                        center = true,
                    )
                } else {
                    Text(
                        title.ifBlank { stringResource(R.string.sticker_countdown_placeholder) },
                        color = Color.Black.copy(0.92f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (isEditingInline) {
                        Modifier.clickable { openCountdownDatePicker() }
                    } else {
                        Modifier
                    },
                ) {
                    parts.forEachIndexed { index, chunk ->
                        chunk.forEach { ch ->
                            Box(
                                Modifier
                                    .size(width = 26.dp, height = 32.dp)
                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    ch.toString(),
                                    color = Color.Black.copy(0.92f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                )
                            }
                        }
                        if (index < parts.lastIndex) {
                            Text(
                                ":",
                                color = Color(0xFF6E2970),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            )
                        }
                    }
                }
            }
        }
        "quiz" -> {
            val letters = listOf("A", "B", "C", "D")
            val options = (sticker.quizOptions ?: listOf("", "", "")).let {
                when {
                    it.isEmpty() -> listOf("", "", "")
                    it.size > 4 -> it.take(4)
                    else -> it
                }
            }
            val correct = sticker.quizCorrectIndex ?: 0
            Column(
                modifier
                    .background(Color.White.copy(0.96f), RoundedCornerShape(24.dp))
                    .widthIn(min = 240.dp, max = 300.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFF8A00), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isEditingInline) {
                        InlineStickerField(
                            value = sticker.quizQuestion.orEmpty(),
                            placeholder = stringResource(R.string.sticker_quiz_question_prompt),
                            onValueChange = {
                                val q = it.take(80)
                                onUpdate(sticker.copy(quizQuestion = q, content = q))
                            },
                            focusRequester = focusRequester,
                            bold = true,
                            center = true,
                            textColor = Color.White,
                            placeholderColor = Color.White.copy(0.55f),
                            cursorColor = Color.White,
                        )
                    } else {
                        Text(
                            sticker.quizQuestion?.ifBlank { null }
                                ?: stringResource(R.string.sticker_quiz_question_placeholder),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                        )
                    }
                }
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    options.forEachIndexed { index, option ->
                        val isCorrect = correct == index
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCorrect) Color(0xFF2E7D32).copy(0.12f) else Color.Black.copy(0.045f),
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .background(
                                        if (isCorrect) Color(0xFF2E7D32) else Color.Black.copy(0.12f),
                                        CircleShape,
                                    )
                                    .then(
                                        if (isEditingInline) {
                                            Modifier.clickable {
                                                HapticManager.shared.heavyImpact()
                                                onUpdate(sticker.copy(quizCorrectIndex = index))
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    letters.getOrElse(index) { "${index + 1}" },
                                    color = if (isCorrect) Color.White else Color.Black.copy(0.75f),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                )
                            }
                            if (isEditingInline) {
                                InlineStickerField(
                                    value = option,
                                    placeholder = "${stringResource(R.string.sticker_quiz_option_prompt)} ${index + 1}…",
                                    onValueChange = {
                                        val next = options.toMutableList().also { list -> list[index] = it.take(40) }
                                        onUpdate(sticker.copy(quizOptions = next))
                                    },
                                    modifier = Modifier.weight(1f),
                                    bold = true,
                                )
                            } else {
                                Text(
                                    option.ifBlank { "…" },
                                    color = Color.Black.copy(0.88f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    if (isEditingInline && options.size < 4) {
                        Text(
                            stringResource(R.string.sticker_quiz_add_option),
                            color = Color.Black.copy(0.7f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(0.06f), RoundedCornerShape(12.dp))
                                .clickable {
                                    HapticManager.shared.selection()
                                    onUpdate(sticker.copy(quizOptions = options + ""))
                                }
                                .padding(vertical = 10.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        "emojiSlider" -> {
            val emoji = sticker.sliderEmoji?.ifBlank { null } ?: "😍"
            val prompt = sticker.sliderPrompt.orEmpty()
            val value = 0.5f
            Column(
                modifier
                    .background(Color.White.copy(0.96f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .widthIn(min = 220.dp, max = 280.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isEditingInline || prompt.isNotBlank()) {
                    if (isEditingInline) {
                        InlineStickerField(
                            value = prompt,
                            placeholder = stringResource(R.string.sticker_emoji_slider_prompt),
                            onValueChange = {
                                onUpdate(sticker.copy(sliderPrompt = it.take(48), content = emoji))
                            },
                            focusRequester = focusRequester,
                            bold = true,
                            center = true,
                        )
                    } else {
                        Text(
                            prompt,
                            color = Color.Black.copy(0.92f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(0.12f))
                            .align(Alignment.Center),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(value)
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(0.22f))
                            .align(Alignment.CenterStart),
                    )
                    Text(
                        emoji,
                        fontSize = (28f + value * 14f).sp,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = ((220f * value) - 18f).coerceAtLeast(0f).dp),
                    )
                }
            }
        }
        else -> {
            Text(
                sticker.content,
                color = Color.White,
                fontSize = 15.sp,
                modifier = modifier
                    .background(Color.Black.copy(0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun InlineStickerField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    bold: Boolean = false,
    center: Boolean = false,
    textColor: Color = Color.Black.copy(0.92f),
    placeholderColor: Color = Color.Black.copy(0.35f),
    cursorColor: Color = Color.Black,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = textColor,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 15.sp,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        ),
        cursorBrush = SolidColor(cursorColor),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        decorationBox = { inner ->
            Box {
                if (value.isBlank()) {
                    Text(
                        placeholder,
                        color = placeholderColor,
                        fontSize = 15.sp,
                        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = if (center) Modifier.align(Alignment.Center) else Modifier,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun StoryCanvasTextLabel(
    overlay: StoryTextOverlayDraft,
    modifier: Modifier = Modifier,
) {
    // ≡ StoryTextOverlayLabel — treatments + motion (no Text plano).
    StoryTextOverlayLabel(
        overlay = overlay,
        maxWidth = 280.dp,
        modifier = modifier,
    )
}

@Composable
private fun EmojiSliderPresetBar(
    selectedEmoji: String,
    onSelect: (String) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    emojiUsageTracker: com.moments.android.utilities.EmojiUsageTracker,
) {
    // ≡ iOS resolvedEmojiSliderEmojis + emojiSliderPresetBar
    val presets = emojiUsageTracker.orderedEmojis(
        com.moments.android.utilities.EmojiReactionDefaults.emojiSlider,
        limit = 8,
    )
    val controlFg = if (isSystemInDarkTheme()) Color.White else Color.Black.copy(0.82f)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .momentsChromeGlass(RoundedCornerShape(22.dp), interactive = false)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { emoji ->
            val selected = emoji == selectedEmoji
            Text(
                emoji,
                fontSize = 28.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color.White.copy(0.18f) else Color.Transparent)
                    .clickable { onSelect(emoji) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Box(
            Modifier
                .padding(start = 4.dp)
                .size(52.dp)
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.EmojiEmotions, null, tint = controlFg, modifier = Modifier.size(24.dp))
        }
    }
}


/** ≡ iOS `StickerData.from` para chat — posiciones ya normalizadas en Android. */
private fun StoryStickerDraft.toChatStickerData(zIndex: Int): StickerData = StickerData(
    stickerId = id,
    type = type,
    content = content,
    position = Point(normalizedX, normalizedY),
    scale = scale,
    rotation = rotationRadians,
    zIndex = zIndex,
    username = username,
    userId = userId,
    hashtag = hashtag,
    location = location,
    latitude = latitude,
    longitude = longitude,
    styleVariant = styleVariant,
    questionText = questionText,
    pollOptions = pollOptions,
    weatherSymbol = weatherSymbol,
    linkURL = linkURL,
    linkTitle = linkTitle,
    countdownTitle = countdownTitle,
    countdownTargetAtMs = countdownTargetAtMs,
    sliderEmoji = sliderEmoji,
    sliderPrompt = sliderPrompt,
    caption = caption,
    profileImagePath = profileImagePath,
    quizQuestion = quizQuestion,
    quizOptions = quizOptions,
    quizCorrectIndex = quizCorrectIndex,
    revealType = revealType,
    revealPattern = revealPattern,
    revealPrimaryColor = revealPrimaryColor,
    revealSecondaryColor = revealSecondaryColor,
    revealEffectColor = revealEffectColor,
    frameStyle = frameStyle,
    contentScale = contentScale,
    contentOffsetX = contentOffsetX,
    contentOffsetY = contentOffsetY,
    audioURL = audioURL,
    audioDuration = audioDuration,
    isAnimated = isAnimated,
    gifURL = gifURL,
    videoURL = videoURL,
)

/** ≡ iOS `renderStoryWithOverlays` JPEG path for chat image send. */
private fun renderChatImageJpeg(
    context: android.content.Context,
    media: CreatorMedia?,
    filteredImage: Bitmap?,
    drawingImage: Bitmap?,
    drawingScale: Float,
    drawingOffsetX: Float,
    drawingOffsetY: Float,
    backgroundPalette: List<androidx.compose.ui.graphics.Color>,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    imageRotationRadians: Float,
    editorCanvasWidth: Float,
    editorCanvasHeight: Float,
): ByteArray? {
    val mediaBmp = when {
        media == null -> null
        filteredImage != null && !filteredImage.isRecycled -> filteredImage
        else -> context.contentResolver.openInputStream(media.uri)?.use(BitmapFactory::decodeStream)
            ?: return null
    }
    val composed = renderStoryWithOverlays(
        mediaImage = mediaBmp,
        backgroundPalette = backgroundPalette,
        drawing = drawingImage,
        drawingScale = drawingScale,
        drawingOffsetX = drawingOffsetX,
        drawingOffsetY = drawingOffsetY,
        imageScale = imageScale,
        imageOffsetX = imageOffsetX,
        imageOffsetY = imageOffsetY,
        imageRotationRadians = imageRotationRadians,
        editorCanvasWidth = editorCanvasWidth,
        editorCanvasHeight = editorCanvasHeight,
    )
    val bytes = ByteArrayOutputStream().use { out ->
        composed.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
    }
    composed.recycle()
    if (mediaBmp != null && mediaBmp !== filteredImage) mediaBmp.recycle()
    return bytes
}

/** Decodifica JPEG/PNG Base64 de [StickerData.content] para tipos imagen (shareMoment, etc.). */
private fun decodeStickerContentBitmap(type: String, content: String): Bitmap? {
    if (content.isBlank() || content.startsWith("http") || content.startsWith("sticker_")) return null
    val imageTypes = setOf(
        "shareMoment", "generic", "sticker", "selfie", "frame", "questionResponse",
    )
    if (type !in imageTypes) return null
    return runCatching {
        val bytes = android.util.Base64.decode(content, android.util.Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

private fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "Moment_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Moments")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        } ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        true
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}

private fun saveUriToGallery(context: android.content.Context, source: Uri, isVideo: Boolean): Boolean {
    val resolver = context.contentResolver
    val mime = if (isVideo) "video/mp4" else "image/jpeg"
    val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "Moment_${System.currentTimeMillis()}${if (isVideo) ".mp4" else ".jpg"}")
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/Moments" else "Pictures/Moments")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val target = resolver.insert(collection, values) ?: return false
    return try {
        resolver.openInputStream(source)?.use { input ->
            resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
        } ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        true
    } catch (_: Exception) {
        resolver.delete(target, null, null)
        false
    }
}

@Composable
private fun StoryEditorPaletteChip(
    iconTint: Color,
    previewColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Palette, null, tint = iconTint, modifier = Modifier.size(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            previewColors.forEach { c ->
                Box(Modifier.size(10.dp).background(c, CircleShape))
            }
        }
    }
}

@Composable
private fun ChromeTool(
    onClick: () -> Unit,
    stroke: Color,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .momentsChromeGlass(CircleShape, interactive = true)
            .border(1.dp, stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SideTool(
    icon: ImageVector,
    tint: Color,
    stroke: Color,
    onClick: () -> Unit,
) {
    SideToolContent(
        tint = tint,
        stroke = stroke,
        icon = icon,
        iconRes = null,
        iconSizeDp = 20,
        onClick = onClick,
    )
}

/** ≡ iOS `EditingToolIcon` — `MomentsStickerTool` vía drawable (`iconRes`), resto Material. */
@Composable
private fun SideTool(
    iconRes: Int,
    tint: Color,
    stroke: Color,
    iconSizeDp: Int = 30,
    onClick: () -> Unit,
) {
    SideToolContent(
        tint = tint,
        stroke = stroke,
        icon = null,
        iconRes = iconRes,
        iconSizeDp = iconSizeDp,
        onClick = onClick,
    )
}

@Composable
private fun SideToolContent(
    tint: Color,
    stroke: Color,
    icon: ImageVector?,
    iconRes: Int?,
    iconSizeDp: Int,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .momentsChromeGlass(CircleShape, interactive = true)
            .border(1.dp, stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSizeDp.dp),
            )
        } else if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(iconSizeDp.dp))
        }
    }
}

@Composable
private fun audienceLabel(audience: ContentAudience): String = when (audience) {
    ContentAudience.EVERYONE -> stringResource(R.string.audience_type_everyone)
    ContentAudience.MUTUALS -> stringResource(R.string.audience_type_mutuals)
    ContentAudience.BEST_FRIENDS -> stringResource(R.string.audience_type_best_friends)
    ContentAudience.CUSTOM, ContentAudience.CUSTOM_LIST -> stringResource(R.string.audience_type_custom)
    ContentAudience.ONLY_ME -> stringResource(R.string.audience_type_only_me)
}
