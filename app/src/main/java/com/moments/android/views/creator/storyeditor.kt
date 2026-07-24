package com.moments.android.views.creator

import com.moments.android.views.creator.components.ActiveEditorMode
import com.moments.android.views.creator.components.StoryDrawingEditorOverlay
import com.moments.android.views.creator.components.StoryFilterSelectorView
import com.moments.android.views.creator.components.StoryTextOverlayDraft
import com.moments.android.views.creator.components.StoryTextStyle
import com.moments.android.views.creator.components.StoryTextGradientSettings
import com.moments.android.views.creator.components.StoryVideoGravity
import com.moments.android.views.creator.components.StoryVideoPlayerView
import com.moments.android.views.components.AnimatedStickerView
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.components.StickerPolaroidFrameView
import com.moments.android.views.components.StoryPolaroidFrameStyle
import com.moments.android.views.creator.components.parseStoryColorHex
import com.moments.android.views.creator.components.rememberStoryFontFamily
import com.moments.android.views.creator.creatorscreens.CreatorFlowPendingScreen
import com.moments.android.views.creator.creatorscreens.SelfieStickerLiveCameraView
import com.moments.android.views.creator.creatorscreens.StoryOverlayDragState
import com.moments.android.views.creator.creatorscreens.StoryOverlayTrashZone
import com.moments.android.views.creator.creatorscreens.StoryOverlayToast
import com.moments.android.views.creator.creatorscreens.StoryOverlayToastHost
import com.moments.android.views.creator.creatorscreens.StickerOverlayView
import com.moments.android.views.creator.creatorscreens.StoryTextEditor
import com.moments.android.views.creator.creatorscreens.StoryTextOverlayItem
import com.moments.android.views.creator.creatorscreens.isPointOverStoryOverlayTrash
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.CachedSticker
import com.moments.android.models.CachedStickerInteractionData
import com.moments.android.models.Point
import com.moments.android.services.content.FilterService
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.BackgroundStoryUploadService
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorFlow
import com.moments.android.views.creator.CreatorMedia
import com.moments.android.views.creator.audienceselector.AudienceSelectionView
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
import android.graphics.Color as AndroidColor

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
@Composable
fun StoryEditingView(
    selectedMediaItems: List<CreatorMedia>,
    onSelectedMediaItemsChange: (List<CreatorMedia>) -> Unit,
    onCurrentFlowChange: (CreatorFlow) -> Unit,
    startInTextMode: Boolean,
    onStartInTextModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
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
    var selectedFilter by remember { mutableStateOf(FilterService.FilterType.NORMAL) }
    var filterIntensity by remember { mutableDoubleStateOf(1.0) }
    var filteredImage by remember { mutableStateOf<Bitmap?>(null) }
    var filterJob by remember { mutableStateOf<Job?>(null) }
    var showingIntensitySlider by remember { mutableStateOf(false) }
    var isVideoPreviewMuted by remember { mutableStateOf(false) }
    var stickers by remember { mutableStateOf<List<StoryStickerDraft>>(emptyList()) }
    var showingStickerPicker by remember { mutableStateOf(false) }
    var nextStickerZ by remember { mutableIntStateOf(0) }
    var activeEditingStickerId by remember { mutableStateOf<String?>(null) }
    var deleteArmedStickerId by remember { mutableStateOf<String?>(null) }
    var focusedInlineStickerOriginal by remember { mutableStateOf<StoryStickerDraft?>(null) }
    var editingPolaroidId by remember { mutableStateOf<String?>(null) }
    var editingPolaroidOriginal by remember { mutableStateOf<StoryStickerDraft?>(null) }
    var polaroidCaptionBuffer by remember { mutableStateOf("") }
    var polaroidSwipeOffsetX by remember { mutableStateOf(0f) }
    var editingRevealId by remember { mutableStateOf<String?>(null) }
    var storyOverlayToast by remember { mutableStateOf<StoryOverlayToast?>(null) }

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

    LaunchedEffect(startInTextMode) {
        if (startInTextMode) {
            beginCreatingTextOverlay()
            onStartInTextModeChange(false)
        }
    }

    val media = selectedMediaItems.firstOrNull()
    val hasTextOverlays = textOverlays.any { it.isReady } ||
        (activeEditorMode == ActiveEditorMode.TEXT && editorBuffer.trim().isNotEmpty())
    val hasDrawing = drawingImage != null
    val hasStickers = stickers.isNotEmpty()
    val hasContent = media != null || hasTextOverlays || hasDrawing || hasStickers

    if (showingAudience) {
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
            modifier = modifier,
        )
        return
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

    fun publishStory() {
        if (isPublishing || FirebaseAuth.getInstance().currentUser == null || !hasContent) return
        finishTextEditing()
        val prepared = textOverlays.filter { it.isReady }
            .sortedBy { it.layerOrder }
            .map { it.toMetadata() }
        if (media == null && prepared.isEmpty() && drawingImage == null && stickers.isEmpty()) return
        isPublishing = true
        scope.launch {
            val publishMedia = when {
                filteredImage != null &&
                    selectedFilter != FilterService.FilterType.NORMAL &&
                    media != null && !media.isVideo -> {
                    val bmp = filteredImage!!
                    val dir = File(context.cacheDir, "story_filters").also { it.mkdirs() }
                    val file = File(dir, "filter_${UUID.randomUUID()}.jpg")
                    withContext(Dispatchers.IO) {
                        FileOutputStream(file).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                    }
                    media.copy(uri = Uri.fromFile(file), hasEdits = true)
                }
                media != null -> media
                else -> {
                    val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(if (isDark) AndroidColor.parseColor("#0B1215") else AndroidColor.parseColor("#FAF9F6"))
                    val dir = File(context.cacheDir, "story_text_only").also { it.mkdirs() }
                    val file = File(dir, "text_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    bitmap.recycle()
                    CreatorMedia(
                        uri = Uri.fromFile(file),
                        isVideo = false,
                        aspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
                        recommendedAspectRatio = CreatorAspectRatio.NINE_BY_SIXTEEN,
                    )
                }
            }
            val primary = prepared.firstOrNull()
            val drawingBytes = drawingImage?.let { bmp ->
                ByteArrayOutputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                }
            }
            val pendingDir = File(context.filesDir, "pending_uploads").also { it.mkdirs() }
            val cachedStickers = withContext(Dispatchers.Default) {
                stickers.sortedBy { it.zIndex }.map { draft ->
                    val stickerBitmap = when {
                        draft.type == "emoji" && draft.content.isNotBlank() -> renderEmojiStickerBitmap(draft.content)
                        draft.type == "selfie" || draft.type == "frame" -> draft.image
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
            val actionId = BackgroundStoryUploadService.uploadStory(
                media = publishMedia,
                storyText = primary?.text,
                textPosition = primary?.normalizedPosition,
                selectedTextStyle = primary?.styleRaw,
                textOverlayMetadata = primary,
                textOverlays = prepared.takeIf { it.isNotEmpty() },
                drawingData = drawingBytes,
                stickers = cachedStickers.takeIf { it.isNotEmpty() },
                audienceSetting = audience.raw,
                customViewers = customSelectedUsers.takeIf { it.isNotEmpty() },
                customListId = selectedListId,
                selectedListName = selectedListName,
                expirationHours = expirationHours,
            )
            if (actionId != null) {
                HapticManager.shared.success()
                onSelectedMediaItemsChange(emptyList())
                textOverlays = emptyList()
                stickers = emptyList()
                drawingImage?.recycle()
                drawingImage = null
                delay(400)
                onDismiss()
            } else {
                HapticManager.shared.warning()
                isPublishing = false
            }
        }
    }

    Box(modifier.fillMaxSize().background(canvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChromeTool(
                    onClick = {
                        when (activeEditorMode) {
                            ActiveEditorMode.TEXT -> finishTextEditing()
                            ActiveEditorMode.DRAWING -> activeEditorMode = ActiveEditorMode.IDLE
                            ActiveEditorMode.FILTERS -> activeEditorMode = ActiveEditorMode.IDLE
                            else -> onCurrentFlowChange(CreatorFlow.STORY_CAMERA)
                        }
                    },
                    stroke = controlStroke,
                ) {
                    Icon(Icons.Filled.Close, null, tint = controlFg, modifier = Modifier.size(18.dp))
                }
                ChromeTool(
                    onClick = { pendingTool = "StoryEditingView.saveToGallery (pending)" },
                    stroke = controlStroke,
                ) {
                    Icon(Icons.Filled.Download, null, tint = controlFg, modifier = Modifier.size(18.dp))
                }
            }

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black),
            ) {
                val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                when {
                    media != null && !media.isVideo -> {
                        val preview = filteredImage
                        if (preview != null && selectedFilter != FilterService.FilterType.NORMAL) {
                            Image(
                                bitmap = preview.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
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
                        Box(Modifier.fillMaxSize()) {
                            StoryVideoPlayerView(
                                videoUri = media.uri,
                                videoGravity = StoryVideoGravity.RESIZE_ASPECT_FILL,
                                isMuted = isVideoPreviewMuted,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .size(42.dp)
                                    .momentsChromeGlass(CircleShape, interactive = true)
                                    .clickable { isVideoPreviewMuted = !isVideoPreviewMuted },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (isVideoPreviewMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    else -> {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(if (isDark) Color(0xFF1A2226) else Color(0xFFE8E4DC)),
                        )
                    }
                }

                // Drawing layer under text overlays
                if (activeEditorMode != ActiveEditorMode.DRAWING) {
                    drawingImage?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Equivalente al fondo de foco de StoryOverlaysView.swift: queda detrás del
                // sticker editado y permite volver a su transformación original al tocar fuera.
                if (activeEditingStickerId != null || editingPolaroidId != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
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
                        StickerOverlayView(
                            sticker = sticker,
                            canvasWidthPx = boxW.roundToInt(),
                            canvasHeightPx = boxH.roundToInt(),
                            isSelected = armed || editing,
                            isDragging = false,
                            isContentEditing = false,
                            isEditingInline = editing,
                            onUpdate = { updated ->
                                stickers = stickers.map { if (it.id == updated.id) updated else it }
                            },
                            onDelete = {
                                stickers = stickers.filterNot { it.id == sticker.id }
                                deleteArmedStickerId = null
                                activeEditingStickerId = null
                                HapticManager.shared.warning()
                            },
                            onDragChanged = { updated ->
                                val overTrash = isPointOverStoryOverlayTrash(
                                    x = updated.normalizedX.toFloat() * boxW,
                                    y = updated.normalizedY.toFloat() * boxH,
                                    canvasWidthPx = boxW,
                                    canvasHeightPx = boxH,
                                )
                                if (!overlayDragState.isOverTrash && overTrash) {
                                    HapticManager.shared.mediumImpact()
                                }
                                overlayDragState = StoryOverlayDragState(isDragging = true, isOverTrash = overTrash)
                                deleteArmedStickerId = null
                                stickers = stickers.map { if (it.id == updated.id) updated else it }
                            },
                            onDragEnded = { updated ->
                                if (overlayDragState.isOverTrash) {
                                    stickers = stickers.filterNot { it.id == updated.id }
                                    activeEditingStickerId = null
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
                                        restoreFocusedInlineSticker()
                                        HapticManager.shared.warning()
                                    }
                                    stickerSupportsInlineEdit(sticker) -> {
                                        if (editing) {
                                            activeEditingStickerId = null
                                            restoreFocusedInlineSticker()
                                        } else {
                                            focusInlineSticker(sticker)
                                        }
                                    }
                                    sticker.type == "frame" -> beginPolaroidEditing(sticker)
                                    else -> {
                                        activeEditingStickerId = null
                                        deleteArmedStickerId = sticker.id
                                    }
                                }
                            },
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
                        Row(
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 100.dp)
                                .momentsChromeGlass(RoundedCornerShape(24.dp), interactive = true)
                                .clickable {
                                    editingRevealId = stickers.firstOrNull { it.type == "reveal" }?.id
                                    HapticManager.shared.mediumImpact()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Reveal active", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
                            Icon(
                                Icons.Filled.Close,
                                null,
                                tint = Color.White.copy(.65f),
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(16.dp)
                                    .clickable {
                                        stickers = stickers.filterNot { it.type == "reveal" }
                                        editingRevealId = null
                                    },
                            )
                        }
                    }

                    StoryOverlayTrashZone(overlayDragState)

                    // Pie de foto vivo del frame, sobre el fondo de foco y bajo el sticker hero.
                    if (editingPolaroidId != null) {
                        BasicTextField(
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
                            singleLine = false,
                            textStyle = TextStyle(
                                color = Color.Black,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            ),
                            cursorBrush = SolidColor(Color.Black),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                                .widthIn(max = 320.dp)
                                .momentsChromeGlass(CircleShape, interactive = true)
                                .padding(horizontal = 25.dp, vertical = 12.dp),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.Center) {
                                    if (polaroidCaptionBuffer.isBlank()) {
                                        Text(
                                            "Add a note",
                                            color = Color.Black.copy(.42f),
                                            fontSize = 24.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                    inner()
                                }
                            },
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

                // Side toolbar
                if (activeEditorMode == ActiveEditorMode.IDLE && activeEditingStickerId == null && editingPolaroidId == null && editingRevealId == null) {
                    Column(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SideTool(Icons.Filled.TextFields, controlFg, controlStroke) {
                            beginCreatingTextOverlay()
                        }
                        SideTool(Icons.Filled.EmojiEmotions, controlFg, controlStroke) {
                            showingStickerPicker = true
                        }
                        SideTool(Icons.Filled.Brush, controlFg, controlStroke) {
                            activeEditorMode = ActiveEditorMode.DRAWING
                        }
                        SideTool(Icons.Filled.Filter, controlFg, controlStroke) {
                            if (media != null && !media.isVideo) {
                                activeEditorMode = ActiveEditorMode.FILTERS
                                showingIntensitySlider = selectedFilter != FilterService.FilterType.NORMAL
                                applySelectedFilter()
                            } else {
                                pendingTool = "StoryFilterSelectorView.swift (image only)"
                            }
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .momentsChromeGlass(CircleShape, interactive = true)
                                .border(1.dp, controlStroke, CircleShape)
                                .clickable { expirationHours = if (expirationHours == 24) 48 else 24 },
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
                    )
                }

                if (activeEditorMode == ActiveEditorMode.DRAWING) {
                    StoryDrawingEditorOverlay(
                        baseDrawing = drawingImage,
                        onCancel = { activeEditorMode = ActiveEditorMode.IDLE },
                        onDone = { result ->
                            val previous = drawingImage
                            drawingImage = result
                            if (previous != null && previous !== result && !previous.isRecycled) {
                                previous.recycle()
                            }
                            activeEditorMode = ActiveEditorMode.IDLE
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

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

            if (activeEditorMode == ActiveEditorMode.IDLE && activeEditingStickerId == null && editingPolaroidId == null && editingRevealId == null) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
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

                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(shareBg.copy(if (hasContent && !isPublishing) 1f else 0.55f))
                            .clickable(enabled = hasContent && !isPublishing) { publishStory() }
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
                Spacer(Modifier.height(8.dp))
            }
        }

        val editingSlider = stickers.firstOrNull {
            it.id == activeEditingStickerId && it.type == "emojiSlider"
        }
        if (editingSlider != null) {
            EmojiSliderPresetBar(
                selectedEmoji = editingSlider.sliderEmoji ?: "😍",
                onSelect = { emoji ->
                    stickers = stickers.map { item ->
                        if (item.id != editingSlider.id) item
                        else item.copy(sliderEmoji = emoji, content = emoji)
                    }
                    HapticManager.shared.lightImpact()
                },
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
                onDismiss = { showingStickerPicker = false },
            )
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
        Box(modifier.size(72.dp).background(Color.White.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Mic, null, tint = Color.White, modifier = Modifier.size(28.dp))
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
            Column(
                modifier
                    .background(Color.White.copy(0.92f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    sticker.questionText ?: sticker.content.substringBefore("·").trim(),
                    color = Color.Black.copy(0.92f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
                Text(
                    sticker.caption ?: sticker.content.substringAfter("·", "").trim(),
                    color = Color.Black.copy(0.48f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                )
            }
        }
        "hashtag" -> {
            Row(
                modifier
                    .background(Color.White.copy(0.92f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(Color(0xFFF56694), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("#", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                if (isEditingInline) {
                    BasicTextField(
                        value = sticker.hashtag.orEmpty(),
                        onValueChange = { raw ->
                            val cleaned = raw.removePrefix("#").filterNot { it.isWhitespace() }.take(24)
                            onUpdate(
                                sticker.copy(
                                    hashtag = cleaned,
                                    content = if (cleaned.isBlank()) "#" else "#$cleaned",
                                ),
                            )
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.Black.copy(0.92f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        ),
                        cursorBrush = SolidColor(Color.Black),
                        modifier = Modifier
                            .widthIn(min = 72.dp, max = 160.dp)
                            .focusRequester(focusRequester),
                        decorationBox = { inner ->
                            if (sticker.hashtag.isNullOrBlank()) {
                                Text("#", color = Color.Black.copy(0.35f), fontWeight = FontWeight.SemiBold)
                            }
                            inner()
                        },
                    )
                } else {
                    Text(
                        sticker.hashtag?.takeIf { it.isNotBlank() } ?: sticker.content.removePrefix("#"),
                        color = Color.Black.copy(0.92f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
        "mention" -> {
            Row(
                modifier
                    .background(Color.White.copy(0.92f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(Color(0xFF2E7D32), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("@", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    sticker.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: sticker.content,
                    color = Color.Black.copy(0.92f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
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
                ?: sticker.content.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.sticker_link_fallback)
            val host = sticker.linkURL?.let { stickerHostLabel(it) }.orEmpty()
            Row(
                modifier
                    .background(Color.White.copy(0.94f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(Color(0xFF2EA8FA), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Link,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.widthIn(max = 180.dp)) {
                    Text(
                        title,
                        color = Color.Black.copy(0.92f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                    )
                    if (host.isNotBlank()) {
                        Text(
                            host,
                            color = Color.Black.copy(0.48f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        "location" -> {
            val label = (sticker.location ?: sticker.content).let {
                if (it.length > 22) it.take(22) + "…" else it
            }
            Row(
                modifier
                    .background(Color.White.copy(0.92f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(Color(0xFFFA6B42), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    label,
                    color = Color.Black.copy(0.90f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                )
            }
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
    val style = StoryTextStyle.fromRaw(overlay.styleRaw)
    val fontFamily = rememberStoryFontFamily(style)
    Text(
        style.displayText(overlay.text),
        color = parseStoryColorHex(overlay.colorHex),
        fontSize = overlay.fontSize.toFloat().sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        fontFamily = fontFamily,
        modifier = modifier,
    )
}

@Composable
private fun EmojiSliderPresetBar(
    selectedEmoji: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // iOS ModernEmojiSliderInputView / emojiSliderPresetBar presets
    val presets = listOf("😍", "🔥", "😂", "🥹", "🤩", "😮", "😢", "👏", "💯", "🤯")
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
    Box(
        Modifier
            .size(44.dp)
            .momentsChromeGlass(CircleShape, interactive = true)
            .border(1.dp, stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
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
