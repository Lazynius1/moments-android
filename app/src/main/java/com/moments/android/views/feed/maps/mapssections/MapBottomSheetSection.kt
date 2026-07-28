package com.moments.android.views.feed.maps.mapssections

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.LiveUsernameText
import com.moments.android.views.feed.maps.MapPlaceCluster
import com.moments.android.views.feed.maps.mapAvailabilityKey
import com.moments.android.views.feed.maps.mapHasVideoMedia
import com.moments.android.views.feed.maps.mapPreferredImageUrl
import com.moments.android.views.feed.maps.mapPreferredVideoThumbnailUrl
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.story.StoryRingAvatarView

/**
 * Port de `MapBottomSheetSection.swift`.
 * Tipos iOS en ese archivo: `LocationBottomSheet`, `MapBottomSheetGridCell`,
 * `MapsVideoThumbnailView`, `ModernLocationMomentRow`, `MomentUnavailableOverlay`.
 */

private enum class LocationBottomSheetViewMode { Gallery, List }

/** Compat wrapper cuando el host solo tiene un cluster (mapa de lugar). */
@Composable
fun MapBottomSheetSection(
    cluster: MapPlaceCluster?,
    modifier: Modifier = Modifier,
    momentAvailability: Map<String, Boolean> = emptyMap(),
    isLoadingMoments: Boolean = false,
    onMomentTap: (Moment) -> Unit = {},
) {
    if (cluster == null) return
    LocationBottomSheet(
        moments = cluster.moments,
        momentAvailability = momentAvailability,
        isLoadingMoments = isLoadingMoments,
        locationName = cluster.displayName,
        onMomentTap = onMomentTap,
        modifier = modifier,
    )
}

