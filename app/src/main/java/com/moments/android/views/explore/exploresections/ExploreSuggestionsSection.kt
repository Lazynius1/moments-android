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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.views.components.ModernFollowButton
import com.moments.android.views.feed.rememberAdaptiveColors

/** Suggested people use a compact avatar row, matching iOS. */
@Composable
fun ExploreSuggestionsSection(
    users: List<AppUser>,
    onUserTap: (AppUser) -> Unit,
    onShowMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    if (users.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.explore_suggested_users_title),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = colors.secondary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onShowMore) {
                Text(stringResource(R.string.explore_suggested_users_see_more), fontSize = 14.sp, color = colors.accent)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(users, key = { it.id }) { user ->
                SuggestedUserAvatar(user = user, onTap = { onUserTap(user) })
            }
        }
    }
}

@Composable
private fun SuggestedUserAvatar(user: AppUser, onTap: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier.width(76.dp)
            .clickable(role = Role.Button, onClick = onTap)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(colors.secondary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            if (user.profileImagePath.isNullOrBlank()) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(22.dp))
            } else {
                AsyncImage(model = user.profileImagePath, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(user.username, fontSize = 12.sp, color = colors.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
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
