package com.moments.android.views.feed.video

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.moments.android.models.MediaItem as MomentsMediaItem
import com.moments.android.services.cache.VideoPreloader
import com.moments.android.services.performance.VideoMoment
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.services.video.ReelPrebufferService
import com.moments.android.services.video.SharedVideoPlayerPool
import com.moments.android.services.video.VideoAdaptiveTierController
import com.moments.android.services.video.VideoLayerLease
import com.moments.android.services.video.VideoPlaybackRecovery
import com.moments.android.services.video.VideoPlaybackSelector
import com.moments.android.services.video.configure
import com.moments.android.services.video.makeConfiguredPlayerItem
import com.moments.android.services.video.videoPlaybackSource
import com.moments.android.utilities.MomentsAudioSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Port 1:1 de `ReelVideoPlayerManager` (Reels.swift ~L1143–1530).
 * ExoPlayer ↔ AVPlayer; pool + ReelPrebuffer + startAtSeconds.
 */
class ReelVideoPlayerManager {
    var player: ExoPlayer? by mutableStateOf(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isMuted by mutableStateOf(true)
    var progress by mutableDoubleStateOf(0.0)
    var duration by mutableDoubleStateOf(0.0)
    var isBuffering by mutableStateOf(false)
    var isLoaded by mutableStateOf(false)

    private var playerItem: MediaItem? = null
    private var isSeeking = false
    private var pendingStartAtSeconds: Double? = null
    private var adaptiveController: VideoAdaptiveTierController? = null
    var consumerId: String? = null
        private set
    private var leaseGeneration: Long = 0
    private var appContext: Context? = null

    private var playerListener: Player.Listener? = null
    private var timeObserverJob: Job? = null
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun setupPlayer(
        video: VideoMoment,
        startAtSeconds: Double = 0.0,
        appContext: Context? = null,
        handoffConsumerId: String? = null,
    ) {
        this.appContext = appContext?.applicationContext ?: this.appContext
        val moment = video.moment
        val newConsumerId = if (!handoffConsumerId.isNullOrBlank()) {
            handoffConsumerId
        } else {
            GlobalVideoManager.profileVideoConsumerId(moment)
        }

        if (consumerId != null && consumerId != newConsumerId) {
            val preserve = GlobalVideoManager.shouldPreserveSharedPlayer(consumerId!!)
            cleanup(releaseFromPool = !preserve)
        } else if (consumerId != null) {
            teardownObserversOnly(pausePlayback = false)
        }

        consumerId = newConsumerId
        leaseGeneration = VideoLayerLease.generation
        pendingStartAtSeconds = if (startAtSeconds > 0) startAtSeconds else null

        val mediaItem = moment.primaryVisibleMediaItem
        val source = moment.videoPlaybackSource()
        val playbackUrl = source?.playbackUrl ?: video.playbackUrl
        if (playbackUrl.isNullOrBlank()) return

        adaptiveController = if (mediaItem != null && mediaItem.type == MomentsMediaItem.MediaType.VIDEO) {
            VideoAdaptiveTierController(
                mediaItem = mediaItem,
                moment = moment,
                initialTier = source?.tier,
            )
        } else {
            null
        }

        SharedVideoPlayerPool.setEvictionHandler(newConsumerId) {
            handlePoolEviction()
        }

        val pooledPlayer = SharedVideoPlayerPool.player(newConsumerId)
        val reuseExistingItem =
            SharedVideoPlayerPool.hasActiveItem(newConsumerId) && pooledPlayer.playerError == null

        if (reuseExistingItem) {
            playerItem = pooledPlayer.currentMediaItem
            player = pooledPlayer
            isLoaded = pooledPlayer.playbackState == Player.STATE_READY
            applySessionMuteState()
            configureAudioSession()
            observePlayerItem()
            setupLooping()
            observePlayback()
            if (isLoaded) {
                applyPendingStartAndPlayIfNeeded()
            }
            return
        }

        val prepared = ReelPrebufferService.takePreparedItem(forUrlString = playbackUrl)
        playerItem = prepared
            ?: if (mediaItem != null) {
                val tier = adaptiveController?.currentTier ?: VideoPlaybackSelector.recommendedTier()
                VideoPlaybackSelector.makeConfiguredPlayerItem(mediaItem, moment, tier)
                    ?: VideoPreloader.getPlayerItem(playbackUrl)
            } else {
                VideoPreloader.getPlayerItem(playbackUrl)
            }

        val tier = adaptiveController?.currentTier ?: VideoPlaybackSelector.recommendedTier()
        VideoPlaybackSelector.configure(pooledPlayer, tier, isActivelyPlaying = true)

        pooledPlayer.setMediaItem(playerItem!!)
        pooledPlayer.prepare()
        player = pooledPlayer
        isLoaded = pooledPlayer.playbackState == Player.STATE_READY
        applySessionMuteState()
        configureAudioSession()
        observePlayerItem()
        setupLooping()
        observePlayback()
    }

    private fun handlePoolEviction() {
        stopTimeObserver()
        teardownPlayerListener()
        adaptiveController = null
        player = null
        playerItem = null
        consumerId = null
        isPlaying = false
        isLoaded = false
        isBuffering = false
        progress = 0.0
        duration = 0.0
    }

    private fun teardownObserversOnly(pausePlayback: Boolean = true) {
        if (pausePlayback) {
            val id = consumerId
            val preserve = id?.let { GlobalVideoManager.shouldPreserveSharedPlayer(it) } ?: false
            if (!preserve) {
                player?.pause()
                isPlaying = false
            }
        }
        stopTimeObserver()
        teardownPlayerListener()
        adaptiveController = null
        progress = 0.0
        duration = 0.0
        isBuffering = false
        isLoaded = false
        isSeeking = false
        pendingStartAtSeconds = null
    }

    fun updateProgress(to: Double) {
        progress = to
    }

    fun seekToProgress(targetProgress: Double, precise: Boolean = false) {
        val exo = player ?: return
        if (duration <= 0) return
        val targetMs = (targetProgress * duration * 1000).toLong()
        isSeeking = true
        if (precise) {
            exo.seekTo(targetMs)
            isSeeking = false
        } else {
            // Seek rápido durante scrub (tolerancia amplia en iOS)
            exo.seekTo(targetMs)
        }
    }

    private fun configureAudioSession() {
        managerScope.launch {
            MomentsAudioSession.activate()
        }
    }

    private fun observePlayerItem() {
        val exo = player ?: return
        teardownPlayerListener()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isLoaded = true
                        isBuffering = false
                        if (exo.duration > 0) {
                            duration = exo.duration / 1000.0
                        }
                        applyPendingStartAndPlayIfNeeded()
                    }
                    Player.STATE_BUFFERING -> {
                        if (isPlaying && !isSeeking) {
                            isBuffering = true
                            recoverFromPlaybackStall()
                        }
                    }
                    Player.STATE_ENDED -> {
                        // setupLooping
                        pendingStartAtSeconds = null
                        exo.seekTo(0)
                        exo.play()
                        isPlaying = true
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                if (!isLoading && exo.playbackState == Player.STATE_READY) {
                    isBuffering = false
                    adaptiveController?.notePlaybackHealthy()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isBuffering = false
            }
        }
        playerListener = listener
        exo.addListener(listener)
    }

