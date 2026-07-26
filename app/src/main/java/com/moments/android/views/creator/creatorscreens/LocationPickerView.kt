package com.moments.android.views.creator.creatorscreens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import com.moments.android.views.feed.maps.FeedMaps
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.permission.shared.LocationPermissionGate
import com.moments.android.views.permission.shared.LocationPermissionGateHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val DefaultCenter = LatLng(41.3874, 2.1686) // Barcelona ≡ iOS
private const val MapZoom = 13.5f // ≈ span 0.05

/**
 * Port de `LocationPickerView.swift`.
 * MapKit → Google Maps Compose si hay key; Geocoder ≈ MKLocalSearch.
 */
@Composable
fun LocationPickerView(
    selectedLocation: Moment.LocationCoordinate?,
    locationName: String,
    onSelectedLocationChange: (Moment.LocationCoordinate?) -> Unit,
    onLocationNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = rememberAdaptiveColors()
    val scope = rememberCoroutineScope()
    val locationGate = remember { LocationPermissionGate() }

    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceHit>>(emptyList()) }
    var nearbyPlaces by remember { mutableStateOf<List<PlaceHit>>(emptyList()) }
    var showingNearbyPlaces by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }
    var isRequestingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var mapCenter by remember {
        mutableStateOf(
            selectedLocation?.let { LatLng(it.latitude, it.longitude) } ?: DefaultCenter,
        )
    }
    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }

    val permissionDeniedMessage = stringResource(R.string.creator_location_permission_denied)
    val currentLocationFallback = stringResource(R.string.creator_location_current)
    val selectedFallback = stringResource(R.string.creator_location_selected)
    val unnamedPlace = stringResource(R.string.creator_location_unnamed)
    val categoryPlace = stringResource(R.string.creator_location_category_place)
    val nearbyQueries = listOf(
        stringResource(R.string.sticker_location_query_restaurants) to
            ("restaurant" to stringResource(R.string.creator_location_category_restaurant)),
        stringResource(R.string.sticker_location_query_cafes) to
            ("cafe" to stringResource(R.string.creator_location_category_cafe)),
        stringResource(R.string.sticker_location_query_shops) to
            ("store" to stringResource(R.string.creator_location_category_store)),
        stringResource(R.string.sticker_location_query_parks) to
            ("park" to stringResource(R.string.creator_location_category_park)),
        stringResource(R.string.sticker_location_query_museums) to
            ("museum" to stringResource(R.string.creator_location_category_museum)),
        stringResource(R.string.sticker_location_query_hotels) to
            ("hotel" to stringResource(R.string.creator_location_category_hotel)),
        stringResource(R.string.sticker_location_query_pharmacies) to
            ("pharmacy" to stringResource(R.string.creator_location_category_pharmacy)),
        stringResource(R.string.sticker_location_query_banks) to
            ("bank" to stringResource(R.string.creator_location_category_bank)),
        stringResource(R.string.sticker_location_query_metro) to
            ("metro" to stringResource(R.string.creator_location_category_metro)),
        stringResource(R.string.sticker_location_query_libraries) to
            ("library" to stringResource(R.string.creator_location_category_library)),
    )

    fun commitSelection(coord: Moment.LocationCoordinate, name: String) {
        onSelectedLocationChange(coord)
        onLocationNameChange(name)
        mapCenter = LatLng(coord.latitude, coord.longitude)
    }

    fun loadNearby() {
        scope.launch {
            val center = withContext(Dispatchers.IO) {
                lastKnownLocation(context)?.let { it.latitude to it.longitude }
                    ?: selectedLocation?.let { it.latitude to it.longitude }
                    ?: (mapCenter.latitude to mapCenter.longitude)
            }
            nearbyPlaces = withContext(Dispatchers.IO) {
                loadNearbyPlaces(context, center.first, center.second, nearbyQueries, categoryPlace)
            }
        }
    }

    fun applyCurrentLocation(alsoSelectIfEmpty: Boolean = true) {
        scope.launch {
            isRequestingLocation = true
            locationError = null
            val loc = withContext(Dispatchers.IO) { lastKnownLocation(context) }
            if (loc != null) {
                val coord = Moment.LocationCoordinate(loc.latitude, loc.longitude)
                val name = withContext(Dispatchers.IO) {
                    generateCleanLocationName(context, loc.latitude, loc.longitude)
                } ?: currentLocationFallback
                if (alsoSelectIfEmpty || selectedLocation == null) {
                    commitSelection(coord, name)
                } else {
                    mapCenter = LatLng(loc.latitude, loc.longitude)
                }
                loadNearby()
            }
            isRequestingLocation = false
            hasPermission = hasLocationPermission(context)
        }
    }

    fun requestCurrentLocation() {
        locationError = null
        if (hasLocationPermission(context)) {
            hasPermission = true
            applyCurrentLocation(alsoSelectIfEmpty = true)
        } else {
            isRequestingLocation = false
            locationGate.requestAccess(context) {
                hasPermission = true
                applyCurrentLocation(alsoSelectIfEmpty = true)
            }
        }
    }

    fun updateCurrentLocationAndNearbyPlaces() {
        locationError = null
        if (!hasLocationPermission(context)) {
            locationError = permissionDeniedMessage
            return
        }
        applyCurrentLocation(alsoSelectIfEmpty = selectedLocation == null)
    }

    fun runSearch() {
        if (searchText.isBlank()) return
        scope.launch {
            isSearching = true
            showingNearbyPlaces = false
            val origin = withContext(Dispatchers.IO) {
                lastKnownLocation(context)?.let { it.latitude to it.longitude }
                    ?: (mapCenter.latitude to mapCenter.longitude)
            }
            searchResults = withContext(Dispatchers.IO) {
                geocodeSearch(
                    context = context,
                    query = searchText,
                    originLat = origin.first,
                    originLng = origin.second,
                    halfSpanDegrees = 0.05,
                    maxResults = 20,
                    defaultCategoryLabel = categoryPlace,
                )
            }
            isSearching = false
        }
    }

    LaunchedEffect(Unit) { loadNearby() }

    var wasGatePresenting by remember { mutableStateOf(false) }
    LaunchedEffect(locationGate.isPresenting) {
        val granted = hasLocationPermission(context)
        hasPermission = granted
        // ≡ iOS onReceive denied — solo tras cerrar el gate sin grant
        if (wasGatePresenting && !locationGate.isPresenting && !granted) {
            locationError = permissionDeniedMessage
            isRequestingLocation = false
        }
        if (granted) locationError = null
        wasGatePresenting = locationGate.isPresenting
    }

    Box(modifier.fillMaxSize().background(colors.surfaceBackground)) {
        Column(Modifier.fillMaxSize()) {
            // Toolbar ≡ NavigationStack toolbar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = onDismiss),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.creator_add_location),
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.creator_tag_done),
                    color = if (selectedLocation != null) colors.primary else colors.secondary.copy(alpha = 0.4f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable(enabled = selectedLocation != null, onClick = onDismiss),
                )
            }

            // Search bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = colors.secondary, modifier = Modifier.size(18.dp))
                BasicTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.primary, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    decorationBox = { inner ->
                        if (searchText.isEmpty()) {
                            Text(
                                stringResource(R.string.creator_location_search),
                                color = colors.secondary,
                                fontSize = 15.sp,
                            )
                        }
                        inner()
                    },
                )
                if (searchText.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        null,
                        tint = colors.secondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                searchText = ""
                                showingNearbyPlaces = true
                            },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Map ≡ MapKit 200pt
            LocationPickerMap(
                center = mapCenter,
                selected = selectedLocation,
                markerTitle = locationName.ifBlank { stringResource(R.string.creator_location_selected) },
                onCameraMoved = { mapCenter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )

            // Current location + Update
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .clickable(enabled = !isRequestingLocation, onClick = ::requestCurrentLocation)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isRequestingLocation) {
                        CircularProgressIndicator(
                            color = colors.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        AttachmentIconView(
                            icon = AttachmentIcon.LOCATION,
                            preset = AttachmentIconPreset.LOCATION_PICKER_INLINE,
                            tintColor = colors.primary,
                        )
                    }
                    Text(
                        stringResource(R.string.creator_location_use_current),
                        color = colors.primary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }

                if (hasPermission) {
                    Spacer(Modifier.width(16.dp))
                    Row(
                        Modifier
                            .clickable(enabled = !isRequestingLocation, onClick = ::updateCurrentLocationAndNearbyPlaces)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.common_update),
                            color = colors.primary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
            }

            locationError?.let { error ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF9500), modifier = Modifier.size(16.dp))
                    Text(error, color = Color(0xFFFF9500), fontSize = 12.sp)
                }
            }

            if (isSearching) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.creator_searching),
                            color = colors.secondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                val places = if (showingNearbyPlaces) nearbyPlaces else searchResults
                LazyColumn(Modifier.fillMaxSize()) {
                    if (showingNearbyPlaces) {
                        item {
                            Text(
                                stringResource(R.string.creator_location_nearby),
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp),
                            )
                        }
                    }
                    items(places, key = { "${it.lat},${it.lng},${it.name}" }) { place ->
                        LocationRow(
                            place = place,
                            primary = colors.primary,
                            secondary = colors.secondary,
                            isDark = colors.isDark,
                            unnamedLabel = unnamedPlace,
                            onTap = {
                                commitSelection(
                                    Moment.LocationCoordinate(place.lat, place.lng),
                                    place.name.ifBlank { selectedFallback },
                                )
                            },
                        )
                    }
                }
            }
        }

        LocationPermissionGateHost(gate = locationGate)
    }
}

