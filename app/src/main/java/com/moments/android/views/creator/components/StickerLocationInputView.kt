package com.moments.android.views.creator.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.permission.shared.LocationPermissionGate
import com.moments.android.views.permission.shared.LocationPermissionGateHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Port de `StickerLocationInputView.swift` / `SmartLocationInputView`.
 * MapKit POI → Geocoder (mismo contrato onSelect + UI).
 */
data class StickerLocationResult(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val fullName: String = displayName,
    val address: String,
    val distanceMeters: Double? = null,
    val category: String = "place",
    val latitude: Double,
    val longitude: Double,
) {
    val distanceString: String
        get() {
            val d = distanceMeters ?: return ""
            return if (d < 1000) "${d.roundToInt()}m" else String.format(Locale.getDefault(), "%.1fkm", d / 1000.0)
        }

    /** ≡ `categoryIcon` SF Symbols → Material. */
    val categoryIcon: ImageVector
        get() = when {
            category.contains("restaurant", ignoreCase = true) ||
                category.contains("food", ignoreCase = true) ||
                category.contains("cafe", ignoreCase = true) -> Icons.Filled.Restaurant
            category.contains("shopping", ignoreCase = true) ||
                category.contains("store", ignoreCase = true) ||
                category.contains("shop", ignoreCase = true) -> Icons.Filled.ShoppingBag
            category.contains("entertainment", ignoreCase = true) ||
                category.contains("museum", ignoreCase = true) -> Icons.Filled.TheaterComedy
            category.contains("gas", ignoreCase = true) -> Icons.Filled.LocalGasStation
            category.contains("hospital", ignoreCase = true) -> Icons.Filled.LocalHospital
            category.contains("school", ignoreCase = true) -> Icons.Filled.School
            category.contains("park", ignoreCase = true) -> Icons.Filled.Park
            category.contains("gym", ignoreCase = true) -> Icons.Filled.FitnessCenter
            category.contains("hotel", ignoreCase = true) -> Icons.Filled.Hotel
            category.contains("pharmacy", ignoreCase = true) -> Icons.Filled.LocalPharmacy
            category.contains("bank", ignoreCase = true) -> Icons.Filled.AccountBalance
            category.contains("metro", ignoreCase = true) ||
                category.contains("transit", ignoreCase = true) -> Icons.Filled.Train
            category.contains("library", ignoreCase = true) -> Icons.Filled.LocalLibrary
            else -> Icons.Filled.LocationOn
        }
}

