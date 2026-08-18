package com.moments.android.views.feed.core.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.adaptive.LocalAdaptiveWindowState
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.checkIfSaved
import com.moments.android.services.firestore.toggleSaveMoment
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.utilities.momentsPressIcon
import com.moments.android.utilities.HapticManager
import com.moments.android.views.components.CurrentUserVerifiedBadge
import com.moments.android.views.components.MomentCaptionView
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.feed.FeedTeal
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.feed.moments.HiddenLayersOverlayView
import com.moments.android.views.feed.moments.MomentCarouselLayoutRules
import com.moments.android.views.feed.moments.MomentMediaCarousel
import com.moments.android.views.components.ModernActionButtons
import com.moments.android.views.components.ModernFollowButton
import com.moments.android.views.feed.uploads.StoryUploadProgressManager
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.messaging.components.chatMessagePressClassifier
import com.moments.android.views.story.StoriesView
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Métricas compartidas — port de `FeedMomentCardLayout` (iOS).
private val ListHorizontalPadding = FeedMomentCardLayout.listHorizontalPadding
private val HeaderHorizontalPadding = FeedMomentCardLayout.headerHorizontalPadding
private val HeaderVerticalPaddingTop = 8.dp
private val HeaderVerticalPaddingBottom = 4.dp
private val ActionRowHorizontalPadding = FeedMomentCardLayout.actionRowHorizontalPadding
private val PostAvatarSize = 44.dp
private val HeaderIconHitSize = 36.dp
private val HeaderIconSize = 22.dp
private val MediaCornerShape = FeedMomentCardLayout.continuousRoundedRectShape

/** Port de `ModernPostCardView.AspectRatioType` (FeedMomentComponents.swift). */
private enum class PostCardAspectRatioType(
    val maxHeight: Float,
    val exactRatio: Float,
    val displayName: String,
) {
    SQUARE(400f, 1.0f, "1:1"),
    PORTRAIT(500f, 0.8f, "4:5"),
    LANDSCAPE(300f, 1.78f, "16:9"),
    REELS(600f, 0.5625f, "9:16"),
}

/** Port de `classifyAspectRatio` (FeedMomentComponents.swift). */
private fun classifyAspectRatio(ratio: Float): PostCardAspectRatioType {
    val tolerance = 0.05f
    return when {
        abs(ratio - 1.0f) < tolerance -> PostCardAspectRatioType.SQUARE
        abs(ratio - 0.8f) < tolerance -> PostCardAspectRatioType.PORTRAIT
        abs(ratio - 0.5625f) < tolerance -> PostCardAspectRatioType.REELS
        ratio > 1.4f -> PostCardAspectRatioType.LANDSCAPE
        ratio < 0.7f -> PostCardAspectRatioType.REELS
        else -> PostCardAspectRatioType.SQUARE
    }
}

/**
 * Port de `ModernPostCardView.mediaItems` (FeedMomentComponents.swift).
 * visible → si vacío, placeholder vacío (legacy ya resuelto al construir FeedMoment).
 */
private fun FeedMoment.postCardMediaItems(): List<FeedMediaItem> {
    val visible = visibleMediaItems
    if (visible.isNotEmpty()) return visible
    return listOf(
        FeedMediaItem(
            id = "${id}_empty",
            type = "image",
            url = "",
            thumbnailUrl = null,
            aspectRatio = aspectRatio,
        ),
    )
}

private fun initialDisplayAspectRatio(moment: FeedMoment, media: List<FeedMediaItem>): Float {
    val raw = MomentCarouselLayoutRules.aspectRatioValue(
        moment.aspectRatio ?: media.firstOrNull()?.aspectRatio,
    )
    return MomentCarouselLayoutRules.feedDisplayAspectRatio(raw)
}

private fun initialRealAspectRatio(moment: FeedMoment, media: List<FeedMediaItem>): Float =
    MomentCarouselLayoutRules.aspectRatioValue(
        moment.aspectRatio ?: media.firstOrNull()?.aspectRatio,
    )

