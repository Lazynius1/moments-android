package com.moments.android.views.feed.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.moments.android.views.creator.creatoruikit.feedCropTransformations
import com.moments.android.views.feed.maps.mapPreferredFeedCrop
import com.moments.android.views.feed.maps.mapPreferredImageUrl
import com.moments.android.R
import androidx.compose.material3.MaterialTheme
import com.moments.android.views.components.LocationMomentCardSkeletonView
import com.moments.android.views.feed.maps.mapssections.MapBottomSheetGridCell
import com.moments.android.views.feed.maps.mapssections.ModernLocationMomentRow
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.story.StoryRingAvatarView
import com.moments.android.views.components.LiveUsernameText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class MapPlaceSheetViewMode { Gallery, List }

/**
 * Port de `MapPlaceBottomSheet.swift`.
 */
@Composable
fun MapPlaceBottomSheet(
    cluster: MapPlaceCluster,
    momentAvailability: Map<String, Boolean> = emptyMap(),
    isLoading: Boolean = false,
    onMomentTap: (String) -> Unit = {},
    onPlaceStoriesTap: (MapPlaceCluster) -> Unit = {},
    weather: WeatherData? = null,
    userLatitude: Double? = null,
    userLongitude: Double? = null,
    placeIndex: List<MapPlaceCluster> = emptyList(),
    onPlaceTap: ((MapPlaceCluster) -> Unit)? = null,
    /** null ≡ iOS Binding opcional — no muestra chips (LocationMap). */
    timeFilter: MapDiscoverTimeFilter? = null,
    onTimeFilterChange: ((MapDiscoverTimeFilter) -> Unit)? = null,
    socialMode: Boolean = false,
    showsHeader: Boolean = true,
    showsStoryStrip: Boolean = true,
    contentHeight: androidx.compose.ui.unit.Dp = 520.dp,
    hasMoreContent: Boolean = false,
    paginationKey: String = "",
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = rememberAdaptiveColors()
    val showsPlaceIndex = !socialMode && cluster.isAggregate && placeIndex.size > 1 && onPlaceTap != null
    var viewMode by remember(cluster.id, showsPlaceIndex, socialMode) {
        mutableStateOf(if (socialMode || showsPlaceIndex) MapPlaceSheetViewMode.List else MapPlaceSheetViewMode.Gallery)
    }
    var displayTitle by remember(cluster.id, cluster.displayName) { mutableStateOf(cluster.displayName) }

    LaunchedEffect(cluster.id, cluster.displayName, cluster.latitude, cluster.longitude) {
        displayTitle = cluster.displayName
        displayTitle = withContext(Dispatchers.IO) {
            MapLocationDisplayFormatter.resolveTitle(
                context = context,
                place = cluster.displayName,
                latitude = cluster.latitude,
                longitude = cluster.longitude,
            )
        }
    }

    val statsText = when {
        showsPlaceIndex -> stringResource(
            R.string.maps_zone_sheet_stats,
            placeIndex.size,
            cluster.momentCount,
        )
        cluster.storyCount > 0 -> stringResource(
            R.string.maps_place_sheet_stats,
            cluster.momentCount,
            cluster.storyCount,
        )
        else -> stringResource(R.string.maps_bottom_sheet_moments, cluster.momentCount)
    }
    val hasContent = cluster.moments.isNotEmpty() || cluster.stories.isNotEmpty()

    Column(modifier.fillMaxWidth()) {
        MapPlaceSheetHeader(
            cluster = cluster,
            displayTitle = displayTitle.ifBlank { cluster.displayName },
            statsText = statsText,
            weather = weather,
            viewMode = viewMode,
            onViewModeChange = { viewMode = it },
            onPlaceStoriesTap = onPlaceStoriesTap,
            showsStoryPreview = !socialMode,
            showsTitle = showsHeader,
        )

        if (timeFilter != null) {
            MapPlaceTimeFilterChips(
                selected = timeFilter,
                onSelect = { onTimeFilterChange?.invoke(it) },
            )
        }

        if (showsStoryStrip && socialMode && !isLoading && cluster.stories.isNotEmpty()) {
            val authors = cluster.stories.sortedByDescending { it.timestamp.time }.distinctBy { it.authorId }
            LazyRow(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(authors, key = { it.authorId }) { story ->
                    Column(
                        Modifier.width(80.dp).clickable {
                            onPlaceStoriesTap(cluster.copy(
                                id = "${cluster.id}-${story.authorId}",
                                stories = cluster.stories.filter { it.authorId == story.authorId },
                                moments = emptyList(),
                                friends = emptyList(),
                            ))
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StoryRingAvatarView(userId = story.authorId, size = 48.dp, lineWidth = 2.dp)
                        LiveUsernameText(userId = story.authorId, fallbackUsername = story.username, prefix = "@",
                            color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        when {
            isLoading -> {
                LocationMomentCardSkeletonView(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(220.dp),
                )
            }
            !hasContent -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.maps_bottom_sheet_empty_title),
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.maps_bottom_sheet_empty_subtitle),
                        color = colors.secondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                when {
                    showsPlaceIndex && viewMode == MapPlaceSheetViewMode.List -> {
                        LazyColumn(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = (contentHeight - 44.dp).coerceAtLeast(40.dp)),
                            contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 30.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item {
                                Text(
                                    stringResource(R.string.maps_zone_sheet_places),
                                    color = colors.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                            items(placeIndex, key = { it.id }) { place ->
                                MapPlaceIndexRow(
                                    place = place,
                                    userLatitude = userLatitude,
                                    userLongitude = userLongitude,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { onPlaceTap?.invoke(place) },
                                )
                            }
                            if (hasMoreContent) {
                                item(key = "map-pagination") { MapPaginationFooter(paginationKey, isLoadingMore, onLoadMore) }
                            }
                        }
                    }
                    cluster.moments.isNotEmpty() && viewMode == MapPlaceSheetViewMode.Gallery -> {
                        // Altura fija al max scrollable: no re-measure mid-fling si cambia el count.
                        val galleryRows = ((cluster.moments.size + 2) / 3).coerceAtLeast(1)
                        val galleryHeight = minOf((contentHeight - 44.dp).coerceAtLeast(40.dp), (galleryRows * 118 + if (hasMoreContent) 64 else 0).dp)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(galleryHeight),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            contentPadding = PaddingValues(start = 2.dp, top = 0.dp, end = 2.dp, bottom = 30.dp),
                        ) {
                            itemsIndexed(
                                cluster.moments,
                                key = { index, m -> m.id?.takeIf { it.isNotEmpty() } ?: "map-place-$index-${m.mapAvailabilityKey}" },
                            ) { _, moment ->
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
                                        ) {
                                            onMomentTap(moment.id ?: moment.mapAvailabilityKey)
                                        },
                                )
                            }
                            if (hasMoreContent) {
                                item(key = "map-pagination", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) { MapPaginationFooter(paginationKey, isLoadingMore, onLoadMore) }
                            }
                        }
                    }
                    cluster.moments.isNotEmpty() -> {
                        val listHeight = minOf((contentHeight - 44.dp).coerceAtLeast(40.dp), (cluster.moments.size * 220 + 40 + if (hasMoreContent) 64 else 0).dp)
                        LazyColumn(
                            Modifier
                                .fillMaxWidth()
                                .height(listHeight),
                            contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 30.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            itemsIndexed(
                                cluster.moments.sortedByDescending { it.timestamp.time },
                                key = { index, m -> m.id?.takeIf { it.isNotEmpty() } ?: "map-place-row-$index-${m.mapAvailabilityKey}" },
                            ) { _, moment ->
                                ModernLocationMomentRow(
                                    moment = moment,
                                    isAvailable = momentAvailability[moment.mapAvailabilityKey] ?: true,
                                    onTap = { onMomentTap(it.id ?: it.mapAvailabilityKey) },
                                )
                            }
                            if (hasMoreContent) {
                                item(key = "map-pagination") { MapPaginationFooter(paginationKey, isLoadingMore, onLoadMore) }
                            }
                        }
                    }
                }
            }
        }
            if (hasMoreContent && cluster.moments.isEmpty() && !showsPlaceIndex && !isLoading) {
            MapPaginationFooter(paginationKey, isLoadingMore, onLoadMore)
        }
    }

}

@Composable
private fun MapPlaceSheetHeader(
    cluster: MapPlaceCluster,
    displayTitle: String,
    statsText: String,
    weather: WeatherData?,
    viewMode: MapPlaceSheetViewMode,
    onViewModeChange: (MapPlaceSheetViewMode) -> Unit,
    onPlaceStoriesTap: (MapPlaceCluster) -> Unit,
    showsStoryPreview: Boolean,
    showsTitle: Boolean = true,
) {
    val colors = rememberAdaptiveColors()
    Column {
        if (showsTitle) Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            cluster.primaryStory?.takeIf { showsStoryPreview }?.let { story ->
                Box(
                    Modifier
                        .padding(bottom = 4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onPlaceStoriesTap(cluster) },
                ) {
                    StoryRingAvatarView(
                        userId = story.authorId,
                        size = 44.dp,
                        lineWidth = 2.5.dp,
                    )
                    if (cluster.storyCount > 1) {
                        Text(
                            "${cluster.storyCount}",
                            color = colors.surfaceBackground,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .background(colors.primary, CircleShape)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                displayTitle,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
            Text(
                statsText,
                color = colors.secondary,
                fontSize = 13.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            weather?.let { MapPlaceWeatherChip(it) }
            if (cluster.friends.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    cluster.friends.take(3).forEach { friend ->
                        StoryRingAvatarView(
                            userId = friend.authorId,
                            size = 30.dp,
                            lineWidth = 1.5.dp,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (cluster.moments.isNotEmpty()) {
                MapPlaceViewModeToggle(viewMode = viewMode, onChange = onViewModeChange)
            }
        }
    }
}

@Composable
private fun MapPlaceWeatherChip(weather: WeatherData) {
    val colors = rememberAdaptiveColors()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(percent = 50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            weather.condition.icon(),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(11.dp),
        )
        Text(
            weather.temperatureFormatted,
            color = colors.secondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
        )
    }
        Text("OpenWeather", color = colors.secondary, fontSize = 10.sp,
            modifier = Modifier.clickable { uriHandler.openUri("https://openweathermap.org/") })
    }

}

@Composable
private fun MapPlaceViewModeToggle(
    viewMode: MapPlaceSheetViewMode,
    onChange: (MapPlaceSheetViewMode) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MapPlaceSheetViewMode.entries.forEach { mode ->
            val selected = viewMode == mode
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (selected) {
                            Modifier.background(
                                brush = Brush.linearGradient(
                                    listOf(colors.primary, colors.primary.copy(alpha = 0.8f)),
                                ),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onChange(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (mode == MapPlaceSheetViewMode.Gallery) Icons.Filled.GridView else Icons.Filled.List,
                    contentDescription = null,
                    tint = if (selected) colors.surfaceBackground else colors.tertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MapPlaceTimeFilterChips(
    selected: MapDiscoverTimeFilter,
    onSelect: (MapDiscoverTimeFilter) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MapDiscoverTimeFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            Text(
                stringResource(filter.titleKeyRes),
                color = if (isSelected) colors.surfaceBackground else colors.secondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                modifier = Modifier
                    .then(
                        if (isSelected) {
                            Modifier.background(colors.primary, CircleShape)
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(filter) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/** ≡ iOS `MapPlaceIndexRow`. */
@Composable
fun MapPlaceIndexRow(
    place: MapPlaceCluster,
    userLatitude: Double? = null,
    userLongitude: Double? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = rememberAdaptiveColors()
    val distance = remember(userLatitude, userLongitude, place.latitude, place.longitude) {
        MapDistanceFormatter.string(
            context, userLatitude, userLongitude, place.latitude, place.longitude,
        )
    }
    val metadata = buildList {
        distance?.let(::add)
        add(MapRelativeTimeFormatter.string(place.latestTimestamp))
        add(context.getString(R.string.maps_bottom_sheet_moments, place.momentCount))
    }.joinToString(" · ")

    Row(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    place.displayName,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (place.hasFreshStory) {
                    Box(Modifier.size(7.dp).background(colors.primary, CircleShape))
                }
            }
            Text(metadata, color = colors.secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (place.storyCount > 0) {
                Text(
                    stringResource(R.string.maps_zone_sheet_stories_count, place.storyCount),
                    color = colors.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            place.moments.take(3).forEach { moment ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(moment.mapPreferredImageUrl)
                        .transformations(feedCropTransformations(moment.mapPreferredFeedCrop))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Gray.copy(alpha = 0.18f)),
                )
            }
            if (place.momentCount > 3) {
                Box(
                    Modifier
                        .width(30.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Gray.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+${place.momentCount - 3}",
                        color = colors.secondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MapPaginationFooter(key: String, isLoading: Boolean, onLoadMore: () -> Unit) {
    LaunchedEffect(key) { onLoadMore() }
    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
        if (isLoading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = rememberAdaptiveColors().primary, strokeWidth = 2.dp)
        else androidx.compose.material3.TextButton(onClick = onLoadMore) {
            Text(stringResource(R.string.feed_see_more), color = rememberAdaptiveColors().primary)
        }
    }
}
