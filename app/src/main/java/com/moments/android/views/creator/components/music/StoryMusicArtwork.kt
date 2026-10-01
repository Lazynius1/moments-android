package com.moments.android.views.creator.components.music

import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.models.StoryMusicSelection
import com.moments.android.views.components.MomentsCardStickerBackgroundGradient
import com.moments.android.views.components.AnimatedMomentsCardStickerSurface
import com.moments.android.views.components.momentsCardStickerTextColor

@Composable
fun StoryMusicArtwork(selection: StoryMusicSelection, animates: Boolean = true) {
    val density = LocalDensity.current
    // Story artwork has a saved canvas transform; device accessibility typography must
    // not change its natural bounds between editor, exported artwork and viewer.
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = 1f),
        LocalTextStyle provides TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
    ) {
        MusicArtworkContent(selection, animates)
    }
}

@Composable
private fun MusicArtworkContent(selection: StoryMusicSelection, animates: Boolean) {
    val moving = animates && LocalStoryMusicPlaying.current
    val ink = remember(selection.colorHex) { runCatching { Color(android.graphics.Color.parseColor("#" + selection.colorHex.removePrefix("#"))) }.getOrDefault(Color.White) }
    if (selection.style == "hidden") return
    when (selection.style) {
        "card" -> {
            val variant = (selection.cardStyleVariant ?: 0) % 6
            val isDark = isSystemInDarkTheme()
            val textColor = momentsCardStickerTextColor(variant, isDark)
            Box(Modifier.clip(RoundedCornerShape(10.dp))) {
                if (animates) AnimatedMomentsCardStickerSurface(variant, isDark, Modifier.matchParentSize())
                else MomentsCardStickerBackgroundGradient(variant, isDark, Modifier.matchParentSize())
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(32.dp)) { MusicCover(selection, 32); if (moving) Equalizer(Modifier.align(Alignment.Center)) }
                Column(Modifier.width(musicTextWidth(selection, 180, compact = true)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(selection.track.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(selection.track.artist, fontSize = 11.sp, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                }
            }
        }
        "cover", "record" -> Column(
            Modifier.width(maxOf(if (selection.style == "cover") 128.dp else 164.dp, musicTextWidth(selection, 220))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (selection.style == "cover") 10.dp else 8.dp),
        ) {
            if (selection.style == "record") {
                val transition = rememberInfiniteTransition(label = "record")
                val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(15000, easing = LinearEasing)), label = "spin")
                Box(Modifier.size(164.dp).rotate(if (moving) rotation else 0f).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) { repeat(8) { drawCircle(Color.White.copy(.1f), radius = size.minDimension / 2 - (it * 5 + 4).dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) } }
                    Box(Modifier.size(86.dp).clip(CircleShape)) { MusicCover(selection, 86) }
                }
            } else Box(Modifier.size(128.dp)) {
                MusicCover(selection, 128)
                if (moving) Equalizer(Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            if (selection.style == "cover") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MusicLabels(selection, ink)
                }
            } else {
                MusicLabels(selection, ink)
            }
        }
        else -> Column(Modifier.width(musicTextWidth(selection, 260)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(selection.track.title, color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center)
            Text(selection.track.artist, color = ink, fontSize = 12.sp, maxLines = 1)
        }
    }
}
@Composable
private fun MusicLabels(selection: StoryMusicSelection, ink: Color) {
    Text(selection.track.title, color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    Text(selection.track.artist, color = ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Same content-dependent width limits as iOS StoryMusicStickerArtwork. */
@Composable
private fun musicTextWidth(selection: StoryMusicSelection, limit: Int, compact: Boolean = false): androidx.compose.ui.unit.Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val platform = PlatformTextStyle(includeFontPadding = false)
    val title = measurer.measure(
        selection.track.title,
        TextStyle(fontSize = (if (compact) 14 else 17).sp, fontWeight = FontWeight.SemiBold, platformStyle = platform),
        constraints = Constraints(maxWidth = with(density) { limit.dp.roundToPx() }),
    ).size.width
    val artist = measurer.measure(
        selection.track.artist,
        TextStyle(fontSize = (if (compact) 11 else 12).sp, platformStyle = platform),
        maxLines = 1, softWrap = false,
    ).size.width
    return with(density) { (maxOf(title, artist).toDp() + 2.dp).coerceIn(1.dp, limit.dp) }
}

@Composable
private fun MusicCover(selection: StoryMusicSelection, side: Int) {
    Box(Modifier.size(side.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(.15f)), contentAlignment = Alignment.Center) {
        AsyncImage(selection.track.artworkURL, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (selection.track.artworkURL == null) Icon(Icons.Default.MusicNote, null, tint = Color.White)
    }
}
@Composable
private fun Equalizer(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val phase by transition.animateFloat(0f, 6.283f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "bars")
    Canvas(modifier.size(18.dp, 18.dp)) {
        repeat(3) { index ->
            val h = size.height * (.35f + .65f * kotlin.math.abs(kotlin.math.sin(phase + index)))
            drawLine(Color.White, Offset((index + .5f) * size.width / 3, (size.height - h) / 2), Offset((index + .5f) * size.width / 3, (size.height + h) / 2), strokeWidth = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}