/** Port de `detectAspectRatio` — DB first; fallback Coil / MediaMetadataRetriever. */
private suspend fun detectPostCardAspectRatio(
    context: android.content.Context,
    moment: FeedMoment,
    mediaItems: List<FeedMediaItem>,
    currentDetected: Float,
): Triple<Float, Float, PostCardAspectRatioType>? {
    val saved = moment.aspectRatio?.takeIf { it.isNotBlank() }
    if (saved != null) {
        val expected = MomentCarouselLayoutRules.aspectRatioValue(saved)
        val display = MomentCarouselLayoutRules.feedDisplayAspectRatio(expected)
        if (currentDetected == display) return null
        val type = when {
            display < 0.7f -> PostCardAspectRatioType.REELS
            display < 0.9f -> PostCardAspectRatioType.PORTRAIT
            display < 1.3f -> PostCardAspectRatioType.SQUARE
            else -> PostCardAspectRatioType.LANDSCAPE
        }
        return Triple(display, expected, type)
    }

    // Solo fallback si aún no se detectó (iOS: detected == 1.0 || == 0)
    if (currentDetected != 1f && currentDetected != 0f) return null

    val first = mediaItems.firstOrNull()
    if (first == null || first.url.isBlank()) {
        return Triple(0.8f, 0.8f, PostCardAspectRatioType.PORTRAIT)
    }

    return if (first.type == "image") {
        val ratio = withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context).data(first.url).build()
                val result = context.imageLoader.execute(req)
                if (result !is SuccessResult) return@runCatching null
                val d = result.drawable
                val w = d.intrinsicWidth
                val h = d.intrinsicHeight
                if (w > 0 && h > 0) w.toFloat() / h.toFloat() else null
            }.getOrNull()
        }
        when {
            ratio != null && ratio > 0f && ratio.isFinite() ->
                Triple(ratio, ratio, classifyAspectRatio(ratio))
            else -> Triple(0.8f, 0.8f, PostCardAspectRatioType.PORTRAIT)
        }
    } else {
        // iOS: default reels 0.5625, then refine with track size
        val videoRatio = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(first.url, HashMap())
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: return@runCatching null
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: return@runCatching null
                    if (w > 0 && h > 0) w.toFloat() / h.toFloat() else null
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
        when {
            videoRatio != null && videoRatio > 0f && videoRatio.isFinite() ->
                Triple(videoRatio, videoRatio, classifyAspectRatio(videoRatio))
            else -> Triple(0.5625f, 0.5625f, PostCardAspectRatioType.REELS)
        }
    }
}

private data class FeedAdaptiveColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val icon: Color,
    val accent: Color,
    val shadow: Color,
    val surfaceBackground: Color,
)

@Composable
private fun rememberFeedAdaptiveColors(): FeedAdaptiveColors {
    val base = rememberAdaptiveColors()
    return remember(base) {
        FeedAdaptiveColors(
            primary = base.primary,
            secondary = base.secondary,
            tertiary = base.tertiary,
            icon = if (base.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.8f),
            accent = base.accent,
            shadow = base.shadowColor,
            surfaceBackground = base.surfaceBackground,
        )
    }
}

/** Port de `ModernStoryButton` (FeedMomentComponents.swift). */
@Composable
fun ModernStoryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val colors = rememberFeedAdaptiveColors()
    val interaction = remember { MutableInteractionSource() }
    val uploading = StoryUploadProgressManager.isUploading
    val progress = StoryUploadProgressManager.progress.toFloat()
    val scale by animateFloatAsState(
        targetValue = if (uploading) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.72f),
        label = "storyBtnScale",
    )

    Box(
        modifier
            .size(HeaderIconHitSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isDark) Color(0xFFFAF9F6).copy(alpha = 0.05f)
                else Color(0xFF0B1215).copy(alpha = 0.03f),
            )
            .momentsPressIcon()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (uploading) Icons.Filled.KeyboardArrowUp else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = colors.icon,
            modifier = Modifier.size(16.dp),
        )
        if (uploading) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(32.dp),
                color = colors.accent,
                trackColor = colors.accent.copy(alpha = 0.3f),
                strokeWidth = 2.dp,
            )
        }
    }
}

