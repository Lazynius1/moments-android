package com.moments.android.views.feed.sharing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.feed.moments.MomentCarouselLayoutRules

private const val StickerWidthDp = 260f

/** Port de `ShareMomentSticker.swift` — sticker de momento para historias. */
@Composable
fun ShareMomentSticker(
    username: String,
    previewUrl: String?,
    aspectRatio: String? = null,
    caption: String = "",
    isVideo: Boolean = false,
    mediaCount: Int = 1,
    renderClean: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val usernameSize = with(density) { legacyPoppinsSize(context, 13).toSp() }
    val captionSize = with(density) { legacyPoppinsSize(context, 12).toSp() }
    val heightOverWidth = (1f / MomentCarouselLayoutRules.aspectRatioValue(aspectRatio)).coerceIn(0.5f, 1.8f)
    val corner = FeedMomentCardLayout.mediaCornerRadius

    Column(
        modifier
            .width(StickerWidthDp.dp)
            .clip(RoundedCornerShape(corner))
            .background(Color(0xFF1A1A1A)),
    ) {
        if (!renderClean) {
            Text(
                "@$username",
                color = Color.White,
                fontSize = usernameSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f / heightOverWidth),
            contentAlignment = Alignment.Center,
        ) {
            if (!previewUrl.isNullOrBlank()) {
                AsyncImage(
                    model = previewUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Box(Modifier.matchParentSize().background(Color.Gray.copy(alpha = 0.2f)))
            }
            if (isVideo) {
                Text(
                    "▶",
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .padding(14.dp),
                )
            }
            if (mediaCount > 1 && !renderClean) {
                Text(
                    "⧉",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(6.dp),
                )
            }
        }
        if (caption.isNotBlank() && !renderClean) {
            Text(
                caption,
                color = Color.White,
                fontSize = captionSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(12.dp),
                maxLines = 2,
            )
        }
    }
}
