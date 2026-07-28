package com.moments.android.views.feed.sharing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.models.Moment
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.components.LiveUsernameText
import com.moments.android.views.feed.moments.FeedMomentCardLayout

private val StickerWidth = 260.dp

/**
 * Port de `ShareMomentSticker.swift` — sticker de momento para historias.
 * `UIImage` → [Bitmap]; `renderClean` omite header, caption e indicador de galería.
 */
@Composable
fun ShareMomentSticker(
    moment: Moment,
    profileImage: Bitmap?,
    contentImage: Bitmap?,
    modifier: Modifier = Modifier,
    renderClean: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val corner = FeedMomentCardLayout.mediaCornerRadius
    val cardShape = RoundedCornerShape(corner)
    val height = remember(moment.aspectRatio, contentImage?.width, contentImage?.height) {
        shareMomentStickerHeight(moment.aspectRatio, contentImage)
    }
    val usernameSize = with(density) { legacyPoppinsSize(context, 13).toSp() }
    val captionSize = with(density) { legacyPoppinsSize(context, 12).toSp() }

    Box(
        modifier
            .width(StickerWidth)
            .clip(cardShape)
            .background(Color(0.1f, 0.1f, 0.1f))
            .background(Color.White.copy(0.08f))
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(0.4f),
                        Color.White.copy(0.05f),
                        Color.White.copy(0.2f),
                    ),
                ),
                shape = cardShape,
            ),
    ) {
        // 1. MAIN CONTENT (Image/Video + Caption + gallery)
        Box(
            Modifier
                .width(StickerWidth)
                .height(height),
        ) {
            if (contentImage != null) {
                Image(
                    bitmap = contentImage.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.Gray.copy(0.2f)))
            }

            if (!moment.videoUrl.isNullOrBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(0.22f))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (moment.content.isNotBlank() && !renderClean) {
                Text(
                    moment.content,
                    color = Color.White,
                    fontSize = captionSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 12.dp, bottom = 20.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(0.18f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            val mediaCount = moment.mediaItems?.size ?: 0
            if (mediaCount > 1 && !renderClean) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 54.dp, end = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(0.18f))
                        .padding(6.dp),
                ) {
                    Icon(
                        Icons.Filled.FilterNone,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }

        // 2. FLOATING HEADER
        if (!renderClean) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(0.55f),
                                Color.Black.copy(0.35f),
                                Color.Transparent,
                            ),
                        ),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (profileImage != null) {
                    Image(
                        bitmap = profileImage.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    listOf(Color.White.copy(0.5f), Color.Transparent),
                                ),
                                shape = CircleShape,
                            ),
                    )
                } else {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White.copy(0.5f),
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    LiveUsernameText(
                        userId = moment.authorId,
                        fallbackUsername = moment.username,
                        color = Color.White,
                        style = TextStyle(
                            fontSize = usernameSize,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** ≡ iOS `calculatedHeight` — h/w capped 0.5…1.8; fallback 340. */
fun shareMomentStickerHeight(aspectRatio: String?, contentImage: Bitmap?): Dp {
    val ratioString = aspectRatio?.trim().orEmpty()
    if (ratioString.contains(":")) {
        val parts = ratioString.split(":")
        if (parts.size == 2) {
            val w = parts[0].toDoubleOrNull()
            val h = parts[1].toDoubleOrNull()
            if (w != null && h != null && w > 0.0) {
                val ratio = (h / w).toFloat().coerceIn(0.5f, 1.8f)
                return StickerWidth * ratio
            }
        }
    }
    if (contentImage != null && contentImage.width > 0) {
        val ratio = (contentImage.height.toFloat() / contentImage.width).coerceIn(0.5f, 1.8f)
        return StickerWidth * ratio
    }
    return 340.dp
}
