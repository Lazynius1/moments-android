package com.moments.android.views.profile.userprofile.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.ChromeIconDescription
import com.moments.android.extensions.MomentsGlassButtonPreset
import com.moments.android.extensions.ProfileChromeIconButton
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.profile.core.sections.ProfileSectionEmptyIcon
import com.moments.android.views.profile.core.sections.ProfileSectionEmptyState

/**
 * Port de `UserProfileStateViews.swift` — estados no-felices del perfil visitado:
 * sin momentos, bloqueado (por mí / por él), privado, no disponible y sin conexión.
 *
 * Mensajería: callbacks `onOpenMessage` (el host abre chat vía `MessagingViewModel`).
 * `UserModernBlockedView`: swipe derecha > 100dp → dismiss (≡ DragGesture iOS).
 */
@Composable
fun UserModernEmptyMomentsView(modifier: Modifier = Modifier) {
    ProfileSectionEmptyState(
        icon = ProfileSectionEmptyIcon.CAMERA,
        title = R.string.user_profile_no_moments_title,
        subtitle = R.string.user_profile_no_moments_description,
        modifier = modifier,
    )
}

@Composable
fun UserModernBlockedView(
    isBlockedByCurrentUser: Boolean,
    safeAreaTop: Dp,
    safeAreaBottom: Dp,
    onUnblock: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(bottom = safeAreaBottom + 20.dp)
            // ≡ DragGesture iOS: swipe derecha > 100pt → dismiss
            .pointerInput(onDismiss) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (total > 100f) onDismiss()
                        total = 0f
                    },
                    onDragCancel = { total = 0f },
                    onHorizontalDrag = { _, amount -> total += amount },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(safeAreaTop))

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(
                Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.15f))
                    .border(2.dp, Color.Red.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PersonOff, null, tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(40.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(
                        if (isBlockedByCurrentUser) R.string.user_profile_blocked_user
                        else R.string.user_profile_restricted_access,
                    ),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(
                        if (isBlockedByCurrentUser) R.string.user_profile_blocked_by_you
                        else R.string.user_profile_blocked_you,
                    ),
                    color = Color.Gray.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isBlockedByCurrentUser) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(UserProfileAccent)
                            .clickable(onClick = onUnblock)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.user_profile_unblock_user),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    stringResource(R.string.user_profile_back),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
fun UserModernPrivateProfileView(
    userProfile: AppUser?,
    userId: String,
    followButtonState: FollowButtonState,
    safeAreaTop: Dp,
    safeAreaBottom: Dp,
    onFollowAction: () -> Unit,
    onDismiss: () -> Unit,
    onOpenStories: () -> Unit,
    onOpenMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val content = if (colors.isDark) Color.White else Color.Black

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserProfileStateTopBar(
            title = userProfile?.username ?: stringResource(R.string.profile_default_username),
            onDismiss = onDismiss,
            modifier = Modifier.padding(top = 4.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserModernAvatar(
                userId = userId,
                size = 96.dp,
                onOpenStories = onOpenStories,
                showStoryRing = false,
            )

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        userProfile?.username ?: stringResource(R.string.profile_default_username),
                        color = content,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    VerifiedBadgeView(userId = userId, size = 18.dp)
                }
                userProfile?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    Text(
                        bio,
                        color = if (colors.isDark) Color.White.copy(0.62f) else Color.Black.copy(0.54f),
                        fontSize = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val actionable = followButtonState.isActionable
            Row(
                Modifier
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = actionable)
                    .clickable(enabled = actionable, onClick = onFollowAction)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(followButtonIcon(followButtonState), null, tint = content, modifier = Modifier.size(13.dp))
                Text(followButtonText(followButtonState), color = content, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (followButtonState == FollowButtonState.FOLLOWING) {
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = content, modifier = Modifier.size(10.dp))
                }
            }

            Row(
                Modifier
                    .weight(1f)
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .clickable(onClick = onOpenMessage)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, tint = content, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.messaging_send_message), color = content, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }

        UserProfilePlaceholderStats(Modifier.padding(horizontal = 20.dp))

        Column(
            Modifier.fillMaxWidth().padding(vertical = 40.dp).padding(bottom = safeAreaBottom + 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Filled.Lock,
                null,
                tint = if (colors.isDark) Color.White.copy(0.48f) else Color.Black.copy(0.42f),
                modifier = Modifier.size(32.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.user_profile_private_title), color = content, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.user_profile_private_description),
                    color = if (colors.isDark) Color.White.copy(0.56f) else Color.Black.copy(0.50f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
        }
    }
}

/** Port de `ProfileUnavailableAvatar`. */
@Composable
fun ProfileUnavailableAvatar(size: Dp, modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (colors.isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f))
            .border(1.dp, if (colors.isDark) Color.White.copy(0.14f) else Color.Black.copy(0.10f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.PersonOff,
            null,
            tint = if (colors.isDark) Color.White.copy(0.72f) else Color.Black.copy(0.62f),
            modifier = Modifier.size(size * 0.38f),
        )
    }
}

