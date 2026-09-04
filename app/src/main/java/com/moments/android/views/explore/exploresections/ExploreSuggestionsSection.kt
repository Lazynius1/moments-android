package com.moments.android.views.explore.exploresections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.utilities.HapticManager
import com.moments.android.views.components.ModernFollowButton
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.feed.rememberAdaptiveColors

/**
 * Port de `SuggestedUsersSection` (ExploreSuggestionsSection.swift).
 * Nombre `ExploreSuggestionsSection` conservado por call sites Android.
 *
 * `SearchResultCard` / `EmptySearchView` viven en [ExploreResultsSection]
 * (mismo split Swift; ya cableados desde resultados).
 */
@Composable
fun ExploreSuggestionsSection(
    users: List<AppUser>,
    moments: List<Moment>,
    userButtonStates: Map<String, FollowButtonState>,
    currentUserInterests: List<String>,
    onFollowUser: (String) -> Unit,
    onUserTap: (AppUser) -> Unit,
    onShowMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    if (users.isEmpty()) return

    val interestSet = remember(currentUserInterests) { currentUserInterests.toSet() }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.explore_suggested_users_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = colors.primary,
                )
                Text(
                    stringResource(R.string.explore_suggested_users_subtitle),
                    fontSize = 13.sp,
                    color = colors.secondary,
                )
            }
            TextButton(onClick = onShowMore) {
                Text(
                    stringResource(R.string.explore_suggested_users_see_more),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colors.accent,
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(users, key = { it.id }) { user ->
                val latestMoment = moments.firstOrNull { it.authorId == user.id }
                SuggestedUserCard(
                    user = user,
                    backgroundMoment = latestMoment,
                    commonInterests = user.interests.toSet().intersect(interestSet).size,
                    buttonState = userButtonStates[user.id] ?: FollowButtonState.CAN_FOLLOW,
                    onFollow = { onFollowUser(user.id) },
                    onTap = { onUserTap(user) },
                )
            }
        }
    }
}

/** Port de `SuggestedUserCard`. */
@Composable
fun SuggestedUserCard(
    user: AppUser,
    backgroundMoment: Moment?,
    commonInterests: Int,
    buttonState: FollowButtonState,
    onFollow: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val bgUrl = backgroundMoment?.previewImageURLString
    val hasPhotoBg = !bgUrl.isNullOrBlank()
    val isPassive = buttonState == FollowButtonState.REQUEST_PENDING

    Box(
        modifier
            .width(132.dp)
            .height(176.dp)
            .shadow(6.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = 0.10f))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .border(0.8.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
            .clickable(onClick = onTap),
    ) {
        if (hasPhotoBg) {
            AsyncImage(
                model = bgUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(4.dp),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.50f)),
                        ),
                    ),
            )
        } else {
            // iOS defaultBackground: primary 6% sobre base negra (sin chrome opaco light).
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.primary.copy(alpha = 0.06f)),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(10.dp)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Spacer(Modifier.weight(1f))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(48.dp)
                        .blur(6.dp)
                        .background(colors.accent.copy(alpha = 0.3f), CircleShape),
                )
                ExploreProfileImage(imagePath = user.profileImagePath, size = 42.dp)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        user.username,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    VerifiedBadgeView(userId = user.id, size = 10.dp)
                }
                Text(
                    if (commonInterests > 0) {
                        stringResource(R.string.explore_common_interests, commonInterests)
                    } else {
                        stringResource(R.string.explore_suggested_users_suggested_for_you)
                    },
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                suggestedFollowButtonTitle(buttonState),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (isPassive) 0.78f else 1f }
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = buttonState.isActionable)
                    .clickable(enabled = buttonState.isActionable, onClick = onFollow)
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun suggestedFollowButtonTitle(state: FollowButtonState): String = when (state) {
    FollowButtonState.FOLLOWING -> stringResource(R.string.user_profile_following)
    FollowButtonState.CAN_REQUEST_FOLLOW -> stringResource(R.string.feed_follow_request)
    FollowButtonState.REQUEST_PENDING -> stringResource(R.string.feed_follow_requested)
    FollowButtonState.REQUEST_PENDING_CANCELLABLE -> stringResource(R.string.feed_follow_cancel_request)
    FollowButtonState.BLOCKED -> stringResource(R.string.user_profile_blocked)
    else -> stringResource(R.string.feed_follow)
}

