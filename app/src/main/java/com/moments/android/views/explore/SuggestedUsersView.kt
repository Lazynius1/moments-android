package com.moments.android.views.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.views.feed.core.sections.ModernFollowButton
import com.moments.android.views.feed.rememberAdaptiveColors

/**
 * Port MVP de `SuggestedUsersView.swift` — lista + follow (sin paginación infinita).
 */
@Composable
fun SuggestedUsersView(
    users: List<AppUser>,
    userButtonStates: Map<String, FollowButtonState>,
    onFollowUser: (String) -> Unit,
    onUserTap: (AppUser) -> Unit,
    onDismiss: () -> Unit,
    onAppearUser: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    LaunchedEffect(users) {
        users.forEach { onAppearUser(it.id) }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceBackground)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
            }
            Text(
                stringResource(R.string.explore_suggested_users_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = colors.primary,
            )
        }
        if (users.isEmpty()) {
            Text(
                stringResource(R.string.explore_suggested_users_empty),
                Modifier.padding(24.dp),
                color = colors.secondary,
            )
        } else {
            LazyColumn {
                items(users, key = { it.id }) { user ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onUserTap(user) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                            Text(user.username, fontWeight = FontWeight.SemiBold, color = colors.primary)
                        }
                        ModernFollowButton(
                            state = userButtonStates[user.id] ?: FollowButtonState.CAN_FOLLOW,
                            isLoading = false,
                            onClick = { onFollowUser(user.id) },
                        )
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}
