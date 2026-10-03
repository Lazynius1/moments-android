package com.moments.android.utilities

import android.content.Context
import android.media.AudioAttributes as PlatformAudioAttributes
import androidx.media3.common.AudioAttributes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Keeps muted playback focus-free and releases focus on pause, finish or disposal. */
fun ExoPlayer.withMomentsAudioFocus(context: Context): ExoPlayer = MomentsFocusExoPlayer(this, context)

private class MomentsFocusExoPlayer(private val delegate: ExoPlayer, context: Context) : ExoPlayer by delegate {
    private val session = MomentsAudioSession.lease { delegate.pause() }
    private var desiredVolume = delegate.volume
    private var released = false
    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { updateFocus() }
        override fun onPlaybackStateChanged(playbackState: Int) { updateFocus() }
        override fun onPlayerError(error: PlaybackException) { pause() }
    }

    init {
        MomentsAudioSession.initialize(context)
        delegate.setAudioAttributes(delegate.audioAttributes, false)
        delegate.addListener(listener)
    }

    private fun claimFocus(): Boolean = session.activate(
        // Routing attributes stay on the player; shared focus uses the media stream.
        usage = PlatformAudioAttributes.USAGE_MEDIA,
        contentType = delegate.audioAttributes.contentType,
    )

    private fun updateFocus() {
        if (released) return
        if (!delegate.playWhenReady || desiredVolume <= 0f ||
            delegate.playbackState == Player.STATE_ENDED || delegate.playbackState == Player.STATE_IDLE) {
            session.release()
        } else if (!claimFocus()) {
            delegate.volume = 0f
            delegate.pause()
        } else {
            delegate.volume = desiredVolume
        }
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {
        delegate.setAudioAttributes(audioAttributes, false)
    }

    private fun canOutputAudio(): Boolean = delegate.playbackState != Player.STATE_IDLE &&
        delegate.playbackState != Player.STATE_ENDED

    override fun setVolume(volume: Float) {
        desiredVolume = volume
        if (volume > 0f && delegate.playWhenReady && canOutputAudio()) {
            if (!claimFocus()) { delegate.volume = 0f; delegate.pause(); return }
        } else { session.release() }
        delegate.volume = if (delegate.playWhenReady && volume > 0f && !canOutputAudio()) 0f else volume
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady && desiredVolume > 0f && canOutputAudio() && !claimFocus()) {
            delegate.volume = 0f
            delegate.pause()
            return
        }
        delegate.volume = if (playWhenReady && !canOutputAudio()) 0f else desiredVolume
        delegate.playWhenReady = playWhenReady
        if (!playWhenReady) session.release()
    }
    override fun play() = setPlayWhenReady(true)
    override fun pause() = setPlayWhenReady(false)
    override fun stop() { delegate.stop(); session.release() }
    override fun release() {
        if (released) return
        released = true
        delegate.pause()
        delegate.removeListener(listener)
        session.release()
        delegate.release()
    }
}
