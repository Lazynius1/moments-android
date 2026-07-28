package com.moments.android.views.feed.maps

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.mapbox.geojson.Point
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.feed.maps.mapssections.MapCanvasSection
import com.moments.android.views.feed.maps.mapssections.MapHeaderCloseStyle
import com.moments.android.views.feed.maps.mapssections.MapHeaderSection
import com.moments.android.views.feed.maps.mapssections.ModernLocationGalleryView
import com.moments.android.views.permission.shared.LocationPermissionGate
import com.moments.android.views.permission.shared.LocationPermissionGateHost
import com.moments.android.views.profile.core.sections.MomentZoomDestination
import com.moments.android.views.profile.core.sections.MomentZoomDetailDestination
import com.moments.android.views.profile.core.sections.MomentZoomOpener
import com.moments.android.views.profile.core.sections.MomentZoomPresentationKind
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

/**
 * Port de `LocationMapView` en `Maps.swift` (~1985 líneas iOS).
 * Compose + `LocationMapViewSupport` + `LocationMapChrome`.
 */
@Composable
fun LocationMapView(
    locationName: String,
    latitude: Double? = null,
    longitude: Double? = null,
    echoHistoryUserId: String? = null,
    echoHistoryOnly: Boolean = false,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    momentCount: Int? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val isDark = isSystemInDarkTheme()
    val colors = rememberAdaptiveColors()
    val primary = if (isDark) Color.White else FeedInk
    val secondary = primary.copy(alpha = 0.72f)
    val tertiary = primary.copy(alpha = 0.55f)

    val isEchoHistoryMode = echoHistoryOnly
    val locationGate = remember { LocationPermissionGate() }

    var mapHeaderLocationName by remember { mutableStateOf(locationName) }
    var echoHistoryMoments by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var echoIdByMomentIdentity by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var availabilityValidationToken by remember { mutableStateOf(UUID.randomUUID()) }
    var locationMoments by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var nearbyMoments by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var locationStories by remember { mutableStateOf<List<MapStoryPreview>>(emptyList()) }
    var momentAvailability by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMoments by remember { mutableStateOf(false) }
    var isLoadingNearbyMoments by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var contentErrorMessage by remember { mutableStateOf<String?>(null) }
    var showingBottomSheet by remember { mutableStateOf(false) }
    var showingGallery by remember { mutableStateOf(false) }
    var selectedPlaceCluster by remember { mutableStateOf<MapPlaceCluster?>(null) }
    var currentWeather by remember { mutableStateOf<WeatherData?>(null) }
    var weatherEffectsEnabled by remember { mutableStateOf(true) }
    var hasInitializedMap by remember { mutableStateOf(false) }
    var locationPermissionGranted by remember {
        mutableStateOf(LocationUtilities.hasForegroundPermission(context))
    }
    var userLatitude by remember { mutableStateOf<Double?>(null) }
    var userLongitude by remember { mutableStateOf<Double?>(null) }
    var isViewActive by remember { mutableStateOf(true) }
    var showSearchInAreaButton by remember { mutableStateOf(false) }
    var lastNearbyQueryKey by remember { mutableStateOf("") }
    var focusNonce by remember { mutableIntStateOf(0) }
    var focusCenter by remember { mutableStateOf<Point?>(null) }
    var focusZoom by remember { mutableStateOf(MapRegionStore.zoomFromLongitudeDelta(0.01)) }
    var currentRegion by remember {
        mutableStateOf(
            MapRegionStore.Region(
                centerLat = latitude ?: 40.4168,
                centerLon = longitude ?: -3.7038,
                latitudeDelta = 0.01,
                longitudeDelta = 0.01,
            ),
        )
    }
    var zoomDestination by remember { mutableStateOf<MomentZoomDestination?>(null) }
    var zoomMapMomentsPool by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var resumeBottomSheetAfterDetail by remember { mutableStateOf(false) }
    var storyViewerPresentation by remember { mutableStateOf<LocationMapStoryViewerPresentation?>(null) }
    var pendingStoryPresentation by remember { mutableStateOf<LocationMapStoryViewerPresentation?>(null) }
    var isOpeningStory by remember { mutableStateOf(false) }

    val unavailableMsg = stringResource(R.string.maps_error_map_unavailable)
    val partialMsg = stringResource(R.string.maps_error_map_partial_content)
    val defaultLocationMsg = stringResource(R.string.maps_default_location_message, locationName)
    val defaultTitle = stringResource(R.string.maps_default_location_title)

    val effectiveHeaderLocationName = mapHeaderLocationName.trim().ifBlank { locationName }
        .ifBlank { stringResource(R.string.feed_location_default) }

    fun selectionKey(moment: Moment): String =
        if (isEchoHistoryMode) LocationMapViewSupport.momentIdentityKey(moment)
        else moment.mapAvailabilityKey

    val mapDisplayMoments = remember(locationMoments, nearbyMoments) {
        val seen = linkedSetOf<String>()
        (locationMoments + nearbyMoments).filter { seen.add(selectionKey(it)) }
    }
    val mapPinMoments = remember(mapDisplayMoments, momentAvailability, isEchoHistoryMode) {
        mapDisplayMoments.filter { momentAvailability[it.mapAvailabilityKey] ?: !isEchoHistoryMode }
    }
    val locationMapPlaceLayout = remember(
        mapPinMoments, locationStories, currentRegion, isEchoHistoryMode, echoIdByMomentIdentity, effectiveHeaderLocationName,
    ) {
        if (isEchoHistoryMode) {
            MapPlaceLayout(
                placeClusters = mapPinMoments.mapNotNull { moment ->
                    val coord = moment.locationCoordinate ?: return@mapNotNull null
                    val echoId = echoIdByMomentIdentity[LocationMapViewSupport.momentIdentityKey(moment)]
                    val (lat, lon) = if (!echoId.isNullOrEmpty()) {
                        LocationMapViewSupport.jitteredCoordinate(coord.latitude, coord.longitude, echoId)
                    } else {
                        coord.latitude to coord.longitude
                    }
                    MapPlaceCluster(
                        id = selectionKey(moment),
                        latitude = lat,
                        longitude = lon,
                        displayName = moment.location?.trim()?.takeIf { it.isNotEmpty() }
                            ?: effectiveHeaderLocationName,
                        moments = listOf(moment),
                    )
                },
                standaloneFriends = emptyList(),
            )
        } else {
            MapPlaceClusterEngine.build(
                moments = mapPinMoments,
                stories = locationStories,
                friendPins = emptyList(),
                filter = MapDiscoverContentFilter.All,
                centerLat = currentRegion.centerLat,
                centerLon = currentRegion.centerLon,
                latitudeDelta = currentRegion.latitudeDelta,
                longitudeDelta = currentRegion.longitudeDelta,
            )
        }
    }

    val sheetCluster = selectedPlaceCluster ?: MapPlaceClusterEngine.aggregateRegionCluster(
        title = effectiveHeaderLocationName,
        moments = locationMoments.filter { momentAvailability[it.mapAvailabilityKey] ?: !isEchoHistoryMode },
        stories = locationStories,
        latitude = currentRegion.centerLat,
        longitude = currentRegion.centerLon,
    )

    val contextualPlaceIndex = if (!isEchoHistoryMode && selectedPlaceCluster == null) {
        locationMapPlaceLayout.placeClusters.takeIf { it.size > 1 }.orEmpty()
    } else {
        emptyList()
    }

    val subtitle = when {
        locationMoments.isNotEmpty() ->
            stringResource(R.string.maps_location_moments, locationMoments.size)
        momentCount != null && momentCount > 0 ->
            stringResource(R.string.maps_location_moments, momentCount)
        else -> stringResource(R.string.maps_discover_subtitle)
    }

    val mapLocation = MapLocationData.from(
        name = effectiveHeaderLocationName,
        latitude = currentRegion.centerLat,
        longitude = currentRegion.centerLon,
    )

    fun refreshMomentAvailability(moments: List<Moment>) {
        val base = moments.associate { it.mapAvailabilityKey to !isEchoHistoryMode }
        momentAvailability = base
        if (!isEchoHistoryMode) return
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val token = UUID.randomUUID()
        availabilityValidationToken = token
        scope.launch {
            moments.forEach { moment ->
                val ok = LocationMapViewSupport.validateLiveAvailability(moment, viewerId)
                if (!isViewActive || availabilityValidationToken != token) return@launch
                momentAvailability = momentAvailability + (moment.mapAvailabilityKey to ok)
            }
        }
    }

    fun loadWeather(lat: Double, lon: Double) {
        if (currentWeather != null) return
        scope.launch {
            currentWeather = WeatherService.getWeatherSafely(lat, lon)
        }
    }

    fun loadLocationMoments() {
        isLoadingMoments = true
        contentErrorMessage = null
        if (isEchoHistoryMode) {
            scope.launch {
                val result = LocationMapViewSupport.loadEchoHistoryMoments(
                    locationName = locationName,
                    // iOS: si coordinate == nil solo matchea por nombre (no inventar Madrid)
                    targetLat = latitude,
                    targetLon = longitude,
                    echoHistoryUserId = echoHistoryUserId,
                )
                if (!isViewActive) return@launch
                echoHistoryMoments = result.moments
                echoIdByMomentIdentity = result.echoIdByMomentIdentity
                locationMoments = result.moments
                momentAvailability = result.availability
                isLoadingMoments = false
                showSearchInAreaButton = true
                if (result.moments.isNotEmpty()) {
                    result.moments.mapNotNull { it.location?.trim()?.takeIf(String::isNotEmpty) }
                        .firstOrNull()?.let { mapHeaderLocationName = it }
                    showingBottomSheet = true
                }
            }
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        LocationSearchService.searchMomentsByLocation(locationName, uid) { result ->
            if (!isViewActive) return@searchMomentsByLocation
            isLoadingMoments = false
            result.fold(
                onSuccess = { moments ->
                    locationMoments = moments
                    refreshMomentAvailability(moments)
                    if (moments.isNotEmpty() || locationStories.isNotEmpty()) {
                        errorMessage = null
                        contentErrorMessage = null
                        showingBottomSheet = true
                    }
                },
                onFailure = {
                    locationMoments = emptyList()
                    refreshMomentAvailability(emptyList())
                    if (locationStories.isEmpty()) {
                        errorMessage = unavailableMsg
                    } else {
                        contentErrorMessage = partialMsg
                    }
                },
            )
        }
        LocationSearchService.searchStoriesByLocation(locationName) { result ->
            if (!isViewActive) return@searchStoriesByLocation
            result.fold(
                onSuccess = { stories ->
                    locationStories = stories
                    if (stories.isNotEmpty() && locationMoments.isEmpty() && !isLoadingMoments) {
                        errorMessage = null
                        contentErrorMessage = null
                        showingBottomSheet = true
                    } else if (stories.isNotEmpty()) {
                        errorMessage = null
                    }
                },
                onFailure = {
                    locationStories = emptyList()
                    if (locationMoments.isEmpty() && !isLoadingMoments) {
                        errorMessage = unavailableMsg
                    } else if (locationMoments.isNotEmpty()) {
                        contentErrorMessage = partialMsg
                    }
                },
            )
        }
    }

    fun nearbyQueryKey(region: MapRegionStore.Region): String {
        val lat = (region.centerLat * 100).roundToHundredths()
        val lon = (region.centerLon * 100).roundToHundredths()
        val latDelta = (region.latitudeDelta * 100).roundToHundredths()
        val lonDelta = (region.longitudeDelta * 100).roundToHundredths()
        return "$lat|$lon|$latDelta|$lonDelta"
    }

    fun loadNearbyMoments(region: MapRegionStore.Region, queryKey: String) {
        isLoadingNearbyMoments = true
        lastNearbyQueryKey = queryKey
        contentErrorMessage = null
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        LocationSearchService.searchMomentsInRegion(region, uid) { result ->
            if (!isViewActive || lastNearbyQueryKey != queryKey) return@searchMomentsInRegion
            isLoadingNearbyMoments = false
            result.fold(
                onSuccess = { moments ->
                    nearbyMoments = moments
                    showSearchInAreaButton = false
                    errorMessage = null
                    contentErrorMessage = null
                    selectedPlaceCluster = null
                    if (moments.isNotEmpty()) {
                        locationMoments = moments
                        refreshMomentAvailability(moments)
                        moments.mapNotNull { it.location?.trim()?.takeIf(String::isNotEmpty) }
                            .firstOrNull()?.let { mapHeaderLocationName = it }
                        showingBottomSheet = true
                    }
                },
                onFailure = {
                    nearbyMoments = emptyList()
                    contentErrorMessage = unavailableMsg
                    showSearchInAreaButton = true
                },
            )
        }
    }

    fun searchInCurrentArea() {
        HapticManager.shared.lightImpact()
        val key = nearbyQueryKey(currentRegion)
        if (isEchoHistoryMode) {
            isLoadingNearbyMoments = true
            lastNearbyQueryKey = key
            val filtered = echoHistoryMoments.filter { moment ->
                val coord = moment.locationCoordinate ?: return@filter false
                LocationMapViewSupport.regionContains(currentRegion, coord.latitude, coord.longitude)
            }
            nearbyMoments = filtered
            locationMoments = filtered
            refreshMomentAvailability(filtered)
            isLoadingNearbyMoments = false
            showSearchInAreaButton = false
            if (filtered.isNotEmpty()) {
                filtered.mapNotNull { it.location?.trim()?.takeIf(String::isNotEmpty) }
                    .firstOrNull()?.let { mapHeaderLocationName = it }
                showingBottomSheet = true
            }
            return
        }
        loadNearbyMoments(currentRegion, key)
    }

    fun setupMapWithCoordinate(lat: Double, lon: Double) {
        if (hasInitializedMap) return
        focusNonce += 1
        focusCenter = Point.fromLngLat(lon, lat)
        focusZoom = MapRegionStore.zoomFromLongitudeDelta(0.01)
        currentRegion = MapRegionStore.Region(lat, lon, 0.01, 0.01)
        hasInitializedMap = true
        isLoading = false
        errorMessage = null
        nearbyMoments = emptyList()
        lastNearbyQueryKey = nearbyQueryKey(currentRegion)
        showSearchInAreaButton = true
        loadLocationMoments()
        loadWeather(lat, lon)
    }

    fun setupDefaultLocation(showMessage: Boolean) {
        val lat = 40.4168
        val lon = -3.7038
        focusNonce += 1
        focusCenter = Point.fromLngLat(lon, lat)
        focusZoom = MapRegionStore.zoomFromLongitudeDelta(0.05)
        currentRegion = MapRegionStore.Region(lat, lon, 0.05, 0.05)
        hasInitializedMap = true
        isLoading = false
        // ≡ iOS `setupDefaultLocation`: mensaje en `errorMessage` → `modernErrorView`.
        errorMessage = if (showMessage) defaultLocationMsg else null
        mapHeaderLocationName = defaultTitle
        nearbyMoments = emptyList()
        lastNearbyQueryKey = nearbyQueryKey(currentRegion)
        showSearchInAreaButton = true
        loadLocationMoments()
        loadWeather(lat, lon)
    }

    fun geocodeAndSetup() {
        scope.launch {
            when (val outcome = LocationMapViewSupport.geocodeLocationName(context, locationName)) {
                is LocationMapViewSupport.GeocodeOutcome.Success ->
                    setupMapWithCoordinate(outcome.latitude, outcome.longitude)
                LocationMapViewSupport.GeocodeOutcome.GenericFallback ->
                    setupDefaultLocation(showMessage = false)
                LocationMapViewSupport.GeocodeOutcome.NoResults -> {
                    // iOS: errorMessage + isLoading=false — sin mapa Madrid ni loadMoments
                    errorMessage = context.getString(R.string.maps_error_no_results, locationName)
                    isLoading = false
                }
                LocationMapViewSupport.GeocodeOutcome.Failed -> {
                    // iOS default CLError path → setupDefaultLocation(showMessage: true)
                    setupDefaultLocation(showMessage = true)
                }
            }
        }
    }

    /** ≡ iOS `setupMapLocation` — retry de `modernErrorView`. */
    fun setupMapLocation() {
        isLoading = true
        errorMessage = null
        locationPermissionGranted = LocationUtilities.hasForegroundPermission(context)
        when {
            latitude != null && longitude != null -> {
                if (hasInitializedMap) {
                    isLoading = false
                    loadLocationMoments()
                } else {
                    setupMapWithCoordinate(latitude, longitude)
                }
            }
            LocationMapViewSupport.isGenericLocationQuery(context, locationName) ->
                setupDefaultLocation(showMessage = false)
            else -> geocodeAndSetup()
        }
    }

    fun handleMapRegionChanged(center: Point, zoom: Double) {
        val lonDelta = MapRegionStore.longitudeDeltaFromZoom(zoom)
        currentRegion = MapRegionStore.Region(
            centerLat = center.latitude(),
            centerLon = center.longitude(),
            latitudeDelta = lonDelta,
            longitudeDelta = lonDelta,
        )
        if (!hasInitializedMap) return
        // iOS: cualquier cambio de región → showSearchInAreaButton = true
        showSearchInAreaButton = true
    }

    fun openPlaceStories(cluster: MapPlaceCluster, startingAt: MapStoryPreview? = null) {
        if (cluster.stories.isEmpty() || isOpeningStory) return
        isOpeningStory = true
        val presentation = LocationMapStoryViewerPresentation(
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
        val available = cluster.moments.filter {
            momentAvailability[it.mapAvailabilityKey] ?: !isEchoHistoryMode
        }
        if (cluster.storyCount == 0 && available.isEmpty()) return
        if (available.isEmpty() && cluster.primaryStory != null) {
            openPlaceStories(cluster)
            return
        }
        locationMoments = available.ifEmpty { cluster.moments }
        refreshMomentAvailability(locationMoments)
        cluster.displayName.trim().takeIf { it.isNotEmpty() }?.let { mapHeaderLocationName = it }
        selectedPlaceCluster = cluster
        showingBottomSheet = true
    }

    fun openMomentDetail(at: Int, pool: List<Moment> = locationMoments) {
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
            presentation = MomentZoomPresentationKind.Map(effectiveHeaderLocationName),
            setDestination = { zoomDestination = it },
            zoomIDPrefix = "location-map",
        )
    }

    fun selectPlaceFromIndex(place: MapPlaceCluster) {
        selectedPlaceCluster = place
        val available = place.moments.filter {
            momentAvailability[it.mapAvailabilityKey] ?: !isEchoHistoryMode
        }
        locationMoments = available.ifEmpty { place.moments }
        refreshMomentAvailability(locationMoments)
        place.displayName.trim().takeIf { it.isNotEmpty() }?.let { mapHeaderLocationName = it }
        currentRegion = MapRegionStore.Region(
            centerLat = place.latitude,
            centerLon = place.longitude,
            latitudeDelta = 0.015,
            longitudeDelta = 0.015,
        )
        focusNonce += 1
        focusCenter = Point.fromLngLat(place.longitude, place.latitude)
        focusZoom = MapRegionStore.zoomFromLongitudeDelta(0.015)
    }

    fun closeLocationMap() {
        showingBottomSheet = false
        zoomDestination = null
        storyViewerPresentation = null
        pendingStoryPresentation = null
        onDismiss()
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
            if (isViewActive && sheetCluster.totalCount > 0) showingBottomSheet = true
        }
    }

    BackHandler(onBack = ::closeLocationMap)

    DisposableEffect(Unit) {
        isViewActive = true
        onDispose { isViewActive = false }
    }

    LaunchedEffect(Unit) {
        if (mapHeaderLocationName.isBlank()) mapHeaderLocationName = locationName
        when {
            latitude != null && longitude != null -> setupMapWithCoordinate(latitude, longitude)
            LocationMapViewSupport.isGenericLocationQuery(context, locationName) ->
                setupDefaultLocation(showMessage = false)
            LocationUtilities.hasForegroundPermission(context) -> {
                LocationUtilities.getCurrentLocation(context) { point ->
                    if (!isViewActive) return@getCurrentLocation
                    if (point != null) {
                        userLatitude = point.latitude()
                        userLongitude = point.longitude()
                    }
                    if (point != null && LocationMapViewSupport.isGenericLocationQuery(context, locationName)) {
                        setupMapWithCoordinate(point.latitude(), point.longitude())
                    } else {
                        geocodeAndSetup()
                    }
                }
            }
            else -> {
                // iOS: solo pide permiso y espera authorizationStatus — no geocode en paralelo
                locationGate.requestAccess(context) {
                    locationPermissionGranted = LocationUtilities.hasForegroundPermission(context)
                    LocationUtilities.getCurrentLocation(context) { point ->
                        if (!isViewActive) return@getCurrentLocation
                        if (point != null) {
                            userLatitude = point.latitude()
                            userLongitude = point.longitude()
                        }
                        if (point != null && LocationMapViewSupport.isGenericLocationQuery(context, locationName)) {
                            setupMapWithCoordinate(point.latitude(), point.longitude())
                        } else {
                            geocodeAndSetup()
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(showingBottomSheet) {
        if (!showingBottomSheet) presentDeferredMapContent()
    }

    // iOS: onChange(of: sheetCluster.totalCount) → dismiss si 0 y no loading
    LaunchedEffect(sheetCluster.totalCount, isLoadingMoments, showingBottomSheet) {
        if (showingBottomSheet && sheetCluster.totalCount == 0 && !isLoadingMoments) {
            showingBottomSheet = false
        }
    }

    LaunchedEffect(zoomDestination) {
        if (zoomDestination == null) {
            zoomMapMomentsPool = emptyList()
            restoreBottomSheetIfNeeded()
        }
    }

    Box(modifier.fillMaxSize()) {
        // ≡ iOS `modernMapView`
        when {
            isLoading -> ModernLocationLoadingView(locationName = effectiveHeaderLocationName)
            errorMessage != null -> ModernLocationErrorView(
                message = errorMessage!!,
                locationPermissionGranted = locationPermissionGranted,
                onRetry = ::setupMapLocation,
            )
            else -> {
                MapCanvasSection(
                    location = mapLocation,
                    placeClusters = locationMapPlaceLayout.placeClusters,
                    onPlaceClusterTap = ::openPlaceCluster,
                    onCameraIdle = { center, zoom ->
                        if (!hasInitializedMap) return@MapCanvasSection
                        handleMapRegionChanged(center, zoom)
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (weatherEffectsEnabled) {
                    currentWeather?.let { weather ->
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
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            // iOS: VStack centra sus hijos
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                // iOS: HStack { pill; Spacer(); weather } — el pill toma su tamaño natural
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MapHeaderSection(
                    title = effectiveHeaderLocationName,
                    subtitle = subtitle,
                    closeStyle = MapHeaderCloseStyle.Location,
                    onClose = ::closeLocationMap,
                    modifier = Modifier.weight(1f, fill = false),
                )
                currentWeather?.let { weather ->
                    Column(horizontalAlignment = Alignment.End) {
                        Row(
                            Modifier
                                .shadow(10.dp, RoundedCornerShape(percent = 50), clip = false)
                                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(percent = 50))
                                .clickable {
                                    HapticManager.shared.lightImpact()
                                    weatherEffectsEnabled = !weatherEffectsEnabled
                                }
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                weather.condition.icon(weatherEffectsEnabled),
                                null,
                                tint = if (weatherEffectsEnabled) {
                                    weather.condition.accentColor()
                                } else {
                                    primary.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.size(16.dp),
                            )
                            Column {
                                Text(weather.temperatureFormatted, color = primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(weather.condition.displayName(), color = secondary, fontSize = 9.sp, maxLines = 1)
                            }
                        }
                        if (weatherEffectsEnabled) {
                            Row(
                                Modifier.padding(end = 8.dp, top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(stringResource(R.string.weather_attribution_text), color = secondary.copy(alpha = 0.8f), fontSize = 7.sp)
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
            }

            contentErrorMessage?.takeIf { errorMessage == null }?.let { message ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .momentsChromeGlass(RoundedCornerShape(16.dp), interactive = false)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF9500), modifier = Modifier.size(14.dp))
                    Text(
                        message,
                        color = primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.maps_error_retry),
                        color = colors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = ::searchInCurrentArea),
                    )
                }
            }

            AnimatedVisibility(
                visible = hasInitializedMap && showSearchInAreaButton && errorMessage == null && !isLoading,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier
                            .shadow(10.dp, RoundedCornerShape(percent = 50), clip = false)
                            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                            .clickable(onClick = ::searchInCurrentArea)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isLoadingNearbyMoments) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = colors.accent, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Search, null, tint = primary, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            stringResource(R.string.maps_search_this_area),
                            color = primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            if (locationMoments.isNotEmpty() && errorMessage == null && !isLoading) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Column(
                        Modifier
                            .shadow(10.dp, RoundedCornerShape(30.dp), clip = false)
                            .momentsChromeGlass(RoundedCornerShape(30.dp), interactive = true)
                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(30.dp))
                            .clickable {
                                HapticManager.shared.lightImpact()
                                // ≡ iOS: tap stats → toggle bottom sheet (no gallery)
                                showingBottomSheet = !showingBottomSheet
                            }
                            .padding(horizontal = 10.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LocationStatItem(
                            tintColor = colors.accent,
                            icon = { Icon(Icons.Filled.Photo, null, tint = colors.accent, modifier = Modifier.size(12.dp)) },
                            value = "${locationMoments.size}",
                            label = stringResource(R.string.maps_stats_photos),
                            primary = primary,
                            tertiary = tertiary,
                        )
                        LocationStatItem(
                            tintColor = Color(0xFF007AFF),
                            icon = { Icon(Icons.Filled.People, null, tint = Color(0xFF007AFF), modifier = Modifier.size(12.dp)) },
                            value = "${locationMoments.map { it.authorId }.toSet().size}",
                            label = stringResource(R.string.maps_stats_users),
                            primary = primary,
                            tertiary = tertiary,
                        )
                        LocationStatItem(
                            tintColor = Color(0xFFFF9500),
                            icon = { Icon(Icons.Filled.CalendarMonth, null, tint = Color(0xFFFF9500), modifier = Modifier.size(12.dp)) },
                            value = formatDateRange(locationMoments),
                            label = stringResource(R.string.maps_stats_time),
                            primary = primary,
                            tertiary = tertiary,
                        )
                    }
                }
            }
        }

        LaunchedEffect(focusNonce) {
            val point = focusCenter ?: return@LaunchedEffect
            handleMapRegionChanged(point, focusZoom)
        }

        LocationPermissionGateHost(gate = locationGate)
    }

    if (showingBottomSheet) {
        MomentsModalSheet(
            onDismissRequest = { showingBottomSheet = false },
            largeOnly = false,
        ) {
            MapPlaceBottomSheet(
                cluster = sheetCluster,
                momentAvailability = momentAvailability,
                isLoading = isLoadingMoments,
                onMomentTap = { momentId ->
                    val index = sheetCluster.moments.indexOfFirst { it.id == momentId || selectionKey(it) == momentId }
                    if (index >= 0) {
                        locationMoments = sheetCluster.moments
                        openMomentDetail(index, sheetCluster.moments)
                    }
                },
                onPlaceStoriesTap = { openPlaceStories(it) },
                weather = currentWeather,
                userLatitude = userLatitude,
                userLongitude = userLongitude,
                placeIndex = contextualPlaceIndex,
                onPlaceTap = if (contextualPlaceIndex.isEmpty()) null else { place -> selectPlaceFromIndex(place) },
                onDismiss = { showingBottomSheet = false },
            )
        }
    }

    if (showingGallery) {
        Dialog(
            onDismissRequest = { showingGallery = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ModernLocationGalleryView(
                locationName = effectiveHeaderLocationName,
                moments = locationMoments,
                onDismiss = { showingGallery = false },
                modifier = Modifier.fillMaxSize().background(if (isDark) Color.Black else Color.White),
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
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
    }
}

private data class LocationMapStoryViewerPresentation(
    val id: String = UUID.randomUUID().toString(),
    val previews: List<MapStoryPreview>,
    val initialPreviewId: String?,
)

/** ≡ iOS `StatisticItem` (Maps.swift). */
@Composable
private fun LocationStatItem(
    tintColor: Color,
    icon: @Composable () -> Unit,
    value: String,
    label: String,
    primary: Color,
    tertiary: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // iOS: Circle().fill(color.opacity(0.15)).frame(32x32) detrás del icono
        Box(
            Modifier
                .size(32.dp)
                .background(tintColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(
                label.uppercase(),
                color = tertiary,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

private fun formatDateRange(moments: List<Moment>): String {
    if (moments.isEmpty()) return "N/A"
    val sorted = moments.sortedBy { it.timestamp.time }
    val oldest = sorted.first().timestamp
    val newest = sorted.last().timestamp
    val cal = Calendar.getInstance()
    cal.time = oldest
    val m1 = cal.get(Calendar.MONTH)
    val y1 = cal.get(Calendar.YEAR)
    cal.time = newest
    val m2 = cal.get(Calendar.MONTH)
    val y2 = cal.get(Calendar.YEAR)
    val oldestLabel = MomentsFormat.smartDate(oldest, MomentsFormat.DateContext.MONTH_ABBREVIATED)
    return if (m1 == m2 && y1 == y2) {
        oldestLabel
    } else {
        val newestLabel = MomentsFormat.smartDate(newest, MomentsFormat.DateContext.MONTH_ABBREVIATED)
        "$oldestLabel-$newestLabel"
    }
}

private fun Double.roundToHundredths(): Double = (this * 100).toLong() / 100.0