    private fun teardownPlayerListener() {
        val exo = player
        playerListener?.let { exo?.removeListener(it) }
        playerListener = null
    }

    private fun recoverFromPlaybackStall() {
        val exo = player ?: return
        VideoPlaybackRecovery.recoverFromStall(
            player = exo,
            isPlaying = isPlaying,
            adaptive = adaptiveController,
            onTierDowngrade = {
                isLoaded = false
                isBuffering = true
            },
        ) { newItem ->
            playerItem = newItem
            teardownPlayerListener()
            observePlayerItem()
            setupLooping()
        }
    }

    private fun setupLooping() {
        // Cubierto por STATE_ENDED en el listener (paridad AVPlayerItemDidPlayToEndTime).
    }

    private fun applyPendingStartAndPlayIfNeeded() {
        if (!isCurrentLeaseGeneration()) return
        val exo = player
        if (exo == null) {
            play()
            return
        }
        val startAt = pendingStartAtSeconds
        if (startAt == null) {
            play()
            return
        }
        val bounded = startAt.coerceAtLeast(0.0)
        pendingStartAtSeconds = null
        val current = exo.currentPosition / 1000.0
        if (current.isFinite() && kotlin.math.abs(current - bounded) < 0.35) {
            play()
            return
        }
        val generation = leaseGeneration
        exo.seekTo((bounded * 1000).toLong())
        if (generation == leaseGeneration && isCurrentLeaseGeneration()) {
            play()
        }
    }