@Composable
private fun LocationPickerMap(
    center: LatLng,
    selected: Moment.LocationCoordinate?,
    markerTitle: String,
    onCameraMoved: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasKey = FeedMaps.hasGoogleMapsKey()
    if (!hasKey) {
        Box(
            modifier.background(Color.Gray.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Place, null, tint = Color(0xFF007AFF), modifier = Modifier.size(28.dp))
                Text(markerTitle, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        return
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, MapZoom)
    }
    LaunchedEffect(center.latitude, center.longitude) {
        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(center, MapZoom))
        onCameraMoved(center)
    }
    val markerLatLng = selected?.let { LatLng(it.latitude, it.longitude) }
    val markerState = rememberMarkerState(position = markerLatLng ?: center)
    LaunchedEffect(markerLatLng?.latitude, markerLatLng?.longitude) {
        markerLatLng?.let { markerState.position = it }
    }

    GoogleMap(
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
        ),
        modifier = modifier,
    ) {
        if (selected != null) {
            Marker(state = markerState, title = markerTitle)
        }
    }
}

@Composable
private fun LocationRow(
    place: PlaceHit,
    primary: Color,
    secondary: Color,
    isDark: Boolean,
    unnamedLabel: String,
    onTap: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onTap)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                place.categoryIcon,
                null,
                tint = primary,
                modifier = Modifier
                    .width(30.dp)
                    .size(18.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    place.name.ifBlank { unnamedLabel },
                    color = primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
                Text(place.categoryLabel, color = secondary, fontSize = 12.sp)
                if (place.subtitle.isNotBlank()) {
                    Text(place.subtitle, color = secondary.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = secondary,
                modifier = Modifier.size(14.dp),
            )
        }
        HorizontalDivider(
            color = if (isDark) Color.Gray.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f),
        )
    }
}

