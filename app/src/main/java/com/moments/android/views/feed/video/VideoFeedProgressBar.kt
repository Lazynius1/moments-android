package com.moments.android.views.feed.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Port de `VideoFeedProgressBar.swift`. */
@Composable
fun VideoFeedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(VideoPlaybackChromeStyle.progressBarHeight)
            .background(VideoPlaybackChromeStyle.progressTrackColor),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(VideoPlaybackChromeStyle.progressBarHeight)
                .background(VideoPlaybackChromeStyle.progressActiveColor),
        )
    }
}
