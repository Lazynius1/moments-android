package com.moments.android.views.feed.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moments.android.R

/**
 * Cómo decide el reproductor cuándo reproducirse automáticamente.
 * Port de `VideoPlaybackActivationMode` (VideoPlaybackChromeStyle.swift).
 */
enum class VideoPlaybackActivationMode {
    /** Feed / detalle perfil: solo si `FeedVisibilityCoordinator` lo marca activo. */
    FeedVisibility,
    /** Hero de perfil y previews: reproduce al aparecer. */
    AlwaysWhenVisible,
}

/**
 * Estilo de controles del reproductor (moderno vs clásico).
 * Port de `VideoPlaybackChromeStyle` (enum en Swift).
 */
enum class VideoPlaybackChromeStyle {
    /** Barra de progreso, mute persistente, overlay play grande al tap. */
    Classic,
    /** Sin barra; tap = pausa; mute + play pequeños solo cuando está pausado. */
    SocialReels,
}

/**
 * Controles centrados al pausar.
 * Port de `SocialVideoPausedControls` (VideoPlaybackChromeStyle.swift).
 */
@Composable
fun SocialVideoPausedControls(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val unmuteLabel = stringResource(R.string.feed_video_unmute)
    val muteLabel = stringResource(R.string.feed_video_mute)
    val playLabel = stringResource(R.string.feed_video_play)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .semantics {
                        contentDescription = if (isMuted) unmuteLabel else muteLabel
                    }
                    .clickable(onClick = onToggleMute),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .semantics { contentDescription = playLabel }
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
