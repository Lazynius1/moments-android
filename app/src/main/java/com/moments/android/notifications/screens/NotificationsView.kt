package com.moments.android.notifications.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.NotificationType
import com.moments.android.notifications.components.NotificationDateHeader
import com.moments.android.notifications.components.NotificationDeletionUndoToast
import com.moments.android.notifications.components.NotificationGroupedFollowersOverlay
import com.moments.android.notifications.components.NotificationSkeletonRow
import com.moments.android.notifications.core.NotificationGroup
import com.moments.android.notifications.core.NotificationsViewModel
import com.moments.android.notifications.row.EnhancedNotificationRow
import com.moments.android.notifications.services.NotificationBadgeService
import com.moments.android.notifications.services.NotificationNavigationService
import com.moments.android.notifications.services.NotificationOpenIntentStore
import com.moments.android.notifications.services.NotificationService
import com.moments.android.views.feed.FeedCanvas
import com.moments.android.views.feed.FeedInk

/** Port de NotificationsView.swift */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onNotificationsCleared: (() -> Unit)? = null,
    viewModel: NotificationsViewModel = remember { NotificationsViewModel() },
) {
    val isDark = isSystemInDarkTheme()
    val canvas = if (isDark) FeedInk else FeedCanvas
    val ink = if (isDark) Color.White else FeedInk

    val selectedTab by viewModel.selectedTab.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val groupedNotifications by viewModel.groupedNotifications.collectAsState()
    val dateKeys by viewModel.dateKeys.collectAsState()
    val groupedByDate by viewModel.groupedByDate.collectAsState()
    val pendingDeletion by viewModel.pendingDeletion.collectAsState()
    val pendingRequestsCount by viewModel.pendingRequestsCount.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    var overlayGroup by remember { mutableStateOf<NotificationGroup?>(null) }

    LaunchedEffect(Unit) {
        NotificationOpenIntentStore.consumeFilter()?.let { filter ->
            NotificationOpenIntentStore.tab(filter)?.let(viewModel::setSelectedTab)
        }
        viewModel.refreshNotifications()
        viewModel.markAllAsRead()
        NotificationBadgeService.clearNotificationBadge()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.commitPendingDeletion()
            NotificationBadgeService.clearNotificationBadge()
            onNotificationsCleared?.invoke()
        }
    }

    Scaffold(
        containerColor = canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.notifications_title), fontWeight = FontWeight.SemiBold, color = ink)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = canvas),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).background(canvas),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                NotificationTabBar(
                    selectedTab = selectedTab,
                    pendingRequestsCount = pendingRequestsCount,
                    ink = ink,
                    onTabSelected = viewModel::setSelectedTab,
                )
                when {
                    isLoading -> Column(Modifier.padding(16.dp)) { repeat(5) { NotificationSkeletonRow(isDark) } }
                    groupedNotifications.isEmpty() -> EmptyNotifications(selectedTab, isDark)
                    else -> NotificationsList(
                        dateKeys = dateKeys,
                        groupedByDate = groupedByDate,
                        viewModel = viewModel,
                        isDark = isDark,
                        canLoadMore = canLoadMore,
                        isLoadingMore = isLoadingMore,
                        onShowGroupedFollowers = { overlayGroup = it },
                        onOpenProfile = onOpenProfile,
                    )
                }
            }

            pendingDeletion?.let {
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                    NotificationDeletionUndoToast(it.notifications.size, isDark) {
                        viewModel.undoPendingDeletion()
                    }
                }
            }

            overlayGroup?.let { group ->
                NotificationGroupedFollowersOverlay(
                    group = group,
                    viewModel = viewModel,
                    isDark = isDark,
                    onDismiss = { overlayGroup = null },
                    onOpenProfile = { overlayGroup = null; onOpenProfile(it) },
                )
            }
        }
    }
}

