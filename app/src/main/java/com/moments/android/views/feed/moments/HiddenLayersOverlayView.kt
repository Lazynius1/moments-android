package com.moments.android.views.feed.moments

import com.moments.android.utilities.MomentsAudioSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.HiddenLayerImageFrameStyle
import com.moments.android.models.HiddenLayerPresentationStyle
import com.moments.android.models.HiddenLayerTextStyle
import com.moments.android.models.MomentHiddenLayer
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchHiddenLayers
import com.moments.android.services.firestore.recordHiddenLayerDiscovery
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.hiddenlayers.HiddenLayerHintAppearanceView
import com.moments.android.views.components.hiddenlayers.HiddenLayerLayout
import com.moments.android.views.creator.HiddenLayerRemotePolaroidPreview
import java.util.Calendar
import java.util.Date
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Port de `HiddenLayersOverlayView.swift`.
 */
@Composable
fun HiddenLayersOverlayView(
    momentId: String,
    authorId: String = "",
    hasHiddenLayers: Boolean = true,
    hiddenLayerCount: Int = 1,
    isImmersive: Boolean = false,
    requiresFocusForIntro: Boolean = false,
    @Suppress("UNUSED_PARAMETER") onOpenLayers: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!hasHiddenLayers || hiddenLayerCount <= 0 || authorId.isBlank() || momentId.isBlank()) {
        return
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid
    val prefs = remember {
        context.getSharedPreferences("moments_hidden_layers_seen", android.content.Context.MODE_PRIVATE)
    }

    var layers by remember(momentId) { mutableStateOf<List<MomentHiddenLayer>>(emptyList()) }
    var isLoading by remember(momentId) { mutableStateOf(false) }
    var revealedIds by remember(momentId) { mutableStateOf(setOf<String>()) }
    var autoplayIds by remember(momentId) { mutableStateOf(setOf<String>()) }
    var revealBurstIds by remember(momentId) { mutableStateOf(setOf<String>()) }
    var showIntroShimmer by remember(momentId) { mutableStateOf(false) }
    var hasPlayedIntro by remember(momentId) { mutableStateOf(false) }
    var isFocusQualified by remember { mutableStateOf(!requiresFocusForIntro) }
    var viewerNow by remember { mutableStateOf(Date()) }
    var temporaryTopMessage by remember { mutableStateOf<String?>(null) }
    var temporaryLockedLayerId by remember { mutableStateOf<String?>(null) }
    var temporaryLockedExpiry by remember { mutableStateOf<Date?>(null) }
    var temporaryLockedToken by remember { mutableStateOf(UUID.randomUUID()) }
    var overlayWindowBounds by remember { mutableStateOf<Rect?>(null) }

    fun seenKey(layerId: String): String {
        val uid = viewerId ?: "anonymous"
        return "hiddenLayerSeen:$uid:$momentId:$layerId"
    }
    fun wasSeen(layerId: String) = prefs.getBoolean(seenKey(layerId), false)
    fun markSeen(layerId: String) {
        prefs.edit().putBoolean(seenKey(layerId), true).apply()
    }

    val hintTapSparkles = stringResource(R.string.hidden_layers_viewer_hint)
    val lockedGeneric = stringResource(R.string.hidden_layers_viewer_locked_generic)
    val unlockedMsg = stringResource(R.string.hidden_layers_viewer_unlocked)

    fun unlockSummaryString(date: Date): String {
        val seconds = (date.time - viewerNow.time) / 1000.0
        if (seconds > 0 && seconds < 24 * 60 * 60) {
            return MomentsFormat.relativeTime(
                date,
                MomentsFormat.RelativeTimeStyle.CONVERSATIONAL,
                viewerNow,
            )
        }
        val cal = Calendar.getInstance()
        val today = Calendar.getInstance().apply { time = viewerNow }
        cal.time = date
        if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        ) {
            return context.getString(
                R.string.hidden_layers_viewer_unlock_today,
                MomentsFormat.smartDate(date, MomentsFormat.DateContext.TIME_ONLY),
            )
        }
        return MomentsFormat.smartDate(date, MomentsFormat.DateContext.MEDIUM_DATE_TIME)
    }

    fun lockedMessage(layer: MomentHiddenLayer): String {
        val unlockAt = layer.unlockAt ?: return lockedGeneric
        return context.getString(
            R.string.hidden_layers_viewer_locked_until,
            unlockSummaryString(unlockAt),
        )
    }

    fun lockedSummaryText(): String? {
        val locked = layers.filter { !it.isUnlocked(viewerNow) }
        if (locked.isEmpty()) return null
        val nextUnlock = locked.mapNotNull { it.unlockAt }.minByOrNull { it.time } ?: return null
        if (locked.size == 1) {
            return context.getString(
                R.string.hidden_layers_viewer_locked_single,
                unlockSummaryString(nextUnlock),
            )
        }
        val available = max(0, layers.size - locked.size)
        return if (available == 0) {
            context.getString(
                R.string.hidden_layers_viewer_locked_all,
                locked.size,
                unlockSummaryString(nextUnlock),
            )
        } else {
            context.getString(
                R.string.hidden_layers_viewer_locked_mixed,
                available,
                locked.size,
                unlockSummaryString(nextUnlock),
            )
        }
    }

    val temporaryLockedLayer: MomentHiddenLayer? = run {
        val id = temporaryLockedLayerId ?: return@run null
        val expiry = temporaryLockedExpiry ?: return@run null
        if (expiry.before(Date())) return@run null
        val layer = layers.firstOrNull { it.id == id } ?: return@run null
        if (layer.isUnlocked(viewerNow)) return@run null
        layer
    }

    val lockedLayerHint = temporaryLockedLayer
    // iOS topHintText priority; banner solo si intro || temp message || temp locked
    val topHintText: String? = when {
        temporaryTopMessage != null -> temporaryTopMessage
        lockedLayerHint != null -> lockedMessage(lockedLayerHint)
        lockedSummaryText() != null -> lockedSummaryText()
        else -> hintTapSparkles
    }
    val showHintBanner = showIntroShimmer ||
        temporaryTopMessage != null ||
        lockedLayerHint != null

    fun showTemporaryTopMessage(message: String) {
        temporaryTopMessage = message
        scope.launch {
            delay(1800)
            if (temporaryTopMessage == message) temporaryTopMessage = null
        }
    }

    fun scheduleTemporaryLockedRefresh(token: UUID) {
        scope.launch {
            delay(1000)
            if (temporaryLockedToken != token) return@launch
            viewerNow = Date()
            val id = temporaryLockedLayerId
            val expiry = temporaryLockedExpiry
            val layer = id?.let { lid -> layers.firstOrNull { it.id == lid } }
            if (id == null || expiry == null || layer == null ||
                expiry.before(Date()) || layer.isUnlocked(viewerNow)
            ) {
                temporaryLockedLayerId = null
                temporaryLockedExpiry = null
                return@launch
            }
            scheduleTemporaryLockedRefresh(token)
        }
    }

    fun showTemporaryLockedMessage(layer: MomentHiddenLayer) {
        temporaryTopMessage = null
        temporaryLockedLayerId = layer.id
        temporaryLockedExpiry = Date(System.currentTimeMillis() + 2400)
        val token = UUID.randomUUID()
        temporaryLockedToken = token
        scheduleTemporaryLockedRefresh(token)
    }

    fun scheduleIntroIfNeeded() {
        if (hasPlayedIntro) return
        if (layers.isEmpty() || isLoading) return
        if (layers.none { !wasSeen(it.id) }) return
        if (isImmersive) return
        if (!isFocusQualified) return
        hasPlayedIntro = true
        showIntroShimmer = true
        scope.launch {
            delay(4000)
            showIntroShimmer = false
        }
    }

    fun scheduleNextUnlockUpdate() {
        scope.launch {
            while (isActive) {
                val nextUnlock = layers
                    .filter { !it.isUnlocked(viewerNow) }
                    .mapNotNull { it.unlockAt }
                    .minByOrNull { it.time }
                    ?: break
                val delayMs = max(200L, nextUnlock.time - System.currentTimeMillis())
                delay(delayMs)
                val previousLocked = layers.filter { !it.isUnlocked(viewerNow) }.map { it.id }.toSet()
                viewerNow = Date()
                val stillLocked = layers.filter { !it.isUnlocked(viewerNow) }.map { it.id }.toSet()
                val newlyUnlocked = previousLocked - stillLocked
                if (newlyUnlocked.isNotEmpty()) {
                    if (temporaryLockedLayerId in newlyUnlocked) {
                        temporaryLockedLayerId = null
                        temporaryLockedExpiry = null
                    }
                    HapticManager.shared.lightImpact()
                    showTemporaryTopMessage(unlockedMsg)
                }
            }
        }
    }

    fun reveal(layer: MomentHiddenLayer) {
        val alreadySeen = wasSeen(layer.id)
        HapticManager.shared.lightImpact()
        revealBurstIds = revealBurstIds + layer.id
        revealedIds = revealedIds + layer.id
        if (layer.type == MomentHiddenLayer.LayerType.AUDIO) {
            autoplayIds = autoplayIds + layer.id
        }
        markSeen(layer.id)
        if (!alreadySeen) {
            val uid = viewerId
            if (uid != null && uid != authorId) {
                scope.launch {
                    runCatching {
                        firestore.recordHiddenLayerDiscovery(
                            ownerUserId = authorId,
                            momentId = momentId,
                            layerId = layer.id,
                            viewerId = uid,
                        )
                    }
                }
            }
        }
        scope.launch {
            delay(600)
            revealBurstIds = revealBurstIds - layer.id
        }
    }

    fun burstColor(layer: MomentHiddenLayer): Color = when (layer.type) {
        MomentHiddenLayer.LayerType.TEXT -> when (layer.presentationStyle) {
            HiddenLayerPresentationStyle.MARKER_LABEL -> Color.Yellow
            HiddenLayerPresentationStyle.PAPER_NOTE -> Color(1f, 0.9f, 0.6f)
            else -> Color(1f, 0.84f, 0.42f)
        }
        MomentHiddenLayer.LayerType.IMAGE -> Color.White
        MomentHiddenLayer.LayerType.AUDIO -> Color(0.4f, 0.8f, 1f)
    }

    // Load
    LaunchedEffect(momentId, authorId) {
        if (layers.isNotEmpty() || isLoading) return@LaunchedEffect
        isLoading = true
        val fetched = runCatching { firestore.fetchHiddenLayers(authorId, momentId) }
            .getOrDefault(emptyList())
        val visible = fetched.filter { it.isVisibleInViewer }.sortedBy { it.zIndex }
        layers = visible
        viewerNow = Date()
        revealedIds = visible
            .filter { wasSeen(it.id) && it.isUnlocked(viewerNow) }
            .map { it.id }
            .toSet()
        autoplayIds = emptySet()
        hasPlayedIntro = false
        isLoading = false
        scheduleIntroIfNeeded()
        scheduleNextUnlockUpdate()
    }

    LaunchedEffect(isFocusQualified, layers, isLoading, isImmersive) {
        if (isFocusQualified) scheduleIntroIfNeeded()
    }

    // Focus qualification (iOS overlayHasFocus)
    LaunchedEffect(overlayWindowBounds, requiresFocusForIntro, isImmersive) {
        if (!requiresFocusForIntro) {
            isFocusQualified = true
            return@LaunchedEffect
        }
        val bounds = overlayWindowBounds ?: return@LaunchedEffect
        val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
        val screenW = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screen = Rect(0f, 0f, screenW, screenH)
        val visible = bounds.intersect(screen)
        if (visible.isEmpty || bounds.height <= 0f) {
            isFocusQualified = false
            return@LaunchedEffect
        }
        val visibleRatio = visible.height / bounds.height
        val centerDistance = abs(bounds.center.y - screen.center.y)
        val maxCenterDistance = min(screenH * 0.18f, 150f)
        isFocusQualified = visibleRatio > 0.72f && centerDistance < maxCenterDistance
    }

    if (isImmersive) return

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                overlayWindowBounds = Rect(
                    pos.x,
                    pos.y,
                    pos.x + coords.size.width,
                    pos.y + coords.size.height,
                )
            },
    ) {
        val imageRect = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        if (isLoading && layers.isEmpty()) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center).size(28.dp),
                strokeWidth = 2.dp,
            )
        }

        layers.forEachIndexed { index, layer ->
            val frame = HiddenLayerLayout.frame(
                layer,
                imageRect,
                minimumSizePx = with(density) { 44.dp.toPx() },
            )
            val revealed = layer.id in revealedIds

            Box(
                Modifier
                    .offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
                    .size(
                        width = with(density) { frame.width.toDp() },
                        height = with(density) { frame.height.toDp() },
                    )
                    .clickable(
                        interactionSource = remember(layer.id) { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (revealed) return@clickable
                        val now = Date()
                        viewerNow = now
                        if (!layer.isUnlocked(now)) {
                            HapticManager.shared.warning()
                            showTemporaryLockedMessage(layer)
                            return@clickable
                        }
                        reveal(layer)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (revealed) {
                    HiddenLayerRevealedContent(
                        layer = layer,
                        frameWidthPx = frame.width,
                        frameHeightPx = frame.height,
                        shouldAutoplay = layer.id in autoplayIds,
                    )
                } else if (showIntroShimmer || !wasSeen(layer.id)) {
                    HiddenLayerHintAppearanceView(
                        type = layer.type,
                        shape = layer.shape,
                        style = layer.hintStyle ?: MomentHiddenLayer.HintStyle.ACTUAL,
                        isSeen = wasSeen(layer.id),
                        delaySec = index * 0.12,
                        isIntro = showIntroShimmer,
                    )
                }
                if (layer.id in revealBurstIds) {
                    HiddenLayerRevealBurst(color = burstColor(layer), shape = layer.shape)
                }
            }
        }

        AnimatedVisibility(
            visible = showHintBanner && topHintText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
        ) {
            val isDark = isSystemInDarkTheme()
            Text(
                text = topHintText.orEmpty(),
                color = MomentsChromeGlass.contentColor(isDark),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .momentsChromeGlass(RoundedCornerShape(percent = 50))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Contenido revelado compartido por overlay de feed y preview estático de grid.
 * ≡ iOS reveals con `isAnimated` (false en thumbnails).
 */
@Composable
fun HiddenLayerRevealedContent(
    layer: MomentHiddenLayer,
    frameWidthPx: Float,
    frameHeightPx: Float,
    shouldAutoplay: Boolean = false,
    isAnimated: Boolean = true,
) {
    when (layer.type) {
        MomentHiddenLayer.LayerType.TEXT -> {
            HiddenLayerTextReveal(
                layer = layer,
                frameWidthPx = frameWidthPx,
                frameHeightPx = frameHeightPx,
                isAnimated = isAnimated,
            )
        }
        MomentHiddenLayer.LayerType.IMAGE -> {
            val url = layer.mediaURL
            if (url != null) {
                HiddenLayerImageReveal(
                    url = url,
                    caption = layer.caption,
                    captionStyle = layer.textStyle,
                    frameStyle = layer.imageFrameStyle ?: HiddenLayerImageFrameStyle.CLASSIC,
                    imageOffsetX = layer.imageOffsetX ?: 0.0,
                    imageOffsetY = layer.imageOffsetY ?: 0.0,
                    imageScale = layer.imageScale ?: 1.0,
                    canvasWidthPx = frameWidthPx,
                    canvasHeightPx = frameHeightPx,
                    isAnimated = isAnimated,
                )
            }
        }
        MomentHiddenLayer.LayerType.AUDIO -> {
            val url = layer.mediaURL
            if (url != null) {
                HiddenLayerAudioReveal(
                    audioURL = url,
                    duration = layer.duration ?: 15.0,
                    frameWidthPx = frameWidthPx,
                    shouldAutoplay = shouldAutoplay && isAnimated,
                    isAnimated = isAnimated,
                )
            }
        }
    }
}

/** Port de `HiddenLayerTextReveal` + `TypewriterText`. */
@Composable
internal fun HiddenLayerTextReveal(
    layer: MomentHiddenLayer,
    frameWidthPx: Float,
    frameHeightPx: Float,
    isAnimated: Boolean = true,
) {
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    // Thumbnails: texto ya completo (sin typewriter / mask).
    var appearProgress by remember(layer.id, isAnimated) {
        mutableFloatStateOf(if (isAnimated) 0f else 1f)
    }
    LaunchedEffect(layer.id, isAnimated) {
        if (!isAnimated) {
            appearProgress = 1f
            return@LaunchedEffect
        }
        val charCount = layer.text?.length ?: 0
        val durationMs = (max(0.6, min(2.5, charCount * 0.045)) * 1000).toLong()
        delay(200)
        val start = System.currentTimeMillis()
        while (true) {
            val t = ((System.currentTimeMillis() - start).toFloat() / durationMs).coerceIn(0f, 1f)
            appearProgress = t
            if (t >= 1f) break
            delay(16)
        }
    }

    val corner = when (layer.presentationStyle) {
        HiddenLayerPresentationStyle.CAPTION_PILL -> 999.dp
        HiddenLayerPresentationStyle.MARKER_LABEL -> 10.dp
        else -> 18.dp
    }
    val shape = RoundedCornerShape(corner)
    val foreground = when (layer.presentationStyle) {
        HiddenLayerPresentationStyle.PAPER_NOTE -> Color.Black.copy(0.82f)
        HiddenLayerPresentationStyle.MARKER_LABEL -> Color.Black
        HiddenLayerPresentationStyle.GLASS_CARD ->
            if (isDark) Color.White else Color.Black.copy(0.88f)
        HiddenLayerPresentationStyle.MINIMAL_TEXT ->
            if (isDark) Color.White.copy(0.96f) else Color.Black.copy(0.9f)
        HiddenLayerPresentationStyle.CAPTION_PILL,
        HiddenLayerPresentationStyle.FLOATING_QUOTE,
        -> Color.White
    }
    val fontSize = when (layer.textStyle ?: HiddenLayerTextStyle.CLEAN) {
        HiddenLayerTextStyle.CLEAN -> 15.sp
        HiddenLayerTextStyle.SERIF -> 16.sp
        HiddenLayerTextStyle.HANDWRITTEN -> 21.sp
        HiddenLayerTextStyle.MONO -> 14.sp
        HiddenLayerTextStyle.BUBBLE -> 16.sp
        HiddenLayerTextStyle.EDITORIAL -> 18.sp
    }
    val fontFamily = when (layer.textStyle ?: HiddenLayerTextStyle.CLEAN) {
        HiddenLayerTextStyle.SERIF, HiddenLayerTextStyle.EDITORIAL -> FontFamily.Serif
        HiddenLayerTextStyle.MONO -> FontFamily.Monospace
        HiddenLayerTextStyle.HANDWRITTEN -> FontFamily.Cursive
        else -> FontFamily.Default
    }
    val fontWeight = when (layer.textStyle ?: HiddenLayerTextStyle.CLEAN) {
        HiddenLayerTextStyle.BUBBLE -> FontWeight.Black
        HiddenLayerTextStyle.EDITORIAL -> FontWeight.Bold
        else -> FontWeight.SemiBold
    }
    val rawText = layer.text.orEmpty()
    val visibleText = if (isAnimated) {
        rawText.take((rawText.length * appearProgress).toInt().coerceIn(0, rawText.length))
    } else {
        rawText
    }
    var fittedScale by remember(layer.id, rawText, frameWidthPx, frameHeightPx) {
        mutableFloatStateOf(1f)
    }

    Box(
        Modifier
            .size(
                width = with(density) { frameWidthPx.toDp() },
                height = with(density) { frameHeightPx.toDp() },
            )
            .shadow(12.dp, shape, ambientColor = Color.Black.copy(0.22f), spotColor = Color.Black.copy(0.22f)),
    ) {
        when (layer.presentationStyle) {
            HiddenLayerPresentationStyle.GLASS_CARD -> {
                Box(Modifier.fillMaxSize().momentsChromeGlass(shape, interactive = false))
            }
            HiddenLayerPresentationStyle.CAPTION_PILL -> {
                Box(Modifier.fillMaxSize().clip(shape).background(Color.Black.copy(0.58f)))
            }
            HiddenLayerPresentationStyle.PAPER_NOTE -> {
                Box(Modifier.fillMaxSize().clip(shape).background(Color(1f, 0.94f, 0.76f)))
            }
            HiddenLayerPresentationStyle.MARKER_LABEL -> {
                Box(Modifier.fillMaxSize().clip(shape).background(Color.Yellow.copy(0.9f)))
            }
            HiddenLayerPresentationStyle.FLOATING_QUOTE -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color.Black.copy(0.72f), Color.Black.copy(0.34f)),
                            ),
                        ),
                )
            }
            HiddenLayerPresentationStyle.MINIMAL_TEXT -> Unit
        }

        Text(
            text = visibleText,
            color = foreground,
            fontSize = fontSize * fittedScale,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            onTextLayout = { result ->
                if (result.hasVisualOverflow && fittedScale > 0.6f) {
                    fittedScale = (fittedScale - 0.05f).coerceAtLeast(0.6f)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 11.dp)
                .then(
                    if (isAnimated) {
                        Modifier.graphicsLayer {
                            alpha = if (appearProgress > 0f) 1f else 0f
                            val s = 0.96f + appearProgress * 0.04f
                            scaleX = s
                            scaleY = s
                        }
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

/** Port de `HiddenLayerAudioReveal` + `HiddenLayerAudioTagView`. */
@Composable
internal fun HiddenLayerAudioReveal(
    audioURL: String,
    duration: Double,
    frameWidthPx: Float,
    shouldAutoplay: Boolean,
    isAnimated: Boolean = true,
) {
    val density = LocalDensity.current
    val scale = max(0.7f, min(2.4f, frameWidthPx / 88f))
    Box(
        Modifier
            .size(with(density) { frameWidthPx.toDp() })
            .graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center,
    ) {
        HiddenLayerAudioTagView(
            audioURL = audioURL,
            duration = duration,
            shouldAutoplay = shouldAutoplay,
            isAnimated = isAnimated,
        )
    }
}

@Composable
private fun HiddenLayerAudioTagView(
    audioURL: String,
    duration: Double,
    shouldAutoplay: Boolean,
    isAnimated: Boolean = true,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var isPreparing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var didAppear by remember { mutableStateOf(!isAnimated) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var waveHeights by remember { mutableStateOf(listOf(10f, 14f, 10f)) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val scope = rememberCoroutineScope()

    var loadJob by remember { mutableStateOf<Job?>(null) }
    val audioSession = remember { MomentsAudioSession.lease {
        loadJob?.cancel(); player?.pause(); isPlaying = false; isPreparing = false
    } }

    fun stopPlayback() {
        loadJob?.cancel(); loadJob = null
        audioSession.release()
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        isPlaying = false
        isPreparing = false
        progress = 0f
    }

    fun startProgressLoop() {
        scope.launch {
            while (isPlaying && player != null) {
                val p = player ?: break
                val dur = max(p.duration.takeIf { it > 0 }?.toDouble() ?: (duration * 1000), 1.0)
                progress = (p.currentPosition / dur).toFloat().coerceIn(0f, 1f)
                if (!p.isPlaying && p.currentPosition >= dur - 50) {
                    stopPlayback()
                    break
                }
                delay(100)
            }
        }
    }

    fun startWave() {
        scope.launch {
            while (isPlaying) {
                waveHeights = listOf(
                    Random.nextFloat() * 10f + 6f,
                    Random.nextFloat() * 10f + 10f,
                    Random.nextFloat() * 10f + 6f,
                )
                delay(200)
            }
            waveHeights = listOf(10f, 14f, 10f)
        }
    }

    fun startPlayback(userInitiated: Boolean = false) {
        if (isPreparing) return
        val manager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        if (!userInitiated && manager?.isMusicActive == true) return
        isPreparing = true
        loadJob?.cancel()
        loadJob = scope.launch {
            var prepared: MediaPlayer? = null
            var installed = false
            var claimed = false
            try {
                withContext(Dispatchers.IO) {
                    val next = MediaPlayer()
                    prepared = next
                    next.apply { setDataSource(audioURL); prepare() }
                }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val active = prepared ?: return@launch
                if (!audioSession.activate(contentType = android.media.AudioAttributes.CONTENT_TYPE_SPEECH)) return@launch
                claimed = true
                player = active
                active.setOnCompletionListener { stopPlayback() }
                active.start()
                installed = true
                isPlaying = true
                isPreparing = false
                startProgressLoop()
                startWave()
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // Keep failed or cancelled preparation silent.
            } finally {
                if (!installed) { prepared?.release(); if (claimed) audioSession.release(); isPreparing = false }
            }
        }
    }

    fun togglePlayback() {
        if (isPlaying) {
            player?.pause()
            audioSession.release()
            isPlaying = false
        } else if (player != null) {
            if (!audioSession.activate(contentType = android.media.AudioAttributes.CONTENT_TYPE_SPEECH)) return
            player?.start()
            isPlaying = true
            startProgressLoop()
            startWave()
        } else {
            startPlayback(userInitiated = true)
        }
    }

    DisposableEffect(audioURL) {
        onDispose { stopPlayback() }
    }

    LaunchedEffect(isAnimated, shouldAutoplay) {
        if (!isAnimated) {
            didAppear = true
            return@LaunchedEffect
        }
        delay(300)
        didAppear = true
        if (shouldAutoplay) startPlayback()
    }

    Box(
        Modifier
            .size(72.dp)
            .momentsChromeGlass(CircleShape, interactive = isAnimated)
            .then(
                if (isAnimated) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { togglePlayback() },
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            drawArc(
                color = Color.White.copy(0.85f),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = when {
                    isPreparing -> Icons.Filled.Download
                    isPlaying -> Icons.Filled.Pause
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = stringResource(R.string.hidden_layers_viewer_audio),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                waveHeights.forEach { h ->
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(if (didAppear) h.dp else 0.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(Color.White),
                    )
                }
            }
        }
    }
}

/** Port de `HiddenLayerImageReveal`. */
@Composable
internal fun HiddenLayerImageReveal(
    url: String,
    caption: String?,
    captionStyle: HiddenLayerTextStyle?,
    frameStyle: HiddenLayerImageFrameStyle,
    imageOffsetX: Double,
    imageOffsetY: Double,
    imageScale: Double,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    @Suppress("UNUSED_PARAMETER") isAnimated: Boolean = true,
) {
    Box(
        Modifier
            // La polaroid incluye el marco y el área de caption fuera del hotspot.
            // SwiftUI permite ese desbordamiento; sin medirlo sin límites, el
            // graphicsLayer de sombra/rotación recorta la parte inferior.
            .wrapContentSize(Alignment.Center, unbounded = true)
            .shadow(8.dp, RoundedCornerShape(4.dp), ambientColor = Color.Black.copy(0.2f), spotColor = Color.Black.copy(0.2f))
            .rotate(-2f),
        contentAlignment = Alignment.Center,
    ) {
        HiddenLayerRemotePolaroidPreview(
            url = url,
            caption = caption,
            captionStyle = captionStyle,
            frameStyle = frameStyle,
            imageOffsetX = imageOffsetX,
            imageOffsetY = imageOffsetY,
            imageScale = imageScale,
            canvasWidthPx = canvasWidthPx,
            canvasHeightPx = canvasHeightPx,
        )
    }
}

@Composable
private fun HiddenLayerRevealBurst(
    color: Color,
    shape: MomentHiddenLayer.LayerShape,
    modifier: Modifier = Modifier,
) {
    var animate by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animate = true }
    val scale by animateFloatAsState(
        targetValue = if (animate) 1.35f else 0.8f,
        animationSpec = tween(650),
        label = "burstScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (animate) 0f else 1f,
        animationSpec = tween(650),
        label = "burstAlpha",
    )
    val corner = if (shape == MomentHiddenLayer.LayerShape.CIRCLE) 999.dp else 18.dp
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                .border(1.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(corner)),
        )
        Box(
            Modifier
                .fillMaxSize(0.55f)
                .graphicsLayer {
                    val s = if (animate) 1.5f else 0.4f
                    scaleX = s
                    scaleY = s
                    this.alpha = alpha * 0.9f
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(color.copy(0.8f), color.copy(0.3f), Color.Transparent),
                    ),
                ),
        )
    }
}
