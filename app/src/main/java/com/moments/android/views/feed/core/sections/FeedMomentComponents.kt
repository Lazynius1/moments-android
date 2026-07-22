package com.moments.android.views.feed.core.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.MomentsGlassStyle
import com.moments.android.extensions.momentsChromeGlass
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
import com.moments.android.views.feed.moments.ClickableHashtagsView
import com.moments.android.views.feed.reactions.PostActionButtons
import com.moments.android.views.feed.uploads.StoryUploadProgressManager
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

// Métricas compartidas — port de `FeedMomentCardLayout` (iOS).
private val ListHorizontalPadding = FeedMomentCardLayout.listHorizontalPadding
private val HeaderHorizontalPadding = FeedMomentCardLayout.headerHorizontalPadding
private val HeaderVerticalPadding = 9.dp
private val ActionRowHorizontalPadding = FeedMomentCardLayout.actionRowHorizontalPadding
private val PostAvatarSize = 44.dp
private val HeaderIconHitSize = 36.dp
private val HeaderIconSize = 22.dp
private val MediaCorner = FeedMomentCardLayout.mediaCornerRadius

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
        // iOS SF Symbol: heart / heart.fill @ 22pt medium
        Icon(
            painter = painterResource(
                if (hasNotification) R.drawable.ic_heart_fill else R.drawable.ic_heart_outline,
            ),
            contentDescription = stringResource(R.string.feed_activity),
            tint = if (hasNotification) Color.Red else colors.icon,
            modifier = Modifier.size(HeaderIconSize),
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
        // iOS SF Symbol: paperplane @ 22pt
        Icon(
            painter = painterResource(R.drawable.ic_paperplane_outline),
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
        // iOS MomentHashtagText (base blanco en este contexto)
        ClickableHashtagsView(
            content = display,
            onHashtagTap = onHashtagTap,
            isDarkTheme = true,
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

/** Port de `ModernFollowButton` (MomentRailComponents.swift) — cápsula glass, no fill teal. */
@Composable
fun ModernFollowButton(
    state: FollowButtonState,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberFeedAdaptiveColors()
    val context = LocalContext.current
    val density = LocalDensity.current
    val label = when (state) {
        FollowButtonState.FOLLOWING -> stringResource(R.string.feed_following_action)
        FollowButtonState.CAN_REQUEST_FOLLOW -> stringResource(R.string.feed_follow_request)
        FollowButtonState.REQUEST_PENDING -> stringResource(R.string.feed_follow_requested)
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> stringResource(R.string.feed_follow_cancel_request)
        FollowButtonState.BLOCKED -> stringResource(R.string.user_profile_blocked)
        else -> stringResource(R.string.feed_follow)
    }
    val icon = when (state) {
        FollowButtonState.FOLLOWING -> Icons.Filled.Verified
        FollowButtonState.CAN_REQUEST_FOLLOW -> Icons.Filled.PersonAdd
        FollowButtonState.REQUEST_PENDING -> Icons.Filled.AccessTime
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> Icons.Filled.Close
        else -> Icons.Filled.PersonAdd
    }
    val isPassive = state == FollowButtonState.OWN_PROFILE ||
        state == FollowButtonState.BLOCKED ||
        state == FollowButtonState.REQUEST_PENDING
    Row(
        modifier
            .graphicsLayer { alpha = if (isPassive) 0.78f else 1f }
            .momentsChromeGlass(
                shape = RoundedCornerShape(percent = 50),
                interactive = state.isActionable,
                style = MomentsGlassStyle.NATIVE,
            )
            .clickable(enabled = !isLoading && state.isActionable, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = colors.primary,
                strokeWidth = 1.5.dp,
            )
        } else {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                color = colors.primary,
                fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
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
    onPeek: ((imageUrl: String, ratio: Float, isPressing: Boolean) -> Unit)? = null,
    onTagTap: ((String) -> Unit)? = null,
    authorHasStory: Boolean = false,
    authorHasUnseenStory: Boolean = false,
    showVerifiedBadge: Boolean = false,
    availableHeight: Float? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
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
    val savedIds by firestore.savedMomentIds.collectAsState()
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid
    val showFollow = viewerId != null && viewerId != moment.authorId
    val visibleMedia = moment.visibleMediaItems.ifEmpty { moment.mediaItems }
    val detectedAspectRatio = MomentCarouselLayoutRules.feedDisplayAspectRatio(
        MomentCarouselLayoutRules.aspectRatioValue(moment.aspectRatio ?: visibleMedia.firstOrNull()?.aspectRatio),
    )
    val realAspectRatio = MomentCarouselLayoutRules.aspectRatioValue(
        moment.aspectRatio ?: visibleMedia.firstOrNull()?.aspectRatio,
    )
    val currentMedia = visibleMedia.getOrNull(currentImageIndex)
    val currentTags = currentMedia?.tags.orEmpty()

    // iOS calculateCardHeight: idealHeight = width/ratio, capped at availableHeight * 0.95
    val cardHeightDp = availableHeight?.let { availPx ->
        with(density) {
            val maxWidthPx = (screenWidthDp.dp.toPx() -
                (ListHorizontalPadding * 2 + ActionRowHorizontalPadding * 2).toPx())
                .coerceAtLeast(1f)
            val ideal = maxWidthPx / detectedAspectRatio.coerceAtLeast(0.01f)
            val capped = min(ideal, availPx * 0.95f).coerceAtLeast(150f)
            max(capped, 200f).toDp()
        }
    }

    LaunchedEffect(moment.id) { onNearEnd() }

    // iOS: onChange savedMomentIds + loadAllPostData checkIfSaved
    LaunchedEffect(savedIds, moment.id) {
        isSaved = savedIds.contains(moment.id)
    }

    LaunchedEffect(moment.id, viewerId) {
        val uid = viewerId ?: return@LaunchedEffect
        if (savedIds.contains(moment.id)) {
            isSaved = true
            return@LaunchedEffect
        }
        runCatching { firestore.checkIfSaved(uid, moment.id) }
            .onSuccess { isSaved = it }
    }

    fun toggleSave() {
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
        modifier.fillMaxWidth().padding(horizontal = ListHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        hasStory -> {
                            // iOS fullScreenCover StoriesView(startWithUserId:) — sin callback del padre,
                            // el feed cablea onAuthorAvatarTap; aquí no inventamos otra ruta.
                        }
                        else -> onOpenProfile()
                    }
                },
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ActionRowHorizontalPadding),
            contentAlignment = Alignment.BottomEnd,
        ) {
            if (visibleMedia.isNotEmpty()) {
                Box(Modifier.fillMaxWidth()) {
                    MomentMediaCarousel(
                        moment = moment,
                        consumerId = "feed_${moment.id}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(MediaCorner),
                                clip = false,
                                ambientColor = Color.Black.copy(alpha = if (isDark) 0.22f else 0.08f),
                                spotColor = Color.Black.copy(alpha = if (isDark) 0.22f else 0.08f),
                            )
                            .clip(RoundedCornerShape(MediaCorner))
                            .carouselImmersivePeekGesture(
                                mediaItems = visibleMedia,
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
                        visibleMedia.size == 1 &&
                        visibleMedia.first().type == "image"
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
                                .clip(RoundedCornerShape(MediaCorner)),
                        )
                    }

                    // iOS: botón glass etiquetas (esquina inferior izquierda)
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
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = if (showTags) Color(0xFF007AFF) else Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Box(Modifier.align(Alignment.BottomEnd)) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isImmersive,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            PostActionButtons(
                                moment = moment,
                                onOpenComments = onOpenComments,
                                onShare = onShare,
                                onContextMenu = { onContextMenu(moment) },
                                isSaved = isSaved,
                                onSave = { toggleSave() },
                            )
                        }
                    }
                }
            }
        }

        if (moment.content.isNotBlank()) {
            AnimatedVisibility(visible = !isImmersive, enter = fadeIn(), exit = fadeOut()) {
                MomentCaptionView(
                    content = moment.content,
                    onHashtagTap = onOpenHashtag,
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
) {
    val colors = rememberFeedAdaptiveColors()
    val context = LocalContext.current
    val density = LocalDensity.current
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = HeaderHorizontalPadding, vertical = HeaderVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // iOS postHeaderView: StoryRingAvatarView(userId:size:onTap:)
        com.moments.android.views.story.StoryRingAvatarView(
            userId = moment.authorId,
            size = PostAvatarSize,
            onTap = onAuthorAvatarTap,
        )

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

                Text(
                    text = "·",
                    color = colors.tertiary,
                    fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
                )
                Text(
                    text = relativeTime(moment.timestamp),
                    color = colors.tertiary,
                    fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
                )
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

/** Port de `StoryProgressCircle` (FeedMomentComponents.swift). */
@Composable
fun StoryProgressCircle(
    progress: Double,
    isUploading: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = progress.coerceIn(0.0, 1.0).toFloat()
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