@Composable
fun SmartLocationInputView(
    onSelect: (displayName: String, latitude: Double?, longitude: Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val palette = rememberStickerDetailPalette()
    val scope = rememberCoroutineScope()
    val locationGate = remember { LocationPermissionGate() }
    val focusRequester = remember { FocusRequester() }

    var searchText by remember { mutableStateOf("") }
    var nearbyPlaces by remember { mutableStateOf<List<StickerLocationResult>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<StickerLocationResult>>(emptyList()) }
    var isLoadingNearby by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }
    var userLatLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    fun cleanupMemory() {
        nearbyPlaces = emptyList()
        searchResults = emptyList()
        searchText = ""
        isSearching = false
        isLoadingNearby = false
        searchJob?.cancel()
    }

    fun loadNearby() {
        scope.launch {
            isLoadingNearby = true
            val loc = withContext(Dispatchers.IO) { lastKnownLocation(context) }
            if (loc == null) {
                nearbyPlaces = emptyList()
                isLoadingNearby = false
                return@launch
            }
            userLatLng = loc.latitude to loc.longitude
            nearbyPlaces = withContext(Dispatchers.IO) {
                loadNearbyPlaces(context, loc.latitude, loc.longitude)
            }
            isLoadingNearby = false
        }
    }

    fun requestLocationAndSearch() {
        hasPermission = true
        loadNearby()
    }

    LaunchedEffect(Unit) {
        delay(50)
        runCatching { focusRequester.requestFocus() }
        locationGate.requestAccess(context) { requestLocationAndSearch() }
    }

    LaunchedEffect(locationGate.isPresenting, hasPermission) {
        if (!locationGate.isPresenting && !hasPermission) {
            isLoadingNearby = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { cleanupMemory() }
    }

    fun runSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return
        }
        searchJob = scope.launch {
            delay(280)
            isSearching = true
            val origin = userLatLng
            searchResults = withContext(Dispatchers.IO) {
                geocodeSearch(
                    context = context,
                    query = query,
                    originLat = origin?.first,
                    originLng = origin?.second,
                    // ≡ 5km search region
                    halfSpanDegrees = 0.045,
                    maxResults = 15,
                ).sortedWith(searchRelevanceComparator())
            }
            isSearching = false
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.sticker_location_search_title),
                        color = palette.primaryText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                    Text(
                        stringResource(R.string.sticker_location_search_subtitle),
                        color = palette.secondaryText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                }
                if (hasPermission) {
                    Row(
                        Modifier
                            .clipCapsuleStroke(palette)
                            .clickable(enabled = !isLoadingNearby) { loadNearby() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, null, tint = palette.primaryText, modifier = Modifier.size(14.dp))
                        Text(
                            stringResource(R.string.sticker_location_refresh),
                            color = palette.primaryText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.Search,
                    null,
                    tint = if (searchText.isEmpty()) palette.searchIcon else palette.searchIconActive,
                    modifier = Modifier.size(18.dp),
                )
                BasicTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                        runSearch(it)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = palette.primaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(palette.primaryText),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (searchText.isEmpty()) {
                            Text(
                                stringResource(R.string.sticker_location_search_placeholder),
                                color = palette.secondaryText,
                                fontSize = 16.sp,
                            )
                        }
                        inner()
                    },
                )
                if (searchText.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        null,
                        tint = palette.clearIcon,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                searchText = ""
                                searchResults = emptyList()
                                isSearching = false
                            },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (searchText.isEmpty()) {
                    if (isLoadingNearby) {
                        item {
                            LocationSectionHeader(
                                title = stringResource(R.string.sticker_location_searching_nearby),
                                accent = Color(0xFF007AFF),
                                muted = palette.secondaryText,
                                useAttachment = false,
                            )
                        }
                        items(5) { SkeletonLocationRow(palette) }
                    } else if (nearbyPlaces.isEmpty()) {
                        item { EmptyNearbyBlock(palette) }
                    } else {
                        item {
                            LocationSectionHeader(
                                title = stringResource(R.string.sticker_location_nearby),
                                accent = Color(0xFFFF3B30),
                                muted = palette.secondaryText,
                                useAttachment = true,
                            )
                        }
                        items(nearbyPlaces, key = { it.id }) { place ->
                            LocationRow(place, palette) {
                                onSelect(place.displayName, place.latitude, place.longitude)
                            }
                        }
                    }
                } else {
                    if (isSearching) {
                        item {
                            LocationSectionHeader(
                                title = stringResource(R.string.sticker_location_searching),
                                accent = Color(0xFF007AFF),
                                muted = palette.secondaryText,
                                useAttachment = false,
                            )
                        }
                        items(3) { SkeletonLocationRow(palette) }
                    } else if (searchResults.isEmpty()) {
                        item { EmptySearchBlock(searchText, palette) }
                    } else {
                        item {
                            val count = searchResults.size
                            LocationSectionHeader(
                                title = if (count == 1) {
                                    stringResource(R.string.sticker_location_results_one, count)
                                } else {
                                    stringResource(R.string.sticker_location_results_other, count)
                                },
                                accent = Color(0xFF34C759),
                                muted = palette.secondaryText,
                                useAttachment = true,
                            )
                        }
                        items(searchResults, key = { it.id }) { place ->
                            LocationRow(place, palette) {
                                onSelect(place.displayName, place.latitude, place.longitude)
                            }
                        }
                    }
                }
            }
        }

        LocationPermissionGateHost(gate = locationGate)
    }
}

private fun Modifier.clipCapsuleStroke(palette: StickerDetailPalette): Modifier =
    this
        .background(palette.buttonFill, RoundedCornerShape(50))
        .border(1.dp, palette.fieldStroke, RoundedCornerShape(50))

