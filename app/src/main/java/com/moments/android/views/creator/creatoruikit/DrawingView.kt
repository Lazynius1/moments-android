package com.moments.android.views.creator.creatoruikit

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.moments.android.views.creator.components.StoryDrawingEditorOverlay

/**
 * Port de `DrawingView` / `DrawingViewController` (`DrawingView.swift`).
 *
 * iOS: PencilKit + toolbars propias (pen/neon/marker/arrow/eraser, slider 2…26,
 * 8 colores). En Android el motor/UI de trazos vive en [StoryDrawingEditorOverlay]
 * (mismo rango de ancho, brushes equivalentes PEN/GLOW/MARKER/ARROW/ERASER).
 *
 * - Fondo: `scaleAspectFill` ≡ [ContentScale.Crop] + dim 0.10; sin imagen → negro
 * - [initialDrawing] ≡ dibujo previo en el canvas (no el fondo)
 * - Done → bitmap de trazos (+ base si hay); luego dismiss (como iOS `onComplete` + `onDismiss`)
 *
 * Nota: en el árbol Swift actual no hay call sites de `DrawingView`; Story usa
 * `StoryDrawingEditorOverlay` directamente (igual que `storyeditor.kt`).
 */
@Composable
fun DrawingView(
    backgroundImage: Bitmap?,
    initialDrawing: Bitmap? = null,
    onComplete: (Bitmap?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (backgroundImage != null) {
            Image(
                bitmap = backgroundImage.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)))
        }

        StoryDrawingEditorOverlay(
            baseDrawing = initialDrawing,
            onCancel = onDismiss,
            onDone = { result ->
                onComplete(result)
                onDismiss()
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
