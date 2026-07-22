package com.moments.android.ad

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.moments.android.R
import com.moments.android.extensions.fromHex
import com.moments.android.services.auth.AuthService
import com.moments.android.services.performance.MotionPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val IMAGE_AD_DURATION_SEC = 8.0
private const val MAX_VIDEO_AD_DURATION_SEC = 60.0
private const val AD_PROGRESS_SAMPLE_SEC = 0.1

class StoryAdVideoPlayback {
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _hasVideo = MutableStateFlow(false)
    val hasVideo: StateFlow<Boolean> = _hasVideo.asStateFlow()

    private val _canUseCustomControls = MutableStateFlow(false)
    val canUseCustomControls: StateFlow<Boolean> = _canUseCustomControls.asStateFlow()

    private val _completedPlaybackCount = MutableStateFlow(0)
    val completedPlaybackCount: StateFlow<Int> = _completedPlaybackCount.asStateFlow()

    private var videoController: VideoController? = null

    fun attach(nativeAd: NativeAd, listener: VideoController.VideoLifecycleCallbacks) {
        val media = nativeAd.mediaContent
        if (media == null || !media.hasVideoContent()) {
            publishState(hasVideo = false, canUseCustomControls = false, isMuted = false, isPaused = false)
            return
        }

        val controller = media.videoController
        videoController = controller
        controller.videoLifecycleCallbacks = listener

        val customControls = controller.isCustomControlsEnabled
        if (customControls) {
            controller.mute(false)
            controller.play()
        }

        publishState(
            hasVideo = true,
            canUseCustomControls = customControls,
            isMuted = if (customControls) false else controller.isMuted,
            isPaused = true,
        )
    }

    fun detach() {
        videoController?.videoLifecycleCallbacks = null
        videoController = null
        publishState(hasVideo = false, canUseCustomControls = false, isMuted = false, isPaused = false)
    }

    fun togglePause() {
        val controller = videoController ?: return
        if (_canUseCustomControls.value) {
            if (_isPaused.value) controller.play() else controller.pause()
            return
        }
        _isPaused.value = !_isPaused.value
    }

    fun toggleMute() {
        val controller = videoController ?: return
        val next = !_isMuted.value
        _isMuted.value = next
        controller.mute(next)
    }

    fun syncPaused(paused: Boolean) {
        _isPaused.value = paused
    }

    fun syncMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    fun syncCompletedPlayback() {
        _completedPlaybackCount.value += 1
    }

    private fun publishState(
        hasVideo: Boolean? = null,
        canUseCustomControls: Boolean? = null,
        isMuted: Boolean? = null,
        isPaused: Boolean? = null,
    ) {
        hasVideo?.let { _hasVideo.value = it }
        canUseCustomControls?.let { _canUseCustomControls.value = it }
        isMuted?.let { _isMuted.value = it }
        isPaused?.let { _isPaused.value = it }
    }
}

class StoryNativeAdManager {
    private val _nativeAd = MutableStateFlow<NativeAd?>(null)
    val nativeAd: StateFlow<NativeAd?> = _nativeAd.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()

    private var adLoader: AdLoader? = null
    private var loadedAd: NativeAd? = null

    fun loadStoryAd(activity: Activity?) {
        if (_isLoading.value) return

        AdMobConfiguration.getPreloadedNativeAd()?.let { preloaded ->
            swapAd(preloaded)
            _isLoading.value = false
            _hasError.value = false
            AdMobConfiguration.clearPreloadedNativeAd()
            return
        }

        val act = activity ?: run {
            _hasError.value = true
            return
        }

        _isLoading.value = true
        _hasError.value = false
        swapAd(null)

        val adUnitId = AdMobConfiguration.getNativeAdUnitId()
        val mediaOptions = NativeAdOptions.Builder()
            .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY)
            .build()

        adLoader = AdLoader.Builder(act, adUnitId)
            .forNativeAd { ad ->
                ad.mediaContent?.videoController?.mute(false)
                swapAd(ad)
                _isLoading.value = false
                _hasError.value = false
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    _isLoading.value = false
                    _hasError.value = true
                }
            })
            .withNativeAdOptions(mediaOptions)
            .build()

        adLoader?.loadAd(AdMobConfiguration.createAdRequest())
    }

    fun cleanup() {
        adLoader = null
        swapAd(null)
        _isLoading.value = false
        _hasError.value = false
    }

    val hasReadyAd: Boolean
        get() = _nativeAd.value != null && !_isLoading.value && !_hasError.value

    private fun swapAd(ad: NativeAd?) {
        loadedAd?.destroy()
        loadedAd = ad
        _nativeAd.value = ad
    }
}