@Composable
private fun LocationSectionHeader(
    title: String,
    accent: Color,
    muted: Color,
    useAttachment: Boolean,
) {
    Row(
        Modifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (useAttachment) {
            AttachmentIconView(
                icon = AttachmentIcon.LOCATION,
                preset = AttachmentIconPreset.STICKER_SECTION_HEADER,
                tintColor = accent,
            )
        } else {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
        }
        Text(title, color = muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun LocationRow(
    place: StickerLocationResult,
    palette: StickerDetailPalette,
    onTap: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            place.categoryIcon,
            null,
            tint = Color(0xFFFF3B30),
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                place.displayName,
                color = palette.primaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (place.address.isNotBlank()) {
                    Text(
                        place.address,
                        color = palette.secondaryText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (place.distanceString.isNotEmpty()) {
                    Text(
                        "• ${place.distanceString}",
                        color = palette.tertiaryText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = palette.tertiaryText,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SkeletonLocationRow(palette: StickerDetailPalette) {
    val fill = palette.skeletonFill
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(fill, CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(width = 140.dp, height = 14.dp).background(fill, RoundedCornerShape(4.dp)))
            Box(Modifier.size(width = 100.dp, height = 12.dp).background(fill, RoundedCornerShape(4.dp)))
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun EmptyNearbyBlock(palette: StickerDetailPalette) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Icon(Icons.Filled.LocationOff, null, tint = palette.secondaryText, modifier = Modifier.size(40.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.sticker_nearby_places_error),
                color = palette.primaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Text(
                stringResource(R.string.sticker_location_permission_error),
                color = palette.secondaryText,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun EmptySearchBlock(query: String, palette: StickerDetailPalette) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.LocationOff, null, tint = palette.secondaryText, modifier = Modifier.size(40.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.sticker_no_places_found),
                color = palette.primaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Text(
                stringResource(R.string.sticker_try_different_search, query),
                color = palette.secondaryText,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
    }
}

private fun lastKnownLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    return providers.mapNotNull { provider ->
        runCatching {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                lm.getLastKnownLocation(provider)
            } else {
                null
            }
        }.getOrNull()
    }.maxByOrNull { it.time }
}

/** ≡ `searchNearbyPlaces` — 10 queries, `prefix(4)`, ≤2 por categoría, ≤12 total. */
private suspend fun loadNearbyPlaces(context: Context, lat: Double, lng: Double): List<StickerLocationResult> =
    coroutineScope {
        val queries = listOf(
            R.string.sticker_location_query_restaurants to "restaurant",
            R.string.sticker_location_query_cafes to "food",
            R.string.sticker_location_query_shops to "store",
            R.string.sticker_location_query_parks to "park",
            R.string.sticker_location_query_museums to "museum",
            R.string.sticker_location_query_hotels to "hotel",
            R.string.sticker_location_query_pharmacies to "pharmacy",
            R.string.sticker_location_query_banks to "bank",
            R.string.sticker_location_query_metro to "metro",
            R.string.sticker_location_query_libraries to "library",
        ).take(4)

        val batches = queries.map { (resId, category) ->
            async(Dispatchers.IO) {
                geocodeSearch(
                    context = context,
                    query = context.getString(resId),
                    originLat = lat,
                    originLng = lng,
                    halfSpanDegrees = 0.015, // ≡ 1.5km
                    maxResults = 4,
                    forcedCategory = category,
                ).take(2)
            }
        }.awaitAll().flatten()

        val current = reverseGeocodePlace(context, lat, lng)
        val seen = linkedSetOf<String>()
        val unique = mutableListOf<StickerLocationResult>()
        (listOfNotNull(current) + batches).forEach { place ->
            val key = "%.5f,%.5f".format(place.latitude, place.longitude)
            if (seen.add(key)) unique += place
        }
        unique.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }.take(12)
    }

@Suppress("DEPRECATION")
private fun geocodeSearch(
    context: Context,
    query: String,
    originLat: Double?,
    originLng: Double?,
    halfSpanDegrees: Double = 0.045,
    maxResults: Int = 12,
    forcedCategory: String? = null,
): List<StickerLocationResult> {
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
        addressToResult(addr, originLat, originLng, forcedCategory)
    }.distinctBy { "%.5f,%.5f".format(it.latitude, it.longitude) }
}

@Suppress("DEPRECATION")
private fun reverseGeocodePlace(context: Context, lat: Double, lng: Double): StickerLocationResult? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.getDefault())
    val addr = runCatching { geocoder.getFromLocation(lat, lng, 1) }.getOrNull().orEmpty().firstOrNull()
        ?: return null
    return addressToResult(addr, lat, lng, category = "place")?.copy(distanceMeters = 0.0)
}

/** ≡ `formatAddress` + LocationResult mapping. */
private fun addressToResult(
    addr: Address,
    originLat: Double?,
    originLng: Double?,
    category: String? = null,
): StickerLocationResult? {
    if (!addr.hasLatitude() || !addr.hasLongitude()) return null
    val name = addr.featureName?.takeIf { it.isNotBlank() && it.any(Char::isLetter) }
        ?: addr.thoroughfare
        ?: addr.locality
        ?: return null
    val address = formatAddress(addr)
    val fullName = if (address.isBlank()) name else "$name, $address"
    val distance = if (originLat != null && originLng != null) {
        val out = FloatArray(1)
        Location.distanceBetween(originLat, originLng, addr.latitude, addr.longitude, out)
        out[0].toDouble()
    } else {
        null
    }
    return StickerLocationResult(
        displayName = name,
        fullName = fullName,
        address = address,
        distanceMeters = distance,
        category = category ?: "place",
        latitude = addr.latitude,
        longitude = addr.longitude,
    )
}

private fun formatAddress(addr: Address): String {
    val components = buildList {
        listOfNotNull(addr.subThoroughfare, addr.thoroughfare)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
            ?.let(::add)
        addr.postalCode?.takeIf { it.isNotBlank() }?.let(::add)
        addr.locality?.takeIf { it.isNotBlank() }?.let(::add)
        addr.adminArea?.takeIf { it.isNotBlank() }?.let(::add)
        addr.countryName?.takeIf { it.isNotBlank() }?.let(::add)
    }
    return components.joinToString(", ")
}

/** ≡ sort de `searchPlaces`: nombre → POI → distancia. */
private fun searchRelevanceComparator(): Comparator<StickerLocationResult> =
    compareByDescending<StickerLocationResult> { it.displayName.isNotEmpty() }
        .thenByDescending { it.category != "place" }
        .thenBy { it.distanceMeters ?: Double.MAX_VALUE }
