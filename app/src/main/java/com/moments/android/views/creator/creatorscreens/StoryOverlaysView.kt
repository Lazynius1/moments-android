package com.moments.android.views.creator.creatorscreens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.components.StoryAlignmentGuideBroker
import com.moments.android.views.creator.components.StoryEditorChromeColor
import com.moments.android.views.creator.components.StoryMediaTransformLimits
import com.moments.android.views.creator.components.StoryTextOverlayDraft
import com.moments.android.views.creator.components.storyAlignAndKeepVisible
import com.moments.android.views.creator.components.storyClampedOverlayCenterOffset
import com.moments.android.views.creator.components.storyKeepOverlayVisibleCenter
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Estado compartido por la papelera temporal de `StoryOverlaysView.swift`. */
data class StoryOverlayDragState(
    val isDragging: Boolean = false,
    val isOverTrash: Boolean = false,
)

/** Contrato de feedback de `StoryOverlayToast` en Swift. */
sealed interface StoryOverlayToast {
    data class UserNotFound(val username: String) : StoryOverlayToast
    data class Hashtag(val hashtag: String) : StoryOverlayToast
    data class Location(val location: String) : StoryOverlayToast
    data object Poll : StoryOverlayToast
    data object Question : StoryOverlayToast
    data object QuestionResponse : StoryOverlayToast
}

/** ≡ `StoryOverlayToast.message` — Localizable `storyOverlay.toast.*`. */
@Composable
private fun storyOverlayToastMessage(toast: StoryOverlayToast): String = when (toast) {
    is StoryOverlayToast.UserNotFound ->
        stringResource(R.string.story_overlay_toast_user_not_found, toast.username)
    is StoryOverlayToast.Hashtag ->
        stringResource(R.string.story_overlay_toast_hashtag, toast.hashtag)
    is StoryOverlayToast.Location ->
        stringResource(R.string.story_overlay_toast_location, toast.location)
    StoryOverlayToast.Poll -> stringResource(R.string.story_overlay_toast_poll)
    StoryOverlayToast.Question -> stringResource(R.string.story_overlay_toast_question)
    StoryOverlayToast.QuestionResponse -> stringResource(R.string.story_overlay_toast_question_response)
}

/** Banner temporal de 2.5 s, el mismo ciclo que `presentToast` en Swift. */
@Composable
fun StoryOverlayToastHost(
    toast: StoryOverlayToast?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_500)
            onDismiss()
        }
    }
    if (toast != null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                storyOverlayToastMessage(toast),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .background(Color.Black.copy(.58f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** Radio y centro de la papelera ≡ iOS `isPointOverTrash` (44pt / 60pt), en px densos. */
fun isPointOverStoryOverlayTrash(
    x: Float,
    y: Float,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    density: Density,
): Boolean {
    val bottomOffsetPx = with(density) { 44.dp.toPx() } // padding 20 + mitad icono 48
    val radiusPx = with(density) { 60.dp.toPx() }
    return hypot(x - canvasWidthPx / 2f, y - (canvasHeightPx - bottomOffsetPx)) < radiusPx
}

/**
 * ≡ papelera de `StoryOverlaysView` — solo visible al arrastrar;
 * scale 1.28 + spring cuando `isOverTrash` (MotionPolicy.Spring.press).
 *
 * Sin hit-target a pantalla completa: solo el icono recibe touches.
 */
@Composable
fun StoryOverlayTrashZone(
    state: StoryOverlayDragState,
    modifier: Modifier = Modifier,
) {
    val visible by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (state.isDragging) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "trashVisible",
    )
    val hotScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (state.isOverTrash) 1.28f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.7f,
            stiffness = 500f,
        ),
        label = "trashHotScale",
    )
    if (visible <= 0.01f && !state.isDragging) return
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = visible },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Icon(
            imageVector = if (state.isOverTrash) Icons.Filled.Delete else Icons.Outlined.Delete,
            contentDescription = null,
            tint = if (state.isOverTrash) Color(0xFFFF3B30) else Color.White,
            modifier = Modifier
                .padding(bottom = 20.dp)
                .size(48.dp)
                .graphicsLayer {
                    scaleX = hotScale
                    scaleY = hotScale
                },
        )
    }
}