/** Port de `ModernNotificationButton` (FeedMomentComponents.swift). */
@Composable
fun ModernNotificationButton(
    hasNotification: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberFeedAdaptiveColors()
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier
            .size(HeaderIconHitSize)
            .momentsPressIcon()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (hasNotification) {
            Box(
                Modifier
                    .size(HeaderIconHitSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Red.copy(alpha = 0.08f)),
            )
        }
        // Keep this control native to Android while preserving the iOS sizing.
        Icon(
            imageVector = if (hasNotification) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(R.string.feed_activity),
            tint = if (hasNotification) Color.Red else colors.icon,
            modifier = Modifier.size(HeaderIconSize),
        )
    }
}

/** Port de botón Nova en FeedHeaderBar (antes ModernMessageButton). */
@Composable
fun ModernNovaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberFeedAdaptiveColors()
    val interactionSource = remember { MutableInteractionSource() }
    val label = stringResource(R.string.tab_bar_nova)

    Box(
        modifier
            .size(HeaderIconHitSize)
            .momentsPressIcon()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        com.moments.android.views.nova.novacore.NovaBrandIcon(
            size = HeaderIconSize,
            color = colors.icon,
        )
    }
}

/** Port de `ModernMessageButton` (FeedMomentComponents.swift). */
@Composable
fun ModernMessageButton(
    hasMessage: Boolean,
    messageCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberFeedAdaptiveColors()
    val interactionSource = remember { MutableInteractionSource() }
    val badgeScale by animateFloatAsState(
        targetValue = if (hasMessage && messageCount > 0) 1f else 0.1f,
        animationSpec = spring(dampingRatio = 0.72f),
        label = "messageBadgeScale",
    )

    Box(
        modifier
            .size(HeaderIconHitSize)
            .momentsPressIcon()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Custom alpha-mask asset, drawn to match the app's iOS paper-plane silhouette.
        Icon(
            painter = painterResource(R.drawable.feed_paperplane_icon),
            contentDescription = stringResource(R.string.feed_messages),
            tint = colors.icon,
            modifier = Modifier.size(HeaderIconSize),
        )

        if (hasMessage && messageCount > 0) {
            val badgeWidth = if (messageCount > 9) 20.dp else 16.dp
            Box(
                Modifier
                    .offset(x = 10.dp, y = (-10).dp)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                    }
                    .size(width = badgeWidth, height = 16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF007AFF)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = minOf(messageCount, 99).toString(),
                    color = Color.White,
                    fontSize = if (messageCount > 9) 10.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Port 1:1 de `ExpandableContentView` (FeedMomentComponents.swift). */
@Composable
fun ExpandableContentView(
    content: String,
    onHashtagTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberFeedAdaptiveColors()
    val context = LocalContext.current
    val density = LocalDensity.current
    // iOS maxCharacters = 15
    val maxCharacters = 15
    var isExpanded by remember { mutableStateOf(false) }
    val needsExpansion = content.length > maxCharacters
    val display = if (isExpanded) {
        content
    } else {
        content.take(maxCharacters) + if (content.length > maxCharacters) "..." else ""
    }
    val scale by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0.95f,
        animationSpec = spring(dampingRatio = 0.72f),
        label = "expandScale",
    )

    Column(
        modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // iOS MomentHashtagText(base white, mention 007AFF, shadow)
        com.moments.android.views.components.MomentHashtagText(
            content = display,
            onHashtagTap = onHashtagTap,
            baseColor = Color.White,
            mentionColor = Color(0xFF007AFF),
            fontSize = 14.sp,
            shadow = androidx.compose.ui.graphics.Shadow(
                color = Color.Black.copy(alpha = 0.4f),
                offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                blurRadius = 3f,
            ),
            onMentionTap = com.moments.android.utilities.MomentMentionNavigation::openProfile,
        )

        if (needsExpansion) {
            Row(
                Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .shadow(4.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                    .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(
                        if (isExpanded) R.string.feed_see_less else R.string.feed_see_more,
                    ),
                    color = Color.White,
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(10.dp)
                        .graphicsLayer { rotationZ = if (isExpanded) 0f else 180f },
                )
            }
        }
    }
}

/** Port de `FeedMomentComponents.swift` / `ModernPostCardView`. */
@Composable
fun ModernPostCardView(
    moment: FeedMoment,
    onOpenProfile: () -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenLocation: (String, com.moments.android.models.Moment.LocationCoordinate?) -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onContextMenu: (FeedMoment) -> Unit = {},
    onNearEnd: () -> Unit = {},
    onAuthorAvatarTap: ((authorId: String, hasStory: Boolean) -> Unit)? = null,
    onAuthorAvatarLongPress: ((authorId: String, Rect) -> Unit)? = null,
    onPeek: ((imageUrl: String, ratio: Float, isPressing: Boolean) -> Unit)? = null,
    onTagTap: ((String) -> Unit)? = null,
    authorHasStory: Boolean = false,
    authorHasUnseenStory: Boolean = false,
    showVerifiedBadge: Boolean = false,
    availableHeight: Float? = null,
    /** ≡ `ModernSavedDetailMomentCard`: `isSaved: .constant(true)` + `onSave` → quitar de guardados. */
    forceSaved: Boolean = false,
    onForcedUnsave: (() -> Unit)? = null,
    /** Sesión Reels de la superficie (iOS `ModernPostCardView.reelsVideos`). */
    reelsVideos: List<com.moments.android.services.performance.VideoMoment> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val colors = rememberFeedAdaptiveColors()
    val density = LocalDensity.current
    val context = LocalContext.current
    val adaptiveWindow = LocalAdaptiveWindowState.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    var followState by remember(moment.authorId) { mutableStateOf(FollowButtonState.CAN_FOLLOW) }
    var followLoading by remember { mutableStateOf(false) }
    var showUnfollowConfirm by remember { mutableStateOf(false) }
    var isImmersive by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var currentImageIndex by remember { mutableStateOf(0) }
    var displayUsername by remember(moment.id) { mutableStateOf(moment.username) }
    var isSaved by remember(moment.id) { mutableStateOf(false) }
    var isSaveLoading by remember { mutableStateOf(false) }
    // iOS showSpecificUserStories / fullScreenCover StoriesView
    var showSpecificUserStories by remember { mutableStateOf(false) }
    val savedIds by firestore.savedMomentIds.collectAsState()
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid
    val showFollow = viewerId != null && viewerId != moment.authorId
    // iOS ModernPostCardView.mediaItems
    val mediaItems = remember(moment.id, moment.mediaItems, moment.aspectRatio) {
        moment.postCardMediaItems()
    }
    var detectedAspectRatio by remember(moment.id) {
        mutableFloatStateOf(initialDisplayAspectRatio(moment, mediaItems))
    }
    var realAspectRatio by remember(moment.id) {
        mutableFloatStateOf(initialRealAspectRatio(moment, mediaItems))
    }
    var aspectRatioType by remember(moment.id) {
        mutableStateOf(classifyAspectRatio(detectedAspectRatio))
    }
    var postWidthPx by remember(moment.id) { mutableFloatStateOf(0f) }
    val currentMedia = mediaItems.getOrNull(currentImageIndex)
    val currentTags = currentMedia?.tags.orEmpty()

    // iOS calculateCardHeight / refreshCardHeight
    val cardHeightDp = availableHeight?.takeIf { postWidthPx > 0f }?.let { availPx ->
        with(density) {
            val maxWidthPx = (postWidthPx -
                (ListHorizontalPadding * 2 + ActionRowHorizontalPadding * 2).toPx())
                .coerceAtLeast(1f)
            val ideal = maxWidthPx / detectedAspectRatio.coerceAtLeast(0.01f)
            val resolved = if (adaptiveWindow.isLargeScreen) {
                ideal
            } else {
                min(ideal, availPx * 0.95f)
            }
            max(resolved.coerceAtLeast(150f), 200f).toDp()
        }
    }

    // iOS onAppear: detectAspectRatio + refreshCardHeight + onNearEnd
    LaunchedEffect(moment.id, mediaItems) {
        onNearEnd()
        val result = detectPostCardAspectRatio(
            context = context,
            moment = moment,
            mediaItems = mediaItems,
            currentDetected = detectedAspectRatio,
        )
        if (result != null) {
            detectedAspectRatio = result.first
            realAspectRatio = result.second
            aspectRatioType = result.third
        }
    }

    // Suppress unused until DEBUG_ASPECT_RATIO overlay (iOS ProcessInfo env)
    @Suppress("UNUSED_VARIABLE")
    val debugAspectLabel = aspectRatioType.displayName

    // iOS: onChange savedMomentIds + loadAllPostData checkIfSaved
    // Saved detail: `isSaved: .constant(true)` — no re-sincronizar desde Firestore.
    LaunchedEffect(savedIds, moment.id, forceSaved) {
        if (forceSaved) {
            isSaved = true
            return@LaunchedEffect
        }
        isSaved = savedIds.contains(moment.id)
    }

    LaunchedEffect(moment.id, viewerId, forceSaved) {
        if (forceSaved) {
            isSaved = true
            return@LaunchedEffect
        }
        val uid = viewerId ?: return@LaunchedEffect
        if (savedIds.contains(moment.id)) {
            isSaved = true
            return@LaunchedEffect
        }
        runCatching { firestore.checkIfSaved(uid, moment.id) }
            .onSuccess { isSaved = it }
    }

    fun toggleSave() {
        if (forceSaved) {
            HapticManager.shared.mediumImpact()
            onForcedUnsave?.invoke()
            return
        }
        val uid = viewerId ?: return
        if (isSaveLoading) return
        isSaved = !isSaved
        isSaveLoading = true
        scope.launch {
            val error = runCatching { firestore.toggleSaveMoment(uid, moment.id) }.exceptionOrNull()
            isSaveLoading = false
            if (error != null) isSaved = !isSaved
        }
    }

    LaunchedEffect(moment.authorId, viewerId) {
        if (viewerId == null || viewerId == moment.authorId) return@LaunchedEffect
        FollowStateStore.state(moment.authorId)?.let { followState = it }
        val authoritative = PrivacyService.getFollowButtonState(viewerId, moment.authorId)
        val reconciled = FollowStateStore.reconciledState(authoritative, moment.authorId)
        followState = reconciled
        FollowStateStore.setState(reconciled, moment.authorId)
    }

    DisposableEffect(moment.authorId) {
        val listener: (String, FollowButtonState) -> Unit = { userId, state ->
            if (userId == moment.authorId) followState = state
        }
        FollowStateStore.addListener(listener)
        onDispose { FollowStateStore.removeListener(listener) }
    }

    DisposableEffect(moment.authorId) {
        UserCacheService.getUser(moment.authorId) { user ->
            val name = user?.username?.trim().orEmpty()
            if (name.isNotEmpty()) displayUsername = name
        }
        onDispose { }
    }

    fun performFollowToggle() {
        val uid = viewerId ?: return
        if (!followState.isActionable) return
        val previous = followState
        val optimistic = when (previous) {
            FollowButtonState.FOLLOWING -> FollowButtonState.CAN_FOLLOW
            FollowButtonState.CAN_REQUEST_FOLLOW -> FollowButtonState.REQUEST_PENDING_CANCELLABLE
            FollowButtonState.REQUEST_PENDING_CANCELLABLE -> FollowButtonState.CAN_REQUEST_FOLLOW
            FollowButtonState.CAN_FOLLOW -> FollowButtonState.FOLLOWING
            else -> previous
        }
        followState = optimistic
        followLoading = true
        scope.launch {
            FollowStateStore.setState(optimistic, moment.authorId)
            val error = runCatching {
                when (previous) {
                    FollowButtonState.FOLLOWING -> firestore.unfollowUser(uid, moment.authorId)
                    FollowButtonState.REQUEST_PENDING_CANCELLABLE ->
                        firestore.cancelFollowRequest(uid, moment.authorId)
                    else -> firestore.followUser(uid, moment.authorId)
                }
            }.exceptionOrNull()
            followLoading = false
            if (error != null) {
                followState = previous
                FollowStateStore.setState(previous, moment.authorId)
            }
        }
    }

    if (showUnfollowConfirm) {
        AlertDialog(
            onDismissRequest = { showUnfollowConfirm = false },
            title = { Text(stringResource(R.string.user_profile_unfollow_confirm_title)) },
            text = { Text(stringResource(R.string.user_profile_unfollow_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnfollowConfirm = false
                        performFollowToggle()
                    },
                ) {
                    Text(stringResource(R.string.user_profile_unfollow_confirm_action), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnfollowConfirm = false }) {
                    Text(stringResource(R.string.feed_actions_cancel))
                }
            },
        )
    }

    Column(
        modifier
            .fillMaxWidth()
            .onSizeChanged { postWidthPx = it.width.toFloat() }
            .padding(horizontal = ListHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        AnimatedVisibility(visible = !isImmersive, enter = fadeIn(), exit = fadeOut()) {
            PostHeader(
                moment = moment,
                displayUsername = displayUsername,
                showFollow = showFollow,
                followState = followState,
                followLoading = followLoading,
                onFollowClick = {
                    if (followState == FollowButtonState.FOLLOWING) {
                        showUnfollowConfirm = true
                    } else {
                        performFollowToggle()
                    }
                },
                onOpenProfile = onOpenProfile,
                onOpenLocation = onOpenLocation,
                onAuthorAvatarTap = { hasStory ->
                    // iOS handleAuthorAvatarTap(hasStory:)
                    when {
                        onAuthorAvatarTap != null -> onAuthorAvatarTap(moment.authorId, hasStory)
                        hasStory -> showSpecificUserStories = true
                        else -> onOpenProfile()
                    }
                },
                onAuthorAvatarLongPress = onAuthorAvatarLongPress,
            )
        }

        if (showSpecificUserStories) {
            Dialog(
                onDismissRequest = { showSpecificUserStories = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                StoriesView(
                    startWithUserId = moment.authorId,
                    onDismiss = { showSpecificUserStories = false },
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ActionRowHorizontalPadding),
            contentAlignment = Alignment.BottomEnd,
        ) {
            if (mediaItems.isNotEmpty()) {
                Box(Modifier.fillMaxWidth()) {
                    MomentMediaCarousel(
                        moment = moment,
                        consumerId = "feed_${moment.id}",
                        mediaItemsOverride = mediaItems,
                        reelsVideos = reelsVideos,
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 8.dp,
                                shape = MediaCornerShape,
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = if (isDark) 0.22f else 0.08f),
                                spotColor = Color.Black.copy(alpha = if (isDark) 0.22f else 0.08f),
                            )
                            .clip(MediaCornerShape)
                            .carouselImmersivePeekGesture(
                                mediaItems = mediaItems,
                                currentImageIndex = currentImageIndex,
                                detectedAspectRatio = detectedAspectRatio,
                                realAspectRatio = realAspectRatio,
                                onImmersiveChange = { isImmersive = it },
                                onPeek = onPeek,
                            ),
                        applyOwnChrome = false,
                        showTags = showTags,
                        onToggleTags = { showTags = !showTags },
                        isImmersive = isImmersive,
                        onImmersiveChange = { isImmersive = it },
                        onPageChange = { currentImageIndex = it },
                        onTagTap = onTagTap ?: { onOpenProfile() },
                        fixedHeight = cardHeightDp,
                    )

                    if (moment.hasHiddenLayers &&
                        moment.hiddenLayerCount > 0 &&
                        mediaItems.size == 1 &&
                        mediaItems.first().type == "image" &&
                        currentImageIndex == 0
                    ) {
                        HiddenLayersOverlayView(
                            momentId = moment.id,
                            authorId = moment.authorId,
                            hasHiddenLayers = true,
                            hiddenLayerCount = moment.hiddenLayerCount,
                            isImmersive = isImmersive,
                            requiresFocusForIntro = true,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(MediaCornerShape),
                        )
                    }

                    // iOS: AttachmentIconView(.tagged, .overlayTaggedGlass)
                    Box(Modifier.align(Alignment.BottomStart)) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isImmersive &&
                                currentMedia?.isHiddenByModeration != true &&
                                currentTags.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Box(
                                Modifier
                                    .padding(start = 12.dp, bottom = 20.dp)
                                    .size(38.dp)
                                    .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.3f))
                                    .momentsChromeGlass(CircleShape, interactive = true)
                                    .clickable { showTags = !showTags },
                                contentAlignment = Alignment.Center,
                            ) {
                                AttachmentIconView(
                                    icon = AttachmentIcon.TAGGED,
                                    preset = AttachmentIconPreset.OVERLAY_TAGGED_GLASS,
                                    // Chrome glass light = fill claro → blanco se pierde; contentColor adaptativo.
                                    tintColor = if (showTags) {
                                        Color(0xFF007AFF)
                                    } else {
                                        MomentsChromeGlass.contentColor(isDark)
                                    },
                                )
                            }
                        }
                    }

                    Box(Modifier.align(Alignment.BottomEnd)) {
                        ModernActionButtons(
                            moment = moment,
                            isSaved = isSaved,
                            isSaveLoading = isSaveLoading,
                            commentCount = moment.commentCount,
                            onComment = onOpenComments,
                            onSave = { toggleSave() },
                            onContextMenu = { onContextMenu(moment) },
                            isImmersive = isImmersive,
                        )
                    }
                }
            }
        }

        // iOS siempre monta MomentCaptionView(style: .feed) — vacío = no-op interno
        AnimatedVisibility(visible = !isImmersive, enter = fadeIn(), exit = fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MomentCaptionView(
                    content = moment.content,
                    onHashtagTap = onOpenHashtag,
                    style = com.moments.android.views.components.MomentCaptionPresentationStyle.Feed,
                    authorId = moment.authorId,
                    username = moment.username,
                    audience = moment.audience,
                    previewImageUrl = moment.visibleMediaItems.firstOrNull()?.let { item ->
                        when (item.type.lowercase()) {
                            "video" -> item.thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }
                                ?: item.url.trim().takeIf { it.isNotEmpty() }
                            else -> item.url.trim().takeIf { it.isNotEmpty() }
                        }
                    },
                    isVideo = moment.visibleMediaItems.firstOrNull()?.type?.equals("video", ignoreCase = true),
                )

                Text(
                    text = relativeTime(moment.timestamp),
                    color = colors.tertiary,
                    fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .padding(horizontal = FeedMomentCardLayout.captionHorizontalPadding)
                        .padding(bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PostHeader(
    moment: FeedMoment,
    displayUsername: String,
    showFollow: Boolean,
    followState: FollowButtonState,
    followLoading: Boolean,
    onFollowClick: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLocation: (String, com.moments.android.models.Moment.LocationCoordinate?) -> Unit,
    onAuthorAvatarTap: (hasStory: Boolean) -> Unit,
    onAuthorAvatarLongPress: ((authorId: String, Rect) -> Unit)? = null,
) {
    val colors = rememberFeedAdaptiveColors()
    val context = LocalContext.current
    val density = LocalDensity.current
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid
    var isAuthorAvatarPressing by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isAuthorAvatarPressing) 0.94f else 1f,
        animationSpec = tween(120),
        label = "authorAvatarPressScale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isAuthorAvatarPressing) 0.88f else 1f,
        animationSpec = tween(120),
        label = "authorAvatarPressAlpha",
    )
    val capture = remember { FeedAuthorAvatarAnchorCapture() }
    capture.onTap = onAuthorAvatarTap
    capture.onLongPress = onAuthorAvatarLongPress
    capture.onPressingChanged = { isAuthorAvatarPressing = it }
    val pressClassifier = remember {
        Modifier.chatMessagePressClassifier(
            onPressingChanged = { capture.onPressingChanged(it) },
            onTap = { capture.onTap(capture.hasStory) },
            onLongPress = {
                val authorId = capture.authorId.trim()
                if (authorId.isNotEmpty()) {
                    capture.onLongPress?.invoke(authorId, capture.globalFrame)
                }
            },
        )
    }
    capture.authorId = moment.authorId

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = HeaderHorizontalPadding)
            .padding(top = HeaderVerticalPaddingTop, bottom = HeaderVerticalPaddingBottom),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // iOS postHeaderView: StoryRingAvatarView + FeedStoryCirclePressModifier
        Box(
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    capture.globalFrame = coords.boundsInWindow()
                }
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                    alpha = pressAlpha
                }
                .then(if (onAuthorAvatarLongPress != null) pressClassifier else Modifier),
        ) {
            com.moments.android.views.story.StoryRingAvatarView(
                userId = moment.authorId,
                size = PostAvatarSize,
                onTap = if (onAuthorAvatarLongPress == null) onAuthorAvatarTap else null,
                onHasStoryChange = { capture.hasStory = it },
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val usernameInteraction = remember { MutableInteractionSource() }
                Text(
                    text = displayUsername,
                    color = colors.primary,
                    fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .momentsPressIcon()
                        .clickable(
                            interactionSource = usernameInteraction,
                            indication = null,
                            onClick = onOpenProfile,
                        ),
                )

                // iOS: CurrentUserVerifiedBadge vs VerifiedBadgeView
                if (viewerId != null && viewerId == moment.authorId) {
                    CurrentUserVerifiedBadge(size = 14.dp)
                } else {
                    VerifiedBadgeView(userId = moment.authorId, size = 14.dp)
                }

            }

            moment.location?.takeIf { it.isNotBlank() }?.let { location ->
                val locationInteraction = remember { MutableInteractionSource() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(
                            interactionSource = locationInteraction,
                            indication = null,
                            onClick = { onOpenLocation(location, moment.locationCoordinate) },
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = location,
                        color = colors.secondary,
                        fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                        maxLines = 1,
                    )
                }
            }
        }

        if (showFollow) {
            ModernFollowButton(
                state = followState,
                isLoading = followLoading,
                onClick = onFollowClick,
            )
        }
    }
}