    private fun isCurrentLeaseGeneration(): Boolean =
        leaseGeneration == VideoLayerLease.generation

    fun togglePlayback() {
        val exo = player ?: return
        if (!isLoaded) return
        if (isPlaying) {
            exo.pause()
            isPlaying = false
        } else {
            exo.play()
            isPlaying = true
        }
    }

    fun play() {
        if (!isCurrentLeaseGeneration()) return
        val exo = player ?: return
        if (!isLoaded) return
        exo.play()
        isPlaying = true
    }

    fun pause() {
        val exo = player ?: return
        exo.pause()
        isPlaying = false
    }

    fun toggleMute() {
        val exo = player ?: return
        if (isMuted && isDeviceVolumeZero()) return

        val wasMuted = isMuted
        isMuted = !isMuted
        exo.volume = if (isMuted) 0f else 1f

        if (wasMuted && !isMuted) {
            GlobalVideoManager.enableSoundForSession()
        } else if (!wasMuted && isMuted) {
            GlobalVideoManager.disableSoundForSession()
        }
    }

    private fun applySessionMuteState() {
        isMuted = GlobalVideoManager.isSessionMuted()
        player?.volume = if (isMuted) 0f else 1f
    }

    private fun observePlayback() {
        stopTimeObserver()
        val exo = player ?: return
        timeObserverJob = managerScope.launch {
            while (isActive) {
                val p = player
                if (p == null || p !== exo) break
                if (!isSeeking) {
                    val durMs = p.duration
                    if (durMs > 0) {
                        val durationSeconds = durMs / 1000.0
                        val currentSeconds = p.currentPosition / 1000.0
                        if (durationSeconds.isFinite() && currentSeconds.isFinite() && durationSeconds > 0) {
                            duration = durationSeconds
                            progress = (currentSeconds / durationSeconds).coerceIn(0.0, 1.0)
                        }
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopTimeObserver() {
        timeObserverJob?.cancel()
        timeObserverJob = null
    }

    private fun isDeviceVolumeZero(): Boolean {
        val ctx = appContext ?: return false
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
    }

    fun cleanup(releaseFromPool: Boolean = true) {
        val id = consumerId
        val shouldPreserve = id?.let { GlobalVideoManager.shouldPreserveSharedPlayer(it) } ?: false
        val handoffConsumer = id != null && VideoLayerLease.isHandoffConsumer(id)
        val keepPlayback = shouldPreserve || handoffConsumer
        teardownObserversOnly(pausePlayback = !keepPlayback)

        val actuallyRelease = releaseFromPool && !keepPlayback

        if (id != null && actuallyRelease) {
            SharedVideoPlayerPool.release(id)
        }

        player = null
        playerItem = null
        consumerId = null
        isMuted = GlobalVideoManager.isSessionMuted()
    }
}
