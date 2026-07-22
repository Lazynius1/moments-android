package com.moments.android.services.video

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.moments.android.services.cache.VideoPreloader

/**
 * Port de ReelPrebufferService.swift.
 * Mantiene el siguiente reel bufferizado en un player mudo y pausado.
 */
object ReelPrebufferService {

    private var warmPlayer: ExoPlayer? = null
    private var preparedUrlString: String? = null
    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (warmPlayer != null) return
        appContext = context.applicationContext
        warmPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
            volume = 0f
            playWhenReady = false
        }
    }

    fun prebuffer(urlString: String) {
        ensureInitialized()
        if (preparedUrlString == urlString) return
        val item = VideoPreloader.getPlayerItem(urlString)
        val player = warmPlayer ?: return
        VideoPlaybackSelector.configure(
            player = player,
            tier = VideoPlaybackSelector.recommendedTier(),
            isActivelyPlaying = false,
        )
        player.setMediaItem(item)
        player.prepare()
        player.pause()
        preparedUrlString = urlString
    }

    fun takePreparedItem(forUrlString: String): MediaItem? {
        ensureInitialized()
        val player = warmPlayer ?: return null
        if (preparedUrlString != forUrlString) return null
        if (player.playerError != null) return null
        val item = player.currentMediaItem ?: return null
        player.clearMediaItems()
        preparedUrlString = null
        return item
    }

    fun discard() {
        warmPlayer?.clearMediaItems()
        preparedUrlString = null
    }

    private fun ensureInitialized() {
        check(warmPlayer != null) {
            "ReelPrebufferService.initialize(context) debe llamarse antes de usarlo"
        }
    }
}