private data class PlaceHit(
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double,
    val categoryKey: String = "place",
    val categoryLabel: String = "",
) {
    val categoryIcon: ImageVector
        get() = when (categoryKey) {
            "restaurant" -> Icons.Filled.Restaurant
            "cafe" -> Icons.Filled.LocalCafe
            "store" -> Icons.Filled.Store
            "park" -> Icons.Filled.Park
            "museum" -> Icons.Filled.Museum
            "hotel" -> Icons.Filled.Hotel
            "pharmacy" -> Icons.Filled.LocalPharmacy
            "bank" -> Icons.Filled.AccountBalance
            "metro" -> Icons.Filled.DirectionsSubway
            "library" -> Icons.Filled.LocalLibrary
            else -> Icons.Filled.Place
        }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun lastKnownLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    return providers.mapNotNull { provider ->
        runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }
}

/** ≡ iOS `loadNearbyPlaces` — prefix(5), ≤3/categoría, ≤15 unique. Queries localizadas. */
private suspend fun loadNearbyPlaces(
    context: Context,
    lat: Double,
    lng: Double,
    queries: List<Pair<String, Pair<String, String>>>,
    defaultCategoryLabel: String,
): List<PlaceHit> =
    coroutineScope {
        val batches = queries.take(5).map { (query, cat) ->
            async(Dispatchers.IO) {
                geocodeSearch(
                    context = context,
                    query = query,
                    originLat = lat,
                    originLng = lng,
                    halfSpanDegrees = 0.025,
                    maxResults = 4,
                    forcedCategory = cat.first,
                    forcedCategoryLabel = cat.second,
                    defaultCategoryLabel = defaultCategoryLabel,
                ).take(3)
            }
        }.awaitAll().flatten()

        val seen = linkedSetOf<String>()
        batches.filter { place ->
            seen.add("%.5f,%.5f".format(place.lat, place.lng))
        }.take(15)
    }

