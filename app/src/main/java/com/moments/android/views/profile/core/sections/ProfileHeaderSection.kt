package com.moments.android.views.profile.core.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.InterestEmojiHelper
import com.moments.android.extensions.ProfileChromeControlsCluster
import com.moments.android.extensions.ProfileChromeGlassMetrics
import com.moments.android.extensions.ProfileChromeIconButton
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.VerifiedBadge
import com.moments.android.views.components.VerifiedUsernameGradientView
import com.moments.android.views.profile.core.ProfileViewModel
import com.moments.android.views.story.StorySegmentedRing
import com.moments.android.views.story.StoryViewModel

/**
 * Port de `ProfileHeaderSection.swift`.
 *
 * Chapas Plus/Support (`PlusBadgeInline` / `SupportBadgeInline` / corona en avatar) 🚫 —
 * no portar (checklist). Zoom source ids: `"settings-view"` / `"edit-profile-view"`.
 */

private object ProfileOwnZoomSource {
    const val SETTINGS = "settings-view"
    const val EDIT_PROFILE = "edit-profile-view"
}

@Composable
fun ProfileOwnPinnedTopChrome(
    username: String,
    isVerified: Boolean,
    collapseProgress: Float,
    isIncognitoActive: Boolean,
    onNotifications: () -> Unit,
    onShowQrCode: () -> Unit,
    onShowIncognito: () -> Unit,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    var menuExpanded by remember { mutableStateOf(false) }
    val content = if (dark) Color.White else Color(0xFF0B1215)
    val progress = collapseProgress.coerceIn(0f, 1f)

    StickyChromeBarLayout(
        modifier = modifier,
        leading = {
            Spacer(Modifier.size(ProfileChromeGlassMetrics.controlSize))
        },
        center = {
            Row(
                modifier = Modifier
                    .alpha(progress)
                    .graphicsLayer {
                        val scale = 0.96f + (progress * 0.04f)
                        scaleX = scale
                        scaleY = scale
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = username,
                    color = content,
                    fontSize = StickyChromeTitleTypography.fontSize,
                    fontWeight = StickyChromeTitleTypography.fontWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isVerified) VerifiedBadge(size = 16.dp)
            }
        },
        trailing = {
            ProfileChromeControlsCluster {
                ProfileChromeIconButton(
                    icon = Icons.Filled.Notifications,
                    onClick = onNotifications,
                    standaloneGlass = false,
                    accessibilityLabel = stringResource(R.string.profile_header_notifications),
                )
                Box {
                    ProfileChromeIconButton(
                        icon = Icons.Filled.MoreHoriz,
                        onClick = { menuExpanded = true },
                        standaloneGlass = false,
                        accessibilityLabel = stringResource(R.string.profile_header_more),
                        modifier = Modifier.profileMomentZoomSource(
                            sourceID = ProfileOwnZoomSource.SETTINGS,
                            cornerRadius = ProfileChromeGlassMetrics.controlSize / 2,
                        ),
                    )
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_header_qr)) },
                            onClick = { menuExpanded = false; onShowQrCode() },
                            leadingIcon = { Icon(Icons.Filled.QrCode, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_header_incognito)) },
                            onClick = { menuExpanded = false; onShowIncognito() },
                            leadingIcon = {
                                Icon(
                                    if (isIncognitoActive) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.profile_header_settings)) },
                            onClick = { menuExpanded = false; onShowSettings() },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun ModernProfileHeader(
    viewModel: ProfileViewModel,
    storyViewModel: StoryViewModel,
    usernameCollapseProgress: Float,
    onEditProfile: () -> Unit,
    onShowStoryViewer: () -> Unit,
    onShowProfileImage: () -> Unit,
    onIdentityMinY: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val user = viewModel.userProfile
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val stories = uid?.let(storyViewModel::storiesFor).orEmpty()
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color(0xFF0B1215)

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
                ProfileHeaderAvatar(
                    user = user,
                    hasActiveStory = storyViewModel.hasActiveStory,
                    storyCount = stories.size,
                    storyAudiences = stories.map { it.audience },
                    onClick = {
                        if (storyViewModel.hasActiveStory && uid != null) onShowStoryViewer()
                        else onShowProfileImage()
                    },
                )
                ProfileAvatarNoteView(
                    note = user?.profileNote,
                    isEditable = true,
                    onSave = { note -> viewModel.updateProfileNote(note) },
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Column(
                    modifier = Modifier
                        .alpha(1f - usernameCollapseProgress.coerceIn(0f, 1f))
                        .reportProfileIdentityMinY(onIdentityMinY),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    VerifiedUsernameGradientView(
                        username = user?.username ?: stringResource(R.string.profile_default_username),
                        isVerified = user?.isVerified == true,
                        gradient = Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFF6B73FF))),
                        badgeSize = 18.dp,
                    )
                    // PlusBadgeInline / SupportBadgeInline 🚫
                }

                ExpandableBioView(
                    bio = user?.bio?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.profile_header_add_bio),
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
                            tint = Color(0xFF007AFF),
                        )
                        Text(
                            website.removePrefix("https://").removePrefix("http://"),
                            color = Color(0xFF007AFF),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                .profileMomentZoomSource(
                    sourceID = ProfileOwnZoomSource.EDIT_PROFILE,
                    cornerRadius = 50.dp,
                )
                .clickable(onClick = onEditProfile)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = content)
            Spacer(Modifier.width(7.dp))
            Text(
                stringResource(R.string.profile_header_edit),
                color = content,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProfileHeaderAvatar(
    user: AppUser?,
    hasActiveStory: Boolean,
    storyCount: Int,
    storyAudiences: List<String?>,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val material = if (dark) Color(0xFF182429) else Color(0xFFEAF0F2)
    val tertiary = Color(0xFF84939A)

    Box(
        modifier = Modifier
            .size(96.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (user?.profileImagePath != null) {
            AsyncImage(
                model = user.profileImagePath,
                contentDescription = stringResource(R.string.profile_header_avatar),
                modifier = Modifier.size(96.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(96.dp).clip(CircleShape).background(material),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = stringResource(R.string.profile_header_avatar),
                    modifier = Modifier.size(56.dp),
                    tint = tertiary,
                )
            }
        }
        // Anillo solo con historia activa; borde Plus dorado 🚫
        if (hasActiveStory && storyCount > 0) {
            StorySegmentedRing(
                storyCount = storyCount,
                hasStory = true,
                hasUnseenStory = false,
                storyViewedStatus = List(storyCount) { true },
                storyAudiences = storyAudiences,
                isOwnStory = true,
                ringSize = 96.dp,
                lineWidth = 3.dp,
            )
        }
    }
}

@Composable
fun ProfileOverviewCard(
    viewModel: ProfileViewModel,
    interests: List<String>,
    onOpenVisits: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenMutuals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showingInterests by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color(0xFF0B1215)
    val secondary = if (dark) Color.White.copy(alpha = .65f) else Color(0xFF52626A)

    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        ModernStatsSection(
            viewModel = viewModel,
            onOpenVisits = onOpenVisits,
            onOpenFollowers = onOpenFollowers,
            onOpenFollowing = onOpenFollowing,
            onOpenMutuals = onOpenMutuals,
            embeddedStyle = true,
        )
        if (interests.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showingInterests = !showingInterests }
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.profile_header_interests),
                    color = content,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.profile_header_interest_count, interests.size),
                    color = secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (!showingInterests) {
                    Text(
                        interests.first(),
                        color = secondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = if (showingInterests) 180f else 0f },
                )
            }
            if (showingInterests) {
                ModernInterestsView(
                    interests = interests,
                    showsTitle = false,
                    embeddedStyle = true,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
fun ModernStatsSection(
    viewModel: ProfileViewModel,
    onOpenVisits: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenMutuals: () -> Unit,
    embeddedStyle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color(0xFF0B1215)
    val secondary = if (dark) Color.White.copy(alpha = .65f) else Color(0xFF52626A)
    val border = if (dark) Color.White.copy(alpha = 0.24f) else Color(0xFF0B1215).copy(alpha = 0.4f)
    val stats = listOf(
        Triple(R.string.profile_header_visits, viewModel.groupedVisits.size, onOpenVisits),
        Triple(R.string.profile_header_followers, viewModel.followers.size, onOpenFollowers),
        Triple(R.string.profile_header_following, viewModel.following.size, onOpenFollowing),
        Triple(R.string.profile_header_mutuals, viewModel.mutuals.size, onOpenMutuals),
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = if (embeddedStyle) 20.dp else 0.dp),
    ) {
        stats.forEachIndexed { index, (label, count, action) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = action)
                    .padding(vertical = if (embeddedStyle) 8.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    MomentsFormat.count(count, MomentsFormat.CountStyle.PROFILE_STAT),
                    color = content,
                    fontSize = if (embeddedStyle) 17.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(label),
                    color = secondary,
                    fontSize = if (embeddedStyle) 10.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (embeddedStyle && index < stats.lastIndex) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(26.dp)
                        .align(Alignment.CenterVertically)
                        .background(border),
                )
            }
        }
    }
}

@Composable
fun ModernInterestsView(
    interests: List<String>,
    showsTitle: Boolean = true,
    embeddedStyle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val content = if (dark) Color.White else Color(0xFF0B1215)
    val chipBg = if (embeddedStyle) {
        if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.05f)
    } else {
        if (dark) Color(0xFF182429) else Color.White
    }
    val chipBorder = if (embeddedStyle) {
        (if (dark) Color.White else Color(0xFF0B1215)).copy(alpha = 0.18f)
    } else {
        Color.Transparent
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showsTitle) {
            Text(
                stringResource(R.string.profile_header_interests),
                color = content,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = if (embeddedStyle) 20.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            interests.forEach { interest ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(chipBg)
                        .then(
                            if (embeddedStyle) Modifier.border(1.dp, chipBorder, RoundedCornerShape(50))
                            else Modifier,
                        )
                        .padding(
                            horizontal = if (embeddedStyle) 14.dp else 16.dp,
                            vertical = if (embeddedStyle) 9.dp else 10.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(InterestEmojiHelper.emojiFor(interest), fontSize = 16.sp)
                    Text(interest, color = content, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