/**
 * ≡ badge superior de reveal en `StoryOverlaysView.swift`.
 * El sticker reveal no se dibuja en el canvas; se controla desde aquí.
 */
@Composable
fun StoryRevealStatusBadge(
    onCustomize: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ≡ iOS: texto/icono heredan label; no forzar blanco (invisible sobre glass claro).
    val chrome = StoryEditorChromeColor.icon(isSystemInDarkTheme())
    Row(
        modifier
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
            .clickable(onClick = onCustomize)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.VisibilityOff,
            contentDescription = null,
            tint = chrome,
            modifier = Modifier.size(14.dp),
        )
        Text(
            stringResource(R.string.story_editor_reveal_active),
            color = chrome,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp),
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = null,
            tint = chrome.copy(alpha = 0.6f),
            modifier = Modifier
                .padding(start = 8.dp)
                .size(16.dp)
                .clickable(onClick = onRemove),
        )
    }
}

/** ≡ TextField polaroid `storyEditor.polaroid.addNote` dentro del canvas. */
@Composable
fun StoryPolaroidCaptionField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = false,
        textStyle = TextStyle(
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(Color.Black),
        modifier = modifier
            .focusRequester(focusRequester)
            .widthIn(max = 320.dp)
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
            .padding(horizontal = 25.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (value.isBlank()) {
                    Text(
                        stringResource(R.string.story_editor_polaroid_add_note),
                        color = Color.Black.copy(alpha = 0.42f),
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                inner()
            }
        },
    )
}

/**
 * Gestos Android (docs Compose + multitouch SO):
 * - 1 dedo → `positionChange()` (drag 1:1)
 * - 2+ dedos → `calculatePan` + `calculateZoom` + `calculateRotation`
 * Un solo `pointerInput` (sin `detectTapGestures` hermano que robe el stream).
 */
private data class OverlayTransformDelta(
    val pan: Offset,
    val zoom: Float,
    val pointerCount: Int,
    val rotationDegrees: Float = 0f,
)

private fun androidx.compose.ui.input.pointer.PointerEvent.overlayTransformDelta(): OverlayTransformDelta {
    val pressed = changes.filter { it.pressed }
    return when {
        pressed.size >= 2 -> OverlayTransformDelta(
            pan = calculatePan(),
            zoom = calculateZoom(),
            pointerCount = pressed.size,
            rotationDegrees = calculateRotation(),
        )
        pressed.size == 1 -> {
            val change = pressed.first()
            OverlayTransformDelta(
                pan = change.positionChange(),
                zoom = 1f,
                pointerCount = 1,
            )
        }
        else -> OverlayTransformDelta(Offset.Zero, 1f, 0)
    }
}

/**
 * ≡ capa de dibujo en `StoryOverlaysView`.
 *
 * Visual (`Image` + graphicsLayer) separado del hit-target a pantalla completa:
 * si el gesture va en el mismo nodo que el scale/translation, el área tocable
 * se encoge y el media de debajo se come el gesto.
 */
