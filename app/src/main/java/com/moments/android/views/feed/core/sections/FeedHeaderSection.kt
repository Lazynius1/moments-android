package com.moments.android.views.feed.core.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Echo
import com.moments.android.notifications.services.NotificationBadgeService
import com.moments.android.notifications.services.NotificationService
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.utilities.momentsPressIcon
import com.moments.android.views.components.EchoesIconMetrics
import com.moments.android.views.components.EchoesIconViewBrandHorizontal
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.feed.controls.FeedType
import com.moments.android.views.feed.controls.FloatingGlassFeedToggle
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.feed.stories.StoryRingTraySkeleton

/** Port 1:1 de `FeedHeaderSection.swift`. */

@Composable
fun FeedRefreshIndicator(mod: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val density = LocalDensity.current
    // iOS ProgressView scaleEffect 0.72 ≈ 14–16dp indicator
    Row(
        mod
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size((16 * 0.72f).dp),
            strokeWidth = 2.dp,
            color = if (isDark) Color.White else Color.Black,
        )
        Text(
            stringResource(R.string.feed_refreshing),
            color = if (isDark) Color.White.copy(alpha = 0.74f) else Color.Black.copy(alpha = 0.62f),
            fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun FeedHeaderBar(
    storyUsers: List<FeedStoryUserState>,
    isLoadingStories: Boolean,
    isLoadingMoreRing: Boolean = false,
    pendingEchoes: List<Echo> = emptyList(),
    ownStoryProfileImageUrl: String? = null,
    ownStoryCount: Int = 0,
    currentUserId: String? = null,
    onCreateStory: () -> Unit,
    onOpenStory: (FeedStoryUserState) -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenEchoHistory: () -> Unit = {},
    onOpenEchoInvitation: (String) -> Unit = {},
    onLoadMoreRing: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val headerBg = rememberAdaptiveColors().surfaceBackground
    val unreadNotifications by NotificationBadgeService.unreadNotificationsCount.collectAsState()
    val unreadMessages by NotificationBadgeService.unreadMessagesCount.collectAsState()
    var echoMenuExpanded by remember { mutableStateOf(false) }

    // iOS: first = current user; ForEach dropFirst
    val ownFromTray = remember(storyUsers, currentUserId) {
        when {
            currentUserId != null -> storyUsers.firstOrNull { it.userId == currentUserId }
            else -> storyUsers.firstOrNull()
        }
    }
    val otherStoryUsers = remember(storyUsers, currentUserId, ownFromTray) {
        when {
            currentUserId != null -> storyUsers.filter { it.userId != currentUserId }
            storyUsers.isNotEmpty() -> storyUsers.drop(1)
            else -> emptyList()
        }
    }
    val resolvedOwnHasStory = ownFromTray?.hasStory == true || ownStoryCount > 0
    val resolvedOwnCount = ownFromTray?.storyCount?.takeIf { it > 0 } ?: ownStoryCount

    // iOS FeedHeaderBar:
    //   .padding(.top, 16).padding(.bottom, 4)
    //   .background(Rectangle().fill(...).ignoresSafeArea(edges: .top))
    // Compose: background antes de statusBarsPadding → color bajo la status bar.
    Row(
        modifier
            .fillMaxWidth()
            .background(headerBg)
            .statusBarsPadding()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 4.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (isLoadingStories && storyUsers.isEmpty()) {
                items(6) { index ->
                    StoryRingTraySkeleton(isOwnStory = index == 0)
                }
            } else {
                item {
                    YourStoryRing(
                        onClick = {
                            // iOS: si ya hay story propia → abrir; si no → creator
                            val own = ownFromTray
                            if (resolvedOwnHasStory && own != null && own.userId.isNotBlank()) {
                                onOpenStory(own)
                            } else if (resolvedOwnHasStory && currentUserId != null) {
                                onOpenStory(
                                    FeedStoryUserState(
                                        userId = currentUserId,
                                        username = "",
                                        profileImageUrl = ownStoryProfileImageUrl,
                                        hasStory = true,
                                        storyCount = resolvedOwnCount,
                                        storyAudiences = ownFromTray?.storyAudiences.orEmpty(),
                                    ),
                                )
                            } else {
                                onCreateStory()
                            }
                        },
                        profileImageUrl = ownStoryProfileImageUrl ?: ownFromTray?.profileImageUrl,
                        hasStory = resolvedOwnHasStory,
                        storyCount = resolvedOwnCount,
                        storyAudiences = ownFromTray?.storyAudiences.orEmpty(),
                    )
                }
                itemsIndexed(otherStoryUsers, key = { _, u -> u.userId }) { index, user ->
                    UserStoryRing(
                        userId = user.userId,
                        username = user.username,
                        imageUrl = user.profileImageUrl,
                        hasStory = user.hasStory,
                        hasUnseenStory = user.hasUnseenStory,
                        storyCount = user.storyCount,
                        viewedStatuses = user.storyViewedStatus,
                        storyAudiences = user.storyAudiences,
                        onClick = { onOpenStory(user) },
                    )
                    if (index >= otherStoryUsers.size - 3) onLoadMoreRing()
                }
                if (isLoadingMoreRing) {
                    item { StoryRingTrayLoadingTail() }
                }
            }
        }

        // iOS Spacer() — weight(1f) en LazyRow ya empuja iconos a la derecha

        Row(
            Modifier.padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                EchoApertureIcon(
                    pendingCount = pendingEchoes.size,
                    onClick = {
                        if (pendingEchoes.isNotEmpty()) echoMenuExpanded = true
                        else onOpenEchoHistory()
                    },
                )
                DropdownMenu(expanded = echoMenuExpanded, onDismissRequest = { echoMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feed_echo_actions_view_invitations)) },
                        onClick = {
                            echoMenuExpanded = false
                            pendingEchoes.firstOrNull()?.id?.let(onOpenEchoInvitation)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feed_echo_actions_view_history)) },
                        onClick = {
                            echoMenuExpanded = false
                            onOpenEchoHistory()
                        },
                    )
                }
            }

            ModernNotificationButton(
                hasNotification = unreadNotifications > 0,
                onClick = {
                    NotificationService.markAllAsRead()
                    NotificationBadgeService.clearNotificationBadge()
                    onOpenActivity()
                },
            )
            ModernMessageButton(
                hasMessage = unreadMessages > 0,
                messageCount = unreadMessages,
                onClick = onOpenMessages,
            )
        }
    }
}

