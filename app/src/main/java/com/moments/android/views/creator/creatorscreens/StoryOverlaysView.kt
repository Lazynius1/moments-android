package com.moments.android.views.creator.creatorscreens

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private fun StoryOverlayToast.message(): String = when (this) {
    is StoryOverlayToast.UserNotFound -> "@$username was not found"
    is StoryOverlayToast.Hashtag -> "#$hashtag"
    is StoryOverlayToast.Location -> location
    StoryOverlayToast.Poll -> "Poll sticker"
    StoryOverlayToast.Question -> "Question sticker"
    StoryOverlayToast.QuestionResponse -> "Question response"
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
                toast.message(),
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .background(Color.Black.copy(.58f), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
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

/** Equivalente visual de la papelera que Swift muestra solo durante un arrastre. */
@Composable
fun StoryOverlayTrashZone(
    state: StoryOverlayDragState,
    modifier: Modifier = Modifier,
) {
    if (!state.isDragging) return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = null,
            tint = if (state.isOverTrash) Color(0xFFFF3B30) else Color.White,
            modifier = Modifier
                .offset(y = (-20).dp)
                .graphicsLayer {
                    scaleX = if (state.isOverTrash) 1.28f else 1f
                    scaleY = if (state.isOverTrash) 1.28f else 1f
                },
        )
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
    var dragCenter by remember(overlay.id) { mutableStateOf<Offset?>(null) }

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
                                dragCenter = Offset(centerX, centerY)
                                onDragStateChange(StoryOverlayDragState(isDragging = true))
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                val base = dragCenter ?: Offset(centerX, centerY)
                                val updated = boundedDraft(x = base.x + drag.x, y = base.y + drag.y)
                                dragCenter = Offset(
                                    updated.normalizedX.toFloat() * canvasWidthPx,
                                    updated.normalizedY.toFloat() * canvasHeightPx,
                                )
                                isOverTrash = isPointOverStoryOverlayTrash(
                                    updated.normalizedX.toFloat() * canvasWidthPx,
                                    updated.normalizedY.toFloat() * canvasHeightPx,
                                    canvasWidthPx,
                                    canvasHeightPx,
                                )
                                onUpdate(updated)
                                onDragStateChange(StoryOverlayDragState(isDragging = true, isOverTrash = isOverTrash))
                            },
                            onDragEnd = {
                                if (isOverTrash) onDelete()
                                dragCenter = null
                                isOverTrash = false
                                onDragStateChange(StoryOverlayDragState())
                            },
                            onDragCancel = {
                                dragCenter = null
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