/** Port de `SearchBarView` (no cableado en ExploreView.swift — usa `.searchable`). */
@Composable
fun ExploreSearchBarView(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    isSearchFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .momentsChromeGlass(RoundedCornerShape(10.dp), interactive = true)
                .border(
                    1.dp,
                    colors.primary.copy(alpha = if (isSearchFocused) 0.28f else 0.08f),
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = if (isSearchFocused) colors.primary else colors.secondary,
                modifier = Modifier.size(15.dp),
            )
            BasicTextField(
                value = searchText,
                onValueChange = {
                    onSearchTextChange(it)
                    onSearch(it)
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                decorationBox = { inner ->
                    if (searchText.isEmpty()) {
                        Text(
                            stringResource(R.string.explore_search_placeholder),
                            color = colors.secondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    inner()
                },
            )
            if (searchText.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onSearchTextChange("")
                        onSearch("")
                    },
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(15.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = isSearchFocused,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            TextButton(
                onClick = {
                    onSearchTextChange("")
                    onSearch("")
                    onFocusChange(false)
                },
            ) {
                Text(
                    stringResource(R.string.explore_search_cancel),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colors.accent,
                )
            }
        }
    }
}

/** Port de `LoadingStateView`. */
@Composable
fun ExploreLoadingStateView(modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    val transition = rememberInfiniteTransition(label = "exploreLoading")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.2f),
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .rotate(angle),
            ) {
                drawArc(
                    color = colors.accent,
                    startAngle = -90f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset.Zero,
                    size = size,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.explore_loading),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = colors.primary,
            )
            Text(
                stringResource(R.string.explore_loading_subtitle),
                fontSize = 14.sp,
                color = colors.secondary,
            )
        }
    }
}

/** Port de `ErrorStateView`. */
@Composable
fun ExploreErrorStateView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(80.dp)
                    .momentsChromeGlass(CircleShape, interactive = false)
                    .border(2.dp, Color.Red.copy(alpha = 0.3f), CircleShape),
            )
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(32.dp))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.explore_error_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = colors.primary,
            )
            Text(
                message,
                fontSize = 16.sp,
                color = colors.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .height(50.dp)
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.explore_error_retry),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.primary,
                )
            }
        }
    }
}

/**
 * Port de `FollowButton` (ExploreSuggestionsSection.swift).
 */
@Composable
fun ExploreSuggestionsFollowButton(
    buttonState: FollowButtonState,
    targetUserId: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModernFollowButton(
        state = buttonState,
        isLoading = false,
        targetUserId = targetUserId,
        onClick = onTap,
        modifier = modifier,
    )
}

/** Port de `ProfileImageeView`. */
@Composable
fun ExploreProfileImage(
    imagePath: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    onDarkPhoto: Boolean = true,
) {
    val colors = rememberAdaptiveColors()
    val stroke = if (onDarkPhoto) Color.White else colors.primary.copy(alpha = 0.28f)
    val placeholderTint = if (onDarkPhoto) Color.White else colors.primary
    if (!imagePath.isNullOrBlank()) {
        AsyncImage(
            model = imagePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.5.dp, stroke, CircleShape),
        )
    } else {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.2f))
                .border(1.5.dp, stroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = placeholderTint,
                modifier = Modifier.size(size * 0.4f),
            )
        }
    }
}

/** Port de `EmptyMomentsView`. */
@Composable
fun EmptyMomentsView(modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(76.dp)
                .momentsChromeGlass(CircleShape, interactive = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Collections,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(31.dp),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.explore_no_moments),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = colors.primary,
            )
            Text(
                stringResource(R.string.explore_no_moments_subtitle),
                fontSize = 14.sp,
                color = colors.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
