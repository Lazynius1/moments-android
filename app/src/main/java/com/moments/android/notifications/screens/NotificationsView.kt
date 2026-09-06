package com.moments.android.notifications.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.AlertDialog
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.AppRouter
import com.moments.android.coordinators.LegacyNavigationBridge
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import com.moments.android.models.MomentsNotification
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
import com.moments.android.reportes.ModerationReviewRequestSheet
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.components.MomentRefreshOverlayHost
import com.moments.android.views.components.momentRefresh
import com.moments.android.views.feed.FeedCanvas
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.shared.MomentsFillScrollTabRow
import com.moments.android.views.messaging.services.ChatNavigationIntentStore
import com.moments.android.views.profile.core.sections.MomentZoomDestination
import com.moments.android.views.profile.core.sections.MomentZoomDetailDestination
import com.moments.android.views.profile.core.sections.MomentZoomOpener
import com.moments.android.views.profile.core.sections.MomentZoomPresentationKind
import com.moments.android.views.profile.core.sections.ProfileMomentZoomNavigation
import com.moments.android.views.shared.MomentsContainerTransformOverlay
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.MomentsSharedTransitionLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Port de NotificationsView.swift */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onNotificationsCleared: (() -> Unit)? = null,
    viewModel: NotificationsViewModel = remember { NotificationsViewModel() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    val showError by viewModel.showError.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var overlayGroup by remember { mutableStateOf<NotificationGroup?>(null) }
    var moderationReviewNotification by remember { mutableStateOf<MomentsNotification?>(null) }
    var zoomDestination by remember { mutableStateOf<MomentZoomDestination?>(null) }
    var zoomResolvedMoment by remember { mutableStateOf<Moment?>(null) }

    // System back: primero cierra zoom de moment; luego la pantalla (Dialog).
    androidx.activity.compose.BackHandler(enabled = zoomDestination != null) {
        zoomDestination = null
        zoomResolvedMoment = null
    }

    // ≡ applyPendingNotificationsFilterIfNeeded + refresh + clearNotificationsAutomatically
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

    fun fetchMomentAndZoom(momentId: String, authorId: String?) {
        scope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val owner = authorId?.trim().orEmpty().ifEmpty { uid }
            val moment = withContext(Dispatchers.IO) {
                runCatching { FirestoreService().fetchMoment(momentId, owner) }.getOrNull()
            } ?: return@launch
            zoomResolvedMoment = moment
            zoomDestination = MomentZoomDestination(
                zoomSourceID = ProfileMomentZoomNavigation.sourceID(moment, 0, "notification"),
                initialIndex = 0,
                initialMomentId = moment.id,
                presentation = MomentZoomPresentationKind.Single,
            )
            HapticManager.shared.lightImpact()
        }
    }

    fun handleNotificationTap(group: NotificationGroup) {
        val first = group.notifications.firstOrNull() ?: return
        when (first.type) {
            NotificationType.LIKE, NotificationType.REACTION, NotificationType.COMMENT, NotificationType.PHOTO_TAG -> {
                first.momentId?.let { fetchMomentAndZoom(it, first.targetAuthorId) }
            }
            NotificationType.MENTION -> {
                when {
                    first.storyId != null ->
                        NotificationNavigationService.navigateToStory(
                            first.storyId,
                            first.storyAuthorId ?: first.targetAuthorId ?: first.senderId,
                        )
                    first.momentId != null -> fetchMomentAndZoom(first.momentId, first.targetAuthorId)
                }
            }
            // Profile open lo hace la row (opensSenderProfileOnTap)
            NotificationType.NEW_FOLLOWER, NotificationType.FOLLOW_REQUEST,
            NotificationType.MUTUAL_CONNECTION, NotificationType.REQUEST_ACCEPTED,
            -> Unit
            NotificationType.STORY_REACTION -> {
                first.storyId?.let { storyId ->
                    NotificationNavigationService.navigateToStory(
                        storyId,
                        FirebaseAuth.getInstance().currentUser?.uid,
                    )
                }
            }
            NotificationType.MESSAGE, NotificationType.MESSAGE_REACTION, NotificationType.CHAT_BUZZ -> {
                val conversationId = first.conversationId ?: first.momentId
                if (!conversationId.isNullOrBlank()) {
                    if (first.type == NotificationType.CHAT_BUZZ) {
                        ChatNavigationIntentStore.enqueueBuzz(conversationId, first.buzzEventId)
                    }
                    NotificationNavigationService.navigateToConversation(conversationId)
                }
            }
            NotificationType.GENTLE_REMINDER -> AppRouter.navigate(AppRouter.Destination.Creator)
            NotificationType.ECHO_SUGGESTION ->
                first.echoId?.let { AppRouter.navigate(AppRouter.Destination.Echo(it)) }
            NotificationType.STORY_CHAIN_CONTINUED ->
                first.chainId?.let {
                    LegacyNavigationBridge.storyChain(it, first.chainTitle.orEmpty())
                }
            NotificationType.DATA_EXPORT_READY -> {
                val url = first.downloadURL?.trim().orEmpty()
                if (url.isNotEmpty()) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            }
            NotificationType.MEDIA_MODERATION ->
                first.momentId?.let { fetchMomentAndZoom(it, first.targetAuthorId) }
            else -> Unit
        }
        NotificationService.markAsRead(first)
    }

    MomentsSharedTransitionLayout(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.notifications_title),
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = canvas.copy(alpha = if (isDark) 0.72f else 0.9f),
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(canvas)
                .momentRefresh {
                    // ≡ iOS await refreshNotifications(); delay para que la gota sea visible
                    viewModel.refreshNotifications()
                    kotlinx.coroutines.delay(700)
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                NotificationTabBar(
                    selectedTab = selectedTab,
                    pendingRequestsCount = pendingRequestsCount,
                    ink = ink,
                    isDark = isDark,
                    onTabSelected = viewModel::setSelectedTab,
                )
                when {
                    isLoading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            repeat(5) { NotificationSkeletonRow(isDark) }
                        }
                    }
                    groupedNotifications.isEmpty() -> EmptyNotifications(selectedTab, isDark, ink)
                    else -> NotificationsList(
                        dateKeys = dateKeys,
                        groupedByDate = groupedByDate,
                        viewModel = viewModel,
                        isDark = isDark,
                        canvas = canvas,
                        canLoadMore = canLoadMore,
                        isLoadingMore = isLoadingMore,
                        onShowGroupedFollowers = { overlayGroup = it },
                        onOpenProfile = onOpenProfile,
                        onModerationReviewTap = { moderationReviewNotification = it },
                        onTapAction = ::handleNotificationTap,
                    )
                }
            }

            MomentRefreshOverlayHost(modifier = Modifier.align(Alignment.TopCenter))

            pendingDeletion?.let {
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                    NotificationDeletionUndoToast(it.notifications.size, isDark) {
            HapticManager.shared.lightImpact()
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

    if (showError) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text(stringResource(R.string.notifications_error_title)) },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text(stringResource(R.string.notifications_ok))
                }
            },
        )
    }

    moderationReviewNotification?.let { notification ->
        // ≡ iOS `.sheet` + `.presentationDetents([.large])`
        MomentsModalSheet(
            onDismissRequest = { moderationReviewNotification = null },
            largeOnly = true,
        ) {
            ModerationReviewRequestSheet(
                notification = notification,
                onDismiss = { moderationReviewNotification = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    MomentsContainerTransformOverlay(visible = zoomDestination != null) {
        val destination = zoomDestination
        if (destination != null) {
            val pool = listOfNotNull(zoomResolvedMoment)
            MomentZoomDetailDestination(
                destination = destination,
                moments = MomentZoomOpener.resolvedMoments(destination, pool),
                onDismiss = {
                    zoomDestination = null
                    zoomResolvedMoment = null
                },
            )
        }
    }
    } // MomentsSharedTransitionLayout
}

@Composable
private fun NotificationTabBar(
    selectedTab: NotificationsViewModel.NotificationsTab,
    pendingRequestsCount: Int,
    ink: Color,
    isDark: Boolean,
    onTabSelected: (NotificationsViewModel.NotificationsTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDark) FeedInk else FeedCanvas),
    ) {
        MomentsFillScrollTabRow(
            items = NotificationsViewModel.NotificationsTab.entries,
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        ) { tab, itemModifier ->
            Column(
                modifier = itemModifier.clickable { onTabSelected(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(tab.labelRes),
                        fontSize = 14.sp,
                        fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selectedTab == tab) ink else Color.Gray.copy(alpha = 0.82f),
                        maxLines = 1,
                    )
                    if (tab == NotificationsViewModel.NotificationsTab.REQUESTS && pendingRequestsCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(Color.Red, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                pendingRequestsCount.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .fillMaxWidth(0.85f)
                        .background(if (selectedTab == tab) ink else Color.Transparent, CircleShape),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsList(
    dateKeys: List<String>,
    groupedByDate: Map<String, List<NotificationGroup>>,
    viewModel: NotificationsViewModel,
    isDark: Boolean,
    canvas: Color,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onShowGroupedFollowers: (NotificationGroup) -> Unit,
    onOpenProfile: (String) -> Unit,
    onModerationReviewTap: (MomentsNotification) -> Unit,
    onTapAction: (NotificationGroup) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
        dateKeys.forEach { section ->
            item(key = "header-$section") { NotificationDateHeader(section, isDark) }
            groupedByDate[section]?.forEach { group ->
                item(key = group.id) {
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                HapticManager.shared.lightImpact()
                                viewModel.deleteNotificationGroup(group)
                                true
                            } else {
                                false
                            }
                        },
                    )
                    // ≡ iOS .swipeActions: el rojo/delete solo se revela al arrastrar.
                    // Sin fondo opaco en el content, backgroundContent se ve siempre.
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val revealing =
                                dismissState.targetValue != SwipeToDismissBoxValue.Settled ||
                                    dismissState.progress > 0.01f
                            if (!revealing) return@SwipeToDismissBox
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (isDark) Color(0xFFFF453A) else Color(0xFFFF3B30))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White)
                                    Text(
                                        stringResource(R.string.notifications_delete),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(canvas),
                        ) {
                            EnhancedNotificationRow(
                                group = group,
                                viewModel = viewModel,
                                isDark = isDark,
                                onTapAction = { onTapAction(group) },
                                onShowGroupedFollowers = onShowGroupedFollowers,
                                onModerationReviewTap = onModerationReviewTap,
                                onOpenProfile = onOpenProfile,
                            )
                        }
                    }
                }
            }
        }
        if (canLoadMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (isLoadingMore) {
                        MomentsCircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            stringResource(R.string.notifications_load_more),
                            modifier = Modifier.clickable { viewModel.loadMoreNotifications() },
                            color = if (isDark) Color.White else Color.Black,
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(listState.canScrollForward, canLoadMore, isLoadingMore) {
        if (!listState.canScrollForward && canLoadMore && !isLoadingMore) {
            viewModel.loadMoreNotifications()
        }
    }
}

@Composable
private fun EmptyNotifications(
    tab: NotificationsViewModel.NotificationsTab,
    isDark: Boolean,
    ink: Color,
) {
    val context = LocalContext.current
    val titleRes = when (tab) {
        NotificationsViewModel.NotificationsTab.REACTIONS -> R.string.notifications_empty_reactions
        NotificationsViewModel.NotificationsTab.FOLLOWS -> R.string.notifications_empty_follows
        NotificationsViewModel.NotificationsTab.COMMENTS -> R.string.notifications_empty_comments
        NotificationsViewModel.NotificationsTab.STORY_REACTIONS -> R.string.notifications_empty_story_reactions
        NotificationsViewModel.NotificationsTab.REQUESTS -> R.string.notifications_empty_requests
        else -> R.string.notifications_empty_default
    }
    val messageRes = when (tab) {
        NotificationsViewModel.NotificationsTab.REACTIONS -> R.string.notifications_empty_reactions_message
        NotificationsViewModel.NotificationsTab.FOLLOWS -> R.string.notifications_empty_follows_message
        NotificationsViewModel.NotificationsTab.COMMENTS -> R.string.notifications_empty_comments_message
        NotificationsViewModel.NotificationsTab.STORY_REACTIONS -> R.string.notifications_empty_story_reactions_message
        NotificationsViewModel.NotificationsTab.REQUESTS -> R.string.notifications_empty_requests_message
        else -> R.string.notifications_empty_default_message
    }
    val icon: ImageVector = when (tab) {
        NotificationsViewModel.NotificationsTab.COMMENTS -> Icons.AutoMirrored.Filled.Comment
        NotificationsViewModel.NotificationsTab.STORY_REACTIONS,
        NotificationsViewModel.NotificationsTab.REACTIONS,
        -> Icons.Filled.FavoriteBorder
        NotificationsViewModel.NotificationsTab.REQUESTS -> Icons.Filled.PersonOff
        NotificationsViewModel.NotificationsTab.FOLLOWS -> Icons.Filled.PersonAdd
        else -> Icons.Filled.NotificationsOff
    }
    val titleSp = with(androidx.compose.ui.platform.LocalDensity.current) {
        legacyPoppinsSize(context, 18).toSp()
    }
    val bodySp = with(androidx.compose.ui.platform.LocalDensity.current) {
        legacyPoppinsSize(context, 14).toSp()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .momentsChromeGlass(CircleShape, interactive = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(31.dp),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(titleRes),
                fontSize = titleSp,
                fontWeight = FontWeight.SemiBold,
                color = ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(messageRes),
                fontSize = bodySp,
                color = if (isDark) Color.White.copy(alpha = 0.58f) else Color.Black.copy(alpha = 0.52f),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
    }
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
