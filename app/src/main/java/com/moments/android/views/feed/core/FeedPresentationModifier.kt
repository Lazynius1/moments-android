package com.moments.android.views.feed.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import com.moments.android.R
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.models.MediaItem
import com.moments.android.notifications.screens.NotificationsScreen
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.comments.ModernCommentsSheet
import com.moments.android.views.echoes.EchoHistoryView
import com.moments.android.views.feed.core.sections.FeedMomentDetailRoute
import com.moments.android.views.feed.maps.LocationMapView
import com.moments.android.views.explore.ExploreView
import com.moments.android.views.messaging.screens.MessagingView
import com.moments.android.views.profile.momentsview.EditMomentView
import com.moments.android.views.profile.userprofile.UserProfileView
import com.moments.android.views.story.StoriesView

/**
 * Port 1:1 de `FeedPresentationModifier.swift`.
 *
 * Presenta las destinations del feed encima del [content] (paridad sheets /
 * navigationDestination / fullScreenCover / alert de iOS).
 * Explore + EditMoment + Comments + EchoHistory + Stories + Messaging + Profile sheet reales.
 * Placeholders residuales: ninguno del contrato FeedPresentations.
 */
@Composable
fun FeedPresentations(
    showNotifications: Boolean,
    onShowNotificationsChange: (Boolean) -> Unit,
    showMessages: Boolean,
    onShowMessagesChange: (Boolean) -> Unit,
    selectedStoryRoute: StoryUserPresentationRoute?,
    onSelectedStoryRouteChange: (StoryUserPresentationRoute?) -> Unit,
    storyRingNavigationUserIds: List<String>,
    showStories: Boolean,
    onShowStoriesChange: (Boolean) -> Unit,
    selectedMoment: FeedMoment?,
    onSelectedMomentChange: (FeedMoment?) -> Unit,
    showExploreWithHashtag: Boolean,
    onShowExploreWithHashtagChange: (Boolean) -> Unit,
    selectedHashtag: String,
    showExplore: Boolean,
    onShowExploreChange: (Boolean) -> Unit,
    showingLocationMap: Boolean,
    onShowingLocationMapChange: (Boolean) -> Unit,
    selectedLocationName: String,
    selectedLocationLatitude: Double?,
    selectedLocationLongitude: Double?,
    showMomentDetail: Boolean,
    onShowMomentDetailChange: (Boolean) -> Unit,
    targetMomentId: String?,
    onTargetMomentIdChange: (String?) -> Unit,
    targetMomentUserId: String?,
    onTargetMomentUserIdChange: (String?) -> Unit,
    showEditSheet: Boolean,
    onShowEditSheetChange: (Boolean) -> Unit,
    showDeleteAlert: Boolean,
    onShowDeleteAlertChange: (Boolean) -> Unit,
    selectedMomentForMenu: FeedMoment?,
    selectedProfileRoute: FeedProfileSheetRoute?,
    onSelectedProfileRouteChange: (FeedProfileSheetRoute?) -> Unit,
    onSelectedUserIdChange: (String) -> Unit,
    showEchoHistory: Boolean,
    onShowEchoHistoryChange: (Boolean) -> Unit,
    targetConversationId: String?,
    onTargetConversationIdChange: (String?) -> Unit,
    @Suppress("UNUSED_PARAMETER") firestoreService: FirestoreService,
    updateMoment: (FeedMoment, EditMomentPayload) -> Unit,
    deleteMoment: (FeedMoment) -> Unit,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()

    // iOS: onChange(of: selectedProfileRoute) → clear selectedUserId
    LaunchedEffect(selectedProfileRoute) {
        if (selectedProfileRoute == null) onSelectedUserIdChange("")
    }

    Box(Modifier.fillMaxSize()) {
        content()

        // navigationDestination → NotificationsView
        if (showNotifications) {
            Dialog(
                onDismissRequest = { onShowNotificationsChange(false) },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    NotificationsScreen(
                        onBack = { onShowNotificationsChange(false) },
                        onNotificationsCleared = {
                            NavigationEventBus.emit(CoordinatorNavigationEvent.NotificationsCleared)
                        },
                    )
                }
            }
        }

        // navigationDestination → MessagingView
        if (showMessages) {
            Dialog(
                onDismissRequest = { onShowMessagesChange(false) },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    MessagingView(
                        targetConversationId = targetConversationId,
                        onTargetConversationIdConsumed = { onTargetConversationIdChange(null) },
                        onDismiss = { onShowMessagesChange(false) },
                    )
                }
            }
        }

        // fullScreenCover(item:) → StoriesView (startAtUserId)
        selectedStoryRoute?.let { route ->
            Dialog(
                onDismissRequest = { onSelectedStoryRouteChange(null) },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                StoriesView(
                    startAtUserId = route.userId,
                    ringNavigationUserIds = storyRingNavigationUserIds,
                    onDismiss = { onSelectedStoryRouteChange(null) },
                )
            }
        }

        // fullScreenCover → StoriesView
        if (showStories && selectedStoryRoute == null) {
            Dialog(
                onDismissRequest = { onShowStoriesChange(false) },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                StoriesView(
                    ringNavigationUserIds = storyRingNavigationUserIds,
                    onDismiss = { onShowStoriesChange(false) },
                )
            }
        }

        // sheet → ModernCommentsView (paridad iOS presentationDetents medium/large)
        selectedMoment?.let { moment ->
            ModernCommentsSheet(
                moment = moment,
                onDismiss = { onSelectedMomentChange(null) },
                onOpenStory = { userId ->
                    onSelectedMomentChange(null)
                    onSelectedStoryRouteChange(StoryUserPresentationRoute(userId))
                },
                onOpenProfile = { userId ->
                    onSelectedMomentChange(null)
                    onSelectedProfileRouteChange(FeedProfileSheetRoute(userId))
                },
            )
        }

        // sheet → ExploreView(initialSearchQuery:)
        if (showExploreWithHashtag) {
            Dialog(
                onDismissRequest = { onShowExploreWithHashtagChange(false) },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    ExploreView(
                        initialSearchQuery = selectedHashtag,
                        isDismissable = true,
                        onDismiss = { onShowExploreWithHashtagChange(false) },
                    )
                }
            }
        }

        // sheet → ExploreView()
        if (showExplore) {
            Dialog(
                onDismissRequest = { onShowExploreChange(false) },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    ExploreView(
                        isDismissable = true,
                        onDismiss = { onShowExploreChange(false) },
                    )
                }
            }
        }

        // navigationDestination → LocationMapView
        if (showingLocationMap) {
            Dialog(
                onDismissRequest = { onShowingLocationMapChange(false) },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    LocationMapView(
                        locationName = selectedLocationName.ifEmpty {
                            stringResource(R.string.feed_location_default)
                        },
                        latitude = selectedLocationLatitude,
                        longitude = selectedLocationLongitude,
                        onDismiss = { onShowingLocationMapChange(false) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // sheet → MomentDetailFromNotificationView
        if (showMomentDetail) {
            val momentId = targetMomentId
            val userId = targetMomentUserId
            if (momentId != null && userId != null) {
                Dialog(
                    onDismissRequest = {
                        onShowMomentDetailChange(false)
                        onTargetMomentIdChange(null)
                        onTargetMomentUserIdChange(null)
                    },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    Surface(Modifier.fillMaxSize()) {
                        FeedMomentDetailRoute(
                            momentId = momentId,
                            authorId = userId,
                            onDismiss = {
                                onShowMomentDetailChange(false)
                                onTargetMomentIdChange(null)
                                onTargetMomentUserIdChange(null)
                            },
                        )
                    }
                }
            }
        }

        // sheet → EditMomentView
        if (showEditSheet) {
            selectedMomentForMenu?.let { moment ->
                Dialog(
                    onDismissRequest = { onShowEditSheetChange(false) },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
                        EditMomentView(
                            moment = moment,
                            onSave = { payload -> updateMoment(moment, payload) },
                            onDismiss = { onShowEditSheetChange(false) },
                        )
                    }
                }
            }
        }

        // alert → delete confirm
        if (showDeleteAlert) {
            AlertDialog(
                onDismissRequest = { onShowDeleteAlertChange(false) },
                title = { Text(stringResource(R.string.feed_actions_delete_title)) },
                text = { Text(stringResource(R.string.feed_delete_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedMomentForMenu?.let { deleteMoment(it) }
                            onShowDeleteAlertChange(false)
                        },
                    ) {
                        Text(stringResource(R.string.feed_actions_delete), color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onShowDeleteAlertChange(false) }) {
                        Text(stringResource(R.string.feed_actions_cancel))
                    }
                },
            )
        }

        // userProfileNavigationDestination
        selectedProfileRoute?.let { route ->
            Dialog(
                onDismissRequest = { onSelectedProfileRouteChange(null) },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    UserProfileView(
                        userId = route.userId,
                        onDismiss = { onSelectedProfileRouteChange(null) },
                    )
                }
            }
        }

        // sheet → EchoHistoryView (paridad iOS)
        if (showEchoHistory) {
            Dialog(
                onDismissRequest = { onShowEchoHistoryChange(false) },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    EchoHistoryView(onDismiss = { onShowEchoHistoryChange(false) })
                }
            }
        }
    }
}

/** Payload espejo de `EditMomentPayload` iOS (EditMomentView.swift). */
data class EditMomentPayload(
    val content: String,
    val audience: String = "everyone",
    val customListId: String? = null,
    val customViewers: List<String> = emptyList(),
    val taggedUsers: List<String> = emptyList(),
    val mentionedUsers: List<String> = emptyList(),
    val locationName: String = "",
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val mediaItems: List<MediaItem>? = null,
)

@Composable
private fun FeedPresentationPlaceholder(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    onPrimary: (() -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(32.dp)
                    .background(
                        if (isDark) Color(0xFF1C1C1E) else Color.White,
                        RoundedCornerShape(14.dp),
                    )
                    .padding(20.dp)
                    .clickable(enabled = false) {},
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, color = FeedInk)
                subtitle?.let { Text(it, color = FeedInk.copy(alpha = 0.7f)) }
                onPrimary?.let {
                    Text(
                        "Save",
                        color = FeedInk,
                        modifier = Modifier.clickable(onClick = it),
                    )
                }
                Text(
                    stringResource(R.string.feed_actions_cancel),
                    color = FeedInk,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }
        }
    }
}
