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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.models.Moment
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.views.components.AnimatedMomentsCardStickerSurface
import com.moments.android.views.components.momentsCardStickerTextColor
import com.moments.android.views.components.LiveUsernameText
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.profile.core.sections.MomentCarouselIndicatorIcon
import com.moments.android.views.story.storystickers.StickerVideoPlayer

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

/** Renderer único del Moment compartido: el editor y el viewer usan esta misma jerarquía. */
@Composable
fun SharedMomentStoryCard(
    bitmap: Bitmap?,
    videoURL: String?,
    username: String,
    userId: String?,
    profileImagePath: String?,
    sharedMediaPath: String?,
    caption: String?,
    mediaCount: Int,
    styleVariant: Int,
    cardLayoutVariant: Int,
    modifier: Modifier = Modifier,
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val palette = ((styleVariant % 6) + 6) % 6
    val layout = ((cardLayoutVariant % 2) + 2) % 2
    val isFullscreenReel = !videoURL.isNullOrBlank() && mediaCount == 1 && layout == 1
    val shape = RoundedCornerShape(if (isFullscreenReel) 0.dp else FeedMomentCardLayout.mediaCornerRadius)

    Box(modifier = modifier) {
        Box(Modifier.matchParentSize().clip(shape).background(Color(0xFF1A1A1A))) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (videoURL.isNullOrBlank() && !sharedMediaPath.isNullOrBlank()) {
                AsyncImage(
                    model = sharedMediaPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!videoURL.isNullOrBlank()) {
                StickerVideoPlayer(
                    url = videoURL,
                    isMuted = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        when {
            layout == 0 -> SharedMomentExpandedChrome(
                username = username,
                userId = userId,
                profileImagePath = profileImagePath,
                caption = caption,
                palette = palette,
                isDark = isDark,
                modifier = Modifier.matchParentSize().clip(shape),
            )
            isFullscreenReel -> SharedMomentFullscreenChrome(
                username = username,
                caption = caption,
                palette = palette,
                isDark = isDark,
                modifier = Modifier.matchParentSize(),
            )
            else -> SharedMomentPostByline(
                username = username,
                palette = palette,
                isDark = isDark,
                modifier = Modifier.align(Alignment.TopEnd).offset(y = (-17).dp).padding(end = 10.dp),
            )
        }

        if (mediaCount > 1) {
            MomentCarouselIndicatorIcon(
                size = 18.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (layout == 0) 52.dp else 12.dp, end = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(5.dp),
            )
        }

        if (!isFullscreenReel) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        1.2.dp,
                        Brush.linearGradient(
                            listOf(Color.White.copy(0.4f), Color.White.copy(0.05f), Color.White.copy(0.2f)),
                        ),
                        shape,
                    ),
            )
        }
    }
}

@Composable
private fun SharedMomentExpandedChrome(
    username: String,
    userId: String?,
    profileImagePath: String?,
    caption: String?,
    palette: Int,
    isDark: Boolean,
    modifier: Modifier,
) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth()) {
            AnimatedMomentsCardStickerSurface(
                styleVariant = palette,
                isDark = isDark,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(if (palette == 0) 0.82f else 0.92f)
                    .verticalAlphaMask(
                        listOf(Color.Black, Color.Black, Color.Transparent),
                    ),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SharedMomentAvatar(userId, profileImagePath)
                Spacer(Modifier.width(10.dp))
                Text(
                    username,
                    color = momentsCardStickerTextColor(palette, isDark),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        caption?.takeIf(String::isNotBlank)?.let {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                SharedMomentCaptionPill(it, palette, isDark, maxLines = 2)
            }
        }
    }
}

@Composable
private fun SharedMomentFullscreenChrome(
    username: String,
    caption: String?,
    palette: Int,
    isDark: Boolean,
    modifier: Modifier,
) {
    Box(modifier) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(112.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.62f))))
                .verticalAlphaMask(listOf(Color.Transparent, Color.Black)),
        ) {
            AnimatedMomentsCardStickerSurface(
                styleVariant = palette,
                isDark = isDark,
                modifier = Modifier.matchParentSize().alpha(if (palette == 0) 0.08f else 0.18f),
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (username.startsWith("@")) username else "@$username",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.End),
            )
            Spacer(Modifier.height(7.dp))
            caption?.takeIf(String::isNotBlank)?.let {
                SharedMomentCaptionPill(it, palette, isDark, maxLines = 1)
            }
        }
    }
}

/** Replica la máscara de luminancia vertical usada por SwiftUI en el chrome del sticker. */
private fun Modifier.verticalAlphaMask(colors: List<Color>): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(colors),
                blendMode = BlendMode.DstIn,
            )
        }

@Composable
private fun SharedMomentPostByline(
    username: String,
    palette: Int,
    isDark: Boolean,
    modifier: Modifier,
) {
    SharedMomentCaptionPill(
        text = if (username.startsWith("@")) username else "@$username",
        palette = palette,
        isDark = isDark,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun SharedMomentCaptionPill(
    text: String,
    palette: Int,
    isDark: Boolean,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(CircleShape)) {
        AnimatedMomentsCardStickerSurface(
            styleVariant = palette,
            isDark = isDark,
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = text,
            color = momentsCardStickerTextColor(palette, isDark),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun SharedMomentAvatar(userId: String?, profileImagePath: String?) {
    val avatarModifier = Modifier
        .size(34.dp)
        .clip(CircleShape)
        .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(0.5f), Color.Transparent)), CircleShape)
    when {
        !userId.isNullOrBlank() -> AsyncProfileImageView(userId = userId, modifier = avatarModifier)
        !profileImagePath.isNullOrBlank() -> AsyncImage(
            model = profileImagePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = avatarModifier,
        )
        else -> Icon(Icons.Filled.Person, null, tint = Color.White.copy(0.5f), modifier = avatarModifier.padding(7.dp))
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
