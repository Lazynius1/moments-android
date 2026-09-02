package com.moments.android.views.feed.video

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MediaItem as MomentsMediaItem
import com.moments.android.models.Moment
import com.moments.android.models.VideoPlaybackTier
import com.moments.android.services.cache.VideoPreloader
import com.moments.android.services.performance.FeedVisibilityCoordinator
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.services.video.RegisteredVideoPlayer
import com.moments.android.services.video.SharedVideoPlayerPool
import com.moments.android.services.video.VideoAdaptiveTierController
import com.moments.android.services.video.VideoLayerLease
import com.moments.android.services.video.VideoLayerRole
import com.moments.android.services.video.VideoPlaybackRecovery
import com.moments.android.services.video.VideoPlaybackSelector
import com.moments.android.services.video.VideoPlaybackSource
import com.moments.android.services.video.configure
import com.moments.android.services.video.makeConfiguredPlayerItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Port de `ModernVideoPlayer` (VideoPlayer.swift ~L249–674).
 * `VideoPlayerManager` aquí es bridge mínimo; trozo 3 completa adaptive/observers.
 */
@Composable
fun ModernVideoPlayer(
    url: String,
    videoId: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 9f / 16f,
    hideMuteButton: Boolean = false,
    chromeStyle: VideoPlaybackChromeStyle = VideoPlaybackChromeStyle.Classic,
    allowsPauseInteraction: Boolean = true,
    posterUrl: String? = null,
    mediaItem: MomentsMediaItem? = null,
    moment: Moment? = null,
    activationMode: VideoPlaybackActivationMode = VideoPlaybackActivationMode.FeedVisibility,
    consumesDetailHandoff: Boolean = true,
    /** Tap externo cuando `allowsPauseInteraction == false` (CroppedVideoPlayer / reels). */
    onExternalTap: (() -> Unit)? = null,
    /** Mute persistente estilo CroppedVideoPlayer (bottomLeading) — no está en ModernVideoPlayer iOS. */
    showCroppedMuteButton: Boolean = false,
) {
    val context = LocalContext.current
    val usesSocialChrome = chromeStyle == VideoPlaybackChromeStyle.SocialReels
    val playerManager = remember(videoId) { VideoPlayerManager() }

    var showControls by remember(videoId) { mutableStateOf(false) }
    var showMuteButton by remember(videoId) { mutableStateOf(true) }
    var progress by remember(videoId) { mutableDoubleStateOf(0.0) }
    var isBuffering by remember(videoId) { mutableStateOf(false) }
    var hasSetupPlayer by remember(videoId) { mutableStateOf(false) }
    var isVisible by remember(videoId) { mutableStateOf(false) }
    var setupRetries by remember(videoId) { mutableIntStateOf(0) }
    var setupGeneration by remember(videoId) { mutableIntStateOf(0) }
    var hasLoadError by remember(videoId) { mutableStateOf(false) }
    var preferMp4Fallback by remember(videoId) { mutableStateOf(false) }
    var hasRenderedFirstFrame by remember(videoId) { mutableStateOf(false) }

    val activeMomentId by FeedVisibilityCoordinator.activeVideoMomentIdFlow.collectAsState()
    val warmingMomentId by FeedVisibilityCoordinator.warmingVideoMomentIdFlow.collectAsState()
    val soundEnabled by GlobalVideoManager.userHasEnabledSoundInSession.collectAsState()
    val isPlaybackHeld by GlobalVideoManager.isPlaybackHeld.collectAsState()

    // Sync mute UI con sesión si el manager aún no está registrado
    LaunchedEffect(soundEnabled) {
        if (playerManager.player == null) return@LaunchedEffect
        playerManager.setMuted(!soundEnabled, respectSilentMode = true)
    }

    fun togglePlayback() {
        if (playerManager.hasFinishedPlayback) {
            GlobalVideoManager.replayFromStart(videoId)
            return
        }
        if (playerManager.isPlaying) {
            GlobalVideoManager.pauseVideo(videoId)
        } else {
            GlobalVideoManager.playVideo(videoId)
        }
    }

    fun updatePlaybackForVisibility(activeId: String?) {
        if (!isVisible) return
        if (GlobalVideoManager.visibilityMatches(activeId, videoId)) {
            GlobalVideoManager.playVideo(videoId)
        } else {
            GlobalVideoManager.pauseVideo(videoId)
        }
    }

    fun applyActivationMode(activeId: String?) {
        if (GlobalVideoManager.isPlaybackHeld.value) {
            GlobalVideoManager.pauseVideo(videoId)
            return
        }
        when (activationMode) {
            VideoPlaybackActivationMode.FeedVisibility -> updatePlaybackForVisibility(activeId)
            VideoPlaybackActivationMode.AlwaysWhenVisible -> {
                if (!isVisible) return
                GlobalVideoManager.playVideo(videoId)
            }
        }
    }

    fun resolvedPlaybackSource(): VideoPlaybackSource? {
        if (mediaItem != null) {
            VideoPlaybackSelector.source(forItem = mediaItem, moment = moment)?.let { return it }
        }
        if (moment != null) {
            VideoPlaybackSelector.source(forMoment = moment)?.let { return it }
        }
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val normalized = trimmed.replace(" ", "%20")
        return VideoPlaybackSource(
            playbackUrl = normalized,
            tier = VideoPlaybackSelector.recommendedTier(),
            preheatUrlStrings = listOf(normalized),
        )
    }

    fun setupPlayer() {
        val source = resolvedPlaybackSource()
        if (source == null) {
            hasLoadError = true
            return
        }
        val playbackUrl = if (preferMp4Fallback) {
            source.fallbackMp4Url ?: source.playbackUrl
        } else {
            source.playbackUrl
        }
        hasLoadError = false
        hasRenderedFirstFrame = false
        setupGeneration += 1

        // ≡ iOS consumeProfileDetailHandoff(forMomentId: profileVideoConsumerId(for: moment))
        val handoff = if (consumesDetailHandoff) {
            moment?.id?.takeIf { it.isNotBlank() }?.let {
                GlobalVideoManager.consumeProfileDetailHandoff(it)
            }
        } else {
            null
        }
        val reuse = handoff?.reuseExistingItem
            ?: GlobalVideoManager.canReuseSharedPlayer(videoId)
        val startAt = handoff?.startAtSeconds?.takeIf { it > 0.05 }

        SharedVideoPlayerPool.initialize(context)
        playerManager.setupPlayer(
            url = playbackUrl,
            consumerId = videoId,
            startAtSeconds = startAt,
            reuseExistingItem = reuse,
            mediaItem = mediaItem,
            moment = moment,
            initialTier = source.tier,
            appContext = context.applicationContext,
        )
        hasSetupPlayer = true

        if (activationMode == VideoPlaybackActivationMode.AlwaysWhenVisible) {
            GlobalVideoManager.playVideo(videoId)
        }
    }

    fun forceReloadPlayer() {
        hasLoadError = false
        setupRetries = 0
        preferMp4Fallback = false
        hasSetupPlayer = false
        playerManager.cleanup(releaseFromPool = true)
        setupPlayer()
        if (isVisible) applyActivationMode(activeMomentId)
    }

    fun handleTap() {
        if (!allowsPauseInteraction) {
            onExternalTap?.invoke()
            return
        }
        if (usesSocialChrome) {
            if (!hasSetupPlayer) {
                setupPlayer()
                GlobalVideoManager.registerPlayer(videoId, playerManager)
            }
            togglePlayback()
            return
        }
        showControls = !showControls
        showMuteButton = true
    }

    // Appear / disappear — paridad iOS: no montar ExoPlayer en el mismo gesto del scroll.
    // FeedVisibility: solo prepare cuando somos el activo (debounce 90ms).
    DisposableEffect(videoId, url) {
        isVisible = true
        if (activationMode == VideoPlaybackActivationMode.AlwaysWhenVisible) {
            setupPlayer()
            GlobalVideoManager.registerPlayer(videoId, playerManager)
            applyActivationMode(FeedVisibilityCoordinator.activeVideoMomentId)
        } else {
            applyActivationMode(FeedVisibilityCoordinator.activeVideoMomentId)
        }
        onDispose {
            if (GlobalVideoManager.shouldPreserveSharedPlayer(videoId)) {
                return@onDispose
            }
            isVisible = false
            if (GlobalVideoManager.isRegisteredPlayer(videoId, playerManager)) {
                GlobalVideoManager.pauseVideo(videoId)
                GlobalVideoManager.unregisterPlayer(videoId, playerManager)
            }
            playerManager.cleanup(releaseFromPool = true)
            hasSetupPlayer = false
            hasLoadError = false
            setupRetries = 0
            preferMp4Fallback = false
            setupGeneration += 1
        }
    }

    LaunchedEffect(activeMomentId, warmingMomentId, isVisible, activationMode, isPlaybackHeld) {
        if (activationMode == VideoPlaybackActivationMode.FeedVisibility && isVisible && !isPlaybackHeld) {
            val shouldWarm = GlobalVideoManager.visibilityMatches(warmingMomentId, videoId)
            val shouldPlay = GlobalVideoManager.visibilityMatches(activeMomentId, videoId)
            if ((shouldWarm || shouldPlay) && !hasSetupPlayer) {
                setupPlayer()
                GlobalVideoManager.registerPlayer(videoId, playerManager)
            }
        }
        applyActivationMode(activeMomentId)
    }

    // Setup timeout retries
    LaunchedEffect(setupGeneration, hasSetupPlayer, isVisible) {
        if (!hasSetupPlayer || !isVisible) return@LaunchedEffect
        val gen = setupGeneration
        delay(4_000)
        if (!isVisible || gen != setupGeneration) return@LaunchedEffect
        if (playerManager.player == null) return@LaunchedEffect
        if (playerManager.isReadyToPlay || playerManager.currentTime > 0.05) return@LaunchedEffect
        val source = resolvedPlaybackSource()
        if (!preferMp4Fallback && source?.isHls == true && !source.fallbackMp4Url.isNullOrBlank()) {
            preferMp4Fallback = true
            hasSetupPlayer = false
            playerManager.cleanup(releaseFromPool = true)
            setupPlayer()
            return@LaunchedEffect
        }
        if (setupRetries < 2) {
            setupRetries += 1
            hasSetupPlayer = false
            playerManager.cleanup(releaseFromPool = true)
            setupPlayer()
        } else {
            hasLoadError = true
        }
    }

    // Progress solo mientras este player está montado y activo (evita 200ms × N cards).
    LaunchedEffect(videoId, hasSetupPlayer, activeMomentId) {
        if (!hasSetupPlayer) return@LaunchedEffect
        while (isActive) {
            val isActiveVideo = activationMode == VideoPlaybackActivationMode.AlwaysWhenVisible ||
                GlobalVideoManager.visibilityMatches(activeMomentId, videoId)
            if (!isActiveVideo) {
                delay(400)
                continue
            }
            val p = playerManager.player
            if (p != null && p.duration > 0) {
                val cur = p.currentPosition / 1000.0
                val dur = p.duration / 1000.0
                playerManager.currentTime = cur
                playerManager.duration = dur
                progress = (cur / dur).coerceIn(0.0, 1.0)
                if (usesSocialChrome) {
                    GlobalVideoManager.setPlaybackPosition(cur, videoId)
                }
                isBuffering = p.playbackState == Player.STATE_BUFFERING
            }
            delay(250)
        }
    }

    // Auto-hide classic controls
    LaunchedEffect(showControls) {
        if (showControls && !usesSocialChrome) {
            delay(3_000)
            showControls = false
        }
    }
    LaunchedEffect(showMuteButton) {
        if (showMuteButton && !usesSocialChrome && !hideMuteButton) {
            delay(4_000)
            showMuteButton = false
        }
    }

    val playerReady = playerManager.player != null && !hasLoadError
    val contentScale = if (usesSocialChrome) ContentScale.Crop else ContentScale.Fit
    val resizeMode = if (usesSocialChrome) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(
        modifier
            .fillMaxWidth()
            .then(if (aspectRatio > 0f) Modifier.aspectRatio(aspectRatio) else Modifier.fillMaxSize())
            .clickable {
                handleTap()
            },
    ) {
        if (playerReady) {
            VideoPlayerRepresentable(
                player = playerManager.player!!,
                resizeMode = resizeMode,
                consumerId = videoId,
                layerRole = VideoLayerRole.Feed,
                onProgress = { progress = it },
                onBuffering = { isBuffering = it },
                onFirstFrameRendered = { hasRenderedFirstFrame = true },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(0.dp)),
            )
            VideoPosterOverlay(
                posterUrl = posterUrl,
                isReadyToPlay = GlobalVideoManager.shouldPreserveSharedPlayer(videoId) ||
                    (playerManager.isReadyToPlay && hasRenderedFirstFrame),
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize()) {
                VideoPosterOverlay(
                    posterUrl = posterUrl,
                    isReadyToPlay = false,
                    modifier = Modifier.fillMaxSize(),
                )
                ModernLoadingView(
                    hasLoadError = hasLoadError,
                    aspectRatio = minOf(aspectRatio, 0.8f),
                    onRetry = { forceReloadPlayer() },
                )
            }
        }

        if (usesSocialChrome) {
            if (!playerManager.hasFinishedPlayback &&
                !playerManager.isPlaying &&
                allowsPauseInteraction
            ) {
                SocialVideoPausedControls(
                    isMuted = playerManager.isMuted,
                    onToggleMute = { GlobalVideoManager.toggleMute(videoId) },
                    onTogglePlay = { togglePlayback() },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (showCroppedMuteButton && !playerManager.hasFinishedPlayback) {
                CroppedMuteButton(
                    isMuted = playerManager.isMuted,
                    onToggle = { GlobalVideoManager.toggleMute(videoId) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                )
            }
        } else {
            ClassicControlsOverlay(
                showControls = showControls,
                isPlaying = playerManager.isPlaying,
                isBuffering = isBuffering,
                onTogglePlay = { togglePlayback() },
            )
            if (!hideMuteButton) {
                AnimatedVisibility(
                    visible = showMuteButton,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                ) {
                    ClassicMuteButton(
                        isMuted = playerManager.isMuted,
                        onToggle = { GlobalVideoManager.toggleMute(videoId) },
                    )
                }
            }
            if (playerManager.duration > 0) {
                VideoFeedProgressBar(
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Compat call site (carousel / messaging) → ModernVideoPlayer socialReels.
 */
@Composable
fun FeedVideoPage(
    url: String,
    thumbnailUrl: String?,
    consumerId: String,
    modifier: Modifier = Modifier,
    allowsPlayback: Boolean = true,
    allowsPauseInteraction: Boolean = true,
    showMute: Boolean = true,
    onTap: (() -> Unit)? = null,
    mediaItem: MomentsMediaItem? = null,
    moment: Moment? = null,
) {
    if (!allowsPlayback) {
        Box(modifier.fillMaxSize()) {
            VideoPosterOverlay(
                posterUrl = thumbnailUrl,
                isReadyToPlay = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    ModernVideoPlayer(
        url = url,
        videoId = consumerId,
        modifier = modifier.fillMaxSize(),
        aspectRatio = 0f, // el padre delimita (carousel); sin aspectRatio forzado
        chromeStyle = VideoPlaybackChromeStyle.SocialReels,
        allowsPauseInteraction = allowsPauseInteraction,
        posterUrl = thumbnailUrl,
        mediaItem = mediaItem,
        moment = moment,
        onExternalTap = onTap,
        showCroppedMuteButton = showMute,
    )
}

// MARK: - Loading / classic chrome

@Composable
private fun ModernLoadingView(
    hasLoadError: Boolean,
    aspectRatio: Float,
    onRetry: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        if (hasLoadError) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.WifiOff, null, tint = Color.White.copy(0.9f), modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.feed_video_load_error),
                    color = Color.White.copy(0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.feed_video_retry),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(0.18f))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        } else {
            HaloLoadingView(cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
            Text(
                stringResource(R.string.feed_video_loading),
                color = Color.White.copy(0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ClassicControlsOverlay(
    showControls: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onTogglePlay: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.3f)))
            Box(
                Modifier
                    .size(80.dp)
                    .shadow(10.dp, CircleShape)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.feed_video_pause else R.string.feed_video_play,
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(50.dp),
                )
            }
        }
        if (isBuffering) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
        }
        if (isBuffering && !showControls) {
            HaloLoadingView(cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ClassicMuteButton(isMuted: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .shadow(5.dp, CircleShape)
            .momentsChromeGlass(CircleShape, interactive = true)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
            contentDescription = stringResource(if (isMuted) R.string.feed_video_mute else R.string.feed_video_unmute),
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CroppedMuteButton(
    isMuted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(35.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.48f))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
            contentDescription = stringResource(if (isMuted) R.string.feed_video_mute else R.string.feed_video_unmute),
            tint = Color.White,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** Port de `HaloLoadingView` (VideoPlayer.swift). */
@Composable
fun HaloLoadingView(
    cornerRadius: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "halo")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "haloRot",
    )
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .rotate(rotation)
                .border(
                    width = 4.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF9C27B0).copy(0f),
                            Color(0xFF9C27B0).copy(0.4f),
                            Color(0xFFE91E63),
                            Color(0xFFFF9800),
                            Color(0xFFE91E63),
                            Color(0xFF9C27B0).copy(0.4f),
                            Color(0xFF9C27B0).copy(0f),
                        ),
                    ),
                    shape = RoundedCornerShape(cornerRadius),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, Color(0xFFE91E63).copy(0.3f), RoundedCornerShape(cornerRadius))
                .blur(4.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// VideoPlayerRepresentable + VideoPlayerManager (VideoPlayer.swift L677–1099)
// ---------------------------------------------------------------------------

/**
 * Port de `VideoPlayerRepresentable` — PlayerView + ticks de progress/buffering.
 *
 * Feed/detalle: [texture_view] (mismo layout que stories). SurfaceView default
 * ignora `Modifier.clip` y el vídeo “invade” la card siguiente.
 */
@Composable
fun VideoPlayerRepresentable(
    player: ExoPlayer,
    resizeMode: Int,
    onProgress: (Double) -> Unit,
    onBuffering: (Boolean) -> Unit,
    onFirstFrameRendered: () -> Unit = {},
    modifier: Modifier = Modifier,
    consumerId: String = "",
    layerRole: VideoLayerRole = VideoLayerRole.Feed,
) {
    val leaseRev by VideoLayerLease.revision.collectAsState()
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering = playbackState == Player.STATE_BUFFERING
                onBuffering(buffering)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) onBuffering(false)
            }
            override fun onRenderedFirstFrame() {
                onFirstFrameRendered()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (isActive) {
            val dur = player.duration
            if (dur > 0) {
                val cur = player.currentPosition.toDouble()
                onProgress((cur / dur).coerceIn(0.0, 1.0))
            }
            val buffering = player.playbackState == Player.STATE_BUFFERING
            onBuffering(buffering)
            delay(250)
        }
    }

    AndroidView(
        factory = { ctx ->
            (android.view.LayoutInflater.from(ctx)
                .inflate(com.moments.android.R.layout.story_player_view, null, false)
                as PlayerView).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                this.resizeMode = resizeMode
                if (layerRole == VideoLayerRole.Reels) {
                    VideoLayerLease.claimReels(consumerId)
                }
                applyLayerLease(this, player, layerRole, consumerId)
            }
        },
        update = { view ->
            view.resizeMode = resizeMode
            if (layerRole == VideoLayerRole.Reels) {
                VideoLayerLease.claimReels(consumerId)
            }
            applyLayerLease(view, player, layerRole, consumerId)
            @Suppress("UNUSED_EXPRESSION")
            leaseRev
        },
        modifier = modifier,
    )
}

private fun applyLayerLease(
    view: PlayerView,
    player: ExoPlayer,
    role: VideoLayerRole,
    consumerId: String,
) {
    if (VideoLayerLease.mayAttach(role, consumerId)) {
        VideoPlayerViewHandoff.attach(view, player, role, consumerId)
    } else if (view.player != null) {
        view.player = null
    }
}

/**
 * Media3 conecta primero el nuevo PlayerView y después libera el anterior.
 * Evita el frame negro que produce `old.player = null; new.player = player`.
 */
private object VideoPlayerViewHandoff {
    private data class Targets(
        var feed: WeakReference<PlayerView>? = null,
        var reels: WeakReference<PlayerView>? = null,
    )

    private val targets = ConcurrentHashMap<String, Targets>()

    fun attach(
        target: PlayerView,
        player: ExoPlayer,
        role: VideoLayerRole,
        consumerId: String,
    ) {
        if (consumerId.isBlank()) {
            if (target.player !== player) target.player = player
            return
        }

        val previous = synchronized(targets) {
            targets.values.forEach { entry ->
                if (entry.feed?.get() === target) entry.feed = null
                if (entry.reels?.get() === target) entry.reels = null
            }
            val entry = targets.getOrPut(consumerId) { Targets() }
            val previousTarget = when (role) {
                VideoLayerRole.Feed -> entry.reels?.get()
                VideoLayerRole.Reels -> entry.feed?.get()
            }
            when (role) {
                VideoLayerRole.Feed -> entry.feed = WeakReference(target)
                VideoLayerRole.Reels -> entry.reels = WeakReference(target)
            }
            previousTarget
        }

        when {
            target.player === player -> Unit
            previous != null && previous !== target && previous.player === player ->
                PlayerView.switchTargetView(player, previous, target)
            else -> target.player = player
        }
    }

    fun switchToFeed(consumerId: String) {
        if (consumerId.isBlank() || !SharedVideoPlayerPool.hasPlayer(consumerId)) return
        val player = SharedVideoPlayerPool.player(consumerId)
        val pair = synchronized(targets) {
            val entry = targets[consumerId] ?: return
            entry.reels?.get() to entry.feed?.get()
        }
        val reels = pair.first
        val feed = pair.second ?: return
        when {
            feed.player === player -> Unit
            reels != null && reels.player === player ->
                PlayerView.switchTargetView(player, reels, feed)
            else -> feed.player = player
        }
    }
}

fun switchVideoSurfaceToFeed(consumerId: String) {
    VideoPlayerViewHandoff.switchToFeed(consumerId)
}

/**
 * Port 1:1 de `VideoPlayerManager` (VideoPlayer.swift ~L677–1023).
 * Misma estructura de métodos que iOS; ExoPlayer ↔ AVPlayer.
 */
class VideoPlayerManager : RegisteredVideoPlayer {
    var player: ExoPlayer? by mutableStateOf(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    override var isMuted by mutableStateOf(true)
        private set
    var isReadyToPlay by mutableStateOf(false)
        private set
    var duration by mutableDoubleStateOf(0.0)
    var currentTime by mutableDoubleStateOf(0.0)
    var hasFinishedPlayback by mutableStateOf(false)

    private var consumerId: String? = null
    /** ≡ iOS `activeItem` — MediaItem cargado en el ExoPlayer. */
    private var activeItem: androidx.media3.common.MediaItem? = null
    private var pendingSeekSeconds: Double? = null
    private var adaptiveController: VideoAdaptiveTierController? = null
    private var lastPublishedTime: Double = -1.0
    private var appContext: android.content.Context? = null

    private var playerListener: Player.Listener? = null
    private var timeObserverJob: Job? = null
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun setupPlayer(
        url: String,
        consumerId: String,
        startAtSeconds: Double? = null,
        reuseExistingItem: Boolean = false,
        mediaItem: MomentsMediaItem? = null,
        moment: Moment? = null,
        initialTier: VideoPlaybackTier? = null,
        appContext: android.content.Context? = null,
    ) {
        this.consumerId = consumerId
        this.appContext = appContext
        pendingSeekSeconds = startAtSeconds
        hasFinishedPlayback = GlobalVideoManager.hasFinishedPlayback(consumerId)

        // ≡ iOS adaptiveController init
        adaptiveController = if (mediaItem != null && mediaItem.type == MomentsMediaItem.MediaType.VIDEO) {
            val tier = initialTier
                ?: VideoPlaybackSelector.source(forItem = mediaItem, moment = moment)?.tier
            VideoAdaptiveTierController(
                mediaItem = mediaItem,
                moment = moment,
                initialTier = tier,
            )
        } else {
            null
        }

        SharedVideoPlayerPool.setEvictionHandler(consumerId) {
            handlePoolEviction()
        }

        val pooledPlayer = SharedVideoPlayerPool.player(consumerId)

        // ≡ iOS reuseExistingItem path
        if (reuseExistingItem &&
            SharedVideoPlayerPool.hasActiveItem(consumerId) &&
            pooledPlayer.playerError == null
        ) {
            player = pooledPlayer
            activeItem = pooledPlayer.currentMediaItem
            isReadyToPlay = pooledPlayer.playbackState == Player.STATE_READY
            isPlaying = pooledPlayer.isPlaying
            applySessionMuteState(pooledPlayer)
            observeItemStatus(pooledPlayer)
            setupAdaptiveObservers(pooledPlayer)
            observePlayback()
            setupLooping(pooledPlayer)
            applyPendingSeekIfPossible(pooledPlayer)
            return
        }

        val tier = adaptiveController?.currentTier ?: VideoPlaybackSelector.recommendedTier()
        val playerItem = if (mediaItem != null) {
            VideoPlaybackSelector.makeConfiguredPlayerItem(mediaItem, moment, tier)
                ?: VideoPreloader.getPlayerItem(url)
        } else {
            VideoPreloader.getPlayerItem(url)
        }

        pooledPlayer.setMediaItem(playerItem)
        VideoPlaybackSelector.configure(pooledPlayer, tier, isActivelyPlaying = true)
        // iOS: automaticallyWaitsToMinimizeStalling = false → Exo carga agresiva vía LoadControl del pool
        pooledPlayer.repeatMode = Player.REPEAT_MODE_OFF
        pooledPlayer.prepare()
        applySessionMuteState(pooledPlayer)

        player = pooledPlayer
        activeItem = playerItem
        isReadyToPlay = pooledPlayer.playbackState == Player.STATE_READY
        isPlaying = false

        observeItemStatus(pooledPlayer)
        setupAdaptiveObservers(pooledPlayer)
        observePlayback()
        setupLooping(pooledPlayer)
        applyPendingSeekIfPossible(pooledPlayer)
    }

    private fun applySessionMuteState(on: ExoPlayer) {
        val shouldMute = GlobalVideoManager.isSessionMuted()
        on.volume = if (shouldMute) 0f else 1f
        isMuted = shouldMute
    }

    private fun applyPendingSeekIfPossible(on: ExoPlayer) {
        val seconds = pendingSeekSeconds ?: return
        if (seconds <= 0.05) return
        if (on.playbackState != Player.STATE_READY) return
        pendingSeekSeconds = null
        val current = on.currentPosition / 1000.0
        if (current.isFinite() && kotlin.math.abs(current - seconds) < 0.35) return
        on.seekTo((seconds * 1000).toLong())
    }

    /** ≡ iOS handlePoolEviction — suelta refs SIN release al pool. */
    private fun handlePoolEviction() {
        stopTimeObserver()
        teardownAdaptiveObservers()
        adaptiveController = null
        player = null
        activeItem = null
        isPlaying = false
        isReadyToPlay = false
        lastPublishedTime = -1.0
        pendingSeekSeconds = null
    }

    /** ≡ iOS observeItemStatus — READY → seek pendiente. */
    private fun observeItemStatus(exo: ExoPlayer) {
        // Cubierto por playerListener en setupAdaptiveObservers / attachStatusListener
        ensurePlayerListener(exo)
    }

    /**
     * ≡ iOS setupAdaptiveObservers:
     * - buffer empty → recoverFromPlaybackStall
     * - likely to keep up → notePlaybackHealthy
     * - stalled → recoverFromPlaybackStall
     */
    private fun setupAdaptiveObservers(exo: ExoPlayer) {
        teardownAdaptiveObservers()
        ensurePlayerListener(exo)
    }

    private fun teardownAdaptiveObservers() {
        val exo = player
        playerListener?.let { exo?.removeListener(it) }
        playerListener = null
    }

    private fun ensurePlayerListener(exo: ExoPlayer) {
        playerListener?.let { exo.removeListener(it) }
        val l = object : Player.Listener {
            private var wasBufferingEmpty = false

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isReadyToPlay = true
                        wasBufferingEmpty = false
                        if (exo.duration > 0) {
                            val d = exo.duration / 1000.0
                            if (kotlin.math.abs(duration - d) > 0.01) duration = d
                        }
                        applyPendingSeekIfPossible(exo)
                        adaptiveController?.notePlaybackHealthy()
                    }
                    Player.STATE_BUFFERING -> {
                        // ≡ isPlaybackBufferEmpty
                        if (isPlaying && !wasBufferingEmpty) {
                            wasBufferingEmpty = true
                            recoverFromPlaybackStall()
                        }
                    }
                    Player.STATE_ENDED -> {
                        val id = consumerId
                        if (id != null && GlobalVideoManager.shouldPreserveSharedPlayer(id)) {
                            return
                        }
                        isPlaying = false
                        hasFinishedPlayback = true
                        if (id != null) {
                            GlobalVideoManager.markPlaybackFinished(id)
                        }
                    }
                }
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                // ≡ isPlaybackLikelyToKeepUp ≈ !isLoading && READY
                if (!isLoading && exo.playbackState == Player.STATE_READY) {
                    adaptiveController?.notePlaybackHealthy()
                    wasBufferingEmpty = false
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // ≡ AVPlayerItemPlaybackStalled / failed
                recoverFromPlaybackStall()
            }
        }
        exo.addListener(l)
        playerListener = l
    }

    /** ≡ iOS setupLooping — loop en STATE_ENDED (arriba). Separado por paridad de API. */
    private fun setupLooping(@Suppress("UNUSED_PARAMETER") exo: ExoPlayer) {
        // Loop manejado en onPlaybackStateChanged(STATE_ENDED).
    }

    private fun recoverFromPlaybackStall() {
        val exo = player ?: return
        VideoPlaybackRecovery.recoverFromStall(
            player = exo,
            isPlaying = isPlaying,
            adaptive = adaptiveController,
            onTierDowngrade = {
                isReadyToPlay = false
            },
        ) { newItem ->
            activeItem = newItem
            VideoPlaybackSelector.configure(
                exo,
                adaptiveController?.currentTier ?: VideoPlaybackSelector.recommendedTier(),
            )
            observeItemStatus(exo)
            setupAdaptiveObservers(exo)
            setupLooping(exo)
        }
    }

    override fun setFinishedPlayback(finished: Boolean) {
        hasFinishedPlayback = finished
    }

    override fun replayFromBeginning() {
        hasFinishedPlayback = false
        val exo = player ?: return
        exo.seekTo(0)
        resumeVideo()
    }

    override fun resumeVideo() {
        if (hasFinishedPlayback) return
        val exo = player ?: return
        exo.play()
        isPlaying = true
    }

    // ≡ iOS pauseVideo
    override fun pauseVideo() {
        val exo = player ?: return
        exo.pause()
        // iOS: canUseNetworkResourcesForLiveStreamingWhilePaused = false
        isPlaying = false
    }

    fun togglePlayback() {
        if (isPlaying) pauseVideo() else resumeVideo()
    }

    override fun toggleMute(respectSilentMode: Boolean) {
        val exo = player ?: return
        if (respectSilentMode && isDeviceVolumeZero()) return
        val muted = !isMuted
        exo.volume = if (muted) 0f else 1f
        isMuted = muted
    }

    override fun setMuted(muted: Boolean, respectSilentMode: Boolean) {
        val exo = player ?: return
        if (respectSilentMode && isDeviceVolumeZero() && !muted) return
        exo.volume = if (muted) 0f else 1f
        isMuted = muted
    }

    /**
     * ≡ iOS observePlayback — periodic 0.25s, umbral 0.08s en currentTime.
     */
    private fun observePlayback() {
        stopTimeObserver()
        val exo = player ?: return
        timeObserverJob = managerScope.launch {
            while (isActive) {
                val p = player
                if (p == null || p !== exo) break
                val durMs = p.duration
                if (durMs > 0) {
                    val durationSeconds = durMs / 1000.0
                    val currentSeconds = p.currentPosition / 1000.0
                    if (durationSeconds.isFinite() && currentSeconds.isFinite() && durationSeconds > 0) {
                        if (kotlin.math.abs(duration - durationSeconds) > 0.01) {
                            duration = durationSeconds
                        }
                        if (kotlin.math.abs(lastPublishedTime - currentSeconds) >= 0.08) {
                            lastPublishedTime = currentSeconds
                            currentTime = currentSeconds
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
        val am = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return false
        return am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0
    }

    fun cleanup(releaseFromPool: Boolean = true) {
        stopTimeObserver()
        teardownAdaptiveObservers()
        adaptiveController = null

        val id = consumerId
        val isCurrent = id?.let { GlobalVideoManager.isRegisteredPlayer(it, this) } ?: true
        val preserve = id?.let { GlobalVideoManager.shouldPreserveSharedPlayer(it) } ?: false

        if (isCurrent && !preserve) {
            player?.pause()
        }
        if (id != null && releaseFromPool && isCurrent && !preserve) {
            SharedVideoPlayerPool.release(id)
        }
        if (id != null && !preserve) {
            GlobalVideoManager.clearPlaybackFinished(id)
        }
        player = null
        activeItem = null
        consumerId = null
        isPlaying = false
        isReadyToPlay = false
        hasFinishedPlayback = false
        lastPublishedTime = -1.0
        pendingSeekSeconds = null
    }
}