@Composable
fun StoryNativeAdView(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    storyCount: Int,
    storyIndex: Int,
    screenWidthDp: Dp,
    screenHeightDp: Dp,
    authService: AuthService = AuthService,
) {
    val context = LocalContext.current
    val currentUser by authService.currentUser.collectAsState()
    val manager = remember { StoryNativeAdManager() }

    var hasAppeared by remember { mutableStateOf(false) }
    var progress by remember { mutableDoubleStateOf(0.0) }
    var isAdTimerPaused by remember { mutableStateOf(false) }
    var didFinishAd by remember { mutableStateOf(false) }
    var currentAdDuration by remember { mutableDoubleStateOf(IMAGE_AD_DURATION_SEC) }

    val isLoading by manager.isLoading.collectAsState()
    val nativeAd by manager.nativeAd.collectAsState()
    val hasError by manager.hasError.collectAsState()

    val shouldShowAds = PlusStatusHelper.shouldShowAds(currentUser)

    fun cleanup() {
        manager.cleanup()
    }

    fun cleanupAndNext() {
        if (didFinishAd) return
        didFinishAd = true
        cleanup()
        onNext()
    }

    fun handleClose() {
        cleanup()
        onClose()
    }

    DisposableEffect(Unit) {
        onDispose { cleanup() }
    }

    LaunchedEffect(Unit) {
        if (!hasAppeared) {
            hasAppeared = true
            if (shouldShowAds) {
                delay(100)
                manager.loadStoryAd(context.findActivity())
                delay(10_000)
                if (manager.isLoading.value) {
                    cleanupAndNext()
                }
            }
        }
    }

    if (!shouldShowAds) {
        LaunchedEffect(Unit) { onNext() }
        return
    }

    if (!hasAppeared) {
        Box(
            modifier = Modifier
                .width(screenWidthDp)
                .height(screenHeightDp)
                .background(Color.Black),
        )
        return
    }

    when {
        isLoading -> {
            StoryAdLoadingView(
                storyCount = storyCount,
                storyIndex = storyIndex,
                progress = progress,
                onNext = ::cleanupAndNext,
                onPrevious = onPrevious,
                onClose = ::handleClose,
                modifier = Modifier.size(screenWidthDp, screenHeightDp),
            )
        }

        nativeAd != null -> {
            LaunchedEffect(nativeAd) {
                currentAdDuration = resolvedAdDuration(nativeAd!!)
                progress = 0.0
                didFinishAd = false
                while (!didFinishAd && progress < 1.0) {
                    if (!isAdTimerPaused) {
                        val ad = manager.nativeAd.value
                        if (ad?.mediaContent?.hasVideoContent() == true) {
                            val reported = ad.mediaContent?.duration?.toDouble() ?: 0.0
                            val effective = if (reported > 0) {
                                minOf(MAX_VIDEO_AD_DURATION_SEC, reported).also { currentAdDuration = it }
                            } else {
                                0.0
                            }
                            val currentTime = ad.mediaContent?.currentTime?.toDouble() ?: 0.0
                            if (effective > 0 && currentTime >= 0) {
                                progress = minOf(maxOf(currentTime / effective, progress), 1.0)
                            }
                        } else {
                            progress = minOf(progress + (AD_PROGRESS_SAMPLE_SEC / currentAdDuration), 1.0)
                        }
                        if (progress >= 1.0) cleanupAndNext()
                    }
                    delay((AD_PROGRESS_SAMPLE_SEC * 1000).toLong())
                }
            }

            StoryAdContentView(
                nativeAd = nativeAd!!,
                storyCount = storyCount,
                storyIndex = storyIndex,
                progress = progress,
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
                onTimerPausedChange = { isAdTimerPaused = it },
                onNext = ::cleanupAndNext,
                onPrevious = onPrevious,
                onClose = ::handleClose,
            )
        }

        hasError -> {
            Box(
                modifier = Modifier
                    .size(screenWidthDp, screenHeightDp)
                    .background(Color.Red.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.ad_story_error_loading),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.ad_story_skipping_soon),
                        color = Color.Gray,
                        fontSize = 12.sp,
                    )
                }
            }
            LaunchedEffect(Unit) {
                delay(2_000)
                cleanupAndNext()
            }
        }

        else -> {
            StoryAdLoadingView(
                storyCount = storyCount,
                storyIndex = storyIndex,
                progress = progress,
                onNext = ::cleanupAndNext,
                onPrevious = onPrevious,
                onClose = ::handleClose,
                modifier = Modifier.size(screenWidthDp, screenHeightDp),
            )
            LaunchedEffect(Unit) {
                delay(1_000)
                if (!manager.isLoading.value && manager.nativeAd.value == null && !manager.hasError.value) {
                    manager.loadStoryAd(context.findActivity())
                }
            }
        }
    }
}

