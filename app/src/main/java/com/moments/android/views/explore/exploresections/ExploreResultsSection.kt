package com.moments.android.views.explore.exploresections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import com.moments.android.models.Moment
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.views.explore.ExploreMomentsGrid
import com.moments.android.views.feed.core.sections.ModernFollowButton
import com.moments.android.views.feed.rememberAdaptiveColors

/**
 * Port de `SmartSearchResultsView` (ExploreResultsSection.swift).
 */
@Composable
fun ExploreResultsSection(
    searchQuery: String,
    users: List<AppUser>,
    moments: List<Moment>,
    userButtonStates: Map<String, FollowButtonState>,
    onFollowUser: (String) -> Unit,
    onUserTap: (AppUser) -> Unit,
    onMomentTap: (Moment, Int, List<Moment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Column(modifier.fillMaxWidth()) {
        if (users.isEmpty() && moments.isEmpty()) {
            Text(
                stringResource(R.string.explore_search_empty_title),
                Modifier.padding(24.dp),
                color = colors.secondary,
                fontSize = 15.sp,
            )
            return
        }

        if (users.isNotEmpty()) {
            Text(
                stringResource(R.string.explore_search_users_title),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = colors.primary,
            )
            users.forEach { user ->
                SearchUserRow(
                    user = user,
                    state = userButtonStates[user.id] ?: FollowButtonState.CAN_FOLLOW,
                    onFollow = { onFollowUser(user.id) },
                    onTap = { onUserTap(user) },
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (moments.isNotEmpty()) {
            Text(
                stringResource(R.string.explore_search_moments_title),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = colors.primary,
            )
            ExploreMomentsGrid(
                moments = moments,
                onMomentTap = onMomentTap,
            )
        }
    }
}

@Composable
private fun SearchUserRow(
    user: AppUser,
    state: FollowButtonState,
    onFollow: () -> Unit,
    onTap: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = user.profileImagePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.secondary.copy(alpha = 0.2f)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                user.username,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            user.bio?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ModernFollowButton(state = state, isLoading = false, onClick = onFollow)
    }
}
