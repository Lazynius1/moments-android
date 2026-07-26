package com.moments.android.views.components

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.moments.android.views.creator.StoryStickerDraft

/**
 * Port de `AnimatedStickerView.swift` (+ `GIFCache` vía Coil memory/disk).
 *
 * iOS: UIImageView scaleAspectFit, clipsToBounds, isUserInteractionEnabled=false
 * (gestos al padre). GIF: cache → URLSession → si falla image=nil (sin fallback
 * estático). Mientras carga: vacío (sin spinner). Estático: sticker.image.
 *
 * Android: `StoryStickerDraft` es el equivalente de UI de `StickerItem` en el
 * editor (Bitmap en lugar de UIImage).
 */
@Composable
fun AnimatedStickerView(
    sticker: StoryStickerDraft,
    size: DpSize,
    modifier: Modifier = Modifier,
) {
    val sizedModifier = modifier.size(size.width, size.height)
    when {
        sticker.isAnimated && !sticker.gifURL.isNullOrBlank() -> {
            // Paridad iOS: vacío mientras carga / si falla; ContentScale.Fit;
            // sin clickable → gestos pasan al contenedor.
            val request = ImageRequest.Builder(LocalContext.current)
                .data(sticker.gifURL)
                .crossfade(false)
                .decoderFactory(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoderDecoder.Factory()
                    } else {
                        GifDecoder.Factory()
                    },
                )
                .build()
            SubcomposeAsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = sizedModifier,
                loading = { Box(Modifier.fillMaxSize()) },
                error = { Box(Modifier.fillMaxSize()) },
                success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) },
            )
        }
        sticker.image != null -> Image(
            bitmap = sticker.image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = sizedModifier,
        )
        else -> Box(sizedModifier)
    }
}