@Composable
private fun relativeTime(timestamp: Long): String {
    val elapsed = System.currentTimeMillis() - timestamp
    return when {
        elapsed < TimeUnit.MINUTES.toMillis(1) -> stringResource(R.string.time_now)
        elapsed < TimeUnit.HOURS.toMillis(1) ->
            "${TimeUnit.MILLISECONDS.toMinutes(elapsed)} ${stringResource(R.string.time_min)}"
        elapsed < TimeUnit.DAYS.toMillis(1) ->
            "${TimeUnit.MILLISECONDS.toHours(elapsed)} ${stringResource(R.string.time_hour)}"
        elapsed < TimeUnit.DAYS.toMillis(7) ->
            "${TimeUnit.MILLISECONDS.toDays(elapsed)} ${stringResource(R.string.time_day)}"
        else -> "${TimeUnit.MILLISECONDS.toDays(elapsed) / 7} ${stringResource(R.string.time_week)}"
    }
}

/** Frame en coordenadas de ventana, leído en el long-press (≡ iOS `FeedStoryCircleAnchorCapture`). */
private class FeedAuthorAvatarAnchorCapture {
    var globalFrame: Rect = Rect.Zero
    var authorId: String = ""
    var hasStory: Boolean = false
    var onTap: (Boolean) -> Unit = {}
    var onLongPress: ((String, Rect) -> Unit)? = null
    var onPressingChanged: (Boolean) -> Unit = {}
}

/** Port de `StoryProgressCircle` (FeedMomentComponents.swift). */
@Composable
fun StoryProgressCircle(
    progress: Double,
    isUploading: Boolean,
    modifier: Modifier = Modifier,
) {
    val p by animateFloatAsState(
        targetValue = progress.coerceIn(0.0, 1.0).toFloat(),
        animationSpec = spring(dampingRatio = 0.72f),
        label = "storyProgress",
    )
    val brush = if (isUploading) {
        Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFFAF52DE)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFFF9500), Color(0xFFFF2D55)))
    }
    Canvas(modifier.size(36.dp)) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 3.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        val dia = size.minDimension
        val topLeft = androidx.compose.ui.geometry.Offset((size.width - dia) / 2f, (size.height - dia) / 2f)
        val arc = androidx.compose.ui.geometry.Size(dia, dia)
        drawArc(
            color = Color.Gray.copy(alpha = 0.3f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arc,
            style = stroke,
        )
        drawArc(
            brush = brush,
            startAngle = -90f,
            sweepAngle = 360f * p,
            useCenter = false,
            topLeft = topLeft,
            size = arc,
            style = stroke,
        )
    }
}