@Composable
fun FeedHeaderSection(
    storyUsers: List<FeedStoryUserState>,
    isLoadingStories: Boolean,
    ownStoryProfileImageUrl: String? = null,
    ownStoryCount: Int = 0,
    currentUserId: String? = null,
    onCreateStory: () -> Unit,
    onOpenStory: (FeedStoryUserState) -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMessages: () -> Unit,
    modifier: Modifier = Modifier,
    pendingEchoes: List<Echo> = emptyList(),
    isLoadingMoreRing: Boolean = false,
    onOpenEchoHistory: () -> Unit = {},
    onOpenEchoInvitation: (String) -> Unit = {},
    onLoadMoreRing: () -> Unit = {},
) {
    FeedHeaderBar(
        storyUsers = storyUsers,
        isLoadingStories = isLoadingStories,
        isLoadingMoreRing = isLoadingMoreRing,
        pendingEchoes = pendingEchoes,
        ownStoryProfileImageUrl = ownStoryProfileImageUrl,
        ownStoryCount = ownStoryCount,
        currentUserId = currentUserId,
        onCreateStory = onCreateStory,
        onOpenStory = onOpenStory,
        onOpenActivity = onOpenActivity,
        onOpenMessages = onOpenMessages,
        onOpenEchoHistory = onOpenEchoHistory,
        onOpenEchoInvitation = onOpenEchoInvitation,
        onLoadMoreRing = onLoadMoreRing,
        modifier = modifier,
    )
}

@Composable
fun FeedFloatingSelector(
    selectedFeedType: FeedType,
    onSelectFeedType: (FeedType) -> Unit,
    isManualRefreshing: Boolean,
    floatingSelectorTopInset: Dp,
    isFeedHeaderHidden: Boolean,
    pendingEchoesCount: Int,
    modifier: Modifier = Modifier,
) {
    // iOS: VStack { Spacer().frame(height: inset); FloatingGlassFeedToggle; … }
    // El inset ya viene animado desde FeedView (altura real del header).
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(floatingSelectorTopInset))
        FloatingGlassFeedToggle(
            selectedFeedType = selectedFeedType,
            onSelect = onSelectFeedType,
            modifier = Modifier.shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(percent = 50),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.15f),
                spotColor = Color.Black.copy(alpha = 0.15f),
            ),
        )
        AnimatedVisibility(
            visible = isManualRefreshing,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
        ) {
            FeedRefreshIndicator(mod = Modifier.padding(top = 8.dp))
        }
    }
    @Suppress("UNUSED_EXPRESSION")
    isFeedHeaderHidden
    @Suppress("UNUSED_EXPRESSION")
    pendingEchoesCount
}

@Composable
private fun EchoApertureIcon(pendingCount: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .momentsPressIcon()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // iOS: EchoesIconView size 32 in 36 frame, orange→purple horizontal
        EchoesIconViewBrandHorizontal(size = EchoesIconMetrics.feedToolbar)
        if (pendingCount > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF9500)), // Color.orange
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$pendingCount",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StoryRingTrayLoadingTail() {
    val isDark = isSystemInDarkTheme()
    Box(
        Modifier
            .padding(horizontal = 8.dp)
            .size(58.dp)
            .clip(CircleShape)
            .background(if (isDark) Color.White.copy(0.06f) else FeedInk.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = if (isDark) Color.White.copy(0.4f) else FeedInk.copy(alpha = 0.4f),
        )
    }
}
