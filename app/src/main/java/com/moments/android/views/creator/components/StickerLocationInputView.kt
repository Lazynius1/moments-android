package com.moments.android.views.creator.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Port de `StickerLocationInputView.swift` / `SmartLocationInputView`.
 * MapKit POI → Geocoder + last-known GPS (mismo contrato onSelect).
 */
data class StickerLocationResult(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val address: String,
    val distanceMeters: Double? = null,
    val latitude: Double,
    val longitude: Double,
) {
    val distanceString: String
        get() {
            val d = distanceMeters ?: return ""
            return if (d < 1000) "${d.roundToInt()}m" else String.format(Locale.getDefault(), "%.1fkm", d / 1000.0)
        }
}

@Composable
fun SmartLocationInputView(
    onSelect: (displayName: String, latitude: Double?, longitude: Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val fg = if (isDark) Color.White else Color.Black.copy(0.92f)
    val muted = if (isDark) Color.White.copy(0.58f) else Color.Black.copy(0.48f)
    val tertiary = muted.copy(0.85f)
    val accent = Color(0xFF8752FA)
    val scope = rememberCoroutineScope()

    var searchText by remember { mutableStateOf("") }
    var nearbyPlaces by remember { mutableStateOf<List<StickerLocationResult>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<StickerLocationResult>>(emptyList()) }
    var isLoadingNearby by remember { mutableStateOf(true) }
    var isSearching by remember { mutableStateOf(false) }
    var userLatLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) loadNearby() else {
            isLoadingNearby = false
            nearbyPlaces = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission()) {
            loadNearby()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
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
                geocodeSearch(context, query, origin?.first, origin?.second)
            }
            isSearching = false
        }
    }

    Column(
        modifier
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
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Text(
                    stringResource(R.string.sticker_location_search_subtitle),
                    color = muted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                )
            }
            if (hasLocationPermission()) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(fg.copy(0.08f))
                        .clickable(enabled = !isLoadingNearby) { loadNearby() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = fg, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.sticker_location_refresh),
                        color = fg,
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
                tint = if (searchText.isEmpty()) muted else accent,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    runSearch(it)
                },
                singleLine = true,
                textStyle = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(fg),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (searchText.isEmpty()) {
                        Text(
                            stringResource(R.string.sticker_location_search_placeholder),
                            color = muted,
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
                    tint = muted,
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
                        SectionLabel(
                            stringResource(R.string.sticker_location_searching_nearby),
                            accent = Color(0xFF2196F3),
                            fg = muted,
                        )
                    }
                    items(5) {
                        SkeletonLocationRow(isDark)
                    }
                } else if (nearbyPlaces.isEmpty()) {
                    item {
                        EmptyNearbyBlock(fg, muted)
                    }
                } else {
                    item {
                        SectionLabel(
                            stringResource(R.string.sticker_location_nearby),
                            accent = Color(0xFFE53935),
                            fg = muted,
                        )
                    }
                    items(nearbyPlaces, key = { it.id }) { place ->
                        LocationRow(place, fg, muted, tertiary) {
                            onSelect(place.displayName, place.latitude, place.longitude)
                        }
                    }
                }
            } else {
                if (isSearching) {
                    item {
                        SectionLabel(
                            stringResource(R.string.sticker_location_searching),
                            accent = Color(0xFF2196F3),
                            fg = muted,
                        )
                    }
                    items(3) { SkeletonLocationRow(isDark) }
                } else if (searchResults.isEmpty()) {
                    item {
                        EmptySearchBlock(searchText, fg, muted)
                    }
                } else {
                    item {
                        val count = searchResults.size
                        SectionLabel(
                            if (count == 1) {
                                stringResource(R.string.sticker_location_results_one, count)
                            } else {
                                stringResource(R.string.sticker_location_results_other, count)
                            },
                            accent = Color(0xFF43A047),
                            fg = muted,
                        )
                    }
                    items(searchResults, key = { it.id }) { place ->
                        LocationRow(place, fg, muted, tertiary) {
                            onSelect(place.displayName, place.latitude, place.longitude)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, accent: Color, fg: Color) {
    Row(
        Modifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(accent, CircleShape),
        )
        Text(title, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun LocationRow(
    place: StickerLocationResult,
    fg: Color,
    muted: Color,
    tertiary: Color,
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
        Icon(Icons.Filled.LocationOn, null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                place.displayName,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (place.address.isNotBlank()) {
                    Text(place.address, color = muted, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1)
                }
                if (place.distanceString.isNotEmpty()) {
                    Text("• ${place.distanceString}", color = tertiary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SkeletonLocationRow(isDark: Boolean) {
    val fill = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(fill, CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(width = 140.dp, height = 14.dp)
                    .background(fill, RoundedCornerShape(4.dp)),
            )
            Box(
                Modifier
                    .size(width = 100.dp, height = 12.dp)
                    .background(fill, RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun EmptyNearbyBlock(fg: Color, muted: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.LocationOff, null, tint = muted, modifier = Modifier.size(40.dp))
        Text(
            stringResource(R.string.sticker_nearby_places_error),
            color = fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Text(
            stringResource(R.string.sticker_location_permission_error),
            color = muted,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun EmptySearchBlock(query: String, fg: Color, muted: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.LocationOff, null, tint = muted, modifier = Modifier.size(40.dp))
        Text(
            stringResource(R.string.sticker_no_places_found),
            color = fg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Text(
            stringResource(R.string.sticker_try_different_search, query),
            color = muted,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
        )
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

@Suppress("DEPRECATION")
private fun loadNearbyPlaces(context: Context, lat: Double, lng: Double): List<StickerLocationResult> {
    val results = linkedMapOf<String, StickerLocationResult>()
    reverseGeocodePlace(context, lat, lng)?.let { results[it.displayName] = it }

    val queries = listOf(
        context.getString(R.string.sticker_location_query_restaurants),
        context.getString(R.string.sticker_location_query_cafes),
        context.getString(R.string.sticker_location_query_shops),
        context.getString(R.string.sticker_location_query_parks),
    )
    for (query in queries) {
        geocodeSearch(context, query, lat, lng).take(2).forEach { hit ->
            results.putIfAbsent("${hit.displayName}|${hit.latitude}|${hit.longitude}", hit)
        }
    }
    return results.values
        .sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
        .take(12)
}

@Suppress("DEPRECATION")
private fun geocodeSearch(
    context: Context,
    query: String,
    originLat: Double?,
    originLng: Double?,
): List<StickerLocationResult> {
    if (!Geocoder.isPresent()) return emptyList()
    val geocoder = Geocoder(context, Locale.getDefault())
    val addresses = runCatching {
        if (originLat != null && originLng != null) {
            // Prefer results near user when possible (bounded box ~1.5km-ish via lower left/upper right).
            val d = 0.015
            geocoder.getFromLocationName(query, 12, originLat - d, originLng - d, originLat + d, originLng + d)
                ?: geocoder.getFromLocationName(query, 12)
        } else {
            geocoder.getFromLocationName(query, 12)
        }
    }.getOrNull().orEmpty()

    return addresses.mapNotNull { addr ->
        val name = addr.featureName?.takeIf { it.isNotBlank() && it.any { ch -> ch.isLetter() } }
            ?: addr.thoroughfare
            ?: addr.locality
            ?: return@mapNotNull null
        val address = listOfNotNull(addr.subLocality, addr.locality, addr.countryName)
            .distinct()
            .joinToString(", ")
        val distance = if (originLat != null && originLng != null) {
            val out = FloatArray(1)
            Location.distanceBetween(originLat, originLng, addr.latitude, addr.longitude, out)
            out[0].toDouble()
        } else {
            null
        }
        StickerLocationResult(
            displayName = name,
            address = address,
            distanceMeters = distance,
            latitude = addr.latitude,
            longitude = addr.longitude,
        )
    }.distinctBy { "${it.displayName}|${"%.5f".format(it.latitude)}|${"%.5f".format(it.longitude)}" }
}

@Suppress("DEPRECATION")
private fun reverseGeocodePlace(context: Context, lat: Double, lng: Double): StickerLocationResult? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.getDefault())
    val addr = runCatching { geocoder.getFromLocation(lat, lng, 1) }.getOrNull().orEmpty().firstOrNull()
        ?: return null
    val name = listOfNotNull(addr.thoroughfare, addr.subLocality, addr.locality)
        .distinct()
        .joinToString(", ")
        .ifBlank { addr.getAddressLine(0).orEmpty() }
        .ifBlank { return null }
    val address = listOfNotNull(addr.locality, addr.countryName).distinct().joinToString(", ")
    return StickerLocationResult(
        displayName = name.take(48),
        address = address,
        distanceMeters = 0.0,
        latitude = lat,
        longitude = lng,
    )
}
