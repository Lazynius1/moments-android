package com.moments.android.views.profile.userprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.services.firestore.PublicProfileAvailability
import com.moments.android.views.explore.toExploreFeedMoment
import com.moments.android.views.feed.core.sections.ModernFollowButton
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.shared.momentdetail.SingleMomentDetailView
import com.moments.android.views.story.StoriesView

/**
 * Port MVP de `UserProfileView.swift` (sheet desde Feed).
 * Tagged / connections / report = stubs honestos (omitidos).
 */
@Composable
fun UserProfileView(
    userId: String,
    onDismiss: () -> Unit,
    showBack: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val viewModel = remember { UserProfileViewModel() }
    var detailMoment by remember { mutableStateOf<Moment?>(null) }
    var showStories by remember { mutableStateOf(false) }

    LaunchedEffect(userId) { viewModel.load(userId) }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.surfaceBackground),
    ) {
        when {
            viewModel.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            viewModel.errorMessage != null && viewModel.user == null -> {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(viewModel.errorMessage.orEmpty(), color = colors.secondary)
                    TextButton(onClick = { viewModel.load(userId) }) {
                        Text(stringResource(R.string.explore_error_retry))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            }
            viewModel.availability != PublicProfileAvailability.AVAILABLE -> {
                ProfileShell(
                    title = stringResource(R.string.user_profile_unavailable_title),
                    subtitle = stringResource(R.string.user_profile_unavailable_message),
                    onDismiss = onDismiss,
                )
            }
            else -> {
                val user = viewModel.user
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item(span = { GridItemSpan(3) }) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (showBack) {
                                    IconButton(onClick = onDismiss) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                                    }
                                }
                                Text(
                                    user?.username.orEmpty(),
                                    Modifier
                                        .weight(1f)
                                        .padding(start = if (showBack) 0.dp else 12.dp),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp,
                                    color = colors.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(colors.secondary.copy(0.2f))
                                        .clickable { showStories = true },
                                ) {
                                    AsyncImage(
                                        model = user?.profileImagePath,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    user?.username.orEmpty(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = colors.primary,
                                )
                                user?.bio?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(6.dp))
                                    Text(it, color = colors.secondary, fontSize = 14.sp)
                                }
                                Spacer(Modifier.height(14.dp))
                                if (!viewModel.isOwnProfile) {
                                    ModernFollowButton(
                                        state = viewModel.followState,
                                        isLoading = false,
                                        onClick = { viewModel.toggleFollow() },
                                    )
                                }
                            }
                            if (!viewModel.canViewContent) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.secondary)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.user_profile_private_title),
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.primary,
                                    )
                                    Text(
                                        stringResource(R.string.user_profile_private_message),
                                        color = colors.secondary,
                                        fontSize = 13.sp,
                                    )
                                }
                            } else if (viewModel.moments.isEmpty()) {
                                Text(
                                    stringResource(R.string.user_profile_no_moments),
                                    Modifier.padding(24.dp),
                                    color = colors.secondary,
                                )
                            }
                        }
                    }
                    if (viewModel.canViewContent) {
                        itemsIndexed(viewModel.moments, key = { _, m -> m.id.orEmpty() }) { _, moment ->
                            val thumb = moment.mediaItems?.firstOrNull()?.thumbnailUrl
                                ?: moment.mediaItems?.firstOrNull()?.url
                                ?: moment.thumbnailUrl
                                ?: moment.imagePath
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .background(Color.Gray.copy(0.15f))
                                    .clickable { detailMoment = moment },
                            ) {
                                if (!thumb.isNullOrBlank()) {
                                    AsyncImage(
                                        model = thumb,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detailMoment?.let { moment ->
        Dialog(
            onDismissRequest = { detailMoment = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SingleMomentDetailView(
                moment = moment.toExploreFeedMoment(),
                onDismiss = { detailMoment = null },
            )
        }
    }

    if (showStories) {
        Dialog(
            onDismissRequest = { showStories = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            StoriesView(
                startAtUserId = userId,
                ringNavigationUserIds = listOf(userId),
                onDismiss = { showStories = false },
            )
        }
    }
}

@Composable
private fun ProfileShell(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = colors.primary)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = colors.secondary)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.common_close))
        }
    }
}
