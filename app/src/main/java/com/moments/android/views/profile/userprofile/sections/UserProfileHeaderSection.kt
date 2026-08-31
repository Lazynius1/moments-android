package com.moments.android.views.profile.userprofile.sections

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.views.feed.sharing.ProfileShareBottomSheet
import com.moments.android.views.feed.sharing.ProfileShareSheetItem
import com.moments.android.views.feed.sharing.SharedProfilePayloadBuilder
import com.moments.android.extensions.ChromeIconDescription
import com.moments.android.extensions.MomentsGlassButtonPreset
import com.moments.android.extensions.ProfileChromeControlsCluster
import com.moments.android.extensions.ProfileChromeIconButton
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.views.components.VerifiedBadge
import com.moments.android.views.components.VerifiedUsernameGradientView
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.profile.core.sections.ExpandableBioView
import com.moments.android.views.profile.core.sections.ProfileAvatarNoteMetrics
import com.moments.android.views.profile.core.sections.ProfileAvatarNoteView
import com.moments.android.views.profile.core.sections.StickyChromeBarLayout
import com.moments.android.views.profile.userprofile.UserProfileViewModel
import com.moments.android.views.profile.userprofile.UserProfileColors

/**
 * Port de `UserProfileHeaderSection.swift` — chrome fijado (back + username + menú) y cabecera
 * moderna (avatar + nota + username verificado + bio + web + botones seguir/mensaje).
 *
 * Puentes conscientes:
 * - Chapas Plus/Support (`UserProfileBadgesView`) 🚫 — no comprar ni mostrar (checklist).
 * - `startConversation` vive en el host vía `onOpenMessage`.
 * - Share ≡ `ShareLink(https://glowsy.app/{username})`; QR es acción aparte.
 */
