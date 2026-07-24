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

/** Fachada de `DrawingView.swift` sobre el editor Compose compartido de trazos. */
@Composable
fun DrawingView(
    backgroundImage: Bitmap?,
    initialDrawing: Bitmap? = null,
    onComplete: (Bitmap?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        backgroundImage?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(.1f)))
        }
        StoryDrawingEditorOverlay(
            baseDrawing = initialDrawing,
            onCancel = onDismiss,
            onDone = onComplete,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