@Suppress("DEPRECATION")
private fun geocodeSearch(
    context: Context,
    query: String,
    originLat: Double?,
    originLng: Double?,
    halfSpanDegrees: Double = 0.05,
    maxResults: Int = 12,
    forcedCategory: String? = null,
    forcedCategoryLabel: String? = null,
    defaultCategoryLabel: String = "Place",
): List<PlaceHit> {
    if (!Geocoder.isPresent()) return emptyList()
    val geocoder = Geocoder(context, Locale.getDefault())
    val addresses = runCatching {
        if (originLat != null && originLng != null) {
            val d = halfSpanDegrees
            geocoder.getFromLocationName(
                query,
                maxResults,
                originLat - d,
                originLng - d,
                originLat + d,
                originLng + d,
            ) ?: geocoder.getFromLocationName(query, maxResults)
        } else {
            geocoder.getFromLocationName(query, maxResults)
        }
    }.getOrNull().orEmpty()

    return addresses.mapNotNull { addr ->
        addressToPlace(addr, forcedCategory, forcedCategoryLabel, defaultCategoryLabel)
    }.distinctBy { "%.5f,%.5f".format(it.lat, it.lng) }
}

private fun addressToPlace(
    addr: Address,
    forcedCategory: String?,
    forcedCategoryLabel: String?,
    defaultCategoryLabel: String,
): PlaceHit? {
    val name = addr.featureName?.takeIf { it.isNotBlank() && it != addr.thoroughfare }
        ?: addr.thoroughfare
        ?: addr.locality
        ?: return null
    val subtitle = listOfNotNull(addr.subLocality, addr.locality, addr.adminArea)
        .distinct()
        .joinToString(", ")
        .ifBlank { addr.getAddressLine(0).orEmpty() }
    return PlaceHit(
        name = name,
        subtitle = subtitle,
        lat = addr.latitude,
        lng = addr.longitude,
        categoryKey = forcedCategory ?: "place",
        categoryLabel = forcedCategoryLabel ?: defaultCategoryLabel,
    )
}

/** ≡ iOS `generateCleanLocationName` / reverse geocode. */
@Suppress("DEPRECATION")
private fun generateCleanLocationName(context: Context, lat: Double, lng: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.getDefault())
    val addr = runCatching { geocoder.getFromLocation(lat, lng, 1) }.getOrNull().orEmpty().firstOrNull()
        ?: return null

    val name = addr.featureName?.takeIf { it.isNotBlank() }
    val locality = addr.locality?.takeIf { it.isNotBlank() }
    if (name != null) {
        return if (locality != null && name != locality) "$name, $locality" else name
    }
    val thoroughfare = addr.thoroughfare?.takeIf { it.isNotBlank() }
    if (thoroughfare != null) {
        return if (locality != null) "$thoroughfare, $locality" else thoroughfare
    }
    if (locality != null) return locality
    return addr.adminArea?.takeIf { it.isNotBlank() }
}