@Composable
fun StoryDrawingCanvasOverlay(
    bitmap: Bitmap,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    hasTextOverlays: Boolean,
    onOffsetChange: (Float, Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onClear: () -> Unit,
    onDragStateChange: (StoryOverlayDragState) -> Unit,
    onBackgroundTap: () -> Unit,
    alignmentGuides: StoryAlignmentGuideBroker,
    modifier: Modifier = Modifier,
) {
    var isOverTrash by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val latestOffsetX by rememberUpdatedState(offsetX)
    val latestOffsetY by rememberUpdatedState(offsetY)
    val latestScale by rememberUpdatedState(scale)
    val latestOnOffset by rememberUpdatedState(onOffsetChange)
    val latestOnScale by rememberUpdatedState(onScaleChange)
    val latestOnClear by rememberUpdatedState(onClear)
    val latestOnDragState by rememberUpdatedState(onDragStateChange)
    val latestOnTap by rememberUpdatedState(onBackgroundTap)
    val latestGuides by rememberUpdatedState(alignmentGuides)
    val latestHasText by rememberUpdatedState(hasTextOverlays)

    Box(
        modifier
            .fillMaxSize(),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX
                    translationY = offsetY
                    scaleX = scale
                    scaleY = scale
                },
        )
        // Hit-target siempre full-canvas (no hereda el graphicsLayer del dibujo).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(canvasWidthPx, canvasHeightPx) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var liveX = latestOffsetX
                        var liveY = latestOffsetY
                        var liveScale = latestScale
                        var moved = false
                        var dragged = false
                        var cumulativePan = Offset.Zero
                        var cumulativeZoom = 1f
                        var gesturePastTouchSlop = false
                        val gestureStartScale = latestScale
                        isOverTrash = false
                        latestOnDragState(StoryOverlayDragState())
                        try {
                            do {
                                val event = awaitPointerEvent()
                                if (!event.changes.any { it.pressed }) break

                                val delta = event.overlayTransformDelta()
                                cumulativePan += delta.pan
                                cumulativeZoom *= delta.zoom
                                val wasPastTouchSlop = gesturePastTouchSlop
                                if (!gesturePastTouchSlop) {
                                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                    val zoomMotion = kotlin.math.abs(1f - cumulativeZoom) * centroidSize
                                    gesturePastTouchSlop =
                                        cumulativePan.getDistance() > viewConfiguration.touchSlop ||
                                        zoomMotion > viewConfiguration.touchSlop
                                }
                                if (!gesturePastTouchSlop) continue
                                // ≡ iOS: `if !textOverlays.isEmpty { return }`
                                if (latestHasText) continue
                                val effectivePan = if (!wasPastTouchSlop) cumulativePan else delta.pan
                                moved = true
                                dragged = dragged ||
                                    cumulativePan.getDistance() > viewConfiguration.touchSlop

                                liveX += effectivePan.x
                                liveY += effectivePan.y
                                if (delta.pointerCount >= 2) {
                                    liveScale = (gestureStartScale * cumulativeZoom).coerceIn(
                                        StoryMediaTransformLimits.minScale,
                                        StoryMediaTransformLimits.maxScale,
                                    )
                                }
                                val drawingSizeX = canvasWidthPx * liveScale
                                val drawingSizeY = canvasHeightPx * liveScale
                                val proposedCenterX = canvasWidthPx / 2f + liveX
                                val proposedCenterY = canvasHeightPx / 2f + liveY
                                val interactionPoint = event.changes.firstOrNull { it.pressed }?.position
                                    ?: Offset(proposedCenterX, proposedCenterY)
                                val over = dragged && isPointOverStoryOverlayTrash(
                                    interactionPoint.x,
                                    interactionPoint.y,
                                    canvasWidthPx,
                                    canvasHeightPx,
                                    density,
                                )
                                if (!isOverTrash && over) HapticManager.shared.mediumImpact()
                                isOverTrash = over
                                val aligned = storyAlignAndKeepVisible(
                                    proposedX = proposedCenterX,
                                    proposedY = proposedCenterY,
                                    itemWidth = drawingSizeX,
                                    itemHeight = drawingSizeY,
                                    canvasWidth = canvasWidthPx,
                                    canvasHeight = canvasHeightPx,
                                    overTrash = over,
                                    broker = latestGuides,
                                    density = density.density,
                                )
                                liveX = aligned.x - canvasWidthPx / 2f
                                liveY = aligned.y - canvasHeightPx / 2f
                                val clampedOffset = storyClampedOverlayCenterOffset(
                                    proposedOffset = Offset(liveX, liveY),
                                    canvasSize = Size(canvasWidthPx, canvasHeightPx),
                                )
                                liveX = clampedOffset.x
                                liveY = clampedOffset.y
                                latestOnOffset(liveX, liveY)
                                latestOnScale(liveScale)
                                latestOnDragState(
                                    StoryOverlayDragState(
                                        isDragging = dragged,
                                        isOverTrash = over,
                                    ),
                                )
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            } while (true)
                        } finally {
                            if (dragged && isOverTrash) {
                                latestOnClear()
                                latestOnOffset(0f, 0f)
                                latestOnScale(1f)
                            } else if (!moved) {
                                latestOnTap()
                            }
                            isOverTrash = false
                            latestGuides.clear()
                            latestOnDragState(StoryOverlayDragState())
                        }
                    }
                },
        )
    }
}

