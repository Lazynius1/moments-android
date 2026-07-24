package com.moments.android.views.messaging.attachments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.moments.android.BuildConfig
import com.moments.android.R
import com.moments.android.utilities.HapticManager
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.messaging.models.LiveLocationDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

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
    var pendingLiveDuration by remember { mutableStateOf<LiveLocationDuration?>(null) }

    fun loadCurrentLocation() {
        scope.launch {
            val location = withContext(Dispatchers.IO) { currentLastKnownLocation(context) } ?: return@launch
            if (hasCenteredOnUser) return@launch
            hasCenteredOnUser = true
            currentLatitude = location.latitude
            currentLongitude = location.longitude
            accuracyMeters = location.accuracy.takeIf { it >= 0f }
            val currentAddress = withContext(Dispatchers.IO) {
                reverseGeocodeAddress(context, location.latitude, location.longitude)
            }
            currentPlaceName = currentAddress?.name
            currentPlaceAddress = currentAddress?.shortAddress
            nearbyPlaces = withContext(Dispatchers.IO) {
                searchNearbyPlaces(context, location.latitude, location.longitude)
            }
        }
    }

    fun startLiveIfPermitted(duration: LiveLocationDuration) {
        pendingLiveDuration = null
        onStartLive(duration)
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // iOS continúa en primer plano si el usuario rechaza Always; Android hace lo mismo.
        pendingLiveDuration?.let(::startLiveIfPermitted)
    }
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (!grants.values.any { it }) return@rememberLauncherForActivityResult
        val duration = pendingLiveDuration
        if (duration != null && requiresBackgroundLocation(context)) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else if (duration != null) {
            startLiveIfPermitted(duration)
        } else {
            loadCurrentLocation()
        }
    }

    fun requestLiveLocation(duration: LiveLocationDuration) {
        pendingLiveDuration = duration
        if (!hasForegroundLocationPermission(context)) {
            foregroundLocationLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        } else if (requiresBackgroundLocation(context)) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            startLiveIfPermitted(duration)
        }
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
                    searchPlaces(context, trimmed, currentLatitude, currentLongitude)
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
        if (hasForegroundLocationPermission(context)) loadCurrentLocation()
    }
    DisposableEffect(Unit) {
        onDispose { searchJob?.cancel() }
    }

    val isShowingSearch = searchText.trim().isNotEmpty()
    val listedPlaces = if (isShowingSearch) searchResults else nearbyPlaces
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(top = SEARCH_OVERLAY_HEIGHT, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!isShowingSearch) {
                item(key = "map") {
                    ChatLocationMapPreview(
                        latitude = currentLatitude,
                        longitude = currentLongitude,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
                item(key = "current") {
                    ChatLocationActionRow(
                        icon = AttachmentIcon.LOCATION,
                        tint = accentColor,
                        title = stringResource(R.string.chat_location_send_current),
                        subtitle = accuracyMeters?.let {
                            stringResource(R.string.chat_location_accuracy, it.toInt())
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
                        text = stringResource(if (isShowingSearch) R.string.chat_location_no_results else R.string.chat_location_no_nearby),
                        color = secondaryText,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    )
                }
                else -> items(listedPlaces, key = { it.id }) { place ->
                    ChatLocationPlaceRow(
                        place = place,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onClick = {
                            HapticManager.shared.lightImpact()
                            onSendStatic(place.latitude, place.longitude, place.name, place.address)
                        },
                    )
                }
            }
        }
        ChatLocationSearchField(
            value = searchText,
            primaryText = primaryText,
            secondaryText = secondaryText,
            onValueChange = ::scheduleSearch,
            onClear = { scheduleSearch("") },
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
}

@Composable
private fun ChatLocationMapPreview(latitude: Double, longitude: Double, modifier: Modifier = Modifier) {
    val center = LatLng(latitude, longitude)
    val cameraPositionState = rememberCameraPositionState()
    LaunchedEffect(latitude, longitude) {
        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(center, MAP_PREVIEW_ZOOM))
    }
    GoogleMap(
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = false,
            scrollGesturesEnabled = false,
            tiltGesturesEnabled = false,
            zoomControlsEnabled = false,
            zoomGesturesEnabled = false,
        ),
        modifier = modifier,
    ) {
        Marker(state = rememberMarkerState(position = center))
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
        text = stringResource(titleRes),
        color = secondaryText,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun ChatLocationSearchField(
    value: String,
    primaryText: Color,
    secondaryText: Color,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (primaryText == Color.White) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = secondaryText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(stringResource(R.string.chat_location_search_places), color = secondaryText, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = primaryText, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = secondaryText,
                modifier = Modifier.size(20.dp).clickable(onClick = onClear),
            )
        }
    }
}

private data class ReverseGeocodedAddress(val name: String?, val shortAddress: String?)

private fun hasForegroundLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun requiresBackgroundLocation(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED

@Suppress("DEPRECATION")
private fun currentLastKnownLocation(context: Context): Location? {
    if (!hasForegroundLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
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

private fun placeFields() = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)

private suspend fun searchNearbyPlaces(context: Context, latitude: Double, longitude: Double): List<ChatLocationPlace> {
    val client = ensurePlacesClient(context) ?: return emptyList()
    val request = SearchNearbyRequest.builder(CircularBounds.newInstance(LatLng(latitude, longitude), NEARBY_RADIUS_METERS), placeFields())
        .setMaxResultCount(20)
        .build()
    return client.searchNearby(request).await().places.mapNotNull(::toChatLocationPlace)
}

private suspend fun searchPlaces(
    context: Context,
    query: String,
    latitude: Double,
    longitude: Double,
): List<ChatLocationPlace> {
    val client = ensurePlacesClient(context) ?: return emptyList()
    val request = SearchByTextRequest.builder(query, placeFields())
        .setMaxResultCount(20)
        .setLocationBias(CircularBounds.newInstance(LatLng(latitude, longitude), SEARCH_RADIUS_METERS))
        .build()
    return client.searchByText(request).await().places.mapNotNull(::toChatLocationPlace)
}

private fun toChatLocationPlace(place: Place): ChatLocationPlace? {
    val coordinate = place.latLng ?: return null
    val name = place.name?.takeIf { it.isNotBlank() } ?: return null
    return ChatLocationPlace(
        id = place.id ?: "${coordinate.latitude}:${coordinate.longitude}:$name",
        name = name,
        address = place.address?.takeIf { it.isNotBlank() },
        latitude = coordinate.latitude,
        longitude = coordinate.longitude,
    )
}

private val BARCELONA_LATITUDE = 41.3874
private val BARCELONA_LONGITUDE = 2.1686
private const val MAP_PREVIEW_ZOOM = 16f
private const val NEARBY_RADIUS_METERS = 1_000.0
private const val SEARCH_RADIUS_METERS = 10_000.0
private const val SEARCH_DEBOUNCE_MILLIS = 350L
private val SEARCH_OVERLAY_HEIGHT = 60.dp
