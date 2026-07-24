package com.moments.android.views.creator.creatorscreens
import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Port de `LocationPickerView.swift` (sin MapKit completo: search + GPS + lista).
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
    val isDark = isSystemInDarkTheme()
    val canvas = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val fg = if (isDark) Color.White else Color.Black
    val secondary = fg.copy(0.55f)
    val scope = rememberCoroutineScope()

    var searchText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceHit>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isRequestingLocation by remember { mutableStateOf(false) }
    var localCoord by remember { mutableStateOf(selectedLocation) }
    var localName by remember { mutableStateOf(locationName) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            scope.launch {
                isRequestingLocation = true
                val loc = withContext(Dispatchers.IO) { lastKnownLocation(context) }
                if (loc != null) {
                    localCoord = Moment.LocationCoordinate(loc.latitude, loc.longitude)
                    localName = withContext(Dispatchers.IO) {
                        reverseGeocode(context, loc.latitude, loc.longitude)
                    } ?: context.getString(R.string.creator_location_current)
                }
                isRequestingLocation = false
            }
        }
    }

    fun runSearch() {
        if (searchText.isBlank()) {
            results = emptyList()
            return
        }
        scope.launch {
            isSearching = true
            results = withContext(Dispatchers.IO) { geocodeSearch(context, searchText) }
            isSearching = false
        }
    }

    Column(modifier.fillMaxSize().background(canvas)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = fg, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.creator_add_location),
                color = fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(enabled = localCoord != null) {
                        onSelectedLocationChange(localCoord)
                        onLocationNameChange(localName)
                        onDismiss()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    null,
                    tint = if (localCoord != null) Color(0xFFE91E63) else secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = secondary, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                singleLine = true,
                textStyle = TextStyle(color = fg, fontSize = 15.sp),
                cursorBrush = SolidColor(fg),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                decorationBox = { inner ->
                    if (searchText.isEmpty()) {
                        Text(stringResource(R.string.creator_location_search), color = secondary, fontSize = 15.sp)
                    }
                    inner()
                },
            )
            if (searchText.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    null,
                    tint = secondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            searchText = ""
                            results = emptyList()
                        },
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.LocationOn, null, tint = Color(0xFF007AFF), modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    localName.ifBlank { stringResource(R.string.creator_location_selected) },
                    color = fg,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
                localCoord?.let {
                    Text(
                        "%.4f, %.4f".format(it.latitude, it.longitude),
                        color = secondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .clickable {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
                        scope.launch {
                            isRequestingLocation = true
                            val loc = withContext(Dispatchers.IO) { lastKnownLocation(context) }
                            if (loc != null) {
                                localCoord = Moment.LocationCoordinate(loc.latitude, loc.longitude)
                                localName = withContext(Dispatchers.IO) {
                                    reverseGeocode(context, loc.latitude, loc.longitude)
                                } ?: context.getString(R.string.creator_location_current)
                            }
                            isRequestingLocation = false
                        }
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isRequestingLocation) {
                CircularProgressIndicator(color = Color(0xFF007AFF), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.MyLocation, null, tint = fg, modifier = Modifier.size(18.dp))
            }
            Text(
                stringResource(R.string.creator_location_use_current),
                color = fg,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (isSearching) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF007AFF))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(results, key = { "${it.lat},${it.lng},${it.name}" }) { place ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                localCoord = Moment.LocationCoordinate(place.lat, place.lng)
                                localName = place.name
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.LocationOn, null, tint = secondary, modifier = Modifier.size(20.dp))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(place.name, color = fg, fontWeight = FontWeight.Medium)
                            if (place.subtitle.isNotBlank()) {
                                Text(place.subtitle, color = secondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PlaceHit(val name: String, val subtitle: String, val lat: Double, val lng: Double)

@Suppress("DEPRECATION")
private fun geocodeSearch(context: android.content.Context, query: String): List<PlaceHit> {
    if (!Geocoder.isPresent()) return emptyList()
    val geocoder = Geocoder(context, Locale.getDefault())
    val addresses = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            // API async omitted for sync helper; fallback list
            geocoder.getFromLocationName(query, 12)
        } else {
            geocoder.getFromLocationName(query, 12)
        }
    }.getOrNull().orEmpty()
    return addresses.mapNotNull { addr ->
        val name = addr.featureName?.takeIf { it.isNotBlank() }
            ?: addr.thoroughfare
            ?: addr.locality
            ?: return@mapNotNull null
        val subtitle = listOfNotNull(addr.subLocality, addr.locality, addr.countryName)
            .distinct()
            .joinToString(", ")
        PlaceHit(name, subtitle, addr.latitude, addr.longitude)
    }
}

@Suppress("DEPRECATION")
private fun reverseGeocode(context: android.content.Context, lat: Double, lng: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.getDefault())
    val addresses = runCatching { geocoder.getFromLocation(lat, lng, 1) }.getOrNull().orEmpty()
    val addr = addresses.firstOrNull() ?: return null
    return listOfNotNull(addr.thoroughfare, addr.subLocality, addr.locality)
        .distinct()
        .joinToString(", ")
        .ifBlank { addr.getAddressLine(0) }
}

private fun lastKnownLocation(context: android.content.Context): Location? {
    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    return providers.mapNotNull { provider ->
        runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }
}
