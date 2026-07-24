package com.moments.android.views.explore.exploresections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.views.feed.core.sections.ModernFollowButton
import com.moments.android.views.feed.rememberAdaptiveColors

/**
 * Port de `SuggestedUsersSection` (ExploreSuggestionsSection.swift).
 */
@Composable
fun ExploreSuggestionsSection(
    users: List<AppUser>,
    userButtonStates: Map<String, FollowButtonState>,
    currentUserInterests: List<String>,
    onFollowUser: (String) -> Unit,
    onUserTap: (AppUser) -> Unit,
    onShowMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    if (users.isEmpty()) return

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.explore_suggested_users_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = colors.primary,
                )
                Text(
                    stringResource(R.string.explore_suggested_users_subtitle),
                    fontSize = 13.sp,
                    color = colors.secondary,
                )
            }
            TextButton(onClick = onShowMore) {
                Text(stringResource(R.string.explore_suggested_users_see_more))
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(users, key = { it.id }) { user ->
                SuggestedUserCard(
                    user = user,
                    state = userButtonStates[user.id] ?: FollowButtonState.CAN_FOLLOW,
                    commonCount = user.interests.toSet().intersect(currentUserInterests.toSet()).size,
                    onFollow = { onFollowUser(user.id) },
                    onTap = { onUserTap(user) },
                )
            }
        }
    }
}

@Composable
private fun SuggestedUserCard(
    user: AppUser,
    state: FollowButtonState,
    commonCount: Int,
    onFollow: () -> Unit,
    onTap: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceBackground)
            .clickable(onClick = onTap)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = user.profileImagePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.secondary.copy(alpha = 0.2f)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            user.username,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = colors.primary,
        )
        if (commonCount > 0) {
            Text(
                stringResource(R.string.explore_common_interests, commonCount),
                fontSize = 11.sp,
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        ModernFollowButton(state = state, isLoading = false, onClick = onFollow)
    }
}
