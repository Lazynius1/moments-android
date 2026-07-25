package com.moments.android.views.profile.userprofile.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.InterestEmojiHelper
import com.moments.android.views.profile.core.SocialConnectionTab
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.profile.userprofile.UserProfileViewModel

/**
 * Port de `UserProfileOverviewSection.swift` — stats (posts/seguidores/seguidos según privacidad)
 * + intereses desplegables del perfil visitado.
 *
 * `@Binding socialConnectionsRoute` / `selectedTab` de iOS → callbacks `onOpenSocial` / `onSelectMoments`.
 */
@Composable
fun UserProfileOverviewSection(
    viewModel: UserProfileViewModel,
    interests: List<String>,
    onOpenSocial: (SocialConnectionTab) -> Unit,
    onSelectMoments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    var showingInterests by remember { mutableStateOf(false) }
    val hasVisibleStats = viewModel.canViewContent ||
        viewModel.visibleConnectionTypes.canViewFollowers ||
        viewModel.visibleConnectionTypes.canViewFollowing

    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (hasVisibleStats) {
            UserModernStatsSection(
                viewModel = viewModel,
                onOpenSocial = onOpenSocial,
                onSelectMoments = onSelectMoments,
                embeddedStyle = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
        }

        if (interests.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showingInterests = !showingInterests }
                    .padding(horizontal = 20.dp)
                    .padding(top = if (hasVisibleStats) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.profile_header_interests),
                    color = colors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("· ${interests.size}", color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (!showingInterests) {
                    Text(
                        interests.first(),
                        color = colors.secondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    null,
                    tint = colors.secondary,
                    modifier = Modifier.rotate(if (showingInterests) 180f else 0f),
                )
            }

            AnimatedVisibility(visible = showingInterests) {
                UserModernInterestsView(
                    interests = interests,
                    showsTitle = false,
                    embeddedStyle = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
fun UserModernStatsSection(
    viewModel: UserProfileViewModel,
    onOpenSocial: (SocialConnectionTab) -> Unit,
    onSelectMoments: () -> Unit,
    embeddedStyle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val postsCount = maxOf(viewModel.moments.size, viewModel.userProfile?.momentsCount ?: 0)

    data class Stat(val label: String, val count: Int, val action: () -> Unit)
    val stats = buildList {
        if (viewModel.canViewContent) {
            add(Stat(stringResource(R.string.profile_ui_posts), postsCount, onSelectMoments))
            add(
                Stat(
                    stringResource(R.string.profile_header_followers),
                    if (viewModel.visibleConnectionTypes.canViewFollowers) viewModel.followers.size else 0,
                ) { onOpenSocial(SocialConnectionTab.FOLLOWERS) },
            )
            add(
                Stat(
                    stringResource(R.string.profile_header_following),
                    if (viewModel.visibleConnectionTypes.canViewFollowing) viewModel.following.size else 0,
                ) { onOpenSocial(SocialConnectionTab.FOLLOWING) },
            )
        } else {
            if (viewModel.visibleConnectionTypes.canViewFollowers) {
                add(Stat(stringResource(R.string.profile_header_followers), viewModel.followers.size) { onOpenSocial(SocialConnectionTab.FOLLOWERS) })
            }
            if (viewModel.visibleConnectionTypes.canViewFollowing) {
                add(Stat(stringResource(R.string.profile_header_following), viewModel.following.size) { onOpenSocial(SocialConnectionTab.FOLLOWING) })
            }
        }
    }

    Row(modifier.padding(horizontal = if (embeddedStyle) 2.dp else 0.dp)) {
        stats.forEachIndexed { index, stat ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = stat.action)
                    .padding(vertical = if (embeddedStyle) 8.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    MomentsFormat.count(stat.count, MomentsFormat.CountStyle.PROFILE_STAT),
                    color = colors.primary,
                    fontSize = if (embeddedStyle) 17.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(stat.label, color = colors.secondary, fontSize = if (embeddedStyle) 10.sp else 11.sp, fontWeight = FontWeight.Medium)
            }
            if (embeddedStyle && index < stats.lastIndex) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(26.dp)
                        .align(Alignment.CenterVertically)
                        .background(colors.secondary.copy(alpha = if (colors.isDark) 0.24f else 0.4f)),
                )
            }
        }
    }
}

/** Port de `UserExpandableBioView`: bio con recorte a 3 líneas y botón ver más/menos. */
@Composable
fun UserExpandableBioView(bio: String, modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    var isExpanded by remember(bio) { mutableStateOf(false) }
    val needsExpansion = bio.length > 100 || bio.count { it == '\n' } > 2

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = bio,
            color = colors.secondary,
            fontSize = 14.sp,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (needsExpansion) {
            Text(
                text = stringResource(if (isExpanded) R.string.user_profile_see_less else R.string.user_profile_see_more),
                color = UserProfileAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(UserProfileAccent.copy(alpha = 0.1f))
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** Port de `UserModernAvatar`: avatar con anillo de historia opcional. */
@Composable
fun UserModernAvatar(
    userId: String,
    size: androidx.compose.ui.unit.Dp,
    onOpenStories: () -> Unit,
    showStoryRing: Boolean = true,
    refreshTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    val isOwn = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid == userId
    Box(modifier) {
        if (showStoryRing) {
            com.moments.android.views.story.StoryRingAvatarView(
                userId = userId,
                size = size,
                lineWidth = 3.dp,
                refreshTrigger = refreshTrigger,
                isOwnStory = isOwn,
                onTap = { hasStory -> if (hasStory) onOpenStories() },
            )
        } else {
            com.moments.android.coordinators.AsyncProfileImageView(
                userId = userId,
                modifier = Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape),
            )
        }
    }
}

/** Port de `UserModernInterestsView`: intereses en scroll horizontal, compartidos resaltados. */
@Composable
fun UserModernInterestsView(
    interests: List<String>,
    showsTitle: Boolean = true,
    embeddedStyle: Boolean = false,
    sharedInterests: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showsTitle) {
            Text(stringResource(R.string.profile_header_interests), color = colors.primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = if (embeddedStyle) 20.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            interests.forEach { interest ->
                val isShared = interest in sharedInterests
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isShared) {
                                Brush.linearGradient(listOf(Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFE91E63)))
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        if (embeddedStyle) (if (colors.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.05f)) else if (colors.isDark) Color(0xFF182429) else Color.White,
                                        if (embeddedStyle) (if (colors.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.05f)) else if (colors.isDark) Color(0xFF182429) else Color.White,
                                    ),
                                )
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = if (embeddedStyle) 9.dp else 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(InterestEmojiHelper.emojiFor(interest), fontSize = 16.sp)
                    Text(
                        interest,
                        color = if (isShared) Color.White else colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
