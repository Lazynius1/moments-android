package com.moments.android.views.story

import android.location.Geocoder
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.moments.android.R
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.feed.maps.FeedMaps
import com.moments.android.views.profile.highlights.HighlightStoryDateBadge
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.story.storyviewer.GlassmorphicEmptyState
import com.moments.android.views.story.storyviewer.StoryViewerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** ≡ `ArchiveView.ArchiveDisplayMode`. */
enum class ArchiveDisplayMode { STORIES, CALENDAR, MAP }

/**
 * Port de `ArchiveView` (`archived stories.swift`).
 * Grid + square card + day viewer + activity sheet + calendar + map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedStoriesView(
    onNavigateBack: () -> Unit = {},
    /** ≡ iOS `ArchiveView(embedInNavigation:false, showsCustomDismiss:false)` — chrome lo aporta el host. */
    showTopBar: Boolean = true,
    viewModel: ArchiveViewModel = viewModel(),
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)

    var displayMode by remember { mutableStateOf(ArchiveDisplayMode.STORIES) }
    val storiesForGrid = viewModel.storiesForGrid
    // ≡ StoryViewerPresentation / StoryStatsPresentation
    var viewerStories by remember { mutableStateOf<List<Story>?>(null) }
    var viewerInitialIndex by remember { mutableIntStateOf(0) }
    var statsStory by remember { mutableStateOf<Story?>(null) }
    val storyViewModel: StoryViewModel = viewModel()

    LaunchedEffect(Unit) {
        viewModel.loadArchivedStories()
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.archived_stories_header_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = textColor,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor),
                )
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                viewModel.isLoading -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = textColor, modifier = Modifier.padding(top = 120.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.archived_stories_loading),
                            color = Color.Gray,
                            fontSize = 16.sp,
                        )
                    }
                }

                viewModel.groupedStories.isEmpty() -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Filled.Archive,
                            contentDescription = null,
                            tint = secondaryColor,
                            modifier = Modifier.size(60.dp),
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            stringResource(R.string.archived_stories_empty_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.archived_stories_empty_description),
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                else -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(textColor.copy(alpha = 0.08f))
                                    .padding(3.dp),
                            ) {
                                val modes = listOf(
                                    ArchiveDisplayMode.STORIES to Icons.Filled.GridView,
                                    ArchiveDisplayMode.CALENDAR to Icons.Filled.CalendarMonth,
                                    ArchiveDisplayMode.MAP to Icons.Filled.Map,
                                )
                                modes.forEach { (mode, icon) ->
                                    val selected = displayMode == mode
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(if (selected) Color(0xFF007AFF) else Color.Transparent)
                                            .clickable { displayMode = mode }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = null,
                                            tint = if (selected) Color.White else secondaryColor,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }

                        when (displayMode) {
                            ArchiveDisplayMode.STORIES -> {
                                if (storiesForGrid.isEmpty()) {
                                    Text(
                                        stringResource(R.string.archived_stories_empty_description),
                                        color = Color.Gray,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(3),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 20.dp, top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                                        verticalArrangement = Arrangement.spacedBy(1.dp),
                                    ) {
                                        items(storiesForGrid, key = { it.id ?: it.hashCode().toString() }) { story ->
                                            ArchiveStorySquareCard(
                                                story = story,
                                                onTap = {
                                                    viewerInitialIndex = 0
                                                    viewerStories = listOf(story)
                                                },
                                                onStatsTap = { statsStory = story },
                                            )
                                        }
                                    }
                                }
                            }
                            ArchiveDisplayMode.CALENDAR -> {
                                ArchiveCalendarView(
                                    allStories = storiesForGrid,
                                    textColor = textColor,
                                    onOpenDay = { dayStories ->
                                        if (dayStories.isNotEmpty()) {
                                            viewerInitialIndex = 0
                                            viewerStories = dayStories
                                        }
                                    },
                                )
                            }
                            ArchiveDisplayMode.MAP -> {
                                ArchiveMapView(
                                    allStories = storiesForGrid,
                                    onOpenPin = { pinStories ->
                                        if (pinStories.isNotEmpty()) {
                                            viewerInitialIndex = 0
                                            viewerStories = pinStories
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ≡ fullScreenCover ArchiveDayStoriesViewer
    viewerStories?.let { stories ->
        Dialog(
            onDismissRequest = { viewerStories = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ArchiveDayStoriesViewer(
                stories = stories,
                initialIndex = viewerInitialIndex,
                storyViewModel = storyViewModel,
                onDismiss = { viewerStories = null },
            )
        }
    }

    // ≡ .sheet StoryStatsView (medium+large)
    statsStory?.let { story ->
        MomentsModalSheet(
            onDismissRequest = { statsStory = null },
            largeOnly = false,
        ) {
            StoryStatsView(
                story = story,
                onDismiss = { statsStory = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Port de `ArchiveStoryCardVisual`. */
@Composable
fun ArchiveStoryCardVisual(
    story: Story,
    cornerRadius: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val preview = story.mediaItem.thumbnailUrl ?: story.mediaItem.url
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.Gray.copy(0.26f)),
    ) {
        if (preview.isNotBlank()) {
            AsyncImage(
                model = preview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.Photo,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        HighlightStoryDateBadge(
            date = story.timestamp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(7.dp),
        )
        if (story.mediaItem.type == MediaItem.MediaType.VIDEO && story.duration > 0) {
            Text(
                text = archiveFormatVideoDuration(story.duration),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.45f),
                        offset = Offset(0f, 1f),
                        blurRadius = 2f,
                    ),
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchiveStorySquareCard(
    story: Story,
    onTap: () -> Unit,
    onStatsTap: () -> Unit,
) {
    // ≡ contextMenu "Ver actividad"
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = { menuExpanded = true },
            ),
    ) {
        ArchiveStoryCardVisual(story = story, cornerRadius = 0.dp)
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.archived_stories_view_activity)) },
                onClick = {
                    menuExpanded = false
                    onStatsTap()
                },
            )
        }
    }
}

/** Port de `archiveMapView`. */
@Composable
private fun ArchiveMapView(
    allStories: List<Story>,
    onOpenPin: (List<Story>) -> Unit,
) {
    val context = LocalContext.current
    var geocodedByStoryId by remember { mutableStateOf<Map<String, LatLng>>(emptyMap()) }
    var isResolving by remember { mutableStateOf(false) }

    LaunchedEffect(allStories) {
        if (isResolving) return@LaunchedEffect
        val candidates = allStories.mapNotNull { story ->
            if (archiveStoryCoordinateFromStickers(story) != null) return@mapNotNull null
            val storyId = story.id ?: return@mapNotNull null
            if (geocodedByStoryId.containsKey(storyId)) return@mapNotNull null
            val locationName = archiveFirstLocationName(story) ?: return@mapNotNull null
            storyId to locationName
        }.take(30)
        if (candidates.isEmpty()) return@LaunchedEffect
        isResolving = true
        val resolved = geocodedByStoryId.toMutableMap()
        for ((storyId, locationName) in candidates) {
            archiveGeocode(context, locationName)?.let { resolved[storyId] = it }
        }
        geocodedByStoryId = resolved
        isResolving = false
    }

    val pins = remember(allStories, geocodedByStoryId) {
        archiveMapPins(allStories, geocodedByStoryId)
    }
    val hasKey = FeedMaps.hasGoogleMapsKey()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 1.5f)
    }

    LaunchedEffect(pins) {
        if (pins.isEmpty()) return@LaunchedEffect
        if (pins.size == 1) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(pins.first().coordinate, 12f),
            )
            return@LaunchedEffect
        }
        val builder = LatLngBounds.builder()
        pins.forEach { builder.include(it.coordinate) }
        runCatching {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 80))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        if (!hasKey) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Gray.copy(0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Place, null, tint = Color(0xFF0A84FF), modifier = Modifier.size(34.dp))
            }
        } else {
            GoogleMap(
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    compassEnabled = false,
                    mapToolbarEnabled = false,
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = false,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                pins.forEach { pin ->
                    key(pin.id) {
                        val markerState = rememberMarkerState(position = pin.coordinate)
                        MarkerComposable(
                            keys = arrayOf<Any>(pin.id, pin.stories.size),
                            state = markerState,
                            onClick = {
                                onOpenPin(pin.stories)
                                true
                            },
                        ) {
                            ArchiveMapPinAnnotation(pin = pin)
                        }
                    }
                }
            }
        }

        if (pins.isEmpty()) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(0.35f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.LocationOff,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.archived_stories_map_empty),
                    color = Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ArchiveMapPinAnnotation(pin: ArchiveStoryPin) {
    val preview = pin.stories.firstOrNull()?.let { archiveCalendarPreviewURL(it) }.orEmpty()
    Box {
        if (preview.isNotBlank()) {
            AsyncImage(
                model = preview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(2.dp, Color.White.copy(0.95f), RoundedCornerShape(10.dp)),
            )
        } else {
            Icon(
                Icons.Filled.Place,
                contentDescription = null,
                tint = Color(0xFF0A84FF),
                modifier = Modifier.size(34.dp),
            )
        }
        if (pin.stories.size > 1) {
            Text(
                "${pin.stories.size}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF0A84FF))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

/** Port de `archiveCalendarView`. */
@Composable
private fun ArchiveCalendarView(
    allStories: List<Story>,
    textColor: Color,
    onOpenDay: (List<Story>) -> Unit,
) {
    val monthSections = remember(allStories) { archiveCalendarMonthSections(allStories) }
    val weekdaySymbols = remember { archiveWeekdaySymbols() }
    val sectionPad = 12.dp

    if (monthSections.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp),
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = Color.Gray.copy(0.75f),
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.archived_stories_calendar_empty),
                    color = Color.Gray,
                    fontSize = 14.sp,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            lazyItems(monthSections, key = { it.id }) { monthSection ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        MomentsFormat.smartDate(
                            from = monthSection.monthStart,
                            context = MomentsFormat.DateContext.MONTH_YEAR_LABEL,
                        ),
                        color = textColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = sectionPad),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = sectionPad),
                    ) {
                        weekdaySymbols.forEach { symbol ->
                            Text(
                                symbol,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val cells = remember(monthSection) { archiveCalendarCells(monthSection) }
                    cells.chunked(7).forEach { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = sectionPad)
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            row.forEach { cell ->
                                val dayNumber = cell.dayNumber
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (dayNumber != null) {
                                        val bucket = cell.bucket
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .then(
                                                    if (bucket != null) {
                                                        Modifier.clickable { onOpenDay(bucket.stories) }
                                                    } else {
                                                        Modifier
                                                    },
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (bucket != null) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (bucket.thumbnailURL.isNotBlank()) {
                                                        AsyncImage(
                                                            model = bucket.thumbnailURL,
                                                            contentDescription = null,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier
                                                                .size(40.dp)
                                                                .clip(CircleShape),
                                                        )
                                                    } else {
                                                        Box(
                                                            Modifier
                                                                .size(40.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.Gray.copy(0.22f)),
                                                        )
                                                    }
                                                    Box(
                                                        Modifier
                                                            .size(40.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Black.copy(0.28f)),
                                                    )
                                                }
                                            }
                                            Text(
                                                "$dayNumber",
                                                color = if (bucket == null) textColor else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(7 - row.size) {
                                Spacer(Modifier.weight(1f).height(42.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Port de `ArchiveDayStoriesViewer`.
 */
@Composable
fun ArchiveDayStoriesViewer(
    stories: List<Story>,
    initialIndex: Int = 0,
    storyViewModel: StoryViewModel = viewModel(),
    onDismiss: () -> Unit,
) {
    var currentIndex by remember(stories) {
        mutableIntStateOf(initialIndex.coerceIn(0, (stories.size - 1).coerceAtLeast(0)))
    }
    val story = stories.getOrNull(currentIndex)

    LaunchedEffect(stories) {
        GlobalVideoManager.pauseAllVideos()
        val authorId = stories.firstOrNull()?.authorId
        if (!authorId.isNullOrBlank()) {
            storyViewModel.hydrateStoriesForAuthor(authorId, stories)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (story != null) {
            StoryViewerScreen(
                story = story,
                segmentCount = stories.size,
                segmentIndex = currentIndex,
                onNext = {
                    if (currentIndex < stories.lastIndex) currentIndex += 1 else onDismiss()
                },
                onPrevious = {
                    if (currentIndex > 0) currentIndex -= 1 else onDismiss()
                },
                onDismiss = onDismiss,
                storyViewModel = storyViewModel,
                viewers = storyViewModel.storyViewers[story.id.orEmpty()].orEmpty(),
                reactions = storyViewModel.storyReactions[story.id.orEmpty()].orEmpty(),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassmorphicEmptyState(
                    icon = Icons.Filled.Warning,
                    message = stringResource(R.string.stories_error_loading_story),
                    showCloseButton = true,
                    onClose = onDismiss,
                )
            }
        }
    }
}

// MARK: - Map helpers (≡ ArchiveStoryPin / storyCoordinate / geocode)

private data class ArchiveStoryPin(
    val id: String,
    val coordinate: LatLng,
    val stories: List<Story>,
)

private fun archiveStoryCoordinateFromStickers(story: Story): LatLng? {
    val stickers = story.stickers ?: return null
    for (sticker in stickers) {
        val lat = sticker.latitude
        val lon = sticker.longitude
        if (lat != null && lon != null) return LatLng(lat, lon)
    }
    return null
}

private fun archiveFirstLocationName(story: Story): String? {
    val stickers = story.stickers ?: return null
    return stickers
        .mapNotNull { it.location?.trim() }
        .firstOrNull { it.isNotEmpty() }
}

private fun archiveStoryCoordinate(
    story: Story,
    geocodedByStoryId: Map<String, LatLng>,
): LatLng? {
    archiveStoryCoordinateFromStickers(story)?.let { return it }
    val storyId = story.id ?: return null
    return geocodedByStoryId[storyId]
}

private fun archiveMapPins(
    allStories: List<Story>,
    geocodedByStoryId: Map<String, LatLng>,
): List<ArchiveStoryPin> {
    val grouped = linkedMapOf<String, Pair<LatLng, MutableList<Story>>>()
    for (story in allStories) {
        val coordinate = archiveStoryCoordinate(story, geocodedByStoryId) ?: continue
        val key = "${(coordinate.latitude * 1000).roundToInt() / 1000.0}|${(coordinate.longitude * 1000).roundToInt() / 1000.0}"
        val existing = grouped[key]
        if (existing == null) {
            grouped[key] = coordinate to mutableListOf(story)
        } else {
            existing.second.add(story)
        }
    }
    return grouped.map { (key, value) ->
        ArchiveStoryPin(
            id = key,
            coordinate = value.first,
            stories = value.second.sortedByDescending { it.timestamp.time },
        )
    }
}

@Suppress("DEPRECATION")
private suspend fun archiveGeocode(context: android.content.Context, locationName: String): LatLng? {
    if (!Geocoder.isPresent()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            Geocoder(context, Locale.getDefault())
                .getFromLocationName(locationName, 1)
                ?.firstOrNull()
                ?.let { LatLng(it.latitude, it.longitude) }
        }.getOrNull()
    }
}

// MARK: - Calendar helpers (≡ ArchiveCalendar*)

private data class ArchiveCalendarDayBucket(
    val dayKey: String,
    val date: Date,
    val stories: List<Story>,
    val thumbnailURL: String,
) {
    val id: String get() = dayKey
}

private data class ArchiveCalendarMonthSection(
    val monthStart: Date,
    val days: List<ArchiveCalendarDayBucket>,
) {
    val id: String
        get() {
            val cal = Calendar.getInstance()
            cal.time = monthStart
            return "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }
}

private data class ArchiveCalendarDayCell(
    val dayNumber: Int?,
    val bucket: ArchiveCalendarDayBucket?,
)

private fun archiveCalendarPreviewURL(story: Story): String {
    return if (story.mediaItem.type == MediaItem.MediaType.VIDEO) {
        story.mediaItem.thumbnailUrl ?: story.mediaItem.url
    } else {
        story.mediaItem.url
    }
}

private fun archiveCalendarDayBuckets(allStories: List<Story>): List<ArchiveCalendarDayBucket> {
    val cal = Calendar.getInstance()
    val grouped = linkedMapOf<Long, MutableList<Story>>()
    for (story in allStories) {
        cal.time = story.timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        grouped.getOrPut(dayStart) { mutableListOf() }.add(story)
    }
    return grouped.map { (dayStart, stories) ->
        val sorted = stories.sortedByDescending { it.timestamp.time }
        val date = Date(dayStart)
        cal.time = date
        val dayKey = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
        ArchiveCalendarDayBucket(
            dayKey = dayKey,
            date = date,
            stories = sorted,
            thumbnailURL = archiveCalendarPreviewURL(sorted.first()),
        )
    }.sortedBy { it.date.time }
}

private fun archiveCalendarMonthSections(allStories: List<Story>): List<ArchiveCalendarMonthSection> {
    val cal = Calendar.getInstance()
    val grouped = linkedMapOf<Long, MutableList<ArchiveCalendarDayBucket>>()
    for (day in archiveCalendarDayBuckets(allStories)) {
        cal.time = day.date
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.timeInMillis
        grouped.getOrPut(monthStart) { mutableListOf() }.add(day)
    }
    return grouped.map { (monthStart, days) ->
        ArchiveCalendarMonthSection(
            monthStart = Date(monthStart),
            days = days.sortedBy { it.date.time },
        )
    }.sortedBy { it.monthStart.time }
}

private fun archiveWeekdaySymbols(): List<String> {
    val cal = Calendar.getInstance()
    val locale = java.util.Locale.getDefault()
    // Índices 0..6 ≡ Calendar.SUNDAY..SATURDAY
    val symbols = (Calendar.SUNDAY..Calendar.SATURDAY).map { day ->
        cal.set(Calendar.DAY_OF_WEEK, day)
        cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.NARROW_STANDALONE, locale)
            ?: cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale)
            ?: ""
    }
    val firstWeekdayIndex = (cal.firstDayOfWeek - Calendar.SUNDAY).coerceIn(0, 6)
    return symbols.drop(firstWeekdayIndex) + symbols.take(firstWeekdayIndex)
}

private fun archiveCalendarCells(monthSection: ArchiveCalendarMonthSection): List<ArchiveCalendarDayCell> {
    val cal = Calendar.getInstance()
    cal.time = monthSection.monthStart
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstWeekday = cal.get(Calendar.DAY_OF_WEEK)
    val leadingBlanks = (firstWeekday - cal.firstDayOfWeek + 7) % 7

    val bucketsByDay = monthSection.days.associateBy {
        cal.time = it.date
        cal.get(Calendar.DAY_OF_MONTH)
    }

    val cells = mutableListOf<ArchiveCalendarDayCell>()
    repeat(leadingBlanks) { cells.add(ArchiveCalendarDayCell(dayNumber = null, bucket = null)) }
    for (day in 1..daysInMonth) {
        cells.add(ArchiveCalendarDayCell(dayNumber = day, bucket = bucketsByDay[day]))
    }
    while (cells.size % 7 != 0) {
        cells.add(ArchiveCalendarDayCell(dayNumber = null, bucket = null))
    }
    return cells
}

private fun archiveFormatVideoDuration(duration: Double): String {
    val total = duration.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
