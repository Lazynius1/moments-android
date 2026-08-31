package com.moments.android.views.feed.stories

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.LegacyNavigationBridge
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.components.ModernFollowButton
import com.moments.android.views.components.ModernFollowButtonStyle
import com.moments.android.views.components.VerifiedBadge
import com.moments.android.views.messaging.components.ChatVideoPlayBadge
import com.moments.android.views.messaging.core.MessagingPresentationRoute
import com.moments.android.views.messaging.core.MessagingViewModel
import com.moments.android.views.messaging.core.PendingChatContextFactory
import com.moments.android.views.profile.core.GridPreviewThumbnailFrame
import com.moments.android.views.profile.core.gridPreviewSettings
import com.moments.android.views.profile.core.sections.ProfileAvatarNoteMetrics
import com.moments.android.views.profile.core.sections.ProfileAvatarNoteView
import com.moments.android.views.profile.userprofile.UserProfileColors
import com.moments.android.views.profile.userprofile.UserProfileViewModel
import com.moments.android.views.settings.hasVideoMedia
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.story.StoryRingAvatarView
import com.moments.android.views.story.storyviewer.GlassmorphicStoryConfirmationDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** ≡ iOS `FeedPostProfilePreviewSelection`. */
data class FeedPostProfilePreviewSelection(
    val userId: String,
    val momentId: String,
    val anchorFrame: Rect,
    val postFrame: Rect,
)

/**
 * Port de `FeedPostProfilePreviewOverlay.swift`.
 * Long-press del avatar del post: tarjeta al centro, grid 4×2 (máx. 8), stats y pie Ver perfil / Mensaje.
 */
