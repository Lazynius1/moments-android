package com.moments.android.views.feed.video

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Port de `VideoPlaybackChromeStyle.swift`. */
enum class VideoPlaybackChromeKind {
    Classic,
    SocialReels,
}

object VideoPlaybackChromeStyle {
    val playButtonBackground = Color.Black.copy(alpha = 0.42f)
    // iOS CroppedVideoPlayer.muteToggleButton: .black.opacity(0.48) + padding 10
    val muteButtonBackground = Color.Black.copy(alpha = 0.48f)
    val progressBarHeight = 2.dp
    val progressTrackColor = Color.White.copy(alpha = 0.3f)
    val progressActiveColor = Color.White
    val playButtonSize = 48.dp
    val muteButtonSize = 35.dp
    val muteIconSize = 15.dp
}