@Composable
fun UserModernUnavailableProfileView(
    safeAreaTop: Dp,
    safeAreaBottom: Dp,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val content = if (colors.isDark) Color.White else Color.Black

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UserProfileStateTopBar(title = "", onDismiss = onDismiss, modifier = Modifier.padding(top = 4.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileUnavailableAvatar(size = 96.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    stringResource(R.string.user_profile_unavailable_username),
                    color = if (colors.isDark) Color.White.copy(0.38f) else Color.Black.copy(0.32f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.user_profile_unavailable_bio),
                    color = if (colors.isDark) Color.White.copy(0.28f) else Color.Black.copy(0.22f),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Filled.PersonOff,
                null,
                tint = if (colors.isDark) Color.White.copy(0.44f) else Color.Black.copy(0.36f),
                modifier = Modifier.size(36.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.user_profile_unavailable_title), color = content, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.user_profile_unavailable_description),
                    color = if (colors.isDark) Color.White.copy(0.56f) else Color.Black.copy(0.50f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(safeAreaBottom + 24.dp))
    }
}

@Composable
fun UserModernOfflineProfileView(
    safeAreaTop: Dp,
    safeAreaBottom: Dp,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val content = if (colors.isDark) Color.White else Color.Black

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UserProfileStateTopBar(title = "", onDismiss = onDismiss, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Filled.WifiOff,
                null,
                tint = if (colors.isDark) Color.White.copy(0.44f) else Color.Black.copy(0.36f),
                modifier = Modifier.size(36.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.user_profile_offline_title), color = content, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.user_profile_offline_description),
                    color = if (colors.isDark) Color.White.copy(0.56f) else Color.Black.copy(0.50f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
            Row(
                Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(UserProfileAccent)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.profile_error_retry_button), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(safeAreaBottom + 24.dp))
    }
}

@Composable
fun UserModernBlockedByMeProfileView(
    userProfile: AppUser?,
    safeAreaTop: Dp,
    safeAreaBottom: Dp,
    onUnblock: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val content = if (colors.isDark) Color.White else Color.Black
    val username = userProfile?.username?.trim()?.takeIf { it.isNotEmpty() }
        ?: stringResource(R.string.profile_default_username)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserProfileStateTopBar(title = username, onDismiss = onDismiss, modifier = Modifier.padding(top = 4.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val path = userProfile?.profileImagePath
            if (!path.isNullOrBlank()) {
                AsyncImage(
                    model = path,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .alpha(0.62f)
                        .border(1.dp, if (colors.isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f), CircleShape),
                )
            } else {
                ProfileUnavailableAvatar(size = 96.dp)
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(username, color = content, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                userProfile?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    Text(
                        bio,
                        color = if (colors.isDark) Color.White.copy(0.54f) else Color.Black.copy(0.48f),
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        UserProfilePlaceholderStats(Modifier.padding(horizontal = 20.dp))

        Column(
            Modifier.fillMaxWidth().padding(vertical = 32.dp).padding(bottom = safeAreaBottom + 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Filled.PersonOff,
                null,
                tint = if (colors.isDark) Color.White.copy(0.44f) else Color.Black.copy(0.36f),
                modifier = Modifier.size(32.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.user_profile_blocked_by_me_title), color = content, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.user_profile_blocked_by_me_description),
                    color = if (colors.isDark) Color.White.copy(0.56f) else Color.Black.copy(0.50f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 34.dp),
                )
            }
            Text(
                stringResource(R.string.user_profile_unblock_user),
                color = content,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .clickable(onClick = onUnblock)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

/** Barra superior común de los estados (back + título centrado), equivalente al `privateTopBar` de iOS. */
@Composable
private fun UserProfileStateTopBar(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        ProfileChromeIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterStart),
            preset = MomentsGlassButtonPreset.NAVIGATION_BACK,
            standaloneGlass = false,
            contentDescriptionKey = ChromeIconDescription.BACK,
        )
        Text(
            title,
            modifier = Modifier.align(Alignment.Center),
            color = colors.primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Stats con "--" que iOS repite en el estado privado y en el bloqueado-por-mí. */
@Composable
private fun UserProfilePlaceholderStats(modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    val muted = if (colors.isDark) Color.White.copy(0.38f) else Color.Black.copy(0.32f)
    val labels = listOf(
        stringResource(R.string.profile_ui_posts),
        stringResource(R.string.profile_header_followers),
        stringResource(R.string.profile_header_following),
    )
    Row(modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Column(
                Modifier.weight(1f).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("--", color = muted, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(label, color = muted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
            if (index < labels.lastIndex) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(26.dp)
                        .align(Alignment.CenterVertically)
                        .background((if (colors.isDark) Color.White else Color.Black).copy(alpha = 0.12f)),
                )
            }
        }
    }
}

/** Iconos del botón de seguir en el estado privado (iOS los define distintos al header). */
private fun followButtonIcon(state: FollowButtonState): ImageVector = when (state) {
    FollowButtonState.OWN_PROFILE -> Icons.Filled.CheckCircle
    FollowButtonState.BLOCKED -> Icons.Filled.PersonOff
    FollowButtonState.FOLLOWING -> Icons.Filled.CheckCircle
    FollowButtonState.CAN_FOLLOW -> Icons.Filled.PersonAdd
    FollowButtonState.CAN_REQUEST_FOLLOW -> Icons.Filled.MailOutline
    FollowButtonState.REQUEST_PENDING -> Icons.Filled.AccessTime
    FollowButtonState.REQUEST_PENDING_CANCELLABLE -> Icons.Filled.Close
}