private fun resolvedAdDuration(nativeAd: NativeAd): Double {
    val media = nativeAd.mediaContent ?: return IMAGE_AD_DURATION_SEC
    if (!media.hasVideoContent()) return IMAGE_AD_DURATION_SEC
    val reported = media.duration.toDouble()
    return if (reported > 0) minOf(MAX_VIDEO_AD_DURATION_SEC, reported) else 0.0
}

@Composable
private fun StoryAdContentView(
    nativeAd: NativeAd,
    storyCount: Int,
    storyIndex: Int,
    progress: Double,
    screenWidthDp: Dp,
    screenHeightDp: Dp,
    onTimerPausedChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    val playback = remember { StoryAdVideoPlayback() }
    val isPaused by playback.isPaused.collectAsState()
    val isMuted by playback.isMuted.collectAsState()
    val canUseCustomControls by playback.canUseCustomControls.collectAsState()
    val completedCount by playback.completedPlaybackCount.collectAsState()
    val hasVideo = nativeAd.mediaContent?.hasVideoContent() == true

    val bottomPanelReserved = 174.dp
    val density = LocalDensity.current

    LaunchedEffect(isPaused) { onTimerPausedChange(isPaused) }
    LaunchedEffect(completedCount) {
        if (completedCount > 0 && hasVideo) onNext()
    }
    DisposableEffect(Unit) { onDispose { playback.detach() } }

    Box(modifier = Modifier.size(screenWidthDp, screenHeightDp)) {
        StoryAdMediaView(
            nativeAd = nativeAd,
            playback = playback,
            modifier = Modifier.fillMaxSize(),
        )

        StoryTouchAreas(
            topReserved = 92.dp,
            bottomReserved = bottomPanelReserved,
            onPrevious = onPrevious,
            onNext = onNext,
            modifier = Modifier.fillMaxSize(),
        )

        if (hasVideo && canUseCustomControls) {
            StoryAdVideoControlsOverlay(
                isPaused = isPaused,
                isMuted = isMuted,
                onTogglePause = playback::togglePause,
                onToggleMute = playback::toggleMute,
                bottomPanelReservedHeight = bottomPanelReserved,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        StoryAdTopChrome(
            storyCount = storyCount,
            storyIndex = storyIndex,
            progress = progress,
            title = nativeAd.advertiser ?: stringResource(R.string.ad_common_sponsored),
            subtitle = stringResource(R.string.ad_common_sponsored),
            iconUri = nativeAd.icon?.uri?.toString(),
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun StoryAdVideoControlsOverlay(
    isPaused: Boolean,
    isMuted: Boolean,
    onTogglePause: () -> Unit,
    onToggleMute: () -> Unit,
    bottomPanelReservedHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(start = 16.dp, bottom = bottomPanelReservedHeight + 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        IconButton(
            onClick = onTogglePause,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .semantics {
                    contentDescription = if (isPaused) "play" else "pause"
                },
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringResource(
                    if (isPaused) R.string.feed_video_play else R.string.feed_video_pause,
                ),
                tint = Color.White,
            )
        }
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = stringResource(
                    if (isMuted) R.string.feed_video_unmute else R.string.feed_video_mute,
                ),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun StoryAdTopChrome(
    storyCount: Int,
    storyIndex: Int,
    progress: Double,
    title: String,
    subtitle: String,
    iconUri: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
        ) {
            repeat(storyCount) { index ->
                val segmentProgress = when {
                    index < storyIndex -> 1f
                    index == storyIndex -> progress.toFloat()
                    else -> 0f
                }
                val animated by animateFloatAsState(
                    targetValue = segmentProgress,
                    animationSpec = tween(100, easing = LinearEasing),
                    label = "storyProgress",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = if (index == storyIndex) 1f else 0.3f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animated)
                            .background(Color.White),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(R.string.ad_common_ad), color = Color.White, fontSize = 9.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
            }
            trailingContent?.invoke()
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White)
            }
        }
    }
}

@Composable
fun StoryAdLoadingView(
    storyCount: Int,
    storyIndex: Int,
    progress: Double,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animate = !MotionPolicy.reduceMotion
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(Color.fromHex("#667eea"), Color.fromHex("#764ba2"))),
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StoryAdTopChrome(
                storyCount = storyCount,
                storyIndex = storyIndex,
                progress = progress,
                title = stringResource(R.string.ad_common_sponsored),
                subtitle = stringResource(R.string.common_loading),
                iconUri = null,
                onClose = onClose,
                trailingContent = {
                    TextButton(onClick = onNext) {
                        Text(stringResource(R.string.ad_common_skip), color = Color.White)
                    }
                },
            )
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (animate) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(60.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.ad_story_preparing),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Text(
                    stringResource(R.string.ad_story_preparing_description),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                )
            }
        }
        StoryTouchAreas(
            topReserved = 120.dp,
            bottomReserved = 40.dp,
            onPrevious = onPrevious,
            onNext = onNext,
            sideFraction = 0.15f,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun StoryTouchAreas(
    topReserved: Dp,
    bottomReserved: Dp,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    sideFraction: Float = 0.33f,
) {
    Box(modifier = modifier) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(sideFraction)
                    .padding(top = topReserved, bottom = bottomReserved)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onPrevious,
                    ),
            )
            Spacer(Modifier.weight(1f - sideFraction * 2))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(sideFraction)
                    .padding(top = topReserved, bottom = bottomReserved)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onNext,
                    ),
            )
        }
    }
}

