package com.moments.android.views.messaging.attachments

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.plugin.gestures.generated.GesturesSettings
import com.moments.android.BuildConfig
import com.moments.android.R
import com.moments.android.utilities.HapticManager
import com.moments.android.views.feed.maps.FeedMaps
import com.moments.android.views.feed.maps.LocationUtilities
import com.moments.android.views.feed.maps.MapRegionStore
import com.moments.android.views.feed.maps.MomentsMapStyle
import com.moments.android.views.feed.maps.MomentsMapboxStandardStyle
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.messaging.components.ChatAttachmentSearchField
import com.moments.android.views.messaging.components.ChatAttachmentSheetMetrics
import com.moments.android.views.messaging.models.LiveLocationDuration
import com.moments.android.views.permission.shared.LocationPermissionAccessLevel
import com.moments.android.views.permission.shared.LocationPermissionGate
import com.moments.android.views.permission.shared.LocationPermissionGateHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/** Port de `Views/Messaging/Attachments/ChatLocationSheet.swift`. */
data class ChatLocationPlace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
)

@Composable
fun ChatLocationSheetContent(
    accentColor: Color,
    onSendStatic: (latitude: Double, longitude: Double, name: String?, address: String?) -> Unit,
    onStartLive: (LiveLocationDuration) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black
    val secondaryText = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)

    var currentLatitude by remember { mutableStateOf(BARCELONA_LATITUDE) }
    var currentLongitude by remember { mutableStateOf(BARCELONA_LONGITUDE) }
    var accuracyMeters by remember { mutableStateOf<Float?>(null) }
    var currentPlaceName by remember { mutableStateOf<String?>(null) }
    var currentPlaceAddress by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }
    var nearbyPlaces by remember { mutableStateOf<List<ChatLocationPlace>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<ChatLocationPlace>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasCenteredOnUser by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var requestVersion by remember { mutableIntStateOf(0) }
    var showLiveDurationDialog by remember { mutableStateOf(false) }
    val locationGate = remember { LocationPermissionGate() }

    fun applyUserLocation(latitude: Double, longitude: Double, accuracy: Float?) {
        if (hasCenteredOnUser) return
        hasCenteredOnUser = true
        currentLatitude = latitude
        currentLongitude = longitude
        accuracyMeters = accuracy?.takeIf { it >= 0f }
        scope.launch {
            val currentAddress = withContext(Dispatchers.IO) {
                reverseGeocodeAddress(context, latitude, longitude)
            }
            currentPlaceName = currentAddress?.name
            currentPlaceAddress = currentAddress?.shortAddress
            nearbyPlaces = withContext(Dispatchers.IO) {
                runCatching { searchNearbyPlaces(context, latitude, longitude) }
                    .getOrDefault(emptyList())
            }
        }
    }

    // ≡ iOS `centerOnUserIfPossible` + LocationUtilities.currentLocation
    fun centerOnUserIfPossible() {
        if (hasCenteredOnUser) return
        if (!LocationUtilities.hasForegroundPermission(context)) return
        LocationUtilities.getCurrentLocation(context) { point ->
            if (point == null || hasCenteredOnUser) return@getCurrentLocation
            val accuracy = lastKnownAccuracyMeters(context)
            applyUserLocation(point.latitude(), point.longitude(), accuracy)
        }
    }

    fun requestLiveLocation(duration: LiveLocationDuration) {
        // ≡ locationGate.requestAccess(level: .always) { onStartLive(duration) }
        locationGate.requestAccess(
            context = context,
            level = LocationPermissionAccessLevel.ALWAYS,
            onGranted = { onStartLive(duration) },
        )
    }

    fun scheduleSearch(query: String) {
        searchText = query
        val trimmed = query.trim()
        searchJob?.cancel()
        if (trimmed.isEmpty()) {
            isSearching = false
            searchResults = emptyList()
            return
        }
        val version = ++requestVersion
        isSearching = true
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            try {
                val found = withContext(Dispatchers.IO) {
                    runCatching {
                        searchPlaces(context, trimmed, currentLatitude, currentLongitude)
                    }.getOrDefault(emptyList())
                }
                if (version == requestVersion) searchResults = found
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (version == requestVersion) searchResults = emptyList()
            } finally {
                if (version == requestVersion) isSearching = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // ≡ iOS onAppear: solo centra si ya hay permiso (no gate WHEN_IN_USE al abrir)
        centerOnUserIfPossible()
    }
    DisposableEffect(Unit) {
        onDispose { searchJob?.cancel() }
    }

    val isShowingSearch = searchText.trim().isNotEmpty()
    val listedPlaces = if (isShowingSearch) searchResults else nearbyPlaces
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(top = ChatAttachmentSheetMetrics.searchOverlayHeight, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!isShowingSearch) {
                item(key = "map") {
                    ChatLocationMapPreview(
                        latitude = currentLatitude,
                        longitude = currentLongitude,
                        accentColor = accentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
                item(key = "current") {
                    ChatLocationActionRow(
                        icon = AttachmentIcon.LOCATION,
                        tint = accentColor,
                        title = stringResource(R.string.chat_location_send_current),
                        subtitle = accuracyMeters?.let {
                            stringResource(R.string.chat_location_accuracy, it.roundToInt())
                        } ?: currentPlaceAddress ?: stringResource(R.string.chat_location_send_current_subtitle),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            HapticManager.shared.lightImpact()
                            onSendStatic(currentLatitude, currentLongitude, currentPlaceName, currentPlaceAddress)
                        },
                    )
                }
                item(key = "live") {
                    ChatLocationActionRow(
                        icon = AttachmentIcon.LIVE_LOCATION,
                        tint = Color(0xFF34C759),
                        title = stringResource(R.string.chat_location_share_live),
                        subtitle = stringResource(R.string.chat_location_live_subtitle),
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = { showLiveDurationDialog = true },
                    )
                }
                item(key = "nearby_header") {
                    ChatLocationSectionHeader(R.string.chat_location_nearby, secondaryText)
                }
            } else {
                item(key = "results_header") {
                    ChatLocationSectionHeader(R.string.chat_location_search_results, secondaryText)
                }
            }
            when {
                isSearching -> item(key = "searching") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                }
                listedPlaces.isEmpty() -> item(key = "empty") {
                    Text(
                        text = stringResource(
                            if (isShowingSearch) R.string.chat_location_no_results else R.string.chat_location_no_nearby,
                        ),
                        color = secondaryText,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    )
                }
                else -> itemsIndexed(listedPlaces, key = { _, place -> place.id }) { index, place ->
                    ChatLocationPlaceRow(
                        place = place,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            HapticManager.shared.lightImpact()
                            onSendStatic(place.latitude, place.longitude, place.name, place.address)
                        },
                    )
                    if (index != listedPlaces.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 60.dp),
                            color = secondaryText.copy(alpha = 0.18f),
                        )
                    }
                }
            }
        }
        ChatAttachmentSearchField(
            placeholderRes = R.string.chat_location_search_places,
            text = searchText,
            onTextChange = ::scheduleSearch,
            onClear = {
                searchText = ""
                searchResults = emptyList()
                isSearching = false
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (showLiveDurationDialog) {
        AlertDialog(
            onDismissRequest = { showLiveDurationDialog = false },
            title = { Text(stringResource(R.string.chat_location_share_live)) },
            text = {
                Column {
                    Text(stringResource(R.string.chat_location_live_permission_info), color = secondaryText)
                    LiveLocationDuration.entries.forEach { duration ->
                        Text(
                            text = stringResource(duration.titleRes),
                            color = primaryText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLiveDurationDialog = false
                                    requestLiveLocation(duration)
                                }
                                .padding(vertical = 14.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = accentColor,
                    modifier = Modifier.clickable { showLiveDurationDialog = false }.padding(12.dp),
                )
            },
        )
    }

    // ≡ .locationPermissionGate(locationGate)
    LocationPermissionGateHost(locationGate)
}

/** Preview pasivo Mapbox ≡ iOS `Map(interactionModes: [])` + pin centrado. */
@Composable
private fun ChatLocationMapPreview(
    latitude: Double,
    longitude: Double,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val previewZoom = remember {
        MapRegionStore.zoomFromLongitudeDelta(MAP_PREVIEW_LONGITUDE_DELTA)
    }
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(longitude, latitude))
            zoom(previewZoom)
            pitch(MomentsMapStyle.CAMERA_PITCH)
            bearing(0.0)
        }
    }
    val mapState = rememberMapState {
        gesturesSettings = GesturesSettings {
            scrollEnabled = false
            pinchToZoomEnabled = false
            rotateEnabled = false
            pitchEnabled = false
            doubleTapToZoomInEnabled = false
            doubleTouchToZoomOutEnabled = false
            quickZoomEnabled = false
            simultaneousRotateAndPinchToZoomEnabled = false
        }
    }

    LaunchedEffect(latitude, longitude) {
        mapViewportState.setCameraOptions {
            center(Point.fromLngLat(longitude, latitude))
            zoom(previewZoom)
            pitch(MomentsMapStyle.CAMERA_PITCH)
            bearing(0.0)
        }
    }

    Box(modifier = modifier) {
        if (FeedMaps.hasMapboxToken()) {
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
                mapState = mapState,
                style = { MomentsMapboxStandardStyle(realisticElevation = false) },
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = null, tint = accentColor, modifier = Modifier.size(34.dp))
            }
        }
        // ≡ iOS `location.circle.fill` centrado (no annotation del mapa)
        Box(
            Modifier
                .align(Alignment.Center)
                .size(28.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(accentColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MyLocation,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ChatLocationActionRow(
    icon: AttachmentIcon,
    tint: Color,
    title: String,
    subtitle: String,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        AttachmentIconView(icon, AttachmentIconPreset.LOCATION_SHEET_ROW, tint, Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = primaryText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = secondaryText, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ChatLocationPlaceRow(
    place: ChatLocationPlace,
    primaryText: Color,
    secondaryText: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        AttachmentIconView(AttachmentIcon.LOCATION, AttachmentIconPreset.LOCATION_SHEET_ROW, Color.Red, Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(place.name, color = primaryText, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            place.address?.let { Text(it, color = secondaryText, fontSize = 12.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun ChatLocationSectionHeader(@StringRes titleRes: Int, secondaryText: Color) {
    Text(
        text = stringResource(titleRes).uppercase(Locale.getDefault()),
        color = secondaryText,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp, bottom = 6.dp),
    )
}

private data class ReverseGeocodedAddress(val name: String?, val shortAddress: String?)

@Suppress("DEPRECATION")
private fun lastKnownAccuracyMeters(context: Context): Float? {
    if (!LocationUtilities.hasForegroundPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
        ?.accuracy
}

@Suppress("DEPRECATION")
private fun reverseGeocodeAddress(context: Context, latitude: Double, longitude: Double): ReverseGeocodedAddress? {
    if (!Geocoder.isPresent()) return null
    val address = runCatching {
        Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1).orEmpty().firstOrNull()
    }.getOrNull() ?: return null
    return ReverseGeocodedAddress(
        name = address.featureName ?: address.locality,
        shortAddress = address.shortAddress(),
    )
}

private fun Address.shortAddress(): String? =
    listOfNotNull(thoroughfare, locality).distinct().joinToString(", ").ifBlank { null }

private fun ensurePlacesClient(context: Context): com.google.android.libraries.places.api.net.PlacesClient? {
    val key = BuildConfig.GOOGLE_MAPS_API_KEY
    if (key.isBlank() || key.startsWith("REPLACE_")) return null
    if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context.applicationContext, key)
    return Places.createClient(context.applicationContext)
}

private fun placeFields() = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION)

/** Nearby ≡ iOS `MKLocalPointsOfInterestRequest` (Places API; no Mapbox Search en el proyecto). */
private suspend fun searchNearbyPlaces(context: Context, latitude: Double, longitude: Double): List<ChatLocationPlace> {
    val client = ensurePlacesClient(context) ?: return emptyList()
    val request = SearchNearbyRequest.builder(
        CircularBounds.newInstance(LatLng(latitude, longitude), NEARBY_RADIUS_METERS),
        placeFields(),
    )
        .setMaxResultCount(20)
        .build()
    return client.searchNearby(request).await().places.mapNotNull(::toChatLocationPlace)
}

/** Search ≡ iOS `MKLocalSearch` (Places SearchByText). */
private suspend fun searchPlaces(
    context: Context,
    query: String,
    latitude: Double,
    longitude: Double,
): List<ChatLocationPlace> {
    val client = ensurePlacesClient(context) ?: return emptyList()
    val request = SearchByTextRequest.builder(query, placeFields())
        .setMaxResultCount(25)
        .setLocationBias(CircularBounds.newInstance(LatLng(latitude, longitude), SEARCH_RADIUS_METERS))
        .build()
    return client.searchByText(request).await().places.mapNotNull(::toChatLocationPlace)
}

private fun toChatLocationPlace(place: Place): ChatLocationPlace? {
    val coordinate = place.location ?: return null
    val name = place.displayName?.takeIf { it.isNotBlank() } ?: return null
    val address = place.formattedAddress?.takeIf { it.isNotBlank() }?.let { formatted ->
        formatted.split(",").take(2).joinToString(",").trim().ifBlank { formatted }
    }
    return ChatLocationPlace(
        id = place.id ?: "${coordinate.latitude}:${coordinate.longitude}:$name",
        name = name,
        address = address,
        latitude = coordinate.latitude,
        longitude = coordinate.longitude,
    )
}

private val BARCELONA_LATITUDE = 41.3874
private val BARCELONA_LONGITUDE = 2.1686
/** ≡ iOS span `latitudeDelta: 0.008` tras centrar en usuario. */
private const val MAP_PREVIEW_LONGITUDE_DELTA = 0.008
private const val NEARBY_RADIUS_METERS = 1_000.0
private const val SEARCH_RADIUS_METERS = 10_000.0
private const val SEARCH_DEBOUNCE_MILLIS = 350L
