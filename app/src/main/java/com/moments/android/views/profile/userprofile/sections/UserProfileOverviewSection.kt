package com.moments.android.views.profile.userprofile.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.InterestEmojiHelper
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.profile.core.SocialConnectionTab
import com.moments.android.views.profile.userprofile.UserProfileColors
import com.moments.android.views.profile.userprofile.UserProfileViewModel
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.tasks.await

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
                    color = UserProfileColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "· ${interests.size}",
                    color = UserProfileColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (!showingInterests) {
                    Text(
                        interests.first(),
                        color = UserProfileColors.textSecondary,
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
                    tint = UserProfileColors.textSecondary,
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
                add(
                    Stat(stringResource(R.string.profile_header_followers), viewModel.followers.size) {
                        onOpenSocial(SocialConnectionTab.FOLLOWERS)
                    },
                )
            }
            if (viewModel.visibleConnectionTypes.canViewFollowing) {
                add(
                    Stat(stringResource(R.string.profile_header_following), viewModel.following.size) {
                        onOpenSocial(SocialConnectionTab.FOLLOWING)
                    },
                )
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
                    color = UserProfileColors.textPrimary,
                    fontSize = if (embeddedStyle) 17.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stat.label,
                    color = UserProfileColors.textSecondary,
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
                        .background(
                            UserProfileColors.borderColor.copy(
                                alpha = if (colors.isDark) 0.24f else 0.4f,
                            ),
                        ),
                )
            }
        }
    }
}

/** Port de `UserExpandableBioView`: bio con recorte a 3 líneas y botón ver más/menos. */
@Composable
fun UserExpandableBioView(bio: String, modifier: Modifier = Modifier) {
    var isExpanded by remember(bio) { mutableStateOf(false) }
    val needsExpansion = bio.length > 100 || bio.count { it == '\n' } > 2

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = bio,
            color = UserProfileColors.textSecondary,
            fontSize = 14.sp,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (needsExpansion) {
            Text(
                text = stringResource(
                    if (isExpanded) R.string.user_profile_see_less else R.string.user_profile_see_more,
                ),
                color = UserProfileColors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(UserProfileColors.accent.copy(alpha = 0.1f))
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
    size: Dp,
    onOpenStories: () -> Unit,
    showStoryRing: Boolean = true,
    refreshTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    val isOwn = FirebaseAuth.getInstance().currentUser?.uid == userId
    Box(modifier) {
        if (showStoryRing) {
            StoryRingAvatarView(
                userId = userId,
                size = size,
                lineWidth = 3.dp,
                refreshTrigger = refreshTrigger,
                isOwnStory = isOwn,
                onTap = { hasStory -> if (hasStory) onOpenStories() },
            )
        } else {
            AsyncProfileImageView(
                userId = userId,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}

/**
 * Port de `UserModernInterestsView`: scroll horizontal; intereses compartidos con el viewer
 * se resaltan. Carga `users/{currentUid}.interests` en LaunchedEffect (≡ `onAppear` iOS).
 */
@Composable
fun UserModernInterestsView(
    interests: List<String>,
    showsTitle: Boolean = true,
    embeddedStyle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var currentUserInterests by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        currentUserInterests = runCatching {
            @Suppress("UNCHECKED_CAST")
            FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                .data?.get("interests") as? List<String>
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showsTitle) {
            Text(
                stringResource(R.string.user_profile_interests),
                color = UserProfileColors.textPrimary,
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
                val isShared = interest in currentUserInterests
                val chipBg = if (isShared) {
                    Brush.linearGradient(listOf(Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFE91E63)))
                } else {
                    val fill = if (embeddedStyle) {
                        UserProfileColors.materialBackground.copy(alpha = 0.62f)
                    } else {
                        UserProfileColors.cardBackground
                    }
                    Brush.linearGradient(listOf(fill, fill))
                }
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = if (isShared) 1.05f else 1f
                            scaleY = if (isShared) 1.05f else 1f
                        }
                        .then(
                            when {
                                isShared -> Modifier.shadow(
                                    6.dp,
                                    RoundedCornerShape(50),
                                    spotColor = Color.Blue.copy(alpha = 0.3f),
                                )
                                !embeddedStyle -> Modifier.shadow(
                                    4.dp,
                                    RoundedCornerShape(50),
                                    spotColor = UserProfileColors.shadowColor,
                                )
                                else -> Modifier
                            },
                        )
                        .clip(RoundedCornerShape(50))
                        .background(chipBg)
                        .then(
                            if (embeddedStyle && !isShared) {
                                Modifier.border(
                                    1.dp,
                                    UserProfileColors.borderColor.copy(alpha = 0.18f),
                                    RoundedCornerShape(50),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = if (embeddedStyle) 9.dp else 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(InterestEmojiHelper.emojiFor(interest), fontSize = 16.sp)
                    Text(
                        interest,
                        color = if (isShared) Color.White else UserProfileColors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
