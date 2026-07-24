package com.moments.android.views.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Port parcial de `Views/Components/InteractiveStickerSharedViews.swift`: Polaroid. */
enum class StoryPolaroidFrameStyle(val raw: String) {
    CLASSIC("classic"),
    CLEAN("clean"),
    VINTAGE("vintage"),
    ALBUM("album");

    companion object {
        fun fromRawOrDefault(raw: String?) = entries.firstOrNull { it.raw == raw } ?: CLASSIC
    }
}

/** Equivalente Compose de `StickerPolaroidFrameView`. */
@Composable
fun StickerPolaroidFrameView(
    image: Bitmap?,
    caption: String? = null,
    frameStyle: StoryPolaroidFrameStyle = StoryPolaroidFrameStyle.CLASSIC,
    contentScale: Float = 1f,
    contentOffsetX: Float = 0f,
    contentOffsetY: Float = 0f,
    progress: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val style = FrameStyleTokens.from(frameStyle)
    Column(
        modifier
            .width(200.dp)
            .background(style.surface, style.shape)
            .clip(style.shape),
    ) {
        Box(
            Modifier
                .padding(style.imagePadding)
                .size(180.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0xFF101218)),
            contentAlignment = Alignment.Center,
        ) {
            image?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val safeScale = contentScale.coerceAtLeast(1f)
                            scaleX = safeScale
                            scaleY = safeScale
                            translationX = contentOffsetX
                            translationY = contentOffsetY
                            alpha = ((progress - 0.05f) / 0.95f).coerceIn(0f, 1f)
                        },
                )
            }
            if (progress < 1f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xB20D0E12)),
                )
            }
        }
        Box(
            Modifier
                .height(40.dp)
                .fillMaxSize()
                .background(style.surface)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            caption?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = style.ink,
                    fontFamily = style.captionFont,
                    fontWeight = style.captionWeight,
                    fontSize = 17.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private data class FrameStyleTokens(
    val surface: Color,
    val ink: Color,
    val imagePadding: androidx.compose.ui.unit.Dp,
    val shape: RoundedCornerShape,
    val captionFont: FontFamily,
    val captionWeight: FontWeight,
) {
    companion object {
        fun from(style: StoryPolaroidFrameStyle) = when (style) {
            StoryPolaroidFrameStyle.CLASSIC -> FrameStyleTokens(Color.White, Color(0xFF161616), 10.dp, RoundedCornerShape(0.dp), FontFamily.Cursive, FontWeight.Medium)
            StoryPolaroidFrameStyle.CLEAN -> FrameStyleTokens(Color(0xFFF0F0F0), Color(0xFF161616), 8.dp, RoundedCornerShape(18.dp), FontFamily.SansSerif, FontWeight.SemiBold)
            StoryPolaroidFrameStyle.VINTAGE -> FrameStyleTokens(Color(0xFFF2E8D1), Color(0xFF382E24), 13.dp, RoundedCornerShape(4.dp), FontFamily.Serif, FontWeight.Medium)
            StoryPolaroidFrameStyle.ALBUM -> FrameStyleTokens(Color(0xFFFBF6ED), Color(0xFF29231D), 12.dp, RoundedCornerShape(20.dp), FontFamily.SansSerif, FontWeight.SemiBold)
        }
    }
}
