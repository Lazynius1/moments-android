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

    val activeMomentId by FeedVisibilityCoordinator.activeVideoMomentIdFlow.collectAsState()
    val soundEnabled by GlobalVideoManager.userHasEnabledSoundInSession.collectAsState()

    // Sync mute UI con sesión si el manager aún no está registrado
    LaunchedEffect(soundEnabled) {
        if (playerManager.player == null) return@LaunchedEffect
        playerManager.setMuted(!soundEnabled, respectSilentMode = true)
    }

    fun togglePlayback() {
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
        hasLoadError = false
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
            url = source.playbackUrl,
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
            togglePlayback()
            return
        }
        showControls = !showControls
        showMuteButton = true
    }

    // Appear / disappear
    DisposableEffect(videoId, url) {
        setupPlayer()
        GlobalVideoManager.registerPlayer(videoId, playerManager)
        isVisible = true
        applyActivationMode(FeedVisibilityCoordinator.activeVideoMomentId)
        onDispose {
            isVisible = false
            if (GlobalVideoManager.isRegisteredPlayer(videoId, playerManager)) {
                GlobalVideoManager.pauseVideo(videoId)
                GlobalVideoManager.unregisterPlayer(videoId, playerManager)
            }
            val preserve = GlobalVideoManager.shouldPreserveSharedPlayer(videoId)
            playerManager.cleanup(releaseFromPool = !preserve)
            hasSetupPlayer = false
            hasLoadError = false
            setupRetries = 0
            setupGeneration += 1
        }
    }

    LaunchedEffect(activeMomentId, isVisible, activationMode) {
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
        if (setupRetries < 2) {
            setupRetries += 1
            hasSetupPlayer = false
            playerManager.cleanup(releaseFromPool = true)
            setupPlayer()
        } else {
            hasLoadError = true
        }
    }

    // Progress + livePlaybackSeconds (social + moment)
    LaunchedEffect(videoId, usesSocialChrome) {
        while (isActive) {
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
            delay(200)
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
                onProgress = { progress = it },
                onBuffering = { isBuffering = it },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(0.dp)),
            )
            VideoPosterOverlay(
                posterUrl = posterUrl,
                isReadyToPlay = playerManager.isReadyToPlay,
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
            if (!playerManager.isPlaying && allowsPauseInteraction) {
                SocialVideoPausedControls(
                    isMuted = playerManager.isMuted,
                    onToggleMute = { GlobalVideoManager.toggleMute(videoId) },
                    onTogglePlay = { togglePlayback() },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (showCroppedMuteButton) {
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
 */
@Composable
fun VideoPlayerRepresentable(
    player: ExoPlayer,
    resizeMode: Int,
    onProgress: (Double) -> Unit,
    onBuffering: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering = playbackState == Player.STATE_BUFFERING
                onBuffering(buffering)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) onBuffering(false)
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
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = false
                this.resizeMode = resizeMode
                this.player = player
            }
        },
        update = { view ->
            view.player = player
            view.resizeMode = resizeMode
        },
        modifier = modifier,
    )
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
        // ≡ iOS: guard item.status == .readyToPlay
        if (on.playbackState != Player.STATE_READY) return
        pendingSeekSeconds = null
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
                        // ≡ setupLooping AVPlayerItemDidPlayToEndTime
                        if (isPlaying) {
                            exo.seekTo(0)
                            exo.play()
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

    // ≡ iOS resumeVideo
    override fun resumeVideo() {
        val exo = player ?: return
        // iOS: canUseNetworkResourcesForLiveStreamingWhilePaused = true (activo)
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

        if (isCurrent) {
            player?.pause()
        }
        if (id != null && releaseFromPool && isCurrent) {
            SharedVideoPlayerPool.release(id)
        }
        player = null
        activeItem = null
        consumerId = null
        isPlaying = false
        isReadyToPlay = false
        lastPublishedTime = -1.0
        pendingSeekSeconds = null
    }
}