@Composable
fun FeedPostProfilePreviewOverlay(
    selection: FeedPostProfilePreviewSelection?,
    onSelectionChange: (FeedPostProfilePreviewSelection?) -> Unit,
    messagingViewModel: MessagingViewModel,
    onOpenProfile: (String) -> Unit,
    onPresentMessages: () -> Unit,
    onPresentedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val canvasColor = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val cardShape = RoundedCornerShape(26.dp)

    var isPresented by remember { mutableStateOf(false) }
    var dismissGeneration by remember { mutableIntStateOf(0) }
    var cardSurfaceAlpha by remember { mutableFloatStateOf(1f) }
    var showUnfollowConfirmation by remember { mutableStateOf(false) }
    var unfollowViewModel by remember { mutableStateOf<UserProfileViewModel?>(null) }
    var overlayWindowBounds by remember { mutableStateOf(Rect.Zero) }

    val reduceMotion = MotionPolicy.reduceMotion
    val presentedAlpha by animateFloatAsState(
        targetValue = if (isPresented) 1f else 0f,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else if (isPresented) {
            spring(dampingRatio = 0.84f, stiffness = 380f)
        } else {
            tween(260)
        },
        label = "postProfilePreviewAlpha",
    )

    fun dismissOverlay(then: (() -> Unit)? = null) {
        dismissGeneration += 1
        val generation = dismissGeneration
        showUnfollowConfirmation = false
        unfollowViewModel = null
        cardSurfaceAlpha = 1f
        isPresented = false
        scope.launch {
            if (!reduceMotion) {
                delay(120)
                if (generation != dismissGeneration) return@launch
                cardSurfaceAlpha = 0f
            } else {
                cardSurfaceAlpha = 0f
            }
        }
        val delayMs = if (reduceMotion) 0L else 260L
        scope.launch {
            delay(delayMs)
            if (generation != dismissGeneration) return@launch
            onPresentedChange(false)
            onSelectionChange(null)
            cardSurfaceAlpha = 1f
            then?.invoke()
        }
    }

    fun handleFollowAction(viewModel: UserProfileViewModel, userId: String) {
        when (viewModel.followButtonState) {
            FollowButtonState.FOLLOWING -> {
                unfollowViewModel = viewModel
                showUnfollowConfirmation = true
            }
            FollowButtonState.CAN_FOLLOW, FollowButtonState.CAN_REQUEST_FOLLOW ->
                viewModel.followUser(userId)
            FollowButtonState.REQUEST_PENDING_CANCELLABLE ->
                viewModel.cancelFollowRequest(userId)
            else -> Unit
        }
    }

    fun openMessage(user: AppUser, profileViewModel: UserProfileViewModel) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        dismissOverlay {
            messagingViewModel.startConversation(user, currentUserId) { conversation ->
                scope.launch {
                    val conversationId = conversation?.id
                    if (conversation != null && !conversationId.isNullOrEmpty()) {
                        LegacyNavigationBridge.conversation(conversationId)
                        return@launch
                    }
                    if (conversation != null) {
                        messagingViewModel.presentationRoute =
                            MessagingPresentationRoute.Conversation(conversation)
                        onPresentMessages()
                        return@launch
                    }
                    if (messagingViewModel.presentationRoute != null) {
                        onPresentMessages()
                        return@launch
                    }
                    if (!messagingViewModel.requiresMessageRequest) return@launch
                    val context = PendingChatContextFactory.outgoing(
                        user = user,
                        currentUserId = currentUserId,
                        followersCountOverride = profileViewModel.followers.size,
                        momentsCountOverride = profileViewModel.moments.size,
                    )
                    messagingViewModel.presentationRoute =
                        MessagingPresentationRoute.PendingChat(context)
                    onPresentMessages()
                }
            }
        }
    }

    LaunchedEffect(selection?.userId) {
        val userId = selection?.userId
        if (userId == null) {
            GlobalVideoManager.endPlaybackHold("feed-profile-preview")
            onPresentedChange(false)
            isPresented = false
            showUnfollowConfirmation = false
            unfollowViewModel = null
            return@LaunchedEffect
        }
        GlobalVideoManager.beginPlaybackHold("feed-profile-preview")
        dismissGeneration += 1
        cardSurfaceAlpha = 1f
        isPresented = false
        showUnfollowConfirmation = false
        unfollowViewModel = null
        delay(1)
        isPresented = true
    }

    if (selection == null) return

    val topInsetPx = WindowInsets.statusBars.getTop(density).toFloat()
    val layout = remember(selection, overlayWindowBounds, density, topInsetPx) {
        previewLayout(
            selection = selection,
            overlayGlobal = overlayWindowBounds,
            density = density,
            topInsetPx = topInsetPx,
        )
    }
    val cardSurfaceAlphaAnimated by animateFloatAsState(
        targetValue = cardSurfaceAlpha,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else {
            tween(durationMillis = 140, easing = FastOutSlowInEasing)
        },
        label = "postProfilePreviewCardHandoffAlpha",
    )
    val centerX by animateFloatAsState(
        targetValue = if (isPresented) layout.targetCenter.x else layout.morphCenter.x,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else if (isPresented) {
            spring(dampingRatio = 0.84f, stiffness = 380f)
        } else {
            tween(260)
        },
        label = "postProfilePreviewCenterX",
    )
    val centerY by animateFloatAsState(
        targetValue = if (isPresented) layout.targetCenter.y else layout.morphCenter.y,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else if (isPresented) {
            spring(dampingRatio = 0.84f, stiffness = 380f)
        } else {
            tween(260)
        },
        label = "postProfilePreviewCenterY",
    )
    val presentedScale by animateFloatAsState(
        targetValue = if (isPresented) 1f else layout.morphScale,
        animationSpec = if (reduceMotion) {
            tween(0)
        } else if (isPresented) {
            spring(dampingRatio = 0.84f, stiffness = 380f)
        } else {
            tween(260)
        },
        label = "postProfilePreviewScale",
    )

    val cardWidth = with(density) { layout.cardWidthPx.toDp() }
    val contentWidth = with(density) { layout.contentWidthPx.toDp() }
    val gridCellSize = with(density) { layout.gridCellSizePx.toDp() }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayWindowBounds = it.boundsInWindow() },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f * presentedAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismissOverlay() },
                ),
        )

        key(selection.userId) {
            FeedPostProfilePreviewCard(
                userId = selection.userId,
                cardWidth = cardWidth,
                contentWidth = contentWidth,
                gridCellSize = gridCellSize,
                canvasColor = canvasColor,
                cardShape = cardShape,
                isDark = isDark,
                isPresented = isPresented,
                presentedScale = presentedScale,
                cardSurfaceAlpha = cardSurfaceAlphaAnimated,
                centerX = centerX,
                centerY = centerY,
                onOpenProfile = {
                    dismissOverlay { onOpenProfile(selection.userId) }
                },
                onMessage = { user, viewModel -> openMessage(user, viewModel) },
                onFollow = { viewModel -> handleFollowAction(viewModel, selection.userId) },
            )
        }

        if (showUnfollowConfirmation) {
            GlassmorphicStoryConfirmationDialog(
                title = stringResource(R.string.user_profile_unfollow_confirm_title),
                message = stringResource(R.string.user_profile_unfollow_confirm_message),
                confirmTitle = stringResource(R.string.user_profile_unfollow_confirm_action),
                cancelTitle = stringResource(R.string.common_cancel),
                isDestructive = true,
                onConfirm = {
                    showUnfollowConfirmation = false
                    unfollowViewModel?.unfollowUser(selection.userId)
                    unfollowViewModel = null
                },
                onCancel = {
                    showUnfollowConfirmation = false
                    unfollowViewModel = null
                },
            )
        }
    }
}