@Composable
fun ProfileVisitorPinnedTopChrome(
    viewModel: UserProfileViewModel,
    collapseProgress: Float,
    onDismiss: () -> Unit,
    onShowQrCode: () -> Unit,
    onShowReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var profileShareItem by remember { mutableStateOf<ProfileShareSheetItem?>(null) }
    val user = viewModel.userProfile

    profileShareItem?.let { item ->
        ProfileShareBottomSheet(
            item = item,
            onDismiss = { profileShareItem = null },
        )
    }

    StickyChromeBarLayout(
        modifier = modifier,
        leading = {
            ProfileChromeIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                onClick = onDismiss,
                preset = MomentsGlassButtonPreset.NAVIGATION_BACK,
                standaloneGlass = false,
                contentDescriptionKey = ChromeIconDescription.BACK,
            )
        },
        center = {
            Row(
                modifier = Modifier
                    .alpha(collapseProgress)
                    .offset(x = (-6 * (1 - collapseProgress)).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = user?.username ?: stringResource(R.string.profile_default_username),
                    color = colors.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (user?.isVerified == true) VerifiedBadge(size = 16.dp)
            }
        },
        trailing = {
            ProfileChromeControlsCluster {
                Box {
                    ProfileChromeIconButton(
                        icon = Icons.Filled.MoreHoriz,
                        onClick = { menuExpanded = true },
                        standaloneGlass = false,
                        accessibilityLabel = stringResource(R.string.profile_header_more),
                    )
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (viewModel.isMutedByCurrentUser) {
                                            R.string.user_profile_relationship_mute_disable
                                        } else {
                                            R.string.user_profile_relationship_mute_enable
                                        },
                                    ),
                                )
                            },
                            onClick = { menuExpanded = false; viewModel.toggleMute() },
                            leadingIcon = {
                                Icon(
                                    if (viewModel.isMutedByCurrentUser) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (viewModel.isBlockedByCurrentUser) R.string.user_profile_unblock_user
                                        else R.string.conversation_settings_block,
                                    ),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                if (viewModel.isBlockedByCurrentUser) viewModel.unblockUser(viewModel.userId)
                                else viewModel.blockUser(viewModel.userId)
                            },
                            leadingIcon = { Icon(Icons.Filled.PersonOff, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.report_action_user)) },
                            onClick = { menuExpanded = false; onShowReport() },
                            leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                        )
                        if (user != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_profile_send_in_chat)) },
                                onClick = {
                                    menuExpanded = false
                                    presentProfileShare(viewModel, user)?.let { profileShareItem = it }
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.qr_code_share)) },
                                onClick = {
                                    menuExpanded = false
                                    shareProfileUrl(context, user.username)
                                },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_header_qr)) },
                            onClick = { menuExpanded = false; onShowQrCode() },
                            leadingIcon = { Icon(Icons.Filled.QrCode, contentDescription = null) },
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun UserModernProfileHeader(
    viewModel: UserProfileViewModel,
    storyRingRefreshTrigger: Int,
    usernameCollapseProgress: Float,
    onFollowAction: () -> Unit,
    onOpenStories: () -> Unit,
    onShowProfileImageFullscreen: () -> Unit,
    onOpenMessage: () -> Unit,
    onAvatarBoundsChange: (Rect) -> Unit = {},
    hideAvatarForFlip: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val user = viewModel.userProfile

    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.width(ProfileAvatarNoteMetrics.columnWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserModernAvatarWithBadges(
                    userProfile = user,
                    size = 96.dp,
                    storyRingRefreshTrigger = storyRingRefreshTrigger,
                    onOpenStories = onOpenStories,
                    onShowProfileImageFullscreen = onShowProfileImageFullscreen,
                    modifier = Modifier
                        .alpha(if (hideAvatarForFlip) 0f else 1f)
                        .onGloballyPositioned { onAvatarBoundsChange(it.boundsInRoot()) },
                )
                ProfileAvatarNoteView(
                    note = user?.profileNote,
                    isEditable = false,
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Column(
                    modifier = Modifier.alpha(1 - usernameCollapseProgress),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    VerifiedUsernameGradientView(
                        username = user?.username ?: stringResource(R.string.profile_default_username),
                        isVerified = user?.isVerified == true,
                        gradient = Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFF6B73FF))),
                        badgeSize = 18.dp,
                    )
                    // UserProfileBadgesView 🚫 (chapas Plus/Support — checklist)
                }

                ExpandableBioView(
                    bio = user?.bio?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.user_profile_no_bio),
                )

                user?.websiteUrl?.takeIf { it.isNotBlank() }?.let { website ->
                    val uriHandler = LocalUriHandler.current
                    val url = if (website.startsWith("http")) website else "https://$website"
                    Row(
                        modifier = Modifier.clickable { runCatching { uriHandler.openUri(url) } },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Link,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = UserProfileColors.accent,
                        )
                        Text(
                            website.removePrefix("https://").removePrefix("http://"),
                            color = UserProfileColors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.width(0.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val actionable = viewModel.followButtonState.isActionable
            val scale by animateFloatAsState(if (actionable) 1f else 0.95f, label = "followScale")
            Row(
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = actionable)
                    .clickable(enabled = actionable, onClick = onFollowAction)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    followButtonText(viewModel.followButtonState),
                    color = colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (viewModel.followButtonState == FollowButtonState.FOLLOWING) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = colors.primary,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .clickable(onClick = onOpenMessage)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = colors.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.user_profile_send_message),
                    color = colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun followButtonText(state: FollowButtonState): String = when (state) {
    FollowButtonState.OWN_PROFILE -> stringResource(R.string.user_profile_follow_button_own_profile)
    FollowButtonState.BLOCKED -> stringResource(R.string.user_profile_blocked)
    FollowButtonState.FOLLOWING -> stringResource(R.string.feed_following_action)
    FollowButtonState.CAN_FOLLOW -> stringResource(R.string.feed_follow)
    FollowButtonState.CAN_REQUEST_FOLLOW -> stringResource(R.string.feed_follow_request)
    FollowButtonState.REQUEST_PENDING -> stringResource(R.string.feed_follow_requested)
    FollowButtonState.REQUEST_PENDING_CANCELLABLE -> stringResource(R.string.feed_follow_cancel_request)
}

/** ≡ ShareLink de iOS: `https://glowsy.app/{username}`. */
private fun shareProfileUrl(context: android.content.Context, username: String) {
    val url = "https://glowsy.app/$username"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/** ≡ iOS `presentProfileShare(for:)` + `ProfileShareSheetItem`. */
private fun presentProfileShare(
    viewModel: UserProfileViewModel,
    user: AppUser,
): ProfileShareSheetItem? {
    val resolvedId = user.id.trim().ifEmpty { viewModel.userId }
    val isOwnProfile = resolvedId == FirebaseAuth.getInstance().currentUser?.uid
    val data = SharedProfilePayloadBuilder.make(
        user = user,
        moments = viewModel.moments,
        canViewContent = viewModel.canViewContent,
        visibleConnectionTypes = viewModel.visibleConnectionTypes,
        isOwnProfile = isOwnProfile,
        fallbackUserId = viewModel.userId,
    )
    val profileUserId = data["profileUserId"]?.trim().orEmpty()
    if (profileUserId.isEmpty()) return null
    return ProfileShareSheetItem(profileUserId = profileUserId, sharedProfileData = data)
}
