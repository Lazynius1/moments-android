package com.moments.android.views.feed.maps

import android.location.Geocoder
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.geojson.Point
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.plugin.gestures.generated.GesturesSettings
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.services.social.StoryRingResolverService
import com.moments.android.services.social.StoryRingSnapshot
import com.moments.android.utilities.HapticManager
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.feed.maps.mapssections.MapFilterChipsSection
import com.moments.android.views.permission.shared.LocationPermissionGate
import com.moments.android.views.permission.shared.LocationPermissionGateHost
import com.moments.android.views.profile.core.sections.MomentZoomDestination
import com.moments.android.views.profile.core.sections.MomentZoomDetailDestination
import com.moments.android.views.profile.core.sections.MomentZoomOpener
import com.moments.android.views.profile.core.sections.MomentZoomPresentationKind
import com.moments.android.views.story.StoryRingAvatarView
import com.moments.android.views.story.StorySegmentedRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * Port de `DiscoverMapView.swift` — mapa inline Discover (Explore).
 * Incluye al final del archivo `MapStoryPin` / `MapFriendActivityPinView` /
 * `MapPlacePin` / `MapMomentPin` (como iOS: pins en Discover + MapCanvasSection).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverMapView(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    zoneName: String? = null,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val colors = rememberAdaptiveColors()
    val primary = if (isDark) Color.White else FeedInk
    val secondary = primary.copy(alpha = 0.72f)
    val tertiary = primary.copy(alpha = 0.55f)
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var contentFilter by remember { mutableStateOf(MapDiscoverContentFilter.All) }
    var timeFilter by remember { mutableStateOf(MapDiscoverTimeFilter.All) }
    var moments by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var stories by remember { mutableStateOf<List<MapStoryPreview>>(emptyList()) }
    var friendPins by remember { mutableStateOf<List<MapFriendActivityPin>>(emptyList()) }
    var followingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasRecoverableError by remember { mutableStateOf(false) }
    var showingBottomSheet by remember { mutableStateOf(false) }
    var selectedPlaceCluster by remember { mutableStateOf<MapPlaceCluster?>(null) }
    var resolvedZoneName by remember { mutableStateOf(zoneName) }
    var discoverWeather by remember { mutableStateOf<WeatherData?>(null) }
    var weatherEffectsEnabled by remember { mutableStateOf(true) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var isViewActive by remember { mutableStateOf(true) }
    var focusNonce by remember { mutableIntStateOf(0) }
    var focusCenter by remember { mutableStateOf<Point?>(null) }
    var focusZoom by remember { mutableStateOf(MomentsMapStyle.DEFAULT_ZOOM) }
    var currentRegion by remember {
        mutableStateOf(MapRegionStore.initialRegion(context))
    }
    var regionSearchJob by remember { mutableStateOf<Job?>(null) }
    // Mapbox `subscribeMapIdle` también dispara al recrear las ViewAnnotations (iOS
    // `onMapCameraChange` no). Sin este guard: buscar → pins nuevos → idle → buscar…
    var lastSearchedRegionKey by remember { mutableStateOf("") }
    var zoomDestination by remember { mutableStateOf<MomentZoomDestination?>(null) }
    var zoomMapMomentsPool by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var resumeBottomSheetAfterDetail by remember { mutableStateOf(false) }
    var storyViewerPresentation by remember { mutableStateOf<MapStoryViewerPresentation?>(null) }
    var pendingStoryPresentation by remember { mutableStateOf<MapStoryViewerPresentation?>(null) }
    var isOpeningStory by remember { mutableStateOf(false) }
    val locationGate = remember { LocationPermissionGate() }

    val emptyMsg = stringResource(R.string.maps_discover_empty)
    val unavailableMsg = stringResource(R.string.maps_error_map_unavailable)
    val partialMsg = stringResource(R.string.maps_error_map_partial_content)
    val defaultTitle = stringResource(R.string.maps_discover_title)
    val defaultSubtitle = stringResource(R.string.maps_discover_subtitle)

    val filteredMoments = remember(moments, contentFilter, timeFilter, followingIds) {
        var result = when (contentFilter) {
            MapDiscoverContentFilter.All, MapDiscoverContentFilter.Places -> moments
            MapDiscoverContentFilter.Friends -> moments.filter { it.authorId in followingIds }
        }
        timeFilter.cutoffDate?.let { cutoff ->
            result = result.filter { !it.timestamp.before(cutoff) }
        }
        result
    }
    val filteredStories = remember(stories, contentFilter, timeFilter, followingIds) {
        var result = when (contentFilter) {
            MapDiscoverContentFilter.All -> stories
            MapDiscoverContentFilter.Friends -> stories.filter { it.authorId in followingIds }
            MapDiscoverContentFilter.Places -> emptyList()
        }
        timeFilter.cutoffDate?.let { cutoff ->
            result = result.filter { !it.timestamp.before(cutoff) }
        }
        result
    }

    val mapPlaceLayout = remember(filteredMoments, filteredStories, friendPins, contentFilter, currentRegion) {
        MapPlaceClusterEngine.build(
            moments = filteredMoments,
            stories = filteredStories,
            friendPins = friendPins,
            filter = contentFilter,
            centerLat = currentRegion.centerLat,
            centerLon = currentRegion.centerLon,
            latitudeDelta = currentRegion.latitudeDelta,
            longitudeDelta = currentRegion.longitudeDelta,
        )
    }

    val sheetCluster = selectedPlaceCluster ?: MapPlaceClusterEngine.aggregateRegionCluster(
        title = resolvedZoneName ?: defaultTitle,
        moments = filteredMoments,
        stories = filteredStories,
        latitude = currentRegion.centerLat,
        longitude = currentRegion.centerLon,
    )

    val title = resolvedZoneName?.takeIf { it.isNotBlank() } ?: defaultTitle
    val subtitle = if (mapPlaceLayout.placeClusters.isNotEmpty()) {
        stringResource(R.string.maps_discover_active_places, mapPlaceLayout.placeClusters.size)
    } else {
        defaultSubtitle
    }

    val showsWeatherEffects = weatherEffectsEnabled && discoverWeather != null

    val initial = remember { MapRegionStore.initialRegion(context) }
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(initial.center)
            zoom(initial.zoom)
            pitch(MomentsMapStyle.CAMERA_PITCH)
            bearing(0.0)
        }
    }
    val mapState = rememberMapState {
        gesturesSettings = GesturesSettings {
            pitchEnabled = false
            rotateEnabled = true
        }
    }

    fun updateBottomSheetForCurrentFilter() {
        when (contentFilter) {
            MapDiscoverContentFilter.Friends -> showingBottomSheet = false
            MapDiscoverContentFilter.All, MapDiscoverContentFilter.Places -> {
                showingBottomSheet = filteredMoments.isNotEmpty() || filteredStories.isNotEmpty()
            }
        }
    }

    fun regionSearchKey(region: MapRegionStore.Region): String {
        fun r(v: Double) = (v * 100).toLong()
        return "${r(region.centerLat)}|${r(region.centerLon)}|${r(region.longitudeDelta)}"
    }

    fun performRegionSearch() {
        isLoading = true
        errorMessage = null
        hasRecoverableError = false
        val region = currentRegion
        lastSearchedRegionKey = regionSearchKey(region)
        MapZoneContextService.zoneName(context, region.centerLat, region.centerLon) { name ->
            if (isViewActive) resolvedZoneName = name ?: zoneName
        }
        scope.launch {
            val weather = WeatherService.getWeatherSafely(region.centerLat, region.centerLon)
            if (isViewActive) discoverWeather = weather
        }
        LocationSearchService.searchDiscoverContentInRegion(region) { payload ->
            if (!isViewActive) return@searchDiscoverContentInRegion
            moments = payload.moments
            stories = payload.stories
            friendPins = LocationSearchService.buildFriendActivityPins(
                moments = payload.moments,
                stories = payload.stories,
                followingIds = followingIds,
            )
            isLoading = false
            when {
                payload.isCompleteFailure -> {
                    errorMessage = unavailableMsg
                    hasRecoverableError = true
                    showingBottomSheet = false
                }
                payload.moments.isEmpty() && payload.stories.isEmpty() -> {
                    errorMessage = emptyMsg
                    hasRecoverableError = false
                    showingBottomSheet = false
                }
                payload.hasPartialFailure -> {
                    errorMessage = partialMsg
                    hasRecoverableError = false
                    selectedPlaceCluster = null
                    showingBottomSheet = contentFilter != MapDiscoverContentFilter.Friends &&
                        (payload.moments.isNotEmpty() || payload.stories.isNotEmpty())
                }
                else -> {
                    errorMessage = null
                    hasRecoverableError = false
                    selectedPlaceCluster = null
                    showingBottomSheet = contentFilter != MapDiscoverContentFilter.Friends &&
                        (payload.moments.isNotEmpty() || payload.stories.isNotEmpty())
                }
            }
        }
    }

    fun scheduleRegionSearch() {
        regionSearchJob?.cancel()
        regionSearchJob = scope.launch {
            delay(900)
            if (isViewActive) performRegionSearch()
        }
    }

    fun focusOn(point: Point, zoom: Double = MapRegionStore.zoomFromLongitudeDelta(0.06), autoSearch: Boolean = true) {
        focusNonce += 1
        focusCenter = point
        focusZoom = zoom
        val lonDelta = MapRegionStore.longitudeDeltaFromZoom(zoom)
        currentRegion = MapRegionStore.Region(
            centerLat = point.latitude(),
            centerLon = point.longitude(),
            latitudeDelta = lonDelta,
            longitudeDelta = lonDelta,
        )
        MapRegionStore.save(context, currentRegion)
        if (autoSearch) performRegionSearch()
    }

    fun bootstrapMapCenter() {
        LocationUtilities.getCurrentLocation(context) { point ->
            if (!isViewActive) return@getCurrentLocation
            if (point != null) {
                focusOn(point)
            } else {
                MapRegionStore.resolveFallbackRegion(context) { region ->
                    if (!isViewActive) return@resolveFallbackRegion
                    currentRegion = region
                    focusOn(region.center, region.zoom, autoSearch = true)
                }
            }
        }
    }

    fun recenterOnUser() {
        HapticManager.shared.lightImpact()
        if (LocationUtilities.hasForegroundPermission(context)) {
            bootstrapMapCenter()
        } else {
            locationGate.requestAccess(context) { bootstrapMapCenter() }
        }
    }

    fun performPlaceSearch() {
        val query = searchText.trim()
        if (query.isEmpty()) return
        scope.launch {
            val point = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.getDefault()).getFromLocationName(query, 1)
                        ?.firstOrNull()
                        ?.let { Point.fromLngLat(it.longitude, it.latitude) }
                }.getOrNull()
            }
            if (point == null || !isViewActive) return@launch
            keyboard?.hide()
            isSearchActive = false
            searchText = ""
            focusOn(point)
        }
    }

    fun openPlaceStories(cluster: MapPlaceCluster, startingAt: MapStoryPreview? = null) {
        if (cluster.stories.isEmpty() || isOpeningStory) return
        isOpeningStory = true
        val presentation = MapStoryViewerPresentation(
            previews = cluster.stories,
            initialPreviewId = startingAt?.id ?: cluster.primaryStory?.id,
        )
        scope.launch {
            isOpeningStory = false
            if (!isViewActive) return@launch
            if (showingBottomSheet) {
                pendingStoryPresentation = presentation
                resumeBottomSheetAfterDetail = true
                showingBottomSheet = false
            } else {
                storyViewerPresentation = presentation
            }
        }
    }

    fun openPlaceCluster(cluster: MapPlaceCluster) {
        selectedPlaceCluster = cluster
        showingBottomSheet = true
    }

    fun openFriendCluster(pin: MapFriendActivityPin) {
        val cluster = MapPlaceClusterEngine.cluster(pin, filteredMoments, filteredStories)
        if (cluster.momentCount == 0 && cluster.primaryStory != null) {
            openPlaceStories(cluster)
            return
        }
        selectedPlaceCluster = cluster
        showingBottomSheet = true
    }

    fun openMomentDetail(at: Int, pool: List<Moment>, title: String) {
        if (pool.getOrNull(at) == null) return
        if (showingBottomSheet) {
            resumeBottomSheetAfterDetail = true
            showingBottomSheet = false
        }
        zoomMapMomentsPool = pool
        MomentZoomOpener.open(
            moment = pool[at],
            moments = pool,
            initialIndex = at,
            presentation = MomentZoomPresentationKind.Map(title),
            setDestination = { zoomDestination = it },
            zoomIDPrefix = "discover-map",
        )
    }

    fun selectPlaceFromIndex(place: MapPlaceCluster) {
        selectedPlaceCluster = place
        focusOn(
            Point.fromLngLat(place.longitude, place.latitude),
            zoom = MapRegionStore.zoomFromLongitudeDelta(0.015),
            autoSearch = false,
        )
    }

    fun closeDiscoverMap() {
        regionSearchJob?.cancel()
        keyboard?.hide()
        isSearchActive = false
        searchText = ""
        showingBottomSheet = false
        zoomDestination = null
        storyViewerPresentation = null
        pendingStoryPresentation = null
        onDismiss()
    }

    fun loadFollowingIds() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid).collection("following")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isViewActive) return@addOnSuccessListener
                val ids = snapshot.documents.map { it.id }.toSet()
                followingIds = ids
                friendPins = LocationSearchService.buildFriendActivityPins(
                    moments = moments,
                    stories = stories,
                    followingIds = ids,
                )
            }
    }

    fun presentDeferredMapContent() {
        val pending = pendingStoryPresentation ?: return
        scope.launch {
            delay(MapSheetPresentationDelay.DISMISS_BEFORE_NEXT_PRESENTATION_MS)
            if (!isViewActive) return@launch
            storyViewerPresentation = pending
            pendingStoryPresentation = null
        }
    }

    fun restoreBottomSheetIfNeeded() {
        if (!resumeBottomSheetAfterDetail) return
        resumeBottomSheetAfterDetail = false
        scope.launch {
            delay(MapSheetPresentationDelay.REOPEN_BOTTOM_SHEET_AFTER_DETAIL_MS)
            if (isViewActive && sheetCluster.totalCount > 0) {
                showingBottomSheet = true
            }
        }
    }

    BackHandler(onBack = ::closeDiscoverMap)

    LaunchedEffect(focusNonce) {
        val point = focusCenter ?: return@LaunchedEffect
        mapViewportState.setCameraOptions {
            center(point)
            zoom(focusZoom)
            pitch(MomentsMapStyle.CAMERA_PITCH)
            bearing(0.0)
        }
    }

    LaunchedEffect(Unit) {
        loadFollowingIds()
        when {
            LocationUtilities.hasForegroundPermission(context) -> bootstrapMapCenter()
            else -> {
                MapRegionStore.resolveFallbackRegion(context) { region ->
                    if (isViewActive) {
                        currentRegion = region
                        focusOn(region.center, region.zoom, autoSearch = true)
                    }
                }
                locationGate.requestAccess(context) { bootstrapMapCenter() }
            }
        }
    }

    DisposableEffect(Unit) {
        isViewActive = true
        onDispose {
            isViewActive = false
            regionSearchJob?.cancel()
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) searchFocus.requestFocus()
    }

    LaunchedEffect(showingBottomSheet) {
        if (!showingBottomSheet) presentDeferredMapContent()
    }

    LaunchedEffect(zoomDestination) {
        if (zoomDestination == null) {
            zoomMapMomentsPool = emptyList()
            restoreBottomSheetIfNeeded()
        }
    }

    Box(modifier.fillMaxSize()) {
        if (FeedMaps.hasMapboxToken()) {
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
                mapState = mapState,
                // ≡ iOS `.mapStyle(.standard(elevation: .realistic))`
                style = { MomentsMapboxStandardStyle(realisticElevation = true) },
            ) {
                MapEffect(Unit) { mapView ->
                    mapView.mapboxMap.subscribeMapIdle {
                        val state = mapView.mapboxMap.cameraState
                        val lonDelta = MapRegionStore.longitudeDeltaFromZoom(state.zoom)
                        currentRegion = MapRegionStore.Region(
                            centerLat = state.center.latitude(),
                            centerLon = state.center.longitude(),
                            latitudeDelta = lonDelta,
                            longitudeDelta = lonDelta,
                        )
                        MapRegionStore.saveCamera(context, state.center, state.zoom)
                        if (regionSearchKey(currentRegion) != lastSearchedRegionKey) {
                            scheduleRegionSearch()
                        }
                    }
                }

                mapPlaceLayout.placeClusters.forEach { cluster ->
                    ViewAnnotation(
                        options = viewAnnotationOptions {
                            geometry(Point.fromLngLat(cluster.longitude, cluster.latitude))
                            annotationAnchor { anchor(ViewAnnotationAnchor.CENTER) }
                            allowOverlap(true)
                        },
                    ) {
                        Box(
                            Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { openPlaceCluster(cluster) }
                                .semantics {
                                    contentDescription = context.getString(
                                        R.string.maps_pin_accessibility,
                                        cluster.displayName,
                                        cluster.totalCount,
                                    )
                                },
                        ) {
                            MapPlacePin(cluster = cluster)
                        }
                    }
                }

                mapPlaceLayout.standaloneFriends.forEachIndexed { index, friend ->
                    val (lat, lon) = MapPlaceClusterEngine.jitteredCoordinate(
                        friend.latitude, friend.longitude, friend.authorId, index,
                    )
                    ViewAnnotation(
                        options = viewAnnotationOptions {
                            geometry(Point.fromLngLat(lon, lat))
                            annotationAnchor { anchor(ViewAnnotationAnchor.CENTER) }
                            allowOverlap(true)
                        },
                    ) {
                        Box(
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { openFriendCluster(friend) },
                        ) {
                            MapFriendActivityPinView(pin = friend)
                        }
                    }
                }
            }
        }

        if (showsWeatherEffects) {
            discoverWeather?.let { weather ->
                // iOS: .animation(.easeInOut(duration: 2.0), value: weather.condition)
                val overlayColor by animateColorAsState(
                    targetValue = weather.mapOverlayColor.copy(alpha = weather.mapOverlayOpacity),
                    animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
                    label = "weatherMapOverlay",
                )
                Box(Modifier.fillMaxSize().background(overlayColor))
                MapWeatherEffectsView(weather = weather, modifier = Modifier.fillMaxSize())
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // iOS: VStack centra sus hijos (los filter chips van centrados)
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                // iOS: HStack { pill; Spacer(); weather } — el pill toma su tamaño natural
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    Modifier
                        .weight(1f, fill = false)
                        .shadow(10.dp, RoundedCornerShape(percent = 50), clip = false)
                        .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(percent = 50))
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = ::closeDiscoverMap,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, null, tint = primary, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            title,
                            color = primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            subtitle,
                            color = tertiary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        Modifier
                            .shadow(10.dp, RoundedCornerShape(percent = 50), clip = false)
                            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(percent = 50))
                            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    HapticManager.shared.lightImpact()
                                    isSearchActive = !isSearchActive
                                    if (!isSearchActive) {
                                        searchText = ""
                                        keyboard?.hide()
                                    }
                                }
                                .padding(2.dp),
                        )
                        Icon(
                            Icons.Filled.MyLocation,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable(onClick = ::recenterOnUser)
                                .padding(2.dp),
                        )
                        discoverWeather?.let { weather ->
                            Row(
                                Modifier.clickable {
                                    HapticManager.shared.lightImpact()
                                    weatherEffectsEnabled = !weatherEffectsEnabled
                                },
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (weatherEffectsEnabled) Icons.Filled.CloudOff else Icons.Filled.CloudOff,
                                    null,
                                    tint = if (weatherEffectsEnabled) colors.accent else primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Column {
                                    Text(
                                        weather.temperatureFormatted,
                                        color = primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                    )
                                    Text(
                                        weather.condition.name,
                                        color = secondary,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    if (discoverWeather != null && weatherEffectsEnabled) {
                        Row(
                            Modifier.padding(end = 8.dp, top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                stringResource(R.string.weather_attribution_text),
                                color = secondary.copy(alpha = 0.8f),
                                fontSize = 7.sp,
                            )
                            Text(
                                stringResource(R.string.weather_attribution_link),
                                color = Color(0xFF007AFF).copy(alpha = 0.6f),
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("https://openweathermap.org/")
                                },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isSearchActive, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Search, null, tint = secondary, modifier = Modifier.size(16.dp))
                    BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performPlaceSearch() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchFocus),
                        decorationBox = { inner ->
                            if (searchText.isEmpty()) {
                                Text(
                                    stringResource(R.string.maps_search_placeholder),
                                    color = secondary,
                                    fontSize = 14.sp,
                                )
                            }
                            inner()
                        },
                    )
                }
            }

            MapFilterChipsSection(
                selected = contentFilter,
                onSelect = {
                    HapticManager.shared.selection()
                    contentFilter = it
                    selectedPlaceCluster = null
                    updateBottomSheetForCurrentFilter()
                },
            )

            if (isLoading) {
                Box(
                    Modifier
                        .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
                        .padding(10.dp),
                ) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp),
                        color = colors.accent,
                        strokeWidth = 2.dp,
                    )
                }
            }

            errorMessage?.let { message ->
                if (hasRecoverableError && moments.isEmpty() && stories.isEmpty()) {
                    DiscoverErrorCard(message = message, primary = primary, onRetry = ::performRegionSearch)
                } else {
                    DiscoverErrorBanner(message = message, primary = primary, onRetry = ::performRegionSearch)
                }
            }
        }

        LocationPermissionGateHost(gate = locationGate)
    }

    if (showingBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showingBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            // Sin esto cae al containerColor claro por defecto de Material3 y en modo
            // oscuro el contenido (texto blanco) se pierde sobre fondo claro.
            containerColor = colors.surfaceBackground,
        ) {
            MapPlaceBottomSheet(
                cluster = sheetCluster,
                isLoading = isLoading,
                onMomentTap = { momentId ->
                    val index = sheetCluster.moments.indexOfFirst { it.id == momentId }
                    if (index >= 0) {
                        openMomentDetail(index, sheetCluster.moments, sheetCluster.displayName)
                    }
                },
                onPlaceStoriesTap = { openPlaceStories(it) },
                weather = discoverWeather,
                placeIndex = mapPlaceLayout.placeClusters,
                onPlaceTap = { selectPlaceFromIndex(it) },
                timeFilter = timeFilter,
                onTimeFilterChange = {
                    timeFilter = it
                    selectedPlaceCluster = null
                    updateBottomSheetForCurrentFilter()
                },
                onDismiss = { showingBottomSheet = false },
            )
        }
    }

    zoomDestination?.let { destination ->
        Dialog(
            onDismissRequest = { zoomDestination = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            MomentZoomDetailDestination(
                destination = destination,
                moments = MomentZoomOpener.resolvedMoments(destination, zoomMapMomentsPool),
                onDismiss = { zoomDestination = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    storyViewerPresentation?.let { presentation ->
        Dialog(
            onDismissRequest = {
                storyViewerPresentation = null
                restoreBottomSheetIfNeeded()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            MapPlaceStoryDeckView(
                previews = presentation.previews,
                initialPreviewId = presentation.initialPreviewId,
                onClose = {
                    storyViewerPresentation = null
                    restoreBottomSheetIfNeeded()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }
    }
}

private data class MapStoryViewerPresentation(
    val id: String = UUID.randomUUID().toString(),
    val previews: List<MapStoryPreview>,
    val initialPreviewId: String?,
)

@Composable
private fun DiscoverErrorBanner(
    message: String,
    primary: Color,
    onRetry: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier
            .fillMaxWidth()
            .momentsChromeGlass(RoundedCornerShape(16.dp), interactive = false)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF9500), modifier = Modifier.size(14.dp))
        Text(message, color = primary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 2)
        Text(
            stringResource(R.string.maps_error_retry),
            color = colors.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onRetry),
        )
    }
}

@Composable
private fun DiscoverErrorCard(
    message: String,
    primary: Color,
    onRetry: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier
            .fillMaxWidth()
            .momentsChromeGlass(RoundedCornerShape(22.dp), interactive = false)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Filled.WifiOff, null, tint = colors.accent, modifier = Modifier.size(28.dp))
        Text(message, color = primary, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.maps_error_retry),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(colors.accent, CircleShape)
                .clickable(onClick = onRetry)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

/** ≡ iOS `MapPlacePin` (MapCanvasSection.swift). */
@Composable
fun MapPlacePin(cluster: MapPlaceCluster, modifier: Modifier = Modifier) {
    val extraCount = maxOf(0, cluster.totalCount - 1)
    // iOS: pulso solo si `hasFreshStory && !MotionPolicy.reduceMotion`.
    val pulses = cluster.hasFreshStory && !MotionPolicy.reduceMotion
    val accent = rememberAdaptiveColors().accent
    var scale = 1f
    var opacity = 0.8f
    if (pulses) {
        val pulse = rememberInfiniteTransition(label = "mapPinPulse")
        scale = pulse.animateFloat(
            initialValue = 1f,
            targetValue = 1.45f,
            animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
            label = "pulseScale",
        ).value
        opacity = pulse.animateFloat(
            initialValue = 0.8f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
            label = "pulseOpacity",
        ).value
    }

    Box(modifier, contentAlignment = Alignment.TopEnd) {
        if (pulses) {
            Box(
                Modifier
                    .size(54.dp)
                    .align(Alignment.Center)
                    .scale(scale)
                    .border(2.dp, accent.copy(alpha = 0.55f * opacity), CircleShape),
            )
        }
        when {
            cluster.primaryStory != null -> MapStoryPin(story = cluster.primaryStory!!)
            cluster.primaryMoment != null -> MapMomentPin(moment = cluster.primaryMoment!!, count = 1)
            else -> Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Place, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        if (extraCount > 0) {
            Text(
                "+$extraCount",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    // iOS: .offset(x: 8, y: -8)
                    .offset(x = 8.dp, y = (-8).dp)
                    .background(Color.Black.copy(alpha = 0.78f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

/** ≡ iOS `MapMomentPin`. */
@Composable
fun MapMomentPin(moment: Moment, count: Int, modifier: Modifier = Modifier) {
    val pinSize = if (count > 1) 56.dp else 48.dp
    val mediaSize = if (count > 1) 40.dp else 42.dp
    val isDark = isSystemInDarkTheme()
    // iOS: mapPreferredImageURL ?? mapPreferredVideoThumbnailURL
    val url = moment.mapPreferredImageUrl ?: moment.mapPreferredVideoThumbnailUrl

    Box(modifier.size(pinSize), contentAlignment = Alignment.Center) {
        // ≡ iOS `stackedPlaceholder` — pila de fotos detrás cuando count > 1.
        if (count > 1) {
            MapMomentStackedPlaceholder(mediaSize, isDark, (-7).dp, 5.dp, 0.88f, 0.55f)
            MapMomentStackedPlaceholder(mediaSize, isDark, 7.dp, (-5).dp, 0.88f, 0.7f)
        }

        // iOS: .shadow(color: .black.opacity(0.28), radius: 7, y: 3) sobre el thumbnail.
        // El shadow debe ir ANTES del clip o queda recortado y no se ve.
        val thumbModifier = Modifier
            .size(mediaSize)
            .shadow(7.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = thumbModifier.border(2.5.dp, Color.White, CircleShape),
            )
        } else {
            Box(
                thumbModifier
                    .background(Color.Black.copy(alpha = if (isDark) 0.35f else 0.15f))
                    .border(2.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Photo,
                    null,
                    tint = if (isDark) Color.White else Color.Black,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        if (count > 1) {
            Text(
                "+$count",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // iOS: .offset(x: 10, y: -10)
                    .offset(x = 10.dp, y = (-10).dp)
                    .background(Color.Black.copy(alpha = 0.78f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

/** ≡ iOS `MapMomentPin.stackedPlaceholder`. */
@Composable
private fun MapMomentStackedPlaceholder(
    mediaSize: Dp,
    isDark: Boolean,
    offsetX: Dp,
    offsetY: Dp,
    scale: Float,
    opacity: Float,
) {
    Box(
        Modifier
            .size(mediaSize * scale)
            .offset(x = offsetX, y = offsetY)
            .alpha(opacity)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (isDark) 0.18f else 0.92f))
            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
    )
}

/** ≡ iOS `MapStoryPin` — thumb 46 + StorySegmentedRing overlay ringSize 54. */
@Composable
fun MapStoryPin(story: MapStoryPreview, modifier: Modifier = Modifier) {
    val ringSize = 54.dp
    val thumbSize = 46.dp
    val ringLineWidth = 3.dp
    val outerSize = ringSize + ringLineWidth + 2.dp
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid
    val isOwnStory = story.authorId == viewerId
    var snapshot by remember(story.authorId) {
        mutableStateOf(
            StoryRingSnapshot(
                hasStory = true,
                hasUnseenStory = true,
                storyCount = 1,
                storyViewedStatus = emptyList(),
                storyAudiences = emptyList(),
            ),
        )
    }

    LaunchedEffect(story.authorId, viewerId) {
        if (viewerId.isNullOrEmpty()) return@LaunchedEffect
        val resolved = StoryRingResolverService.resolve(
            viewerId = viewerId,
            authorId = story.authorId,
        )
        snapshot = if (resolved.hasStory) {
            resolved
        } else {
            StoryRingSnapshot(
                hasStory = true,
                hasUnseenStory = true,
                storyCount = 1,
                storyViewedStatus = listOf(false),
                storyAudiences = emptyList(),
            )
        }
    }

    Box(modifier.size(outerSize), contentAlignment = Alignment.Center) {
        if (!story.previewUrl.isNullOrBlank()) {
            AsyncImage(
                model = story.previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(thumbSize)
                    .clip(CircleShape),
            )
        } else {
            Box(
                Modifier
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Place, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        StorySegmentedRing(
            storyCount = maxOf(snapshot.storyCount, 1),
            hasStory = true,
            hasUnseenStory = snapshot.hasUnseenStory,
            storyViewedStatus = snapshot.storyViewedStatus,
            storyAudiences = snapshot.storyAudiences,
            isOwnStory = isOwnStory,
            ringSize = ringSize,
            lineWidth = ringLineWidth,
            hapticsEnabled = false,
        )
    }
}

/** ≡ iOS `MapFriendActivityPinView` (DiscoverMapView.swift). */
@Composable
fun MapFriendActivityPinView(pin: MapFriendActivityPin, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StoryRingAvatarView(
            userId = pin.authorId,
            size = 42.dp,
            lineWidth = 2.5.dp,
            showBaseStroke = true,
            baseStrokeColor = Color.White.copy(alpha = if (isDark) 0.35f else 0.85f),
            baseStrokeWidth = 2.dp,
        )
        Text(
            pin.username,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .momentsChromeGlass(CircleShape, interactive = false)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