@Composable
private fun FeedPostProfilePreviewCard(
    userId: String,
    cardWidth: Dp,
    contentWidth: Dp,
    gridCellSize: Dp,
    canvasColor: Color,
    cardShape: RoundedCornerShape,
    isDark: Boolean,
    isPresented: Boolean,
    presentedScale: Float,
    cardSurfaceAlpha: Float,
    centerX: Float,
    centerY: Float,
    onOpenProfile: () -> Unit,
    onMessage: (AppUser, UserProfileViewModel) -> Unit,
    onFollow: (UserProfileViewModel) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val viewModel = remember(userId) { UserProfileViewModel(userId) }
    val isOwnProfile = userId == FirebaseAuth.getInstance().currentUser?.uid
    val previewMoments = viewModel.moments.take(8)
    val shouldShowPreviewGrid = previewMoments.isNotEmpty() &&
        (isOwnProfile || viewModel.canViewContent)
    val noteScale = 80f / ProfileAvatarNoteMetrics.columnWidth.value
    var measuredHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(userId) {
        viewModel.checkFollowButtonState()
        // Mismo scan que el perfil: el backend aplica `limit` antes de filtrar visibilidad.
        viewModel.fetchProfile(momentsLimit = 50)
        viewModel.refreshMutualRelationship()
    }

    Column(
        modifier = Modifier
            .width(cardWidth)
            .onSizeChanged { measuredHeightPx = it.height.toFloat() }
            .offset {
                val heightPx = measuredHeightPx.takeIf { it > 0f } ?: estimatedCardHeightPx(
                    density = this,
                    gridCellSizePx = gridCellSize.toPx(),
                    isOwnProfile = isOwnProfile,
                )
                IntOffset(
                    (centerX - cardWidth.toPx() / 2f).roundToInt(),
                    (centerY - heightPx / 2f).roundToInt(),
                )
            }
            .graphicsLayer {
                alpha = cardSurfaceAlpha
                scaleX = presentedScale
                scaleY = presentedScale
                transformOrigin = TransformOrigin.Center
                clip = false
            }
            .shadow(
                elevation = if (isPresented) 28.dp else 0.dp,
                shape = cardShape,
                ambientColor = Color.Black.copy(alpha = if (isPresented) 0.28f else 0f),
                spotColor = Color.Black.copy(alpha = if (isPresented) 0.28f else 0f),
            )
            .clip(cardShape)
            .background(canvasColor)
            .border(
                width = 1.dp,
                color = UserProfileColors.borderColor.copy(alpha = if (isDark) 0.14f else 0.22f),
                shape = cardShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenProfile,
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.width(80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StoryRingAvatarView(
                    userId = userId,
                    size = 56.dp,
                    lineWidth = 2.5.dp,
                    allowOwnStories = true,
                )
                ProfileAvatarNoteView(
                    note = viewModel.userProfile?.profileNote,
                    isEditable = false,
                    modifier = Modifier
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            scaleX = noteScale
                            scaleY = noteScale
                        }
                        .width(80.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val username = viewModel.userProfile?.username
                    ?: stringResource(R.string.user_profile_user)
                val usernameRow = @Composable {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = username,
                            color = UserProfileColors.textPrimary,
                            fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (viewModel.userProfile?.isVerified == true) {
                            VerifiedBadge(size = 14.dp)
                        }
                    }
                }
                if (isOwnProfile) {
                    usernameRow()
                } else {
                    Box(
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenProfile,
                        ),
                    ) { usernameRow() }
                }

                viewModel.userProfile?.bio?.trim()?.takeIf { it.isNotEmpty() }?.let { bio ->
                    Text(
                        text = bio,
                        color = UserProfileColors.textSecondary,
                        fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!isOwnProfile) {
                ModernFollowButton(
                    state = viewModel.followButtonState,
                    isLoading = false,
                    onClick = { onFollow(viewModel) },
                    style = ModernFollowButtonStyle.COMPACT,
                    isMutual = viewModel.isMutualRelationship,
                )
            }
        }

        when {
            shouldShowPreviewGrid -> {
                Column(
                    modifier = Modifier
                        .width(contentWidth)
                        .clip(RoundedCornerShape(10.dp)),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    previewMoments.chunked(4).forEach { rowMoments ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            rowMoments.forEach { moment ->
                                // ≡ ProfileMomentsBentoGrid: el host seguro va dentro de un frame fijo.
                                Box(Modifier.size(gridCellSize)) {
                                    ScreenshotProtectedView(
                                        isProtected = (moment.audience?.lowercase() ?: "") != "everyone",
                                        fillsContainer = true,
                                    ) {
                                        FeedPostProfilePreviewMomentThumb(
                                            moment = moment,
                                            size = gridCellSize,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            (viewModel.isLoading || viewModel.isLoadingMoments) && previewMoments.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = UserProfileColors.accent,
                    )
                }
            }
        }

        val stats = previewStats(viewModel, isOwnProfile)
        if (stats.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                stats.forEachIndexed { index, stat ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = MomentsFormat.count(stat.count, MomentsFormat.CountStyle.PROFILE_STAT),
                            color = UserProfileColors.textPrimary,
                            fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stat.label,
                            color = UserProfileColors.textSecondary,
                            fontSize = with(density) { legacyPoppinsSize(context, 10).toSp() },
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (index < stats.lastIndex) {
                        Box(
                            Modifier
                                .padding(vertical = 2.dp)
                                .width(1.dp)
                                .height(28.dp)
                                .background(
                                    UserProfileColors.borderColor.copy(
                                        alpha = if (isDark) 0.22f else 0.35f,
                                    ),
                                ),
                        )
                    }
                }
            }
        }

        if (!isOwnProfile) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FooterActionButton(
                    title = stringResource(R.string.user_activity_event_action_view_profile),
                    icon = {
                        Icon(
                            Icons.Outlined.AccountCircle,
                            contentDescription = null,
                            tint = UserProfileColors.textPrimary,
                            modifier = Modifier.size(13.dp),
                        )
                    },
                    onClick = onOpenProfile,
                    modifier = Modifier.weight(1f),
                )
                FooterActionButton(
                    title = stringResource(R.string.user_profile_send_message),
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.feed_paperplane_icon),
                            contentDescription = null,
                            tint = UserProfileColors.textPrimary,
                            modifier = Modifier.size(13.dp),
                        )
                    },
                    onClick = {
                        val user = viewModel.userProfile ?: return@FooterActionButton
                        onMessage(user, viewModel)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FooterActionButton(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    Row(
        modifier
            .momentsChromeGlass(
                shape = RoundedCornerShape(percent = 50),
                interactive = true,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = title,
            color = UserProfileColors.textPrimary,
            fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun FeedPostProfilePreviewMomentThumb(moment: Moment, size: Dp) {
    Box(contentAlignment = Alignment.BottomStart) {
        GridPreviewThumbnailFrame(size = size, settings = moment.gridPreviewSettings) { contentScale ->
            val url = moment.previewImageURLString
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(UserProfileColors.cardBackground),
                )
            }
        }
        if (moment.hasVideoMedia) {
            ChatVideoPlayBadge(size = 14.dp, padding = 8.dp)
        }
    }
}

@Composable
private fun previewStats(
    viewModel: UserProfileViewModel,
    isOwnProfile: Boolean,
): List<PreviewStat> {
    val postsLabel = stringResource(R.string.profile_ui_posts)
    val followersLabel = stringResource(R.string.profile_ui_followers)
    val followingLabel = stringResource(R.string.profile_ui_following)
    val postsCount = max(viewModel.moments.size, viewModel.userProfile?.momentsCount ?: 0)
    val stats = mutableListOf<PreviewStat>()
    if (viewModel.canViewContent || isOwnProfile) {
        stats += PreviewStat(postsLabel, postsCount)
        if (viewModel.visibleConnectionTypes.canViewFollowers) {
            stats += PreviewStat(followersLabel, viewModel.followers.size)
        }
        if (viewModel.visibleConnectionTypes.canViewFollowing) {
            stats += PreviewStat(followingLabel, viewModel.following.size)
        }
    } else {
        if (viewModel.visibleConnectionTypes.canViewFollowers) {
            stats += PreviewStat(followersLabel, viewModel.followers.size)
        }
        if (viewModel.visibleConnectionTypes.canViewFollowing) {
            stats += PreviewStat(followingLabel, viewModel.following.size)
        }
    }
    return stats
}

private data class PreviewStat(val label: String, val count: Int)

private data class ProfilePreviewLayout(
    val cardWidthPx: Float,
    val contentWidthPx: Float,
    val gridCellSizePx: Float,
    val morphCenter: Offset,
    val morphScale: Float,
    val targetCenter: Offset,
)

private fun previewLayout(
    selection: FeedPostProfilePreviewSelection,
    overlayGlobal: Rect,
    density: androidx.compose.ui.unit.Density,
    topInsetPx: Float,
): ProfilePreviewLayout {
    val horizontalInsetPx = with(density) { 16.dp.toPx() }
    val cardPaddingPx = with(density) { 16.dp.toPx() }
    val gridSpacingPx = with(density) { 3.dp.toPx() }
    val avatarGapPx = with(density) { 10.dp.toPx() }
    val overlayWidth = overlayGlobal.width.takeIf { it > 1f } ?: with(density) { 360.dp.toPx() }
    val overlayHeight = overlayGlobal.height.takeIf { it > 1f } ?: with(density) { 780.dp.toPx() }

    val anchor = Rect(
        selection.anchorFrame.left - overlayGlobal.left,
        selection.anchorFrame.top - overlayGlobal.top,
        selection.anchorFrame.right - overlayGlobal.left,
        selection.anchorFrame.bottom - overlayGlobal.top,
    )
    val post = Rect(
        selection.postFrame.left - overlayGlobal.left,
        selection.postFrame.top - overlayGlobal.top,
        selection.postFrame.right - overlayGlobal.left,
        selection.postFrame.bottom - overlayGlobal.top,
    )

    val cardWidth = max(with(density) { 300.dp.toPx() }, overlayWidth - horizontalInsetPx * 2f)
    val contentWidth = cardWidth - cardPaddingPx * 2f
    val gridColumns = 4f
    val maxGridRows = 2f
    val gridCellSize = (contentWidth - gridSpacingPx * (gridColumns - 1f)) / gridColumns
    val isOwnProfile = selection.userId == FirebaseAuth.getInstance().currentUser?.uid
    val cardHeight = estimatedCardHeightPx(density, gridCellSize, isOwnProfile)

    val minCenterX = horizontalInsetPx + cardWidth / 2f
    val maxCenterX = max(minCenterX, overlayWidth - horizontalInsetPx - cardWidth / 2f)
    val avatarFallbackCenterX = min(max(anchor.center.x, minCenterX), maxCenterX)
    val avatarFallbackCenterY = anchor.bottom + avatarGapPx + cardHeight / 2f

    val hasMorph = post.width > 1f && post.height > 1f
    val morphCenter: Offset
    val morphScale: Float
    if (hasMorph) {
        morphCenter = post.center
        morphScale = min(
            max(min(post.width / cardWidth, post.height / cardHeight), 0.01f),
            1f,
        )
    } else {
        morphCenter = Offset(avatarFallbackCenterX, avatarFallbackCenterY)
        morphScale = 0.92f
    }

    val safeMidY = topInsetPx + (overlayHeight - topInsetPx) / 2f
    val minCenterY = topInsetPx + cardHeight / 2f + with(density) { 12.dp.toPx() }
    val maxCenterY = overlayHeight - cardHeight / 2f - with(density) { 12.dp.toPx() }
    val targetCenterY = min(max(safeMidY, minCenterY), maxCenterY)
    val targetCenterX = overlayWidth / 2f

    return ProfilePreviewLayout(
        cardWidthPx = cardWidth,
        contentWidthPx = contentWidth,
        gridCellSizePx = gridCellSize,
        morphCenter = morphCenter,
        morphScale = morphScale,
        targetCenter = Offset(targetCenterX, targetCenterY),
    )
}

private fun estimatedCardHeightPx(
    density: androidx.compose.ui.unit.Density,
    gridCellSizePx: Float,
    isOwnProfile: Boolean,
): Float {
    val cardPaddingPx = with(density) { 16.dp.toPx() }
    val gridSpacingPx = with(density) { 3.dp.toPx() }
    val maxGridRows = 2f
    val gridHeight = gridCellSizePx * maxGridRows + gridSpacingPx * (maxGridRows - 1f)
    val footerHeight = if (isOwnProfile) 0f else with(density) { 44.dp.toPx() }
    return cardPaddingPx * 2f + with(density) { 88.dp.toPx() } + gridHeight +
        with(density) { 48.dp.toPx() } + footerHeight + with(density) { 20.dp.toPx() }
}