/** ≡ iOS `LocationBottomSheet`. */
@Composable
fun LocationBottomSheet(
    moments: List<Moment>,
    locationName: String,
    onMomentTap: (Moment) -> Unit,
    modifier: Modifier = Modifier,
    momentAvailability: Map<String, Boolean> = emptyMap(),
    isLoadingMoments: Boolean = false,
) {
    val colors = rememberAdaptiveColors()
    var viewMode by remember { mutableStateOf(LocationBottomSheetViewMode.Gallery) }
    val uniqueContributors = remember(moments) {
        val seen = linkedSetOf<String>()
        moments.filter { seen.add(it.authorId) }
    }
    val statsText = stringResource(
        R.string.maps_bottom_sheet_stats,
        moments.size,
        uniqueContributors.size,
    )

    Column(modifier.fillMaxWidth()) {
        LocationBottomSheetHeader(
            locationName = locationName,
            statsText = statsText,
            uniqueContributors = uniqueContributors,
            momentsEmpty = moments.isEmpty(),
            showSeparator = moments.isNotEmpty() && !isLoadingMoments,
            viewMode = viewMode,
            onViewModeChange = { viewMode = it },
        )

        when {
            isLoadingMoments -> LocationBottomSheetLoading()
            moments.isEmpty() -> LocationBottomSheetEmpty()
            viewMode == LocationBottomSheetViewMode.Gallery -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .height(minOf(520, ((moments.size + 2) / 3) * 118).dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    contentPadding = PaddingValues(start = 2.dp, top = 0.dp, end = 2.dp, bottom = 30.dp),
                ) {
                    gridItems(moments, key = { it.id ?: it.mapAvailabilityKey }) { moment ->
                        val available = momentAvailability[moment.mapAvailabilityKey] ?: true
                        MapBottomSheetGridCell(
                            moment = moment,
                            isAvailable = available,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onMomentTap(moment) },
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .height(minOf(480, moments.size * 220 + 40).dp),
                    contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(moments, key = { it.id ?: it.mapAvailabilityKey }) { moment ->
                        ModernLocationMomentRow(
                            moment = moment,
                            isAvailable = momentAvailability[moment.mapAvailabilityKey] ?: true,
                            onTap = onMomentTap,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationBottomSheetHeader(
    locationName: String,
    statsText: String,
    uniqueContributors: List<Moment>,
    momentsEmpty: Boolean,
    showSeparator: Boolean,
    viewMode: LocationBottomSheetViewMode,
    onViewModeChange: (LocationBottomSheetViewMode) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Column {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .momentsChromeGlass(CircleShape, interactive = false),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    locationName,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!momentsEmpty) {
                    ViewModeToggle(viewMode = viewMode, onChange = onViewModeChange)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(statsText, color = colors.secondary, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                if (uniqueContributors.isNotEmpty()) {
                    ContributorAvatarStack(uniqueContributors)
                }
            }
        }
        if (showSeparator) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(0.5.dp)
                    .background(Brush.horizontalGradient(colors.overlayStroke)),
            )
        }
    }
}

@Composable
private fun ViewModeToggle(
    viewMode: LocationBottomSheetViewMode,
    onChange: (LocationBottomSheetViewMode) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier
            .shadow(4.dp, RoundedCornerShape(14.dp), clip = false)
            .momentsChromeGlass(RoundedCornerShape(14.dp), interactive = false)
            .border(1.dp, Brush.linearGradient(colors.overlayStroke), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LocationBottomSheetViewMode.entries.forEach { mode ->
            val selected = viewMode == mode
            val bg by animateColorAsState(
                if (selected) colors.accent else Color.Transparent,
                label = "viewModeBg",
            )
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onChange(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (mode == LocationBottomSheetViewMode.Gallery) {
                        Icons.Filled.GridView
                    } else {
                        Icons.Filled.List
                    },
                    contentDescription = null,
                    tint = if (selected) Color.White else colors.tertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ContributorAvatarStack(contributors: List<Moment>) {
    val colors = rememberAdaptiveColors()
    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp), verticalAlignment = Alignment.CenterVertically) {
        contributors.take(3).forEach { moment ->
            Box {
                StoryRingAvatarView(userId = moment.authorId, size = 30.dp, lineWidth = 1.5.dp)
                Box(
                    Modifier
                        .matchParentSize()
                        .border(2.dp, colors.background.copy(alpha = 0.9f), CircleShape),
                )
            }
        }
        if (contributors.size > 3) {
            Box(
                Modifier
                    .size(30.dp)
                    .momentsChromeGlass(CircleShape, interactive = false)
                    .border(2.dp, colors.background.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+${contributors.size - 3}",
                    color = colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LocationBottomSheetLoading() {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(top = 16.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .size(80.dp)
                .momentsChromeGlass(CircleShape, interactive = false)
                .border(2.dp, Brush.linearGradient(colors.buttonStroke), CircleShape)
                .shadow(8.dp, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.maps_bottom_sheet_loading_moments),
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Text(
                stringResource(R.string.maps_bottom_sheet_loading_filtering),
                color = colors.secondary,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun LocationBottomSheetEmpty() {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .size(100.dp)
                .momentsChromeGlass(CircleShape, interactive = false)
                .border(2.dp, Brush.linearGradient(colors.overlayStroke), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CameraAlt, null, tint = colors.accent, modifier = Modifier.size(50.dp))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.maps_bottom_sheet_empty_title),
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.maps_bottom_sheet_empty_subtitle),
                color = colors.secondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/** ≡ iOS `MapBottomSheetGridCell`. */
@Composable
fun MapBottomSheetGridCell(
    moment: Moment,
    isAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val hasMultipleMedia = (moment.mediaItems?.size ?: 0) > 1
    Box(
        modifier
            .then(if (isAvailable) Modifier else Modifier.blur(14.dp))
            .background(Color.Gray.copy(alpha = 0.12f)),
    ) {
        if (moment.mapHasVideoMedia) {
            MapsVideoThumbnailView(moment = moment, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
        } else {
            SubcomposeAsyncImage(
                model = moment.mapPreferredImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = colors.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
        if (hasMultipleMedia) {
            Icon(
                Icons.Filled.Collections,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(14.dp),
            )
        }
        if (!isAvailable) {
            MomentUnavailableOverlay(compact = true, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
        }
    }
}

/** ≡ iOS `MapsVideoThumbnailView`. */
@Composable
fun MapsVideoThumbnailView(
    moment: Moment,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
) {
    val colors = rememberAdaptiveColors()
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.Gray.copy(alpha = 0.15f)),
    ) {
        SubcomposeAsyncImage(
            model = moment.mapPreferredVideoThumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                }
            },
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        Icon(
            Icons.Filled.PlayCircle,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.3f)
                .aspectRatio(1f),
        )
        moment.videoDuration?.let { duration ->
            Text(
                formatVideoDuration(duration),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** ≡ iOS `ModernLocationMomentRow`. */
@Composable
fun ModernLocationMomentRow(
    moment: Moment,
    isAvailable: Boolean,
    onTap: (Moment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Column(
        modifier
            .fillMaxWidth()
            .then(if (isAvailable) Modifier else Modifier.blur(16.dp))
            .shadow(10.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (colors.isDark) 0.08f else 0.35f))
            .border(1.dp, Brush.linearGradient(colors.overlayStroke), RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onTap(moment) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(18.dp)),
        ) {
            if (moment.mapHasVideoMedia) {
                MapsVideoThumbnailView(moment = moment, cornerRadius = 18.dp, modifier = Modifier.fillMaxSize())
            } else {
                SubcomposeAsyncImage(
                    model = moment.mapPreferredImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
                        }
                    },
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))),
                    ),
            )
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StoryRingAvatarView(userId = moment.authorId, size = 32.dp, lineWidth = 2.2.dp)
                Column {
                    LiveUsernameText(
                        userId = moment.authorId,
                        fallbackUsername = moment.username,
                        prefix = "@",
                        color = Color.White,
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                    )
                    Text(
                        MomentsFormat.relativeTime(from = moment.timestamp),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                    )
                }
            }
            if (!isAvailable) {
                MomentUnavailableOverlay(compact = false, cornerRadius = 18.dp, modifier = Modifier.fillMaxSize())
            }
        }
        if (moment.content.isNotBlank()) {
            Text(
                moment.content,
                color = colors.primary.copy(alpha = 0.9f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/** ≡ iOS `MomentUnavailableOverlay`. */
@Composable
fun MomentUnavailableOverlay(
    compact: Boolean,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        ) {
            Icon(
                Icons.Filled.VisibilityOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(if (compact) 18.dp else 24.dp),
            )
            Text(
                stringResource(R.string.echo_viewer_unavailable),
                color = Color.White,
                fontSize = if (compact) 10.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 2 else Int.MAX_VALUE,
                modifier = Modifier.padding(horizontal = if (compact) 8.dp else 18.dp),
            )
        }
    }
}

private fun formatVideoDuration(duration: Double): String {
    val total = duration.toInt().coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}