@Composable
private fun StoryAdMediaView(
    nativeAd: NativeAd,
    playback: StoryAdVideoPlayback,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            StoryAdNativeLayout(ctx, nativeAd, playback)
        },
        update = { layout ->
            layout.bind(nativeAd, playback)
        },
    )
}

private class StoryAdNativeLayout(
    context: Context,
    nativeAd: NativeAd,
    playback: StoryAdVideoPlayback,
) : FrameLayout(context) {

    private val nativeAdView = NativeAdView(context)
    private var boundAd: NativeAd? = null

    init {
        addView(
            nativeAdView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        bind(nativeAd, playback)
    }

    fun bind(nativeAd: NativeAd, playback: StoryAdVideoPlayback) {
        if (boundAd == nativeAd) return
        boundAd = nativeAd
        buildLayout(nativeAd, playback)
        nativeAdView.setNativeAd(nativeAd)
    }

    private fun buildLayout(nativeAd: NativeAd, playback: StoryAdVideoPlayback) {
        nativeAdView.removeAllViews()
        val density = resources.displayMetrics.density

        val mediaView = MediaView(context).apply {
            setMediaContent(nativeAd.mediaContent)
        }
        nativeAdView.mediaView = mediaView

        val headline = TextView(context).apply {
            text = nativeAd.headline
            setTextColor(android.graphics.Color.WHITE)
            textSize = 22f
        }
        nativeAdView.headlineView = headline

        val body = TextView(context).apply {
            text = nativeAd.body
            setTextColor(android.graphics.Color.argb(230, 255, 255, 255))
            textSize = 15f
        }
        nativeAdView.bodyView = body

        val cta = Button(context).apply {
            text = nativeAd.callToAction ?: context.getString(R.string.ad_common_sponsored)
        }
        nativeAdView.callToActionView = cta

        val adChoices = AdChoicesView(context)
        nativeAdView.adChoicesView = adChoices

        val attribution = TextView(context).apply {
            text = context.getString(R.string.ad_common_ad)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.argb(204, 0, 0, 0))
            setPadding(8, 4, 8, 4)
        }

        val bottomPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.argb(180, 0, 0, 0))
            setPadding(16, 14, 16, 16)
        }

        bottomPanel.addView(attribution)
        bottomPanel.addView(headline)
        bottomPanel.addView(body)
        bottomPanel.addView(cta)
        bottomPanel.addView(adChoices)

        nativeAdView.addView(mediaView)
        nativeAdView.addView(bottomPanel)

        mediaView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        bottomPanel.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            (144 * density).toInt(),
        ).apply {
            gravity = android.view.Gravity.BOTTOM
            marginStart = (12 * density).toInt()
            marginEnd = (12 * density).toInt()
            bottomMargin = (18 * density).toInt()
        }

        if (nativeAd.mediaContent?.hasVideoContent() == true) {
            playback.attach(
                nativeAd,
                object : VideoController.VideoLifecycleCallbacks() {
                    override fun onVideoPlay() = playback.syncPaused(false)
                    override fun onVideoPause() = playback.syncPaused(true)
                    override fun onVideoEnd() {
                        playback.syncPaused(true)
                        playback.syncCompletedPlayback()
                    }
                    override fun onVideoMute(muted: Boolean) = playback.syncMuted(muted)
                },
            )
        }
    }
}
