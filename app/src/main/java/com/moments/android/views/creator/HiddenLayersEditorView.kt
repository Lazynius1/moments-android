package com.moments.android.views.creator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.CachedHiddenLayerDraft
import com.moments.android.models.HiddenLayerImageFrameStyle
import com.moments.android.models.HiddenLayerPresentationStyle
import com.moments.android.models.HiddenLayerTextStyle
import com.moments.android.models.MomentHiddenLayer
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.InteractiveAudioStickerView
import com.moments.android.views.components.hiddenlayers.HiddenLayerLayout
import com.moments.android.views.creator.components.StoryFontRegistry
import com.moments.android.views.creator.creatoruikit.storyViewerCanvasCornerRadius
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.feed.moments.MomentCarouselLayoutRules
import com.moments.android.views.feed.moments.MomentCarouselPresentationMode
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.permission.shared.PermissionPrimerGate
import com.moments.android.views.permission.shared.PermissionPrimerGateHost
import com.moments.android.views.shared.MomentsModalSheet
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Espejo 1:1 de iOS `HiddenLayerDraft` (HiddenLayersEditorView.swift L6–88).
 * `localAudioUri` ≡ iOS `localAudioURL`; `localImage` ≡ `UIImage` → Bitmap.
 */
data class HiddenLayerDraft(
    val id: String = UUID.randomUUID().toString(),
    val type: MomentHiddenLayer.LayerType = MomentHiddenLayer.LayerType.TEXT,
    val anchorX: Double = 0.5,
    val anchorY: Double = 0.5,
    val width: Double = 0.28,
    val height: Double = 0.16,
    val shape: MomentHiddenLayer.LayerShape = MomentHiddenLayer.LayerShape.ROUNDED_RECT,
    val zIndex: Int = 0,
    val text: String = "",
    val caption: String = "",
    val imageOffsetX: Double = 0.0,
    val imageOffsetY: Double = 0.0,
    val imageScale: Double = 1.0,
    val imageFrameStyle: HiddenLayerImageFrameStyle = HiddenLayerImageFrameStyle.CLASSIC,
    val localImage: Bitmap? = null,
    val localAudioUri: Uri? = null,
    val duration: Double? = null,
    val textStyle: HiddenLayerTextStyle = HiddenLayerTextStyle.CLEAN,
    val presentationStyle: HiddenLayerPresentationStyle = HiddenLayerPresentationStyle.GLASS_CARD,
    val unlockMode: MomentHiddenLayer.UnlockMode = MomentHiddenLayer.UnlockMode.IMMEDIATE,
    val unlockAt: Date? = null,
    val authorTimezoneIdentifier: String? = TimeZone.getDefault().id,
) {
    val isReadyToPublish: Boolean
        get() = when (type) {
            MomentHiddenLayer.LayerType.TEXT -> text.trim().isNotEmpty()
            MomentHiddenLayer.LayerType.IMAGE -> localImage != null
            MomentHiddenLayer.LayerType.AUDIO -> localAudioUri != null
        }

    fun toCached(
        localImageFileName: String? = null,
        localAudioFileName: String? = null,
    ): CachedHiddenLayerDraft = CachedHiddenLayerDraft(
        id = id,
        type = type.raw,
        anchorX = anchorX,
        anchorY = anchorY,
        width = width,
        height = height,
        shape = shape.raw,
        zIndex = zIndex,
        text = text,
        caption = caption,
        imageOffsetX = imageOffsetX,
        imageOffsetY = imageOffsetY,
        imageScale = imageScale,
        imageFrameStyle = imageFrameStyle.raw,
        localImageFileName = localImageFileName,
        localAudioFileName = localAudioFileName,
        duration = duration,
        textStyle = textStyle.raw,
        presentationStyle = presentationStyle.raw,
        unlockMode = unlockMode.raw,
        unlockAt = unlockAt,
        authorTimezoneIdentifier = authorTimezoneIdentifier,
    )
}

private const val MaxHiddenLayers = 3

/**
 * Port de `HiddenLayersEditorView` — shell + dock + audio + hotspot + text/polaroid + schedule sheet.
 */
