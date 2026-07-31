package com.moments.android.views.creator.creatorscreens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.components.StoryTextOverlayDraft
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

/** Radio y posición de la zona de borrado: 20 px de margen + mitad del icono de 48 px. */
fun isPointOverStoryOverlayTrash(
    x: Float,
    y: Float,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
): Boolean = hypot(x - canvasWidthPx / 2f, y - (canvasHeightPx - 44f)) < 60f

/**
 * ≡ papelera de `StoryOverlaysView` — solo visible al arrastrar;
 * scale 1.28 + spring cuando `isOverTrash` (MotionPolicy.Spring.press).
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
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
        Text(
            stringResource(R.string.story_editor_reveal_active),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp),
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
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
 * ≡ capa de dibujo en `StoryOverlaysView`: arrastre, pellizco y papelera
 * solo cuando no hay text overlays (misma regla Swift).
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
    modifier: Modifier = Modifier,
) {
    var pinchStartScale by remember { mutableStateOf<Float?>(null) }
    var dragOrigin by remember { mutableStateOf<Offset?>(null) }
    var accumulatedDrag by remember { mutableStateOf(Offset.Zero) }
    var isOverTrash by remember { mutableStateOf(false) }
    val latestOffsetX by rememberUpdatedState(offsetX)
    val latestOffsetY by rememberUpdatedState(offsetY)
    val latestScale by rememberUpdatedState(scale)

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(hasTextOverlays, canvasWidthPx, canvasHeightPx) {
                if (hasTextOverlays) {
                    detectTapGestures(onTap = { onBackgroundTap() })
                    return@pointerInput
                }
                detectDragGestures(
                    onDragStart = {
                        dragOrigin = Offset(latestOffsetX, latestOffsetY)
                        accumulatedDrag = Offset.Zero
                        isOverTrash = false
                        onDragStateChange(StoryOverlayDragState(isDragging = true))
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        val origin = dragOrigin ?: Offset(latestOffsetX, latestOffsetY)
                        accumulatedDrag += Offset(drag.x, drag.y)
                        val liveX = origin.x + accumulatedDrag.x
                        val liveY = origin.y + accumulatedDrag.y
                        onOffsetChange(liveX, liveY)
                        val fingerX = canvasWidthPx / 2f + liveX
                        val fingerY = canvasHeightPx / 2f + liveY
                        val over = isPointOverStoryOverlayTrash(
                            fingerX,
                            fingerY,
                            canvasWidthPx,
                            canvasHeightPx,
                        )
                        if (!isOverTrash && over) HapticManager.shared.mediumImpact()
                        isOverTrash = over
                        onDragStateChange(StoryOverlayDragState(isDragging = true, isOverTrash = over))
                    },
                    onDragEnd = {
                        if (isOverTrash) {
                            onClear()
                            onOffsetChange(0f, 0f)
                            onScaleChange(1f)
                        }
                        dragOrigin = null
                        accumulatedDrag = Offset.Zero
                        isOverTrash = false
                        onDragStateChange(StoryOverlayDragState())
                    },
                    onDragCancel = {
                        dragOrigin = null
                        accumulatedDrag = Offset.Zero
                        isOverTrash = false
                        onDragStateChange(StoryOverlayDragState())
                    },
                )
            }
            .pointerInput(hasTextOverlays) {
                if (hasTextOverlays) return@pointerInput
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom == 1f) return@detectTransformGestures
                    if (pinchStartScale == null) pinchStartScale = latestScale
                    val base = pinchStartScale ?: latestScale
                    onScaleChange((base * zoom).coerceIn(0.3f, 4f))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onBackgroundTap() })
            },
    )
    LaunchedEffect(scale) {
        delay(120)
        pinchStartScale = null
    }
}

/**
 * Primer bloque de `StoryOverlaysView.swift`: un texto queda centrado en sus coordenadas
 * normalizadas, no puede salir del lienzo, se arrastra a la papelera y se escala entre 16–72.
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var contentWidthPx by remember(overlay.id) { mutableStateOf(0) }
    var contentHeightPx by remember(overlay.id) { mutableStateOf(0) }
    var isOverTrash by remember(overlay.id) { mutableStateOf(false) }
    // Punto propuesto sin clamp: permite alcanzar la papelera aunque el label quede limitado al lienzo.
    var rawDragCenter by remember(overlay.id) { mutableStateOf<Offset?>(null) }

    fun boundedDraft(
        x: Float = overlay.normalizedX.toFloat() * canvasWidthPx,
        y: Float = overlay.normalizedY.toFloat() * canvasHeightPx,
        fontSize: Float = overlay.fontSize.toFloat(),
    ): StoryTextOverlayDraft {
        val halfWidth = minOf(contentWidthPx / 2f, canvasWidthPx / 2f)
        val halfHeight = minOf(contentHeightPx / 2f, canvasHeightPx / 2f)
        return overlay.copy(
            normalizedX = (x.coerceIn(halfWidth, canvasWidthPx - halfWidth) / canvasWidthPx).toDouble(),
            normalizedY = (y.coerceIn(halfHeight, canvasHeightPx - halfHeight) / canvasHeightPx).toDouble(),
            fontSize = fontSize.coerceIn(16f, 72f).toDouble(),
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
            .onSizeChanged {
                contentWidthPx = it.width
                contentHeightPx = it.height
            }
            .then(
                if (isEditorPresented) Modifier else Modifier
                    .pointerInput(overlay.id, canvasWidthPx, canvasHeightPx) {
                        detectDragGestures(
                            onDragStart = {
                                isOverTrash = false
                                rawDragCenter = Offset(centerX, centerY)
                                onDragStateChange(StoryOverlayDragState(isDragging = true))
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                val base = rawDragCenter ?: Offset(centerX, centerY)
                                val proposed = Offset(base.x + drag.x, base.y + drag.y)
                                rawDragCenter = proposed
                                val updated = boundedDraft(x = proposed.x, y = proposed.y)
                                isOverTrash = isPointOverStoryOverlayTrash(
                                    proposed.x,
                                    proposed.y,
                                    canvasWidthPx,
                                    canvasHeightPx,
                                )
                                onUpdate(updated)
                                onDragStateChange(StoryOverlayDragState(isDragging = true, isOverTrash = isOverTrash))
                            },
                            onDragEnd = {
                                if (isOverTrash) onDelete()
                                rawDragCenter = null
                                isOverTrash = false
                                onDragStateChange(StoryOverlayDragState())
                            },
                            onDragCancel = {
                                rawDragCenter = null
                                isOverTrash = false
                                onDragStateChange(StoryOverlayDragState())
                            },
                        )
                    }
                    .pointerInput(overlay.id, overlay.fontSize) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom != 1f) onUpdate(boundedDraft(fontSize = overlay.fontSize.toFloat() * zoom))
                        }
                    }
                    .pointerInput(overlay.id) { detectTapGestures(onTap = { onEdit() }) },
            ),
    ) {
        content()
    }
}
