package com.moments.android.views.feed.core.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.moments.android.notifications.screens.NotificationSummaryPopup
import com.moments.android.notifications.services.NotificationBadgeService
import com.moments.android.services.content.FeedMoment
import com.moments.android.views.echoes.EchoInvitationView
import com.moments.android.views.feed.core.FeedEchoInvitationRoute
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.feed.sharing.ModernShareBottomSheet
import com.moments.android.views.profile.momentsview.ModernContextMenuOverlay
import com.moments.android.views.shared.ScreenshotProtectedView

/** Port 1:1 de `FeedOverlaysSection.swift`. */
@Composable
fun FeedOverlaysSection(
    isPeeking: Boolean,
    peekImageUrl: String?,
    peekAspectRatio: Float = 1f,
    peekIsProtected: Boolean = false,
    showShareSheet: Boolean,
    showContextMenu: Boolean,
    selectedMoment: FeedMoment?,
    pendingEchoInvitationRoute: FeedEchoInvitationRoute? = null,
    showNotificationSummary: Boolean = false,
    onDismissPeek: () -> Unit,
    onDismissShare: () -> Unit,
    onDismissContextMenu: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onNotInterested: ((FeedMoment) -> Unit)? = null,
    onDismissEchoInvitation: () -> Unit = {},
    onAcceptEchoInvitation: (String) -> Unit = {},
    onDismissNotificationSummary: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val unreadNotifications by NotificationBadgeService.unreadNotificationsCount.collectAsState()
    val unreadMessages by NotificationBadgeService.unreadMessagesCount.collectAsState()
    val isDark = isSystemInDarkTheme()
    val peekShape = FeedMomentCardLayout.continuousRoundedRectShape
    // iOS ultraThinMaterial → surface (Android sin material blur nativo equivalente)
    val peekBackdrop = rememberAdaptiveColors().surfaceBackground

    Box(modifier.fillMaxSize()) {
        // iOS: allowsHitTesting(false). Compose 1.11: sin clickable/pointerInput → no elegible
        // para hit-test; el gesto de long-press sigue en el card debajo.
        AnimatedVisibility(
            visible = isPeeking && peekImageUrl != null,
            enter = fadeIn(spring(dampingRatio = 0.85f)),
            exit = fadeOut(),
            modifier = Modifier.zIndex(998f),
        ) {
            ScreenshotProtectedView(isProtected = peekIsProtected, fillsContainer = true) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .background(peekBackdrop),
                    contentAlignment = Alignment.Center,
                ) {
                    val w = maxWidth - 32.dp
                    val h = w / peekAspectRatio.coerceAtLeast(0.2f)
                    AsyncImage(
                        model = peekImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(w)
                            .height(h)
                            .shadow(
                                20.dp,
                                peekShape,
                                ambientColor = Color.Black.copy(alpha = 0.4f),
                                spotColor = Color.Black.copy(alpha = 0.4f),
                            )
                            .clip(peekShape),
                    )
                }
            }
        }

        // iOS: transition move(bottom)+opacity, spring 0.4 / 0.8, zIndex 1000
        AnimatedVisibility(
            visible = showContextMenu && selectedMoment != null,
            enter = slideInVertically(spring(dampingRatio = 0.8f)) { it } + fadeIn(),
            exit = slideOutVertically(spring(dampingRatio = 0.8f)) { it } + fadeOut(),
            modifier = Modifier.zIndex(1000f),
        ) {
            val moment = selectedMoment
            if (moment != null) {
                ModernContextMenuOverlay(
                    moment = moment,
                    isPresented = true,
                    onPresentedChange = { presented ->
                        if (!presented) onDismissContextMenu()
                    },
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onReport = {},
                    onNotInterested = onNotInterested?.let { action -> { action(moment) } },
                )
            }
        }

        // iOS: transition move(bottom)+opacity, zIndex 1001
        AnimatedVisibility(
            visible = showShareSheet && selectedMoment != null,
            enter = slideInVertically(spring(dampingRatio = 0.8f)) { it } + fadeIn(),
            exit = slideOutVertically(spring(dampingRatio = 0.8f)) { it } + fadeOut(),
            modifier = Modifier.zIndex(1001f),
        ) {
            val moment = selectedMoment
            if (moment != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    ModernShareBottomSheet(
                        moment = moment,
                        onDismiss = onDismissShare,
                        onSendMessage = onDismissShare,
                        onAddToStory = onDismissShare,
                    )
                }
            }
        }

        // iOS: VStack { NotificationSummaryPopup; Spacer } zIndex 2000
        Box(Modifier.fillMaxSize().zIndex(2000f), contentAlignment = Alignment.TopCenter) {
            NotificationSummaryPopup(
                isPresented = showNotificationSummary,
                unreadNotifications = unreadNotifications,
                unreadMessages = unreadMessages,
                isDark = isDark,
                onDismiss = onDismissNotificationSummary,
            )
        }

        // iOS: opacity + scale(0.98), zIndex 2100
        AnimatedVisibility(
            visible = pendingEchoInvitationRoute != null,
            enter = fadeIn() + scaleIn(initialScale = 0.98f),
            exit = fadeOut() + scaleOut(targetScale = 0.98f),
            modifier = Modifier.zIndex(2100f),
        ) {
            val route = pendingEchoInvitationRoute
            if (route != null) {
                EchoInvitationView(
                    echoId = route.echoId,
                    onDismiss = onDismissEchoInvitation,
                    onAccept = onAcceptEchoInvitation,
                )
            }
        }
    }
}