@Composable
fun HiddenLayersEditorView(
    mediaItem: CreatorMedia,
    layers: List<HiddenLayerDraft>,
    onLayersChange: (List<HiddenLayerDraft>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isDark = isSystemInDarkTheme()

    // ≡ colores iOS L116–127
    val primaryText = if (isDark) Color.White else Color.Black
    val secondaryText = if (isDark) Color.White.copy(0.64f) else Color.Black.copy(0.55f)
    val tertiaryText = if (isDark) Color.White.copy(0.78f) else Color.Black.copy(0.72f)
    val subtleSurface = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.045f)
    val strongSurface = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.07f)
    val previewStroke = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.08f)
    val sheetTint = if (isDark) Color.Transparent else Color.White.copy(0.58f)

    // ≡ @StateObject audioRecorder + micGate (iOS L99–100)
    val audioRecorder = remember { HiddenLayerAudioRecorder(context.applicationContext) }
    val micGate = remember { PermissionPrimerGate(PermissionPrimerGate.Kind.MICROPHONE) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    var selectedLayerId by remember { mutableStateOf(layers.firstOrNull()?.id) }
    var selectedDockType by remember { mutableStateOf(MomentHiddenLayer.LayerType.TEXT) }
    var switcherTransientOffset by remember { mutableFloatStateOf(0f) }
    var adjustingImageLayerId by remember { mutableStateOf<String?>(null) }
    // ≡ dragOffset / draggingLayerId / magnifyingLayerId / magnifyBaseSize (iOS L106–109)
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var draggingLayerId by remember { mutableStateOf<String?>(null) }
    var magnifyingLayerId by remember { mutableStateOf<String?>(null) }
    var magnifyBaseSize by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var schedulePickerLayerId by remember { mutableStateOf<String?>(null) }
    var pendingScheduleDate by remember { mutableStateOf(Date()) }
    var styleMenuExpanded by remember { mutableStateOf(false) }
    var fontMenuExpanded by remember { mutableStateOf(false) }
    var frameMenuExpanded by remember { mutableStateOf(false) }
    var unlockMenuExpanded by remember { mutableStateOf(false) }
    var opensMenuExpanded by remember { mutableStateOf(false) }

    val selectedLayerIndex = layers.indexOfFirst { it.id == selectedLayerId }.takeIf { it >= 0 }
    val dockEditorLayerIndex = selectedLayerIndex?.takeIf { layers[it].type == selectedDockType }
    // ≡ dockHeight iOS
    val dockHeightDp = if (dockEditorLayerIndex != null) 272.dp else 156.dp

    val readyLayerCount = layers.count { it.isReadyToPublish }
    val incompleteLayerCount = max(0, layers.size - readyLayerCount)
    val layerCountSummary = if (incompleteLayerCount > 0) {
        stringResource(R.string.hidden_layers_count_ready, readyLayerCount, incompleteLayerCount)
    } else {
        stringResource(R.string.hidden_layers_count, layers.size)
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return@rememberLauncherForActivityResult
        addImageLayer(
            image = bitmap,
            layers = layers,
            onLayersChange = onLayersChange,
            selectedLayerId = selectedLayerId,
            onSelectedLayerId = { selectedLayerId = it },
            onSelectedDockType = { selectedDockType = it },
        )
    }

    LaunchedEffect(Unit) {
        val idx = selectedLayerIndex
        selectedDockType = when {
            idx != null -> layers[idx].type
            else -> layers.lastOrNull()?.type ?: MomentHiddenLayer.LayerType.TEXT
        }
    }

    val latestLayersState by rememberUpdatedState(layers)
    val latestOnLayersChangeState by rememberUpdatedState(onLayersChange)

    val caveatTypeface = remember {
        StoryFontRegistry.typeface(context, "Caveat-Bold")
    }

    fun resizeText(layer: HiddenLayerDraft): HiddenLayerDraft =
        resizeTextLayerToFitContent(layer, density, caveatTypeface)

    fun activateLayer(id: String) {
        val snapshot = latestLayersState
        val index = snapshot.indexOfFirst { it.id == id }
        if (index < 0) return
        val layerType = snapshot[index].type
        var next = snapshot
        if (index != snapshot.lastIndex) {
            val layer = snapshot[index]
            next = snapshot.filterNot { it.id == id } + layer
            next = next.mapIndexed { i, item -> item.copy(zIndex = i) }
            latestOnLayersChangeState(next)
        }
        selectedLayerId = id
        selectedDockType = layerType
    }

    fun selectDockType(type: MomentHiddenLayer.LayerType) {
        selectedDockType = type
        val existingId = layers.lastOrNull { it.type == type }?.id
        if (existingId != null) activateLayer(existingId) else selectedLayerId = null
    }

    fun createLayer(type: MomentHiddenLayer.LayerType) {
        when (type) {
            MomentHiddenLayer.LayerType.TEXT -> addTextLayer(
                layers, onLayersChange, density, caveatTypeface,
                onSelected = { selectedLayerId = it },
                onDockType = { selectedDockType = it },
            )
            MomentHiddenLayer.LayerType.AUDIO -> addAudioLayer(
                layers, onLayersChange,
                onSelected = { selectedLayerId = it },
                onDockType = { selectedDockType = it },
            )
            MomentHiddenLayer.LayerType.IMAGE -> imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    fun updateAt(index: Int, transform: (HiddenLayerDraft) -> HiddenLayerDraft) {
        if (index !in layers.indices) return
        onLayersChange(layers.mapIndexed { i, item -> if (i == index) transform(item) else item })
    }

    fun stopAudioPreview() {
        audioPlayer?.runCatching { stop() }
        audioPlayer?.release()
        audioPlayer = null
        isPreviewPlaying = false
    }

    fun startAudioPreview(index: Int) {
        val uri = layers.getOrNull(index)?.localAudioUri ?: return
        stopAudioPreview()
        runCatching {
            val player = MediaPlayer().apply {
                setDataSource(context, uri)
                setOnCompletionListener {
                    runCatching { stop() }
                    release()
                    if (audioPlayer === this) {
                        audioPlayer = null
                        isPreviewPlaying = false
                    }
                }
                prepare()
                start()
            }
            audioPlayer = player
            isPreviewPlaying = true
        }.onFailure { stopAudioPreview() }
    }

    fun clearAudio(index: Int) {
        if (index !in layers.indices) return
        stopAudioPreview()
        if (audioRecorder.isRecording) {
            audioRecorder.stopRecording()
        }
        updateAt(index) { it.copy(localAudioUri = null, duration = null) }
    }

    fun restartAudioRecording(index: Int) {
        clearAudio(index)
    }

    // Auto-stop a 15s ≡ AVAudioRecorder.record(forDuration: 15); asigna como stop manual.
    LaunchedEffect(audioRecorder.isRecording, audioRecorder.elapsedTime, dockEditorLayerIndex) {
        val index = dockEditorLayerIndex ?: return@LaunchedEffect
        if (!audioRecorder.isRecording || audioRecorder.elapsedTime < 15.0) return@LaunchedEffect
        val result = audioRecorder.stopRecording() ?: return@LaunchedEffect
        updateAt(index) {
            it.copy(localAudioUri = result.uri, duration = result.duration)
        }
        startAudioPreview(index)
    }

    fun applyPendingSchedule(layerId: String) {
        val index = layers.indexOfFirst { it.id == layerId }
        if (index < 0) return
        updateAt(index) {
            it.copy(
                unlockMode = MomentHiddenLayer.UnlockMode.SCHEDULED,
                unlockAt = pendingScheduleDate,
                authorTimezoneIdentifier = TimeZone.getDefault().id,
            )
        }
    }

    fun openSchedulePicker(index: Int) {
        if (index !in layers.indices) return
        pendingScheduleDate = layers[index].unlockAt ?: tonightUnlockDate()
        schedulePickerLayerId = layers[index].id
    }

    DisposableEffect(Unit) {
        onDispose {
            if (audioRecorder.isRecording) audioRecorder.stopRecording()
            audioRecorder.release()
            audioPlayer?.runCatching { stop() }
            audioPlayer?.release()
            audioPlayer = null
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val keepStrong = strongSurface
    @Suppress("UNUSED_VARIABLE")
    val keepDragOffset = dragOffset // grab-offset absoluto iOS; pan deltas cubren el drag actual

    // ≡ body GeometryReader + VStack (L167–204)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(subtleSurface)
            .background(sheetTint),
    ) {
        val horizontalPadding = 14.dp
        val headerHeight = 48.dp
        val verticalSpacing = 10.dp
        val topPadding = 6.dp
        val bottomPadding = 8.dp
        val maxCanvasHeightDp = maxOf(
            260.dp,
            maxHeight - headerHeight - dockHeightDp - topPadding - bottomPadding - verticalSpacing * 2,
        )
        val availableWidthPx = with(density) { (maxWidth - horizontalPadding * 2).toPx() }.coerceAtLeast(1f)
        val preferredRatio = mediaItem.aspectRatio.value
        val displayedRatio = HiddenLayerLayout.displayedPostAspectRatio(
            imageWidth = 1f,
            imageHeight = 1f,
            preferredAspectRatio = preferredRatio,
        )
        val mediaRatio = preferredRatio.takeIf { it > 0f && it.isFinite() } ?: displayedRatio
        val screenHpx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val screenWdp = configuration.screenWidthDp.toFloat()
        val previewHpx = previewCanvasHeight(
            availableWidth = availableWidthPx,
            displayedPostAspectRatio = displayedRatio,
            screenWidthDp = screenWdp,
            screenHeightPx = screenHpx,
            density = density.density,
        )
        val canvasHeightDp = minOf(maxCanvasHeightDp, with(density) { previewHpx.toDp() })

        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            // ≡ headerBar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .padding(horizontal = horizontalPadding)
                    .padding(top = topPadding),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable(onClick = onDismiss)
                        .padding(10.dp),
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = primaryText, modifier = Modifier.size(16.dp))
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.hidden_layers_editor_title),
                        color = primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    if (selectedLayerId == null) {
                        Text(layerCountSummary, color = secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Text(
                    stringResource(R.string.common_done),
                    color = primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            // ≡ editorCanvas(height:) + .frame(height: maxCanvasHeight, alignment: .center)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(maxCanvasHeightDp)
                    .padding(horizontal = horizontalPadding),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(canvasHeightDp),
                ) {
                    val containerW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val containerH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    val imageRect = editorPreviewRect(
                        containerSize = Size(containerW, containerH),
                        previewHeight = previewCanvasHeight(
                            availableWidth = containerW,
                            displayedPostAspectRatio = displayedRatio,
                            screenWidthDp = screenWdp,
                            screenHeightPx = screenHpx,
                            density = density.density,
                        ),
                    )
                    val presentationMode = MomentCarouselLayoutRules.presentationMode(mediaRatio, displayedRatio)
                    val corner = storyViewerCanvasCornerRadius

                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(corner))
                            .background(subtleSurface)
                            .momentsChromeGlass(RoundedCornerShape(corner), interactive = false)
                            .clickable { selectedLayerId = null },
                    ) {
                        if (presentationMode == MomentCarouselPresentationMode.FitWithBlur) {
                            AsyncImage(
                                model = mediaItem.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .offset {
                                        IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt())
                                    }
                                    .size(
                                        width = with(density) { imageRect.width.toDp() },
                                        height = with(density) { imageRect.height.toDp() },
                                    )
                                    .clip(RoundedCornerShape(26.dp))
                                    .blur(30.dp),
                            )
                            Box(
                                Modifier
                                    .offset {
                                        IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt())
                                    }
                                    .size(
                                        width = with(density) { imageRect.width.toDp() },
                                        height = with(density) { imageRect.height.toDp() },
                                    )
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(Color.Black.copy(0.18f)),
                            )
                        }
                        AsyncImage(
                            model = mediaItem.uri,
                            contentDescription = null,
                            contentScale = if (presentationMode == MomentCarouselPresentationMode.FitWithBlur) {
                                ContentScale.Fit
                            } else {
                                ContentScale.Crop
                            },
                            modifier = Modifier
                                .offset {
                                    IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt())
                                }
                                .size(
                                    width = with(density) { imageRect.width.toDp() },
                                    height = with(density) { imageRect.height.toDp() },
                                )
                                .clip(RoundedCornerShape(26.dp))
                                .clickable { selectedLayerId = null },
                        )

                        EditorGhostRail(imageRect = imageRect)

                        val latestLayers by rememberUpdatedState(layers)
                        val latestOnLayersChange by rememberUpdatedState(onLayersChange)
                        val latestAdjustingId by rememberUpdatedState(adjustingImageLayerId)

                        layers.sortedBy { if (it.id == draggingLayerId) Int.MAX_VALUE else it.zIndex }.forEach { layer ->
                            val frame = HiddenLayerLayout.frame(layer, imageRect)
                            Box(
                                Modifier
                                    .offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
                                    .size(
                                        width = with(density) { frame.width.toDp() },
                                        height = with(density) { frame.height.toDp() },
                                    )
                                    // ≡ DragGesture + MagnifyGesture simultaneous (hiddenLayerHotspot L752–839)
                                    // + imageAdjustmentGesture cuando adjustingImageLayerId (L1340–1353)
                                    .pointerInput(layer.id, imageRect) {
                                        var zoomAccum = 1f
                                        var adjustStartOffsetX = 0.0
                                        var adjustStartOffsetY = 0.0
                                        var adjustPanAccum = Offset.Zero
                                        var adjustGestureStarted = false
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val adjustingId = latestAdjustingId
                                            val currentLayers = latestLayers
                                            val current = currentLayers.firstOrNull { it.id == layer.id }
                                                ?: return@detectTransformGestures

                                            // ≡ imageAdjustmentGesture (solo offset/scale de la foto)
                                            if (adjustingId == layer.id &&
                                                layer.type == MomentHiddenLayer.LayerType.IMAGE
                                            ) {
                                                if (!adjustGestureStarted) {
                                                    adjustGestureStarted = true
                                                    adjustStartOffsetX = current.imageOffsetX
                                                    adjustStartOffsetY = current.imageOffsetY
                                                    adjustPanAccum = Offset.Zero
                                                    zoomAccum = 1f
                                                }
                                                var next = current
                                                if (zoom != 1f) {
                                                    zoomAccum *= zoom
                                                    // iOS: imageScale = magnification (arranca en 1)
                                                    next = next.copy(
                                                        imageScale = zoomAccum.toDouble().coerceIn(1.0, 2.2),
                                                    )
                                                }
                                                if (pan != Offset.Zero) {
                                                    adjustPanAccum += pan
                                                    next = next.copy(
                                                        imageOffsetX = (adjustStartOffsetX + adjustPanAccum.x)
                                                            .coerceIn(-48.0, 48.0),
                                                        imageOffsetY = (adjustStartOffsetY + adjustPanAccum.y)
                                                            .coerceIn(-48.0, 48.0),
                                                    )
                                                }
                                                if (next != current) {
                                                    latestOnLayersChange(
                                                        currentLayers.map { if (it.id == layer.id) next else it },
                                                    )
                                                }
                                                return@detectTransformGestures
                                            }

                                            selectedLayerId = layer.id
                                            draggingLayerId = layer.id

                                            var next = current
                                            if (zoom != 1f) {
                                                val base = magnifyBaseSize
                                                if (magnifyingLayerId != layer.id || base == null) {
                                                    magnifyingLayerId = layer.id
                                                    magnifyBaseSize = current.width to current.height
                                                    zoomAccum = 1f
                                                }
                                                zoomAccum *= zoom
                                                val ratio = zoomAccum.coerceIn(0.7f, 2.2f)
                                                val (baseW, baseH) = magnifyBaseSize
                                                    ?: (current.width to current.height)
                                                next = magnifyHotspotLayer(next, baseW, baseH, ratio)
                                            }
                                            if (pan != Offset.Zero) {
                                                next = dragHotspotLayer(next, imageRect, pan)
                                            }
                                            if (next != current) {
                                                latestOnLayersChange(
                                                    currentLayers.map { if (it.id == layer.id) next else it },
                                                )
                                            }
                                        }
                                    }
                                    .pointerInput(layer.id) {
                                        // ≡ onEnded de Drag/Magnify (detectTransformGestures no lo expone)
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            do {
                                                val event = awaitPointerEvent(PointerEventPass.Main)
                                            } while (event.changes.any { it.pressed })
                                            val endId = magnifyingLayerId ?: layer.id
                                            activateLayer(endId)
                                            dragOffset = Offset.Zero
                                            magnifyingLayerId = null
                                            magnifyBaseSize = null
                                            draggingLayerId = null
                                        }
                                    }
                                    .clickable { activateLayer(layer.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                CanvasLayerPreview(
                                    layer = layer,
                                    frameWidthPx = frame.width,
                                    frameHeightPx = frame.height,
                                    isAdjusting = adjustingImageLayerId == layer.id,
                                )
                            }
                        }
                    }
                }
            }

            // ≡ dockContent (L412–630)
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(dockHeightDp)
                    .padding(horizontal = horizontalPadding),
            ) {
            val index = dockEditorLayerIndex
            val canCreate = layers.size < MaxHiddenLayers
            if (index != null) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .momentsChromeGlass(RoundedCornerShape(24.dp), interactive = false)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.align(Alignment.CenterStart),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MiniCircleButton(Icons.Filled.KeyboardArrowDown, primaryText, strongSurface) {
                                selectedLayerId = null
                            }
                            if (layers[index].type == MomentHiddenLayer.LayerType.IMAGE && canCreate) {
                                MiniCircleButton(Icons.Filled.Add, primaryText, strongSurface) {
                                    createLayer(selectedDockType)
                                }
                            }
                        }
                        Text(
                            layerTitle(layers[index]),
                            color = primaryText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(
                                    x = if (layers[index].type == MomentHiddenLayer.LayerType.IMAGE) (-12).dp else 0.dp,
                                ),
                        )
                        Row(
                            Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (canCreate && layers[index].type != MomentHiddenLayer.LayerType.IMAGE) {
                                MiniCircleButton(Icons.Filled.Add, primaryText, strongSurface) {
                                    createLayer(selectedDockType)
                                }
                            }
                            if (layers[index].type == MomentHiddenLayer.LayerType.IMAGE) {
                                MiniSheetHeaderIconButton(
                                    icon = Icons.Filled.Image,
                                    primaryText = primaryText,
                                    fill = subtleSurface,
                                    stroke = previewStroke,
                                    isActive = false,
                                    onClick = {
                                        imagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    },
                                )
                                MiniSheetHeaderIconButton(
                                    icon = Icons.Filled.CropFree,
                                    primaryText = primaryText,
                                    fill = if (adjustingImageLayerId == layers[index].id) strongSurface else subtleSurface,
                                    stroke = if (adjustingImageLayerId == layers[index].id) {
                                        if (isDark) Color.White.copy(0.9f) else Color.Black.copy(0.5f)
                                    } else {
                                        previewStroke
                                    },
                                    isActive = adjustingImageLayerId == layers[index].id,
                                    onClick = {
                                        adjustingImageLayerId =
                                            if (adjustingImageLayerId == layers[index].id) null else layers[index].id
                                    },
                                )
                            }
                            MiniCircleButton(Icons.Filled.Delete, primaryText, strongSurface) {
                                val removedType = layers[index].type
                                onLayersChange(layers.filterIndexed { i, _ -> i != index })
                                selectedLayerId = null
                                selectedDockType = removedType
                                HapticManager.shared.warning()
                            }
                        }
                    }

                    when (layers[index].type) {
                        MomentHiddenLayer.LayerType.TEXT -> {
                            BasicTextField(
                                value = layers[index].text,
                                onValueChange = { raw ->
                                    val clipped = raw.take(120)
                                    updateAt(index) {
                                        resizeText(it.copy(text = clipped))
                                    }
                                },
                                textStyle = TextStyle(color = primaryText, fontSize = 16.sp),
                                cursorBrush = SolidColor(primaryText),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .momentsChromeGlass(RoundedCornerShape(18.dp), interactive = true)
                                    .padding(14.dp),
                                decorationBox = { inner ->
                                    if (layers[index].text.isEmpty()) {
                                        Text(
                                            stringResource(R.string.hidden_layers_text_placeholder),
                                            color = secondaryText,
                                        )
                                    }
                                    inner()
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box {
                                    CompactSelectionChip(
                                        title = stringResource(R.string.hidden_layers_text_style),
                                        value = layers[index].presentationStyle.displayName,
                                        primaryText = primaryText,
                                        secondaryText = secondaryText,
                                        previewStroke = previewStroke,
                                        onClick = { styleMenuExpanded = true },
                                    )
                                    DropdownMenu(styleMenuExpanded, onDismissRequest = { styleMenuExpanded = false }) {
                                        HiddenLayerPresentationStyle.entries.forEach { style ->
                                            DropdownMenuItem(
                                                text = { Text(style.displayName) },
                                                onClick = {
                                                    val updated = resizeText(
                                                        layers[index].copy(presentationStyle = style),
                                                    )
                                                    updateAt(index) { updated }
                                                    styleMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                                Box {
                                    CompactSelectionChip(
                                        title = stringResource(R.string.hidden_layers_text_font),
                                        value = layers[index].textStyle.displayName,
                                        primaryText = primaryText,
                                        secondaryText = secondaryText,
                                        previewStroke = previewStroke,
                                        onClick = { fontMenuExpanded = true },
                                    )
                                    DropdownMenu(fontMenuExpanded, onDismissRequest = { fontMenuExpanded = false }) {
                                        HiddenLayerTextStyle.entries.forEach { style ->
                                            DropdownMenuItem(
                                                text = { Text(style.displayName) },
                                                onClick = {
                                                    val updated = resizeText(
                                                        layers[index].copy(textStyle = style),
                                                    )
                                                    updateAt(index) { updated }
                                                    fontMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            AvailabilityControls(
                                layer = layers[index],
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                previewStroke = previewStroke,
                                unlockMenuExpanded = unlockMenuExpanded,
                                onUnlockMenuExpanded = { unlockMenuExpanded = it },
                                opensMenuExpanded = opensMenuExpanded,
                                onOpensMenuExpanded = { opensMenuExpanded = it },
                                onImmediate = {
                                    updateAt(index) {
                                        it.copy(
                                            unlockMode = MomentHiddenLayer.UnlockMode.IMMEDIATE,
                                            unlockAt = null,
                                            authorTimezoneIdentifier = TimeZone.getDefault().id,
                                        )
                                    }
                                },
                                onScheduled = {
                                    updateAt(index) {
                                        it.copy(
                                            unlockMode = MomentHiddenLayer.UnlockMode.SCHEDULED,
                                            unlockAt = it.unlockAt ?: tonightUnlockDate(),
                                            authorTimezoneIdentifier = TimeZone.getDefault().id,
                                        )
                                    }
                                },
                                onTonight = {
                                    updateAt(index) {
                                        it.copy(
                                            unlockAt = tonightUnlockDate(),
                                            authorTimezoneIdentifier = TimeZone.getDefault().id,
                                        )
                                    }
                                },
                                onTomorrow = {
                                    updateAt(index) {
                                        it.copy(
                                            unlockAt = tomorrowUnlockDate(),
                                            authorTimezoneIdentifier = TimeZone.getDefault().id,
                                        )
                                    }
                                },
                                onPickDate = { openSchedulePicker(index) },
                            )
                        }
                        MomentHiddenLayer.LayerType.IMAGE -> {
                            BasicTextField(
                                value = layers[index].caption,
                                onValueChange = { raw -> updateAt(index) { it.copy(caption = raw.take(40)) } },
                                textStyle = TextStyle(color = primaryText, fontSize = 16.sp),
                                cursorBrush = SolidColor(primaryText),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .momentsChromeGlass(RoundedCornerShape(18.dp), interactive = true)
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                decorationBox = { inner ->
                                    if (layers[index].caption.isEmpty()) {
                                        Text(
                                            stringResource(R.string.hidden_layers_image_caption_placeholder),
                                            color = secondaryText,
                                        )
                                    }
                                    inner()
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box {
                                    CompactSelectionChip(
                                        title = stringResource(R.string.hidden_layers_image_frame),
                                        value = layers[index].imageFrameStyle.displayName,
                                        primaryText = primaryText,
                                        secondaryText = secondaryText,
                                        previewStroke = previewStroke,
                                        onClick = { frameMenuExpanded = true },
                                    )
                                    DropdownMenu(frameMenuExpanded, onDismissRequest = { frameMenuExpanded = false }) {
                                        HiddenLayerImageFrameStyle.entries.forEach { style ->
                                            DropdownMenuItem(
                                                text = { Text(style.displayName) },
                                                onClick = {
                                                    updateAt(index) { it.copy(imageFrameStyle = style) }
                                                    frameMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                                Box {
                                    CompactSelectionChip(
                                        title = stringResource(R.string.hidden_layers_image_font),
                                        value = layers[index].textStyle.displayName,
                                        primaryText = primaryText,
                                        secondaryText = secondaryText,
                                        previewStroke = previewStroke,
                                        onClick = { fontMenuExpanded = true },
                                    )
                                    DropdownMenu(fontMenuExpanded, onDismissRequest = { fontMenuExpanded = false }) {
                                        listOf(
                                            HiddenLayerTextStyle.CLEAN,
                                            HiddenLayerTextStyle.HANDWRITTEN,
                                            HiddenLayerTextStyle.MONO,
                                        ).forEach { style ->
                                            DropdownMenuItem(
                                                text = { Text(style.displayName) },
                                                onClick = {
                                                    updateAt(index) { it.copy(textStyle = style) }
                                                    fontMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            AvailabilityControls(
                                layer = layers[index],
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                previewStroke = previewStroke,
                                unlockMenuExpanded = unlockMenuExpanded,
                                onUnlockMenuExpanded = { unlockMenuExpanded = it },
                                opensMenuExpanded = opensMenuExpanded,
                                onOpensMenuExpanded = { opensMenuExpanded = it },
                                onImmediate = {
                                    updateAt(index) {
                                        it.copy(
                                            unlockMode = MomentHiddenLayer.UnlockMode.IMMEDIATE,
                                            unlockAt = null,
                                            authorTimezoneIdentifier = TimeZone.getDefault().id,
                                        )
                                    }
                                },
                                onScheduled = {
                                    updateAt(index) {
                                        it.copy(
                                            unlockMode = MomentHiddenLayer.UnlockMode.SCHEDULED,
                                            unlockAt = it.unlockAt ?: tonightUnlockDate(),
                                            authorTimezoneIdentifier = TimeZone.getDefault().id,
                                        )
                                    }
                                },
                                onTonight = {
                                    updateAt(index) {
                                        it.copy(
                                            unlockAt = tonightUnlockDate(),
                                            authorTimezoneIdentifier = TimeZone.getDefault().id,
                                        )
                                    }
                                },
                                onTomorrow = {
                                    updateAt(index) {
                                        it.copy(
                                            unlockAt = tomorrowUnlockDate(),
                                            authorTimezoneIdentifier = TimeZone.getDefault().id,
                                        )
                                    }
                                },
                                onPickDate = { openSchedulePicker(index) },
                            )
                        }
                        MomentHiddenLayer.LayerType.AUDIO -> {
                            AudioControls(
                                layer = layers[index],
                                primaryText = primaryText,
                                tertiaryText = tertiaryText,
                                subtleSurface = subtleSurface,
                                previewStroke = previewStroke,
                                audioRecorder = audioRecorder,
                                isPreviewPlaying = isPreviewPlaying,
                                onClear = { clearAudio(index) },
                                onRestart = { restartAudioRecording(index) },
                                onTogglePreview = {
                                    if (isPreviewPlaying) stopAudioPreview() else startAudioPreview(index)
                                },
                                onRecordToggle = {
                                    if (audioRecorder.isRecording) {
                                        val result = audioRecorder.stopRecording()
                                        if (result != null) {
                                            updateAt(index) {
                                                it.copy(
                                                    localAudioUri = result.uri,
                                                    duration = result.duration,
                                                )
                                            }
                                            startAudioPreview(index)
                                        }
                                    } else {
                                        stopAudioPreview()
                                        micGate.requestAccess(context) {
                                            audioRecorder.startRecording()
                                        }
                                    }
                                },
                            )
                            AvailabilityControls(
                                layer = layers[index],
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                previewStroke = previewStroke,
                                unlockMenuExpanded = unlockMenuExpanded,
                                onUnlockMenuExpanded = { unlockMenuExpanded = it },
                                opensMenuExpanded = opensMenuExpanded,
                                onOpensMenuExpanded = { opensMenuExpanded = it },
                                onImmediate = {
                                    updateAt(index) {
                                        it.copy(
                                            unlockMode = MomentHiddenLayer.UnlockMode.IMMEDIATE,
                                            unlockAt = null,
                                            authorTimezoneIdentifier = TimeZone.getDefault().id,
                                        )
                                    }
                                },
                                onScheduled = {
                                    updateAt(index) {
                                        it.copy(
                                            unlockMode = MomentHiddenLayer.UnlockMode.SCHEDULED,
                                            unlockAt = it.unlockAt ?: tonightUnlockDate(),
                                            authorTimezoneIdentifier = TimeZone.getDefault().id,
                                        )
                                    }
                                },
                                onTonight = {
                                    updateAt(index) {
                                        it.copy(
                                            unlockAt = tonightUnlockDate(),
                                            authorTimezoneIdentifier = TimeZone.getDefault().id,
                                        )
                                    }
                                },
                                onTomorrow = {
                                    updateAt(index) {
                                        it.copy(
                                            unlockAt = tomorrowUnlockDate(),
                                            authorTimezoneIdentifier = TimeZone.getDefault().id,
                                        )
                                    }
                                },
                                onPickDate = { openSchedulePicker(index) },
                            )
                        }
                    }
                } // glass card
                    Spacer(Modifier.weight(1f, fill = true))
                    TypeSwitcherPill(
                        activeType = selectedDockType,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        previewStroke = previewStroke,
                        isDark = isDark,
                        transientOffset = switcherTransientOffset,
                        onTransientOffset = { switcherTransientOffset = it },
                        onSelect = { type ->
                            if (type != selectedDockType) HapticManager.shared.selection()
                            selectDockType(type)
                            switcherTransientOffset = 0f
                        },
                    )
                } // editing dock VStack
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            emptyStateTitle(selectedDockType),
                            color = primaryText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            emptyStateSubtitle(selectedDockType),
                            color = secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        Modifier
                            .alpha(if (canCreate) 1f else 0.48f)
                            .momentsChromeGlass(RoundedCornerShape(50), interactive = canCreate)
                            .clickable(enabled = canCreate) {
                                createLayer(selectedDockType)
                                HapticManager.shared.success()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .widthIn(min = 168.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        Icon(Icons.Filled.Add, null, tint = primaryText, modifier = Modifier.size(14.dp))
                        Text(
                            addActionTitle(selectedDockType),
                            color = primaryText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f, fill = true))
                    TypeSwitcherPill(
                        activeType = selectedDockType,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        previewStroke = previewStroke,
                        isDark = isDark,
                        transientOffset = switcherTransientOffset,
                        onTransientOffset = { switcherTransientOffset = it },
                        onSelect = { type ->
                            if (type != selectedDockType) HapticManager.shared.selection()
                            selectDockType(type)
                            switcherTransientOffset = 0f
                        },
                    )
                }
            }
            } // dockContent
        } // body Column
    } // GeometryReader ≡ BoxWithConstraints
    // ≡ .sheet schedulePicker (L207–234)
    if (schedulePickerLayerId != null) {
        MomentsModalSheet(
            onDismissRequest = { schedulePickerLayerId = null },
            largeOnly = false,
            showDragHandle = false,
        ) {
            HiddenLayerScheduleSheet(
                date = pendingScheduleDate,
                onDateChange = { pendingScheduleDate = it },
                onCancel = { schedulePickerLayerId = null },
                onApply = {
                    val id = schedulePickerLayerId ?: return@HiddenLayerScheduleSheet
                    applyPendingSchedule(id)
                    schedulePickerLayerId = null
                },
            )
        }
    }
    // ≡ .permissionPrimerGate(micGate)
    PermissionPrimerGateHost(gate = micGate)
}

/** Port de `canvasPreview(for:size:isSelected:)` (L873–888). */
@Composable
private fun CanvasLayerPreview(
    layer: HiddenLayerDraft,
    frameWidthPx: Float,
    frameHeightPx: Float,
    isAdjusting: Boolean,
) {
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    when (layer.type) {
        MomentHiddenLayer.LayerType.TEXT -> {
            TextCanvasPreview(
                layer = layer,
                frameWidthPx = frameWidthPx,
                frameHeightPx = frameHeightPx,
                isDark = isDark,
                density = density,
            )
        }
        MomentHiddenLayer.LayerType.AUDIO -> {
            // ≡ audioCanvasPreview — InteractiveAudioStickerView + scale por width
            val scale = (layer.width / 0.18).toFloat().coerceIn(0.7f, 2.4f)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                InteractiveAudioStickerView(
                    audioURL = layer.localAudioUri?.toString().orEmpty(),
                    duration = layer.duration ?: 15.0,
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
            }
        }
        MomentHiddenLayer.LayerType.IMAGE -> {
            val bmp = layer.localImage
            if (bmp != null) {
                HiddenLayerPolaroidPreview(
                    image = bmp,
                    caption = layer.caption.ifEmpty {
                        stringResource(R.string.hidden_layers_image_caption_default)
                    },
                    captionStyle = layer.textStyle,
                    frameStyle = layer.imageFrameStyle,
                    imageOffsetX = layer.imageOffsetX,
                    imageOffsetY = layer.imageOffsetY,
                    imageScale = layer.imageScale,
                    showsAdjustingMask = isAdjusting,
                    canvasWidthPx = frameWidthPx,
                    canvasHeightPx = frameHeightPx,
                )
            }
        }
    }
}

/** Port de `textCanvasPreview` (L1519–1537). */
@Composable
private fun TextCanvasPreview(
    layer: HiddenLayerDraft,
    frameWidthPx: Float,
    frameHeightPx: Float,
    isDark: Boolean,
    density: Density,
) {
    val context = LocalContext.current
    val placeholder = stringResource(R.string.hidden_layers_text_placeholder)
    val displayText = layer.text.ifEmpty { placeholder }
    val caveat = remember { StoryFontRegistry.typeface(context, "Caveat-Bold") }
    val baseSize = remember(displayText, layer.textStyle, density.density) {
        hiddenLayerTextCardBaseSizePx(displayText, layer.textStyle, density, caveat)
    }
    val scale = min(
        max(frameWidthPx / max(baseSize.width, 1f), 0.72f),
        max(frameHeightPx / max(baseSize.height, 1f), 0.72f),
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(
                    width = with(density) { baseSize.width.toDp() },
                    height = with(density) { baseSize.height.toDp() },
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            HiddenLayerTextCardPreview(
                text = displayText,
                textStyle = layer.textStyle,
                presentationStyle = layer.presentationStyle,
                isPlaceholder = layer.text.isEmpty(),
                isDark = isDark,
                caveatTypeface = caveat,
            )
        }
    }
}

/** Port de `HiddenLayerTextCardPreview` (L1783–1884). */
@Composable
private fun HiddenLayerTextCardPreview(
    text: String,
    textStyle: HiddenLayerTextStyle,
    presentationStyle: HiddenLayerPresentationStyle,
    isPlaceholder: Boolean,
    isDark: Boolean,
    caveatTypeface: android.graphics.Typeface,
) {
    val corner = when (presentationStyle) {
        HiddenLayerPresentationStyle.CAPTION_PILL -> 50.dp
        HiddenLayerPresentationStyle.MARKER_LABEL -> 12.dp
        else -> 20.dp
    }
    val shape = RoundedCornerShape(corner)
    val foreground = when (presentationStyle) {
        HiddenLayerPresentationStyle.PAPER_NOTE,
        HiddenLayerPresentationStyle.MARKER_LABEL,
        -> Color.Black.copy(0.84f)
        HiddenLayerPresentationStyle.MINIMAL_TEXT ->
            if (isDark) Color.White.copy(0.96f) else Color.Black.copy(0.9f)
        else -> if (isDark) Color.White else Color.Black.copy(0.88f)
    }
    val fontSize = when (textStyle) {
        HiddenLayerTextStyle.CLEAN -> 17.sp
        HiddenLayerTextStyle.SERIF -> 18.sp
        HiddenLayerTextStyle.HANDWRITTEN -> 23.sp
        HiddenLayerTextStyle.MONO -> 16.sp
        HiddenLayerTextStyle.BUBBLE -> 18.sp
        HiddenLayerTextStyle.EDITORIAL -> 20.sp
    }
    val fontWeight = when (textStyle) {
        HiddenLayerTextStyle.BUBBLE -> FontWeight.Black
        HiddenLayerTextStyle.EDITORIAL -> FontWeight.Bold
        HiddenLayerTextStyle.HANDWRITTEN -> FontWeight.Medium
        else -> FontWeight.SemiBold
    }
    val fontFamily = when (textStyle) {
        HiddenLayerTextStyle.SERIF, HiddenLayerTextStyle.EDITORIAL -> FontFamily.Serif
        HiddenLayerTextStyle.MONO -> FontFamily.Monospace
        HiddenLayerTextStyle.HANDWRITTEN -> FontFamily(caveatTypeface)
        else -> FontFamily.Default
    }
    val background = when (presentationStyle) {
        HiddenLayerPresentationStyle.GLASS_CARD -> Color.Transparent
        HiddenLayerPresentationStyle.CAPTION_PILL ->
            if (isDark) Color.Black.copy(0.56f) else Color.Black.copy(0.12f)
        HiddenLayerPresentationStyle.PAPER_NOTE -> Color(1f, 0.95f, 0.82f)
        HiddenLayerPresentationStyle.MARKER_LABEL -> Color.Yellow.copy(0.92f)
        HiddenLayerPresentationStyle.FLOATING_QUOTE -> Color.Transparent // gradient below
        HiddenLayerPresentationStyle.MINIMAL_TEXT -> Color.Transparent
    }
    val rotation = if (presentationStyle == HiddenLayerPresentationStyle.PAPER_NOTE) -1.2f else 0f

    val textModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 74.dp)
        .then(
            when (presentationStyle) {
                HiddenLayerPresentationStyle.GLASS_CARD ->
                    Modifier.momentsChromeGlass(shape, interactive = false)
                HiddenLayerPresentationStyle.FLOATING_QUOTE ->
                    Modifier.background(
                        Brush.linearGradient(
                            if (isDark) {
                                listOf(Color.Black.copy(0.66f), Color.Black.copy(0.28f))
                            } else {
                                listOf(Color.White.copy(0.94f), Color.Black.copy(0.04f))
                            },
                        ),
                        shape,
                    )
                HiddenLayerPresentationStyle.CAPTION_PILL ->
                    Modifier.background(background, RoundedCornerShape(50))
                else -> Modifier.background(background, shape)
            },
        )
        .clip(shape)
        .padding(horizontal = 16.dp, vertical = 12.dp)

    Text(
        text,
        color = foreground,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .graphicsLayer {
                rotationZ = rotation
                alpha = if (isPlaceholder) 0.7f else 1f
            }
            .then(textModifier),
    )
}

/**
 * Port de `HiddenLayerPolaroidPreview` (L1886–1989).
 * `showsAdjustingMask` existe en iOS pero no altera el body (paridad de API).
 */
@Composable
private fun HiddenLayerPolaroidPreview(
    image: Bitmap,
    caption: String?,
    captionStyle: HiddenLayerTextStyle?,
    frameStyle: HiddenLayerImageFrameStyle,
    imageOffsetX: Double,
    imageOffsetY: Double,
    imageScale: Double,
    @Suppress("UNUSED_PARAMETER") showsAdjustingMask: Boolean,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val caveat = remember { StoryFontRegistry.typeface(context, "Caveat-Bold") }
    val contentWidth = max(88f, canvasWidthPx)
    val contentHeight = max(96f, canvasHeightPx)
    val imageAreaHeight = contentHeight * 0.76f
    val captionAreaHeight = max(24f, contentHeight - imageAreaHeight)
    val inset = when (frameStyle) {
        HiddenLayerImageFrameStyle.CLASSIC -> 10f
        HiddenLayerImageFrameStyle.CLEAN -> 8f
        HiddenLayerImageFrameStyle.VINTAGE -> 12f
    }
    val frameColor = when (frameStyle) {
        HiddenLayerImageFrameStyle.CLASSIC -> Color.White
        HiddenLayerImageFrameStyle.CLEAN -> Color.White.copy(0.94f)
        HiddenLayerImageFrameStyle.VINTAGE -> Color(0.97f, 0.92f, 0.82f)
    }
    val imageBackground = if (frameStyle == HiddenLayerImageFrameStyle.VINTAGE) {
        Color(0.22f, 0.18f, 0.14f)
    } else {
        Color.Black
    }
    val outerCorner = when (frameStyle) {
        HiddenLayerImageFrameStyle.CLASSIC -> 0.dp
        HiddenLayerImageFrameStyle.CLEAN -> 18.dp
        HiddenLayerImageFrameStyle.VINTAGE -> 6.dp
    }
    val style = captionStyle ?: HiddenLayerTextStyle.HANDWRITTEN
    val captionFontSize = when (style) {
        HiddenLayerTextStyle.CLEAN -> 14.sp
        HiddenLayerTextStyle.MONO -> 13.sp
        else -> 17.sp
    }
    val captionFamily = when (style) {
        HiddenLayerTextStyle.CLEAN -> FontFamily.Default
        HiddenLayerTextStyle.MONO -> FontFamily.Monospace
        else -> FontFamily(caveat)
    }
    val captionColor = if (frameStyle == HiddenLayerImageFrameStyle.VINTAGE) {
        Color.Black.copy(0.78f)
    } else {
        Color.Black.copy(0.85f)
    }
    val captionRotation = if (style == HiddenLayerTextStyle.HANDWRITTEN) -1f else 0f
    val captionOffsetY = if (frameStyle == HiddenLayerImageFrameStyle.CLEAN) -1.dp else (-2).dp

    Column(
        Modifier
            .width(with(density) { (contentWidth + inset * 2).toDp() })
            .clip(RoundedCornerShape(outerCorner))
            .background(frameColor),
    ) {
        Box(
            Modifier
                .padding(with(density) { inset.toDp() })
                .width(with(density) { contentWidth.toDp() })
                .height(with(density) { imageAreaHeight.toDp() })
                .background(imageBackground)
                .clip(RoundedCornerShape(0))
                .graphicsLayer { clip = true },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = imageScale.toFloat()
                        scaleY = imageScale.toFloat()
                        translationX = imageOffsetX.toFloat()
                        translationY = imageOffsetY.toFloat()
                    },
            )
        }
        Box(
            Modifier
                .width(with(density) { (contentWidth + inset * 2).toDp() })
                .height(with(density) { captionAreaHeight.toDp() })
                .background(frameColor),
            contentAlignment = Alignment.Center,
        ) {
            if (!caption.isNullOrEmpty()) {
                Text(
                    caption,
                    color = captionColor,
                    fontSize = captionFontSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = captionFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .offset(y = captionOffsetY)
                        .graphicsLayer { rotationZ = captionRotation },
                )
            }
        }
    }
}

/**
 * Port de `HiddenLayerRemotePolaroidPreview` (L1991–2117) — usado también en Feed/Profile.
 * Developing effect simplificado (brightness/contrast vía alpha overlay).
 */
@Composable
fun HiddenLayerRemotePolaroidPreview(
    url: String,
    caption: String?,
    captionStyle: HiddenLayerTextStyle?,
    frameStyle: HiddenLayerImageFrameStyle,
    imageOffsetX: Double,
    imageOffsetY: Double,
    imageScale: Double,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
) {
    var developingProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(url) {
        developingProgress = 0f
        kotlinx.coroutines.delay(200)
        // easeOut ~0.8s
        val start = SystemClock.elapsedRealtime()
        val durationMs = 800L
        while (true) {
            val t = ((SystemClock.elapsedRealtime() - start).toFloat() / durationMs).coerceIn(0f, 1f)
            val eased = 1f - (1f - t) * (1f - t)
            developingProgress = eased
            if (t >= 1f) break
            kotlinx.coroutines.delay(16)
        }
    }
    val density = LocalDensity.current
    val context = LocalContext.current
    val caveat = remember { StoryFontRegistry.typeface(context, "Caveat-Bold") }
    val contentWidth = max(88f, canvasWidthPx)
    val contentHeight = max(96f, canvasHeightPx)
    val imageAreaHeight = contentHeight * 0.76f
    val captionAreaHeight = max(24f, contentHeight - imageAreaHeight)
    val inset = when (frameStyle) {
        HiddenLayerImageFrameStyle.CLASSIC -> 10f
        HiddenLayerImageFrameStyle.CLEAN -> 8f
        HiddenLayerImageFrameStyle.VINTAGE -> 12f
    }
    val frameColor = when (frameStyle) {
        HiddenLayerImageFrameStyle.CLASSIC -> Color.White
        HiddenLayerImageFrameStyle.CLEAN -> Color.White.copy(0.94f)
        HiddenLayerImageFrameStyle.VINTAGE -> Color(0.97f, 0.92f, 0.82f)
    }
    val imageBackground = if (frameStyle == HiddenLayerImageFrameStyle.VINTAGE) {
        Color(0.22f, 0.18f, 0.14f)
    } else {
        Color.Black
    }
    val outerCorner = when (frameStyle) {
        HiddenLayerImageFrameStyle.CLASSIC -> 0.dp
        HiddenLayerImageFrameStyle.CLEAN -> 18.dp
        HiddenLayerImageFrameStyle.VINTAGE -> 6.dp
    }
    val style = captionStyle ?: HiddenLayerTextStyle.HANDWRITTEN
    val captionFontSize = when (style) {
        HiddenLayerTextStyle.CLEAN -> 14.sp
        HiddenLayerTextStyle.MONO -> 13.sp
        else -> 17.sp
    }
    val captionFamily = when (style) {
        HiddenLayerTextStyle.CLEAN -> FontFamily.Default
        HiddenLayerTextStyle.MONO -> FontFamily.Monospace
        else -> FontFamily(caveat)
    }
    val captionColor = if (frameStyle == HiddenLayerImageFrameStyle.VINTAGE) {
        Color.Black.copy(0.78f)
    } else {
        Color.Black.copy(0.85f)
    }
    val captionRotation = if (style == HiddenLayerTextStyle.HANDWRITTEN) -1f else 0f
    val captionOffsetY = if (frameStyle == HiddenLayerImageFrameStyle.CLEAN) -1.dp else (-2).dp

    Column(
        Modifier
            .width(with(density) { (contentWidth + inset * 2).toDp() })
            .clip(RoundedCornerShape(outerCorner))
            .background(frameColor),
    ) {
        Box(
            Modifier
                .padding(with(density) { inset.toDp() })
                .width(with(density) { contentWidth.toDp() })
                .height(with(density) { imageAreaHeight.toDp() })
                .background(imageBackground)
                .clip(RoundedCornerShape(0)),
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = imageScale.toFloat()
                        scaleY = imageScale.toFloat()
                        translationX = imageOffsetX.toFloat()
                        translationY = imageOffsetY.toFloat()
                        // ≡ brightness/contrast developing: overlay blanco que se desvanece
                        alpha = 0.4f + 0.6f * developingProgress
                    },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(0.6f * (1f - developingProgress))),
            )
        }
        Box(
            Modifier
                .width(with(density) { (contentWidth + inset * 2).toDp() })
                .height(with(density) { captionAreaHeight.toDp() })
                .background(frameColor),
            contentAlignment = Alignment.Center,
        ) {
            if (!caption.isNullOrEmpty()) {
                Text(
                    caption,
                    color = captionColor,
                    fontSize = captionFontSize,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = captionFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .offset(y = captionOffsetY)
                        .graphicsLayer {
                            rotationZ = captionRotation
                            alpha = developingProgress
                        },
                )
            }
        }
    }
}

@Composable
private fun EditorGhostRail(imageRect: Rect) {
    val density = LocalDensity.current
    val railWidth = min(max(imageRect.width * 0.52f, 164f), 214f)
    val railHeight = 56f
    val railX = imageRect.right - 16f - (railWidth / 2f)
    val railY = imageRect.bottom - 16f - (railHeight / 2f)
    Row(
        Modifier
            .offset {
                IntOffset((railX - railWidth / 2f).roundToInt(), (railY - railHeight / 2f).roundToInt())
            }
            .size(with(density) { railWidth.toDp() }, with(density) { railHeight.toDp() })
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(0.12f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(4) { index ->
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(if (index == 0) 0.26f else 0.14f))
                    .border(1.dp, Color.White.copy(if (index == 0) 0.24f else 0.10f), CircleShape),
            )
        }
    }
}

/** Port de `audioControls(for:)` (HiddenLayersEditorView.swift L891–987). */
@Composable
private fun AudioControls(
    layer: HiddenLayerDraft,
    primaryText: Color,
    tertiaryText: Color,
    subtleSurface: Color,
    previewStroke: Color,
    audioRecorder: HiddenLayerAudioRecorder,
    isPreviewPlaying: Boolean,
    onClear: () -> Unit,
    onRestart: () -> Unit,
    onTogglePreview: () -> Unit,
    onRecordToggle: () -> Unit,
) {
    val recordScale by animateFloatAsState(
        targetValue = if (audioRecorder.isRecording) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "hiddenLayerRecordScale",
    )
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (layer.localAudioUri != null && !audioRecorder.isRecording) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircularAudioActionButton(
                    icon = Icons.Filled.Delete,
                    primaryText = primaryText,
                    onClick = onClear,
                )
                Spacer(Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        formattedDetailedDuration(layer.duration ?: 0.0),
                        color = primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        stringResource(R.string.hidden_layers_audio_ready),
                        color = tertiaryText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(subtleSurface)
                            .border(1.dp, previewStroke, RoundedCornerShape(50))
                            .clickable(onClick = onTogglePreview)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            if (isPreviewPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null,
                            tint = primaryText,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            stringResource(R.string.hidden_layers_audio_preview),
                            color = primaryText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                CircularAudioActionButton(
                    icon = Icons.Filled.Refresh,
                    primaryText = primaryText,
                    onClick = onRestart,
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(58.dp)
                        .scale(recordScale)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable(onClick = onRecordToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    if (audioRecorder.isRecording) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Red),
                        )
                    } else {
                        AttachmentIconView(
                            icon = AttachmentIcon.VOICE,
                            preset = AttachmentIconPreset.VOICE_EDITOR,
                            tintColor = Color.Red,
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        formattedDetailedDuration(
                            if (audioRecorder.isRecording) audioRecorder.elapsedTime else 0.0,
                        ),
                        color = if (audioRecorder.isRecording) Color.Red else primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        stringResource(
                            if (audioRecorder.isRecording) {
                                R.string.hidden_layers_audio_recording
                            } else {
                                R.string.hidden_layers_audio_tap_to_record
                            },
                        ),
                        color = tertiaryText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/** Port de `circularAudioActionButton` (L989–997). */
@Composable
private fun CircularAudioActionButton(
    icon: ImageVector,
    primaryText: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .momentsChromeGlass(CircleShape, interactive = true)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = primaryText, modifier = Modifier.size(17.dp))
    }
}

/**
 * Port de `HiddenLayerAudioRecorder` (L1666–1724).
 * AAC/m4a, 44.1 kHz, mono, máx. 15 s.
 */
private data class HiddenLayerAudioRecording(val uri: Uri, val duration: Double)

private class HiddenLayerAudioRecorder(private val context: Context) {
    var isRecording by mutableStateOf(false)
        private set
    var elapsedTime by mutableDoubleStateOf(0.0)
        private set

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtElapsed = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isRecording) return
            elapsedTime = min(
                (SystemClock.elapsedRealtime() - startedAtElapsed) / 1000.0,
                MaxHiddenLayerAudioSeconds,
            )
            handler.postDelayed(this, 50)
        }
    }

    fun startRecording() {
        if (isRecording) return
        val target = File(context.cacheDir, "hidden_layer_audio_${UUID.randomUUID()}.m4a")
        val started = runCatching {
            val next = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(128_000)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            outputFile = target
            recorder = next
            startedAtElapsed = SystemClock.elapsedRealtime()
            elapsedTime = 0.0
            isRecording = true
            handler.removeCallbacks(tickRunnable)
            handler.post(tickRunnable)
        }.isSuccess
        if (!started) {
            target.delete()
            outputFile = null
            recorder = null
            isRecording = false
            HapticManager.shared.warning()
        }
    }

    fun stopRecording(): HiddenLayerAudioRecording? {
        val active = recorder ?: return null
        val file = outputFile
        handler.removeCallbacks(tickRunnable)
        runCatching { active.stop() }
        active.release()
        recorder = null
        isRecording = false
        val duration = min(
            (SystemClock.elapsedRealtime() - startedAtElapsed) / 1000.0,
            MaxHiddenLayerAudioSeconds,
        )
        elapsedTime = duration
        outputFile = null
        if (file == null || !file.exists()) return null
        return HiddenLayerAudioRecording(Uri.fromFile(file), duration)
    }

    fun release() {
        handler.removeCallbacks(tickRunnable)
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
        isRecording = false
    }

    private companion object {
        const val MaxHiddenLayerAudioSeconds = 15.0
    }
}

/** Port de `TimeInterval.formattedDetailedDuration` (L2165–2169). */
private fun formattedDetailedDuration(seconds: Double): String {
    val whole = seconds.toInt().coerceAtLeast(0)
    val tenths = ((seconds - whole) * 10).toInt().coerceAtLeast(0)
    return String.format("00:%02d.%d", whole, tenths)
}

@Composable
private fun TypeSwitcherPill(
    activeType: MomentHiddenLayer.LayerType,
    primaryText: Color,
    secondaryText: Color,
    previewStroke: Color,
    isDark: Boolean,
    transientOffset: Float,
    onTransientOffset: (Float) -> Unit,
    onSelect: (MomentHiddenLayer.LayerType) -> Unit,
) {
    val options = MomentHiddenLayer.LayerType.entries
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
            .border(0.75.dp, previewStroke, RoundedCornerShape(50))
            .pointerInput(activeType) {
                val totalWidth = size.width.toFloat()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var drag = 0f
                    onTransientOffset(0f)
                    val gotDrag = drag(down.id) { change ->
                        val dx = change.positionChange().x
                        drag += dx
                        change.consume()
                        onTransientOffset(
                            constrainedSwitcherTranslation(drag, totalWidth, activeType),
                        )
                    }
                    settleSwitcherSelection(
                        translation = if (gotDrag) drag else 0f,
                        locationX = down.position.x,
                        width = totalWidth,
                        activeType = activeType,
                        onSelect = onSelect,
                        onTransientOffset = onTransientOffset,
                    )
                }
            },
    ) {
        val totalWidth = constraints.maxWidth.toFloat()
        val segmentWidth = max((totalWidth - 6f) / options.size, 1f)
        val baseOffset = switcherBaseOffset(totalWidth, activeType)
        val pillOffset = baseOffset + transientOffset
        Box(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset(pillOffset.roundToInt(), 0) }
                .width(with(LocalDensity.current) { segmentWidth.toDp() })
                .height(34.dp)
                .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                .background(Color.White.copy(if (isDark) 0.055f else 0.035f)),
        )
        Row(Modifier.fillMaxSize().padding(horizontal = 3.dp)) {
            options.forEach { type ->
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        layerIcon(type),
                        null,
                        tint = if (type == activeType) primaryText else secondaryText,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        layerPillTitle(type),
                        color = if (type == activeType) primaryText else secondaryText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniSheetHeaderIconButton(
    icon: ImageVector,
    primaryText: Color,
    fill: Color,
    stroke: Color,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = primaryText, modifier = Modifier.size(16.dp))
    }
    @Suppress("UNUSED_VARIABLE")
    val keep = isActive
}

/** Port de `HiddenLayerScheduleSheet` (L1726–1780). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenLayerScheduleSheet(
    date: Date,
    onDateChange: (Date) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black
    val secondaryText = if (isDark) Color.White.copy(0.64f) else Color.Black.copy(0.55f)
    val initialCal = remember(date) { Calendar.getInstance().apply { time = date } }
    val todayStartMs = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = date.time.coerceAtLeast(todayStartMs),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis >= todayStartMs - 12 * 60 * 60 * 1000L
        },
    )
    val timeState = rememberTimePickerState(
        initialHour = initialCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialCal.get(Calendar.MINUTE),
        is24Hour = true,
    )

    fun emitMerged() {
        val dayMs = dateState.selectedDateMillis ?: date.time
        val utcDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = dayMs
        }
        val local = Calendar.getInstance().apply {
            set(Calendar.YEAR, utcDay.get(Calendar.YEAR))
            set(Calendar.MONTH, utcDay.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcDay.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, timeState.hour)
            set(Calendar.MINUTE, timeState.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // ≡ Date()... en iOS: no permitir pasado
        if (local.time.before(Date())) {
            local.time = Date(System.currentTimeMillis() + 60_000L)
        }
        onDateChange(local.time)
    }

    LaunchedEffect(dateState.selectedDateMillis, timeState.hour, timeState.minute) {
        emitMerged()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .width(36.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(secondaryText.copy(0.25f)),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.hidden_layers_unlock_sheet_title),
                color = primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                stringResource(R.string.hidden_layers_unlock_sheet_subtitle),
                color = secondaryText,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        DatePicker(
            state = dateState,
            title = null,
            headline = null,
            showModeToggle = false,
        )
        TimePicker(state = timeState)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.common_cancel),
                color = primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .clickable(onClick = onCancel)
                    .padding(vertical = 12.dp),
            )
            Text(
                stringResource(R.string.common_done),
                color = primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .clickable {
                        emitMerged()
                        onApply()
                    }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun AvailabilityControls(
    layer: HiddenLayerDraft,
    primaryText: Color,
    secondaryText: Color,
    previewStroke: Color,
    unlockMenuExpanded: Boolean,
    onUnlockMenuExpanded: (Boolean) -> Unit,
    opensMenuExpanded: Boolean,
    onOpensMenuExpanded: (Boolean) -> Unit,
    onImmediate: () -> Unit,
    onScheduled: () -> Unit,
    onTonight: () -> Unit,
    onTomorrow: () -> Unit,
    onPickDate: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            CompactSelectionChip(
                title = stringResource(R.string.hidden_layers_unlock_title),
                value = unlockModeTitle(layer.unlockMode),
                primaryText = primaryText,
                secondaryText = secondaryText,
                previewStroke = previewStroke,
                onClick = { onUnlockMenuExpanded(true) },
            )
            DropdownMenu(unlockMenuExpanded, onDismissRequest = { onUnlockMenuExpanded(false) }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hidden_layers_unlock_now)) },
                    onClick = {
                        onImmediate()
                        onUnlockMenuExpanded(false)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hidden_layers_unlock_scheduled)) },
                    onClick = {
                        onScheduled()
                        onUnlockMenuExpanded(false)
                    },
                )
            }
        }
        if (layer.unlockMode == MomentHiddenLayer.UnlockMode.SCHEDULED) {
            Box {
                CompactSelectionChip(
                    title = stringResource(R.string.hidden_layers_unlock_opens),
                    value = formattedUnlockDate(layer.unlockAt),
                    primaryText = primaryText,
                    secondaryText = secondaryText,
                    previewStroke = previewStroke,
                    onClick = { onOpensMenuExpanded(true) },
                )
                DropdownMenu(opensMenuExpanded, onDismissRequest = { onOpensMenuExpanded(false) }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hidden_layers_unlock_tonight)) },
                        onClick = {
                            onTonight()
                            onOpensMenuExpanded(false)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hidden_layers_unlock_tomorrow)) },
                        onClick = {
                            onTomorrow()
                            onOpensMenuExpanded(false)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hidden_layers_unlock_pick_date)) },
                        onClick = {
                            onPickDate()
                            onOpensMenuExpanded(false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSelectionChip(
    title: String,
    value: String,
    primaryText: Color,
    secondaryText: Color,
    previewStroke: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(42.dp)
            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
            .border(1.dp, previewStroke, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            Text(title, color = secondaryText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Icon(Icons.Filled.UnfoldMore, null, tint = secondaryText, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun MiniCircleButton(
    icon: ImageVector,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun emptyStateTitle(type: MomentHiddenLayer.LayerType): String = when (type) {
    MomentHiddenLayer.LayerType.TEXT -> stringResource(R.string.hidden_layers_empty_text_title)
    MomentHiddenLayer.LayerType.AUDIO -> stringResource(R.string.hidden_layers_empty_audio_title)
    MomentHiddenLayer.LayerType.IMAGE -> stringResource(R.string.hidden_layers_empty_image_title)
}

@Composable
private fun emptyStateSubtitle(type: MomentHiddenLayer.LayerType): String = when (type) {
    MomentHiddenLayer.LayerType.TEXT -> stringResource(R.string.hidden_layers_empty_text_subtitle)
    MomentHiddenLayer.LayerType.AUDIO -> stringResource(R.string.hidden_layers_empty_audio_subtitle)
    MomentHiddenLayer.LayerType.IMAGE -> stringResource(R.string.hidden_layers_empty_image_subtitle)
}

@Composable
private fun addActionTitle(type: MomentHiddenLayer.LayerType): String = when (type) {
    MomentHiddenLayer.LayerType.TEXT -> stringResource(R.string.hidden_layers_add_text_cta)
    MomentHiddenLayer.LayerType.AUDIO -> stringResource(R.string.hidden_layers_add_audio_cta)
    MomentHiddenLayer.LayerType.IMAGE -> stringResource(R.string.hidden_layers_add_image_cta)
}

@Composable
private fun layerTitle(layer: HiddenLayerDraft): String = when (layer.type) {
    MomentHiddenLayer.LayerType.TEXT -> stringResource(R.string.hidden_layers_type_text)
    MomentHiddenLayer.LayerType.AUDIO -> stringResource(R.string.hidden_layers_type_audio)
    MomentHiddenLayer.LayerType.IMAGE -> stringResource(R.string.hidden_layers_type_image)
}

@Composable
private fun layerPillTitle(type: MomentHiddenLayer.LayerType): String = when (type) {
    MomentHiddenLayer.LayerType.TEXT -> stringResource(R.string.hidden_layers_add_text)
    MomentHiddenLayer.LayerType.AUDIO -> stringResource(R.string.hidden_layers_add_audio)
    MomentHiddenLayer.LayerType.IMAGE -> stringResource(R.string.hidden_layers_add_image)
}

@Composable
private fun unlockModeTitle(mode: MomentHiddenLayer.UnlockMode): String = when (mode) {
    MomentHiddenLayer.UnlockMode.IMMEDIATE -> stringResource(R.string.hidden_layers_unlock_now)
    MomentHiddenLayer.UnlockMode.SCHEDULED -> stringResource(R.string.hidden_layers_unlock_scheduled)
}

@Composable
private fun formattedUnlockDate(date: Date?): String {
    if (date == null) return stringResource(R.string.hidden_layers_unlock_pick_date)
    val cal = Calendar.getInstance()
    val today = Calendar.getInstance()
    cal.time = date
    val time = MomentsFormat.smartDate(from = date, context = MomentsFormat.DateContext.TIME_ONLY)
    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) ->
            stringResource(R.string.hidden_layers_unlock_today_time, time)
        run {
            val tom = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            cal.get(Calendar.YEAR) == tom.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == tom.get(Calendar.DAY_OF_YEAR)
        } -> stringResource(R.string.hidden_layers_unlock_tomorrow_time, time)
        else -> MomentsFormat.smartDate(from = date, context = MomentsFormat.DateContext.MEDIUM_DATE_TIME)
    }
}

private fun layerIcon(type: MomentHiddenLayer.LayerType): ImageVector = when (type) {
    MomentHiddenLayer.LayerType.TEXT -> Icons.Filled.TextFields
    MomentHiddenLayer.LayerType.AUDIO -> Icons.Filled.GraphicEq
    MomentHiddenLayer.LayerType.IMAGE -> Icons.Filled.Image
}

private val HiddenLayerTextStyle.displayName: String
    get() = when (this) {
        HiddenLayerTextStyle.CLEAN -> "Clean"
        HiddenLayerTextStyle.SERIF -> "Serif"
        HiddenLayerTextStyle.HANDWRITTEN -> "Hand"
        HiddenLayerTextStyle.MONO -> "Mono"
        HiddenLayerTextStyle.BUBBLE -> "Bubble"
        HiddenLayerTextStyle.EDITORIAL -> "Edit"
    }

private val HiddenLayerPresentationStyle.displayName: String
    get() = when (this) {
        HiddenLayerPresentationStyle.GLASS_CARD -> "Glass"
        HiddenLayerPresentationStyle.CAPTION_PILL -> "Pill"
        HiddenLayerPresentationStyle.PAPER_NOTE -> "Paper"
        HiddenLayerPresentationStyle.MARKER_LABEL -> "Marker"
        HiddenLayerPresentationStyle.FLOATING_QUOTE -> "Quote"
        HiddenLayerPresentationStyle.MINIMAL_TEXT -> "Minimal"
    }

private val HiddenLayerImageFrameStyle.displayName: String
    get() = when (this) {
        HiddenLayerImageFrameStyle.CLASSIC -> "Classic"
        HiddenLayerImageFrameStyle.CLEAN -> "Clean"
        HiddenLayerImageFrameStyle.VINTAGE -> "Vintage"
    }

private fun nextLayerOrigin(count: Int): Pair<Double, Double> {
    val presets = listOf(0.30 to 0.26, 0.50 to 0.56, 0.72 to 0.28)
    return presets[min(count, presets.lastIndex)]
}

private fun addTextLayer(
    layers: List<HiddenLayerDraft>,
    onLayersChange: (List<HiddenLayerDraft>) -> Unit,
    density: Density,
    caveatTypeface: Typeface?,
    onSelected: (String) -> Unit,
    onDockType: (MomentHiddenLayer.LayerType) -> Unit,
) {
    if (layers.size >= MaxHiddenLayers) return
    val origin = nextLayerOrigin(layers.size)
    val textWidth = 0.34
    var layer = HiddenLayerDraft(
        type = MomentHiddenLayer.LayerType.TEXT,
        anchorX = origin.first,
        anchorY = origin.second,
        width = textWidth,
        height = textWidth * HiddenLayerLayout.textAspectRatio,
        zIndex = layers.size,
        text = "",
    )
    layer = resizeTextLayerToFitContent(layer, density, caveatTypeface)
    onLayersChange(layers + layer)
    onSelected(layer.id)
    onDockType(MomentHiddenLayer.LayerType.TEXT)
}

private fun addAudioLayer(
    layers: List<HiddenLayerDraft>,
    onLayersChange: (List<HiddenLayerDraft>) -> Unit,
    onSelected: (String) -> Unit,
    onDockType: (MomentHiddenLayer.LayerType) -> Unit,
) {
    if (layers.size >= MaxHiddenLayers) return
    val origin = nextLayerOrigin(layers.size)
    val layer = HiddenLayerDraft(
        type = MomentHiddenLayer.LayerType.AUDIO,
        anchorX = origin.first,
        anchorY = origin.second,
        width = 0.18,
        height = 0.18,
        zIndex = layers.size,
        presentationStyle = HiddenLayerPresentationStyle.CAPTION_PILL,
    )
    onLayersChange(layers + layer)
    onSelected(layer.id)
    onDockType(MomentHiddenLayer.LayerType.AUDIO)
}

private fun addImageLayer(
    image: Bitmap,
    layers: List<HiddenLayerDraft>,
    onLayersChange: (List<HiddenLayerDraft>) -> Unit,
    selectedLayerId: String?,
    onSelectedLayerId: (String) -> Unit,
    onSelectedDockType: (MomentHiddenLayer.LayerType) -> Unit,
) {
    val selectedIndex = layers.indexOfFirst { it.id == selectedLayerId }
    if (selectedIndex >= 0 && layers[selectedIndex].type == MomentHiddenLayer.LayerType.IMAGE) {
        onLayersChange(
            layers.mapIndexed { i, item ->
                if (i == selectedIndex) item.copy(localImage = image) else item
            },
        )
        onSelectedDockType(MomentHiddenLayer.LayerType.IMAGE)
        return
    }
    if (layers.size >= MaxHiddenLayers) return
    val origin = nextLayerOrigin(layers.size)
    val imageWidth = 0.24
    val imageHeight = imageWidth * HiddenLayerLayout.imageAspectRatio
    val layer = HiddenLayerDraft(
        type = MomentHiddenLayer.LayerType.IMAGE,
        anchorX = origin.first,
        anchorY = origin.second,
        width = imageWidth,
        height = imageHeight.toDouble(),
        zIndex = layers.size,
        localImage = image,
        presentationStyle = HiddenLayerPresentationStyle.PAPER_NOTE,
    )
    onLayersChange(layers + layer)
    onSelectedLayerId(layer.id)
    onSelectedDockType(MomentHiddenLayer.LayerType.IMAGE)
}

private fun resizeTextLayerToFitContent(
    layer: HiddenLayerDraft,
    density: Density,
    caveatTypeface: Typeface? = null,
): HiddenLayerDraft {
    if (layer.type != MomentHiddenLayer.LayerType.TEXT) return layer
    val placeholder = "Escribe el secreto..." // medido como iOS; UI usa stringResource
    val text = layer.text.ifEmpty { placeholder }
    val measured = hiddenLayerTextCardBaseSizePx(text, layer.textStyle, density, caveatTypeface)
    val measuredWidthDp = measured.width / density.density
    val referenceWidth = 220f
    val widthRatio = min(0.62, max(0.16, 0.34 * (measuredWidthDp / referenceWidth))).toDouble()
    val heightRatio = min(0.32, max(0.10, widthRatio * HiddenLayerLayout.textAspectRatio))
    return layer.copy(width = widthRatio, height = heightRatio)
}

/** Port de `hiddenLayerTextCardBaseSize` (L1630–1646). */
private fun hiddenLayerTextCardBaseSizePx(
    text: String,
    textStyle: HiddenLayerTextStyle,
    density: Density,
    caveatTypeface: Typeface?,
): Size {
    val clamped = text.ifEmpty { "Escribe el secreto..." }
    val fontSizeSp = when (textStyle) {
        HiddenLayerTextStyle.CLEAN -> 17f
        HiddenLayerTextStyle.SERIF -> 18f
        HiddenLayerTextStyle.HANDWRITTEN -> 23f
        HiddenLayerTextStyle.MONO -> 16f
        HiddenLayerTextStyle.BUBBLE -> 18f
        HiddenLayerTextStyle.EDITORIAL -> 20f
    }
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        textSize = fontSizeSp * density.density
        typeface = when (textStyle) {
            HiddenLayerTextStyle.MONO -> Typeface.MONOSPACE
            HiddenLayerTextStyle.HANDWRITTEN -> caveatTypeface ?: Typeface.DEFAULT
            HiddenLayerTextStyle.BUBBLE, HiddenLayerTextStyle.EDITORIAL ->
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            else -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val horizontalPadding = 32f * density.density
    val verticalPadding = 24f * density.density
    val minWidth = 132f * density.density
    val maxWidth = 248f * density.density
    val minHeight = 74f * density.density
    val measuredWidth = kotlin.math.ceil(paint.measureText(clamped).toDouble()).toFloat()
    val width = min(max(measuredWidth + horizontalPadding, minWidth), maxWidth)
    val fontMetrics = paint.fontMetrics
    val lineHeight = fontMetrics.descent - fontMetrics.ascent
    val height = max(minHeight, kotlin.math.ceil((lineHeight + verticalPadding).toDouble()).toFloat())
    return Size(width, height)
}

private fun editorPreviewRect(containerSize: Size, previewHeight: Float): Rect {
    if (containerSize.width <= 0f || containerSize.height <= 0f) {
        return Rect(0f, 0f, containerSize.width, containerSize.height)
    }
    val availableWidth = containerSize.width
    val availableHeight = previewHeight
    if (availableWidth <= 0f || availableHeight <= 0f) {
        return Rect(0f, 0f, containerSize.width, containerSize.height)
    }
    return Rect(
        left = 0f,
        top = (containerSize.height - availableHeight) / 2f,
        right = availableWidth,
        bottom = (containerSize.height - availableHeight) / 2f + availableHeight,
    )
}

private fun previewCanvasHeight(
    availableWidth: Float,
    displayedPostAspectRatio: Float,
    screenWidthDp: Float,
    screenHeightPx: Float,
    density: Float,
): Float {
    if (availableWidth <= 0f) return 340f
    val ratio = if (displayedPostAspectRatio > 0f && displayedPostAspectRatio.isFinite()) {
        displayedPostAspectRatio
    } else {
        1f
    }
    val canonicalFeedWidth = FeedMomentCardLayout.mediaContentWidth(screenWidthDp) * density
    val canonicalFeedHeight = feedCardHeight(canonicalFeedWidth, ratio, screenHeightPx, density)
    if (canonicalFeedWidth <= 0f || canonicalFeedHeight <= 0f) return 340f
    val scale = min(availableWidth / canonicalFeedWidth, 1f)
    return canonicalFeedHeight * scale
}

private fun feedCardHeight(width: Float, ratio: Float, screenHeightPx: Float, density: Float): Float {
    if (width <= 0f) return 300f
    val safeRatio = if (ratio > 0f && ratio.isFinite()) ratio else 1f
    val idealHeight = width / safeRatio
    val feedHeaderHeight = 88f * density
    val feedSelectorHeight = 35f * density
    val tabbarHeight = 50f * density
    val availableHeight = screenHeightPx - feedHeaderHeight - feedSelectorHeight - tabbarHeight - 60f * density
    val maxAllowed = availableHeight * 0.95f
    return max(max(min(idealHeight, maxAllowed), 150f * density), 200f * density)
}

private fun switcherBaseOffset(totalWidth: Float, activeType: MomentHiddenLayer.LayerType): Float {
    val options = MomentHiddenLayer.LayerType.entries
    val segmentWidth = (totalWidth - 6f) / options.size
    val start = -((options.size - 1) * segmentWidth) / 2f
    val currentIndex = options.indexOf(activeType).coerceAtLeast(0).toFloat()
    return start + currentIndex * segmentWidth
}

private fun constrainedSwitcherTranslation(
    translation: Float,
    width: Float,
    activeType: MomentHiddenLayer.LayerType,
): Float {
    val options = MomentHiddenLayer.LayerType.entries
    val segmentWidth = (width - 6f) / options.size
    val minOffset = -((options.size - 1) * segmentWidth) / 2f
    val maxOffset = ((options.size - 1) * segmentWidth) / 2f
    val base = switcherBaseOffset(width, activeType)
    val proposed = base + translation
    val clamped = min(max(proposed, minOffset), maxOffset)
    return clamped - base
}

private fun settleSwitcherSelection(
    translation: Float,
    locationX: Float?,
    width: Float,
    activeType: MomentHiddenLayer.LayerType,
    onSelect: (MomentHiddenLayer.LayerType) -> Unit,
    onTransientOffset: (Float) -> Unit,
) {
    val options = MomentHiddenLayer.LayerType.entries
    val segmentWidth = (width - 6f) / options.size
    val proposedOffset = switcherBaseOffset(width, activeType) + translation
    val start = -((options.size - 1) * segmentWidth) / 2f
    val fractionalIndex = (proposedOffset - start) / segmentWidth
    val threshold = min(segmentWidth * 0.28f, 36f)
    val currentIndex = options.indexOf(activeType).coerceAtLeast(0)
    val targetIndex = when {
        abs(translation) > threshold && abs(translation) < segmentWidth * 0.5f -> {
            val direction = if (translation > 0f) 1 else -1
            (currentIndex + direction).coerceIn(0, options.lastIndex)
        }
        abs(translation) < 5f && locationX != null -> {
            (locationX / segmentWidth).toInt().coerceIn(0, options.lastIndex)
        }
        else -> fractionalIndex.roundToInt().coerceIn(0, options.lastIndex)
    }
    val targetType = options[targetIndex]
    if (targetType != activeType) HapticManager.shared.selection()
    onSelect(targetType)
    onTransientOffset(0f)
}

private fun tonightUnlockDate(): Date {
    val calendar = Calendar.getInstance()
    val now = Date()
    calendar.time = now
    calendar.set(Calendar.HOUR_OF_DAY, 22)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val todayAtTen = calendar.time
    return if (todayAtTen.after(now)) todayAtTen else tomorrowUnlockDate()
}

private fun tomorrowUnlockDate(): Date {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 22)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.time
}

/** Port de drag en `hiddenLayerHotspot` (L754–785) — pan deltas ≡ location − dragOffset. */
private fun dragHotspotLayer(
    layer: HiddenLayerDraft,
    imageRect: Rect,
    pan: Offset,
): HiddenLayerDraft {
    val centerX = imageRect.left + imageRect.width * layer.anchorX.toFloat()
    val centerY = imageRect.top + imageRect.height * layer.anchorY.toFloat()
    val proposedX = centerX + pan.x
    val proposedY = centerY + pan.y
    val normalizedX = (proposedX - imageRect.left) / max(imageRect.width, 1f)
    val normalizedY = (proposedY - imageRect.top) / max(imageRect.height, 1f)
    val halfWidthRatio = min(0.5, max(0.0, layer.width / 2))
    val halfHeightRatio = if (layer.type == MomentHiddenLayer.LayerType.IMAGE) {
        val imageHeightRatio =
            (layer.width * HiddenLayerLayout.imageAspectRatio * imageRect.width) /
                max(imageRect.height.toDouble(), 1.0)
        min(0.5, max(0.0, imageHeightRatio / 2))
    } else {
        min(0.5, max(0.0, layer.height / 2))
    }
    return layer.copy(
        anchorX = normalizedX.toDouble().coerceIn(halfWidthRatio, 1.0 - halfWidthRatio),
        anchorY = normalizedY.toDouble().coerceIn(halfHeightRatio, 1.0 - halfHeightRatio),
    )
}

/** Port de MagnifyGesture en `hiddenLayerHotspot` (L793–830). */
private fun magnifyHotspotLayer(
    layer: HiddenLayerDraft,
    baseWidth: Double,
    baseHeight: Double,
    magnification: Float,
): HiddenLayerDraft {
    val ratio = magnification.toDouble().coerceIn(0.7, 2.2)
    val aspect: Double
    val minWidth: Double
    val maxWidth: Double
    when (layer.type) {
        MomentHiddenLayer.LayerType.IMAGE -> {
            aspect = HiddenLayerLayout.imageAspectRatio.toDouble()
            minWidth = max(0.12, 0.10 / aspect)
            maxWidth = min(0.55, 0.42 / aspect)
        }
        MomentHiddenLayer.LayerType.TEXT -> {
            aspect = HiddenLayerLayout.textAspectRatio.toDouble()
            minWidth = 0.16
            maxWidth = 0.62
        }
        MomentHiddenLayer.LayerType.AUDIO -> {
            aspect = max(baseHeight / max(baseWidth, 0.001), 0.25)
            minWidth = 0.12
            maxWidth = 0.55
        }
    }
    val newWidth = (baseWidth * ratio).coerceIn(minWidth, maxWidth)
    val newHeight = if (layer.type == MomentHiddenLayer.LayerType.IMAGE) {
        newWidth * aspect
    } else {
        (newWidth * aspect).coerceIn(0.10, 0.42)
    }
    val halfWidthRatio = min(0.5, max(0.0, newWidth / 2))
    val halfHeightRatio = min(0.5, max(0.0, newHeight / 2))
    return layer.copy(
        width = newWidth,
        height = newHeight,
        anchorX = layer.anchorX.coerceIn(halfWidthRatio, 1.0 - halfWidthRatio),
        anchorY = layer.anchorY.coerceIn(halfHeightRatio, 1.0 - halfHeightRatio),
    )
}