/**
 * Texto en canvas: drag 1 dedo + pinch/rotar 2 dedos (patrón Compose multitouch).
 * Hit mínimo 48dp para que el pellizco sea usable.
 */
@Composable
fun StoryTextOverlayItem(
    overlay: StoryTextOverlayDraft,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    isEditorPresented: Boolean,
    onUpdate: (StoryTextOverlayDraft) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDragStateChange: (StoryOverlayDragState) -> Unit,
    alignmentGuides: StoryAlignmentGuideBroker,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var contentWidthPx by remember(overlay.id) { mutableStateOf(0) }
    var contentHeightPx by remember(overlay.id) { mutableStateOf(0) }
    var isOverTrash by remember(overlay.id) { mutableStateOf(false) }
    val density = LocalDensity.current
    val latestOverlay by rememberUpdatedState(overlay)
    val latestContentW by rememberUpdatedState(contentWidthPx)
    val latestContentH by rememberUpdatedState(contentHeightPx)
    val latestOnUpdate by rememberUpdatedState(onUpdate)
    val latestOnEdit by rememberUpdatedState(onEdit)
    val latestOnDelete by rememberUpdatedState(onDelete)
    val latestOnDragState by rememberUpdatedState(onDragStateChange)
    val latestGuides by rememberUpdatedState(alignmentGuides)

    fun boundedDraft(
        x: Float,
        y: Float,
        fontSize: Float,
        rotationRadians: Double,
        base: StoryTextOverlayDraft,
    ): StoryTextOverlayDraft {
        val (cx, cy) = storyKeepOverlayVisibleCenter(
            x = x,
            y = y,
            canvasWidth = canvasWidthPx,
            canvasHeight = canvasHeightPx,
        )
        return base.copy(
            normalizedX = (cx / canvasWidthPx).toDouble(),
            normalizedY = (cy / canvasHeightPx).toDouble(),
            fontSize = fontSize.coerceIn(
                StoryMediaTransformLimits.minFontSize,
                StoryMediaTransformLimits.maxFontSize,
            ).toDouble(),
            rotationRadians = rotationRadians,
        )
    }

    val centerX = overlay.normalizedX.toFloat() * canvasWidthPx
    val centerY = overlay.normalizedY.toFloat() * canvasHeightPx
    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (centerX - contentWidthPx / 2f).roundToInt(),
                    (centerY - contentHeightPx / 2f).roundToInt(),
                )
            }
            .graphicsLayer {
                rotationZ = Math.toDegrees(overlay.rotationRadians).toFloat()
            }
            .onSizeChanged {
                contentWidthPx = it.width
                contentHeightPx = it.height
            }
            .wrapContentSize(unbounded = true)
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .then(
                if (isEditorPresented) {
                    Modifier
                } else {
                    Modifier.pointerInput(overlay.id, canvasWidthPx, canvasHeightPx) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val start = latestOverlay
                            var liveX = start.normalizedX.toFloat() * canvasWidthPx
                            var liveY = start.normalizedY.toFloat() * canvasHeightPx
                            var liveFont = start.fontSize.toFloat().coerceIn(
                                StoryMediaTransformLimits.minFontSize,
                                StoryMediaTransformLimits.maxFontSize,
                            )
                            var liveRotation = start.rotationRadians
                            isOverTrash = false
                            var moved = false
                            var dragged = false
                            var cumulativePan = Offset.Zero
                            var cumulativeZoom = 1f
                            var cumulativeRotationDegrees = 0f
                            var gesturePastTouchSlop = false
                            val gestureStartFont = liveFont
                            val gestureStartRotation = liveRotation
                            latestOnDragState(StoryOverlayDragState())
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    if (!event.changes.any { it.pressed }) break

                                    val delta = event.overlayTransformDelta()
                                    cumulativePan += delta.pan
                                    cumulativeZoom *= delta.zoom
                                    cumulativeRotationDegrees += delta.rotationDegrees
                                    val wasPastTouchSlop = gesturePastTouchSlop
                                    if (!gesturePastTouchSlop) {
                                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                        val zoomMotion = kotlin.math.abs(1f - cumulativeZoom) * centroidSize
                                        val rotationMotion = kotlin.math.abs(
                                            Math.toRadians(cumulativeRotationDegrees.toDouble()).toFloat(),
                                        ) * centroidSize
                                        gesturePastTouchSlop =
                                            cumulativePan.getDistance() > viewConfiguration.touchSlop ||
                                            zoomMotion > viewConfiguration.touchSlop ||
                                            rotationMotion > viewConfiguration.touchSlop
                                    }
                                    if (!gesturePastTouchSlop) continue
                                    val effectivePan = if (!wasPastTouchSlop) cumulativePan else delta.pan
                                    moved = true
                                    dragged = dragged ||
                                        cumulativePan.getDistance() > viewConfiguration.touchSlop

                                    liveX += effectivePan.x
                                    liveY += effectivePan.y
                                    if (delta.pointerCount >= 2) {
                                        liveFont = (gestureStartFont * cumulativeZoom).coerceIn(
                                            StoryMediaTransformLimits.minFontSize,
                                            StoryMediaTransformLimits.maxFontSize,
                                        )
                                        liveRotation = gestureStartRotation + Math.toRadians(
                                            cumulativeRotationDegrees.toDouble(),
                                        )
                                    }

                                    isOverTrash = dragged && isPointOverStoryOverlayTrash(
                                        liveX,
                                        liveY,
                                        canvasWidthPx,
                                        canvasHeightPx,
                                        density,
                                    )
                                    val itemW = latestContentW.toFloat().coerceAtLeast(1f)
                                    val itemH = latestContentH.toFloat().coerceAtLeast(1f)
                                    val aligned = storyAlignAndKeepVisible(
                                        proposedX = liveX,
                                        proposedY = liveY,
                                        itemWidth = itemW,
                                        itemHeight = itemH,
                                        canvasWidth = canvasWidthPx,
                                        canvasHeight = canvasHeightPx,
                                        overTrash = isOverTrash,
                                        broker = latestGuides,
                                        density = density.density,
                                    )
                                    liveX = aligned.x
                                    liveY = aligned.y
                                    val updated = boundedDraft(liveX, liveY, liveFont, liveRotation, start)
                                    latestOnUpdate(updated)
                                    latestOnDragState(
                                        StoryOverlayDragState(
                                            isDragging = dragged,
                                            isOverTrash = isOverTrash,
                                        ),
                                    )
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) change.consume()
                                    }
                                } while (true)
                            } finally {
                                if (dragged && isOverTrash) {
                                    latestOnDelete()
                                } else if (!moved) {
                                    latestOnEdit()
                                }
                                isOverTrash = false
                                latestGuides.clear()
                                latestOnDragState(StoryOverlayDragState())
                            }
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
