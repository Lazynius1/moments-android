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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.PaddingValues
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
import com.moments.android.views.shared.tabbar.MomentsTabBarHidden
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.pow

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
    initialLatitude: Double? = null,
    initialLongitude: Double? = null,
    originMoment: Moment? = null,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    MomentsTabBarHidden()
    val colors = rememberAdaptiveColors()
    val primary = if (isDark) Color.White else FeedInk
    val secondary = primary.copy(alpha = 0.72f)
    val tertiary = primary.copy(alpha = 0.55f)
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var isSocialMap by remember { mutableStateOf(originMoment == null) }
    var isBrowsingPlaces by remember { mutableStateOf(originMoment == null) }
    var panelState by remember { mutableStateOf(MapPanelState.Small) }
    var panelHeight by remember { mutableStateOf(80.dp) }
    var paginationRegion by remember { mutableStateOf<MapRegionStore.Region?>(null) }
    var paginationLocation by remember { mutableStateOf<String?>(null) }
    var paginationFollowing by remember { mutableStateOf(false) }
    var momentsCursor by remember { mutableStateOf<String?>(null) }
    var storiesCursor by remember { mutableStateOf<String?>(null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var zoneSnapshot by remember { mutableStateOf<AndroidMapZoneSnapshot?>(null) }
    var originCoordinate by remember {
        mutableStateOf(originMoment?.locationCoordinate?.let { Point.fromLngLat(it.longitude, it.latitude) }
            ?: if (initialLatitude != null && initialLongitude != null) Point.fromLngLat(initialLongitude, initialLatitude) else null)
    }
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
    var weatherEffectsEnabled by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var isViewActive by remember { mutableStateOf(true) }
    var userPosition by remember { mutableStateOf<Point?>(null) }
    var needsLocationSelection by remember { mutableStateOf(false) }
    var searchToken by remember { mutableStateOf(UUID.randomUUID()) }
    var focusNonce by remember { mutableIntStateOf(0) }
    var focusCenter by remember { mutableStateOf<Point?>(null) }
    var focusZoom by remember { mutableStateOf(MomentsMapStyle.DEFAULT_ZOOM) }
    var currentRegion by remember {
        mutableStateOf(MapRegionStore.initialRegion(context))
    }
    // Mapbox `subscribeMapIdle` también dispara al recrear las ViewAnnotations (iOS
    // `onMapCameraChange` no). Sin este guard: buscar → pins nuevos → idle → buscar…
    var lastSearchedRegionKey by remember { mutableStateOf("") }
    var hasPerformedInitialSearch by remember { mutableStateOf(false) }
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
            friendPins = emptyList(),
            filter = contentFilter,
            centerLat = currentRegion.centerLat,
            centerLon = currentRegion.centerLon,
            latitudeDelta = currentRegion.latitudeDelta,
            longitudeDelta = currentRegion.longitudeDelta,
        )
    }

    val sheetCluster = selectedPlaceCluster?.let { if (paginationLocation != null) it.copy(moments = filteredMoments, stories = filteredStories) else it } ?: MapPlaceClusterEngine.aggregateRegionCluster(
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
    val mapLegalInset = panelHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp

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
        if (filteredMoments.isEmpty() && filteredStories.isEmpty()) showingBottomSheet = false
    }

    fun regionSearchKey(region: MapRegionStore.Region): String = MapViewportQuery.key(region)

    fun loadMore() {
        if (isLoading || isLoadingMore || (momentsCursor == null && storiesCursor == null)) return
        val token = searchToken
        val postCursor = momentsCursor
        val storyCursor = storiesCursor
        isLoadingMore = true
        LocationSearchService.searchContentPage(
            region = paginationRegion, locationName = paginationLocation,
            followingOnly = paginationFollowing,
            momentsCursor = postCursor, storiesCursor = storyCursor,
            loadMoments = postCursor != null, loadStories = storyCursor != null,
        ) { payload ->
            if (!isViewActive || searchToken != token) return@searchContentPage
            moments = (moments + payload.moments).distinctBy { it.mapAvailabilityKey }
            stories = (stories + payload.stories).distinctBy { "${it.authorId}|${it.id}" }
            // Retain a failed cursor so the footer can retry that stream.
            momentsCursor = if (payload.momentsError != null) postCursor else payload.momentsCursor
            storiesCursor = if (payload.storiesError != null) storyCursor else payload.storiesCursor
            isLoadingMore = false
            if (payload.momentsError != null || payload.storiesError != null) errorMessage = partialMsg
        }
    }

    fun startPageQuery(region: MapRegionStore.Region?, place: String? = null) {
        isLoading = true
        isLoadingMore = false
        errorMessage = null
        hasRecoverableError = false
        val token = UUID.randomUUID()
        searchToken = token
        paginationRegion = region
        paginationLocation = place
        paginationFollowing = !isSocialMap
        momentsCursor = null
        storiesCursor = null
        LocationSearchService.searchContentPage(region = region, locationName = place, followingOnly = paginationFollowing) { payload ->
            if (!isViewActive || searchToken != token) return@searchContentPage
            if (!payload.isCompleteFailure) {
                moments = payload.moments
                stories = payload.stories
                momentsCursor = payload.momentsCursor
                storiesCursor = payload.storiesCursor
            }
            isLoading = false
            hasPerformedInitialSearch = true
            hasRecoverableError = payload.isCompleteFailure
            errorMessage = when {
                payload.isCompleteFailure -> unavailableMsg
                payload.hasPartialFailure -> partialMsg
                !payload.hasContent && payload.momentsCursor == null && payload.storiesCursor == null -> emptyMsg
                else -> null
            }
        }
    }

    fun performRegionSearch() {
        isBrowsingPlaces = true
        selectedPlaceCluster = null
        zoneSnapshot = null
        lastSearchedRegionKey = regionSearchKey(currentRegion)
        startPageQuery(currentRegion)
        val token = searchToken
        MapZoneContextService.zoneName(context, currentRegion.centerLat, currentRegion.centerLon) { name ->
            if (isViewActive && searchToken == token) resolvedZoneName = name ?: zoneName
        }
        val region = currentRegion
        scope.launch {
            val weather = WeatherService.getWeatherSafely(region.centerLat, region.centerLon)
            if (isViewActive && searchToken == token) discoverWeather = weather
        }
    }

    fun returnToZone() {
        val saved = zoneSnapshot ?: return
        searchToken = UUID.randomUUID()
        isLoading = false
        isLoadingMore = false
        moments = saved.moments
        stories = saved.stories
        momentsCursor = saved.momentsCursor
        storiesCursor = saved.storiesCursor
        paginationRegion = saved.region
        paginationLocation = null
        paginationFollowing = saved.following
        selectedPlaceCluster = null
        zoneSnapshot = null
        errorMessage = null
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
                userPosition = point
                needsLocationSelection = false
                focusOn(point)
            } else {
                MapRegionStore.resolveFallbackRegion(context) { region ->
                    if (!isViewActive) return@resolveFallbackRegion
                    needsLocationSelection = true
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
            previews = cluster.stories.sortedBy { it.timestamp.time },
            initialPreviewId = startingAt?.id,
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
        if (selectedPlaceCluster == null) zoneSnapshot = AndroidMapZoneSnapshot(moments, stories, momentsCursor, storiesCursor, paginationRegion, paginationFollowing)
        selectedPlaceCluster = cluster
        startPageQuery(null, cluster.displayName)
        panelState = MapPanelState.Medium
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
        if (selectedPlaceCluster == null) {
            zoneSnapshot = AndroidMapZoneSnapshot(moments, stories, momentsCursor, storiesCursor, paginationRegion, paginationFollowing)
        }
        selectedPlaceCluster = place
        panelState = MapPanelState.Medium
        focusOn(Point.fromLngLat(place.longitude, place.latitude), MapRegionStore.zoomFromLongitudeDelta(0.015), autoSearch = false)
        startPageQuery(null, place.displayName)
    }

    fun closeDiscoverMap() {
        isViewActive = false
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
                val ids = snapshot.documents.map { it.getString("userId") ?: it.id }.toSet()
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

    BackHandler(enabled = zoomDestination == null && storyViewerPresentation == null) {
        when {
            selectedPlaceCluster != null && zoneSnapshot != null -> returnToZone()
            panelState != MapPanelState.Small -> panelState = MapPanelState.Small
            else -> closeDiscoverMap()
        }
    }

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
        if (LocationUtilities.hasForegroundPermission(context)) {
            LocationUtilities.getCurrentLocation(context) { point ->
                if (isViewActive) userPosition = point
            }
        }
        if (originMoment != null) {
            moments = listOf(originMoment)
            hasPerformedInitialSearch = true
            val known = originCoordinate
            if (known != null) {
                focusOn(known, autoSearch = false)
            } else {
                val point = withContext(Dispatchers.IO) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        Geocoder(context, Locale.getDefault()).getFromLocationName(zoneName.orEmpty(), 1)
                            ?.firstOrNull()?.let { Point.fromLngLat(it.longitude, it.latitude) }
                    }.getOrNull()
                }
                if (isViewActive && point != null) {
                    originCoordinate = point
                    focusOn(point, autoSearch = false)
                }
            }
        } else if (initialLatitude != null && initialLongitude != null) {
            focusOn(Point.fromLngLat(initialLongitude, initialLatitude))
        } else if (LocationUtilities.hasForegroundPermission(context)) {
            bootstrapMapCenter()
        } else {
            MapRegionStore.resolveFallbackRegion(context) { region ->
                if (isViewActive) {
                    needsLocationSelection = true
                    currentRegion = region
                    focusOn(region.center, region.zoom, autoSearch = true)
                }
            }
            locationGate.requestAccess(context) { bootstrapMapCenter() }
        }
    }

    DisposableEffect(Unit) {
        isViewActive = true
        onDispose {
            isViewActive = false
        }
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
                scaleBar = {},
                logo = { Logo(contentPadding = PaddingValues(start = 12.dp, bottom = mapLegalInset)) },
                attribution = { Attribution(contentPadding = PaddingValues(start = 100.dp, bottom = mapLegalInset), alignment = Alignment.BottomStart) },
                // ≡ iOS `.mapStyle(.standard(elevation: .realistic))`
                style = { MomentsMapboxStandardStyle(realisticElevation = true) },
            ) {
                MapEffect(Unit) { mapView ->
                    mapView.mapboxMap.subscribeMapIdle {
                        val state = mapView.mapboxMap.cameraState
                        val bounds = mapView.mapboxMap.coordinateBoundsForCamera(
                            com.mapbox.maps.CameraOptions.Builder().center(state.center).zoom(state.zoom).pitch(state.pitch).bearing(state.bearing).build()
                        )
                        val west = bounds.southwest.longitude()
                        val east = bounds.northeast.longitude()
                        val longitudeSpan = (east - west).let { if (it < 0) it + 360 else it }
                        currentRegion = MapRegionStore.Region(
                            centerLat = (bounds.southwest.latitude() + bounds.northeast.latitude()) / 2,
                            centerLon = state.center.longitude(),
                            latitudeDelta = (bounds.northeast.latitude() - bounds.southwest.latitude()).coerceAtLeast(0.0001),
                            longitudeDelta = longitudeSpan.coerceAtLeast(0.0001),
                        )
                        MapRegionStore.saveCamera(context, state.center, state.zoom)
                        // Panning retains the last results until the user searches this area.
                    }
                }

                if (!isSocialMap && originMoment != null && originCoordinate != null) {
                    ViewAnnotation(options = viewAnnotationOptions {
                        geometry(originCoordinate!!)
                        annotationAnchor { anchor(ViewAnnotationAnchor.CENTER) }
                        allowOverlap(true)
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val pinScale = (0.06 / currentRegion.longitudeDelta.coerceAtLeast(0.0001)).pow(0.12).coerceIn(0.72, 1.18).toFloat()
                            MapMomentPin(originMoment, 1, Modifier.scale(pinScale).clickable {
                                openMomentDetail(0, listOf(originMoment), zoneName ?: defaultTitle)
                            })
                            Column(
                                Modifier.background(colors.surfaceBackground, RoundedCornerShape(12.dp))
                                    .clickable { isSocialMap = true; panelState = MapPanelState.Medium; performRegionSearch() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(zoneName ?: defaultTitle, color = secondary, fontSize = 11.sp, maxLines = 1)
                                Text(stringResource(R.string.feed_see_more), color = primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                mapPlaceLayout.placeClusters.filter { cluster ->
                    isSocialMap || originMoment == null || cluster.moments.none { it.mapAvailabilityKey == originMoment.mapAvailabilityKey }
                }.forEach { cluster ->
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

        MapImmersiveChrome(
            title = if (needsLocationSelection) stringResource(R.string.maps_chrome_choose_city) else (resolvedZoneName ?: stringResource(R.string.maps_chrome_nearby)),
            subtitle = subtitle,
            isLoading = isLoading,
            searchText = searchText,
            onSearchTextChange = { searchText = it },
            onClose = ::closeDiscoverMap,
            onSearch = { needsLocationSelection = false; performPlaceSearch() },
            onRecenter = ::recenterOnUser,
            onOpenContent = { selectedPlaceCluster = null; showingBottomSheet = true },
            showsSearchArea = hasPerformedInitialSearch && !isLoading && (!isBrowsingPlaces || regionSearchKey(currentRegion) != lastSearchedRegionKey),
            onSearchArea = { selectedPlaceCluster = null; performRegionSearch() },
            showsDock = false,
            bottomInset = panelHeight + 12.dp,
        )
        errorMessage?.let { message ->
            Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp).padding(top = 68.dp)) {
                DiscoverErrorBanner(message = message, primary = primary, onRetry = ::performRegionSearch)
            }
        }

        if (zoomDestination == null && storyViewerPresentation == null && pendingStoryPresentation == null) {
            MapImmersivePanel(
                cluster = sheetCluster, state = panelState, onStateChange = { panelState = it },
                onHeightChange = { panelHeight = it },
                isLoading = isLoading, onStories = { openPlaceStories(sheetCluster) },
                distance = (selectedPlaceCluster?.let { Point.fromLngLat(it.longitude, it.latitude) }
                    ?: originCoordinate?.takeIf { !isSocialMap })?.let { target ->
                    MapDistanceFormatter.string(context, userPosition?.latitude(), userPosition?.longitude(), target.latitude(), target.longitude())
                },
                onBack = if (selectedPlaceCluster != null && zoneSnapshot != null) ({ returnToZone() }) else null,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxSize(0.92f),
            ) { contentHeight ->
            MapPlaceBottomSheet(
                socialMode = false,
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
                userLatitude = userPosition?.latitude(),
                userLongitude = userPosition?.longitude(),
                placeIndex = mapPlaceLayout.placeClusters,
                onPlaceTap = { selectPlaceFromIndex(it) },
                showsHeader = false,
                showsStoryStrip = false,
                contentHeight = contentHeight,
                hasMoreContent = momentsCursor != null || storiesCursor != null,
                paginationKey = "${searchToken}|${momentsCursor}|${storiesCursor}",
                isLoadingMore = isLoadingMore,
                onLoadMore = ::loadMore,
                onDismiss = { showingBottomSheet = false },
            )
            }
        }
        // The persistent sheet's navigation inset belongs to the panel, not the map.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars)
            .background(colors.surfaceBackground))
        LocationPermissionGateHost(gate = locationGate)
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
            color = colors.primary,
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
        Icon(Icons.Filled.WifiOff, null, tint = colors.primary, modifier = Modifier.size(28.dp))
        Text(message, color = primary, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.maps_error_retry),
            color = colors.surfaceBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(colors.primary, CircleShape)
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

    Box(modifier.size(pinSize, maxOf(pinSize, mediaSize + 12.dp)), contentAlignment = Alignment.TopCenter) {
        // ≡ iOS `stackedPlaceholder` — pila de fotos detrás cuando count > 1.
        if (count > 1) {
            MapMomentStackedPlaceholder(mediaSize, isDark, (-7).dp, 5.dp, 0.88f, 0.55f)
            MapMomentStackedPlaceholder(mediaSize, isDark, 7.dp, (-5).dp, 0.88f, 0.7f)
        }

        val head = mediaSize + 5.dp
        val shape = com.moments.android.views.messaging.components.LocationAvatarPinSilhouette(22.dp, 2.dp, shortTip = true)
        Box(Modifier.size(head, mediaSize + 12.dp), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.matchParentSize().shadow(7.dp, shape).background(Color.White, shape))
            Box(Modifier.size(head), contentAlignment = Alignment.Center) {
                val thumbModifier = Modifier.size(mediaSize).clip(CircleShape)
                if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = thumbModifier)
                else Box(thumbModifier.background(Color.Gray.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Photo, null, tint = Color.Black, modifier = Modifier.size(15.dp))
                }
                Box(Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp).size(22.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(androidx.compose.ui.res.painterResource(R.drawable.attachment_map_icon), null, tint = Color.Black, modifier = Modifier.size(18.dp))
                }
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
    val colors = rememberAdaptiveColors()
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
            color = colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .background(colors.surfaceBackground, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private data class AndroidMapZoneSnapshot(
    val moments: List<Moment>, val stories: List<MapStoryPreview>,
    val momentsCursor: String?, val storiesCursor: String?,
    val region: MapRegionStore.Region?, val following: Boolean,
)