@Composable
private fun NotificationTabBar(
    selectedTab: NotificationsViewModel.NotificationsTab,
    pendingRequestsCount: Int,
    ink: Color,
    onTabSelected: (NotificationsViewModel.NotificationsTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        NotificationsViewModel.NotificationsTab.entries.forEach { tab ->
            Column(
                modifier = Modifier.clickable { onTabSelected(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(tab.labelRes),
                        fontSize = 14.sp,
                        fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selectedTab == tab) ink else Color.Gray.copy(alpha = 0.82f),
                    )
                    if (tab == NotificationsViewModel.NotificationsTab.REQUESTS && pendingRequestsCount > 0) {
                        Text(
                            pendingRequestsCount.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.background(Color.Red, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Box(
                    modifier = Modifier.height(2.dp).fillMaxWidth(0.6f)
                        .background(if (selectedTab == tab) ink else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun NotificationsList(
    dateKeys: List<String>,
    groupedByDate: Map<String, List<NotificationGroup>>,
    viewModel: NotificationsViewModel,
    isDark: Boolean,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onShowGroupedFollowers: (NotificationGroup) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        dateKeys.forEach { section ->
            item(key = "header-$section") { NotificationDateHeader(section, isDark) }
            groupedByDate[section]?.forEach { group ->
                item(key = group.id) {
                    EnhancedNotificationRow(
                        group = group,
                        viewModel = viewModel,
                        isDark = isDark,
                        onTapAction = { handleGroupTap(group) },
                        onShowGroupedFollowers = onShowGroupedFollowers,
                        onOpenProfile = onOpenProfile,
                    )
                }
            }
        }
        if (canLoadMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (isLoadingMore) CircularProgressIndicator(Modifier.height(24.dp))
                    else Text(stringResource(R.string.notifications_load_more), modifier = Modifier.clickable { viewModel.loadMoreNotifications() })
                }
            }
        }
    }
    LaunchedEffect(listState.canScrollForward, canLoadMore, isLoadingMore) {
        if (!listState.canScrollForward && canLoadMore && !isLoadingMore) viewModel.loadMoreNotifications()
    }
}

@Composable
private fun EmptyNotifications(tab: NotificationsViewModel.NotificationsTab, isDark: Boolean) {
    val message = when (tab) {
        NotificationsViewModel.NotificationsTab.REACTIONS -> R.string.notifications_empty_reactions
        NotificationsViewModel.NotificationsTab.FOLLOWS -> R.string.notifications_empty_follows
        NotificationsViewModel.NotificationsTab.COMMENTS -> R.string.notifications_empty_comments
        NotificationsViewModel.NotificationsTab.STORY_REACTIONS -> R.string.notifications_empty_story_reactions
        NotificationsViewModel.NotificationsTab.REQUESTS -> R.string.notifications_empty_requests
        else -> R.string.notifications_empty_default
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(message), color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f))
    }
}

private fun handleGroupTap(group: NotificationGroup) {
    val first = group.notifications.firstOrNull() ?: return
    when (first.type) {
        NotificationType.MESSAGE, NotificationType.MESSAGE_REACTION, NotificationType.CHAT_BUZZ ->
            first.conversationId?.let(NotificationNavigationService::navigateToConversation)
        NotificationType.NEW_FOLLOWER, NotificationType.REQUEST_ACCEPTED,
        NotificationType.MUTUAL_CONNECTION, NotificationType.FOLLOW_REQUEST,
        -> NotificationNavigationService.navigateToProfile(first.senderId)
        NotificationType.REACTION, NotificationType.COMMENT, NotificationType.LIKE,
        NotificationType.PHOTO_TAG, NotificationType.MENTION,
        -> first.momentId?.let {
            NotificationNavigationService.navigateToMoment(it, first.targetAuthorId ?: first.senderId)
        } ?: NotificationNavigationService.navigateToNotifications(null)
        NotificationType.STORY_REACTION, NotificationType.STORY_CHAIN_CONTINUED ->
            first.storyId?.let { NotificationNavigationService.navigateToStory(it, first.storyAuthorId) }
        else -> NotificationNavigationService.navigateToNotifications(null)
    }
    NotificationService.markAsRead(first)
}

private val NotificationsViewModel.NotificationsTab.labelRes: Int
    get() = when (this) {
        NotificationsViewModel.NotificationsTab.ALL -> R.string.notifications_tab_all
        NotificationsViewModel.NotificationsTab.REACTIONS -> R.string.notifications_tab_reactions
        NotificationsViewModel.NotificationsTab.FOLLOWS -> R.string.notifications_tab_follows
        NotificationsViewModel.NotificationsTab.COMMENTS -> R.string.notifications_tab_comments
        NotificationsViewModel.NotificationsTab.STORY_REACTIONS -> R.string.notifications_tab_stories
        NotificationsViewModel.NotificationsTab.REQUESTS -> R.string.notifications_tab_requests
    }

object NotificationsScreen
