package com.moments.android.views.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import com.moments.android.views.creator.StoryStickerDraft
import com.moments.android.views.creator.components.AnimatedGIFView

/**
 * Port de `AnimatedStickerView.swift`.
 * Coil ya aporta caché de memoria/disco y la decodificación GIF, por lo que no se duplica `GIFCache`.
 * No instala gestos: el contenedor del sticker conserva el control de interacción, igual que UIKit.
 */
@Composable
fun AnimatedStickerView(
    sticker: StoryStickerDraft,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val sizedModifier = modifier.size(size.width, size.height)
    when {
        sticker.isAnimated && !sticker.gifURL.isNullOrBlank() -> AnimatedGIFView(
            url = sticker.gifURL,
            modifier = sizedModifier,
        )
        sticker.image != null -> Image(
            bitmap = sticker.image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = sizedModifier,
        )
        else -> Box(sizedModifier.background(Color.Transparent))
    }
}
