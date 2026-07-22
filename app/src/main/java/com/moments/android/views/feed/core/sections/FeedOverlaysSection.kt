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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.moments.android.R
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
    onDismissEchoInvitation: () -> Unit = {},
    onAcceptEchoInvitation: (String) -> Unit = {},
    onDismissNotificationSummary: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val unreadNotifications by NotificationBadgeService.unreadNotificationsCount.collectAsState()
    val unreadMessages by NotificationBadgeService.unreadMessagesCount.collectAsState()
    val isDark = isSystemInDarkTheme()
    val mediaCorner = FeedMomentCardLayout.mediaCornerRadius

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isPeeking && peekImageUrl != null,
            enter = fadeIn(spring(dampingRatio = 0.85f)),
            exit = fadeOut(),
            // iOS: .allowsHitTesting(false) — el overlay de peek no captura gestos
            modifier = Modifier.zIndex(998f),
        ) {
            ScreenshotProtectedView(isProtected = peekIsProtected, fillsContainer = true) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        // Android: surface opaca (sin ultraThinMaterial / transparencia)
                        .background(rememberAdaptiveColors().surfaceBackground),
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
                                RoundedCornerShape(mediaCorner),
                                ambientColor = Color.Black.copy(alpha = 0.4f),
                                spotColor = Color.Black.copy(alpha = 0.4f),
                            )
                            .clip(RoundedCornerShape(mediaCorner)),
                    )
                }
            }
        }

        // Paridad iOS: ModernContextMenuOverlay(moment:isPresented:onEdit:onDelete:onReport:)
        if (showContextMenu && selectedMoment != null) {
            Box(Modifier.fillMaxSize().zIndex(1000f)) {
                ModernContextMenuOverlay(
                    moment = selectedMoment,
                    isPresented = true,
                    onPresentedChange = { presented ->
                        if (!presented) onDismissContextMenu()
                    },
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onReport = {},
                )
            }
        }

        if (showShareSheet && selectedMoment != null) {
            Box(Modifier.fillMaxSize().zIndex(1001f), contentAlignment = Alignment.BottomCenter) {
                ModernShareBottomSheet(
                    moment = selectedMoment,
                    onDismiss = onDismissShare,
                    onSendMessage = onDismissShare,
                    onAddToStory = onDismissShare,
                )
            }
        }

        Box(Modifier.fillMaxSize().zIndex(2000f), contentAlignment = Alignment.TopCenter) {
            NotificationSummaryPopup(
                isPresented = showNotificationSummary,
                unreadNotifications = unreadNotifications,
                unreadMessages = unreadMessages,
                isDark = isDark,
                onDismiss = onDismissNotificationSummary,
                onNavigate = onDismissNotificationSummary,
            )
        }

        // Paridad iOS: EchoInvitationView(echoId:onDismiss:onAccept:)
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

@Composable
private fun OverlayMenuRow(
    label: String,
    onClick: () -> Unit,
    isDark: Boolean,
    destructive: Boolean = false,
) {
    Text(
        label,
        color = when {
            destructive -> Color.Red
            isDark -> Color.White
            else -> Color.Black
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(14.dp),
    )
}
