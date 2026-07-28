package com.moments.android.views.feed.maps.mapssections

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
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
import com.moments.android.views.feed.maps.FeedMaps
import com.moments.android.views.feed.maps.MapLocationData
import com.moments.android.views.feed.maps.MapPlacePin
import com.moments.android.views.feed.maps.MapRegionStore
import com.moments.android.views.feed.maps.MomentsMapStyle
import com.moments.android.views.feed.maps.MomentsMapboxStandardStyle
import com.moments.android.views.feed.maps.MapPlaceCluster
import com.moments.android.views.feed.maps.mapHasVideoMedia
import com.moments.android.views.feed.maps.mapPreferredImageUrl
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.shared.momentdetail.MomentDetailContainerView
import com.moments.android.views.shared.momentdetail.MomentDetailContext
import com.mapbox.geojson.Point
import com.mapbox.maps.ViewAnnotationAnchor

/**
 * Port de `MapCanvasSection.swift` + canvas Mapbox del LocationMapView.
 *
 * iOS en este archivo: `MapPlacePin` / `MapMomentPin` (en Android viven en
 * `DiscoverMapView.kt`, mismo módulo), `ModernLocationPin`, galerías.
 * El MapKit real está en Maps/Discover; aquí Android monta Mapbox para Location.
 */
@Composable
fun MapCanvasSection(
    location: MapLocationData?,
    modifier: Modifier = Modifier,
    showPlaceholderWhenNoKey: Boolean = true,
    placeClusters: List<MapPlaceCluster> = emptyList(),
    onPlaceClusterTap: (MapPlaceCluster) -> Unit = {},
    onCameraIdle: ((center: Point, zoom: Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    if (!FeedMaps.hasMapboxToken()) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color(0xFF2D3436)),
            contentAlignment = Alignment.Center,
        ) {
            if (showPlaceholderWhenNoKey) {
                Text(
                    stringResource(R.string.feed_map_placeholder),
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
        return
    }

    val initial = remember { MapRegionStore.initialRegion(context) }
    val startCenter = location?.coordinate?.let {
        Point.fromLngLat(it.longitude, it.latitude)
    } ?: initial.center
    val startZoom = if (location?.coordinate != null) {
        MomentsMapStyle.DEFAULT_ZOOM
    } else {
        initial.zoom
    }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(startCenter)
            zoom(startZoom)
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

    LaunchedEffect(location?.latitude, location?.longitude) {
        val coord = location?.coordinate ?: return@LaunchedEffect
        mapViewportState.setCameraOptions {
            center(Point.fromLngLat(coord.longitude, coord.latitude))
            zoom(MomentsMapStyle.DEFAULT_ZOOM)
            pitch(MomentsMapStyle.CAMERA_PITCH)
        }
    }

    MapboxMap(
        modifier = modifier.fillMaxSize(),
        mapViewportState = mapViewportState,
        mapState = mapState,
        style = { MomentsMapboxStandardStyle() },
    ) {
        if (onCameraIdle != null) {
            MapEffect(Unit) { mapView ->
                mapView.mapboxMap.subscribeMapIdle {
                    val state = mapView.mapboxMap.cameraState
                    onCameraIdle(state.center, state.zoom)
                }
            }
        }

        // iOS `Maps.swift` solo anota los placeClusters — no hay pin del centro.
        placeClusters.forEach { cluster ->
            ViewAnnotation(
                options = viewAnnotationOptions {
                    geometry(Point.fromLngLat(cluster.longitude, cluster.latitude))
                    annotationAnchor { anchor(ViewAnnotationAnchor.CENTER) }
                    allowOverlap(true)
                },
            ) {
                Box(
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onPlaceClusterTap(cluster) },
                ) {
                    MapPlacePin(cluster = cluster)
                }
            }
        }
    }
}

/** ≡ iOS `ModernLocationPin`. */
@Composable
fun ModernLocationPin(
    locationName: String,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val pulse = rememberInfiniteTransition(label = "locationPin")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "pinScale",
    )

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(45.dp)
                    .offset(y = 2.dp)
                    .background(Color.Black.copy(alpha = 0.2f), CircleShape),
            )
            Box(
                Modifier
                    .scale(scale)
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = false)
                    .border(
                        3.dp,
                        Brush.linearGradient(listOf(colors.accent, colors.accent.copy(alpha = 0.6f))),
                        CircleShape,
                    )
                    .shadow(8.dp, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MyLocation, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            locationName,
            color = colors.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = false)
                .border(1.dp, Brush.linearGradient(colors.overlayStroke), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** ≡ iOS `ModernLocationGallery`. */
@Composable
fun ModernLocationGallery(
    moments: List<Moment>,
    isLoading: Boolean,
    onMomentTap: (Moment) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(15.dp, RoundedCornerShape(24.dp), clip = false)
            .momentsChromeGlass(RoundedCornerShape(24.dp), interactive = false)
            .border(1.dp, Brush.linearGradient(colors.overlayStroke), RoundedCornerShape(24.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = colors.accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.maps_gallery_explore),
                color = colors.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.weight(1f))
            if (moments.isNotEmpty()) {
                Text(
                    stringResource(R.string.maps_gallery_see_all),
                    color = colors.accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .background(colors.accent.copy(alpha = 0.1f), CircleShape)
                        .clickable(onClick = onShowAll)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        when {
            isLoading -> {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 0.dp).padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(4) {
                        Box(
                            Modifier
                                .size(width = 85.dp, height = 110.dp)
                                .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = false),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            moments.isNotEmpty() -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 20.dp),
                ) {
                    itemsIndexed(moments.take(8), key = { _, m -> m.id ?: m.hashCode() }) { _, moment ->
                        Box(
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onMomentTap(moment) },
                        ) {
                            ModernLocationPhotoCard(moment = moment)
                        }
                    }
                }
            }
        }
    }
}

/** ≡ iOS `ModernLocationPhotoCard`. */
@Composable
fun ModernLocationPhotoCard(
    moment: Moment,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    var imageLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(moment.id) { imageLoaded = true }

    Box(
        modifier
            .width(90.dp)
            .height(120.dp)
            .scale(if (imageLoaded) 1f else 0.95f)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        if (moment.mapHasVideoMedia) {
            MapsVideoThumbnailView(moment = moment, cornerRadius = 14.dp, modifier = Modifier.fillMaxSize())
        } else {
            SubcomposeAsyncImage(
                model = moment.mapPreferredImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)),
                        RoundedCornerShape(14.dp),
                    ),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                },
            )
        }
    }
}

/** ≡ iOS `ModernLocationGalleryView`. */
@Composable
fun ModernLocationGalleryView(
    locationName: String,
    moments: List<Moment>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .momentsChromeGlass(RoundedCornerShape(0.dp), interactive = false)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onDismiss),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        locationName,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.maps_bottom_sheet_moments, moments.size),
                        color = colors.accent,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                    )
                }
            }

            if (moments.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.PhotoLibrary, null, tint = colors.tertiary, modifier = Modifier.size(50.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.maps_gallery_empty),
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(top = 2.dp),
                ) {
                    items(moments, key = { it.id ?: it.hashCode() }) { moment ->
                        val index = moments.indexOf(moment)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clickable {
                                    selectedIndex = index
                                },
                        ) {
                            if (moment.mapHasVideoMedia) {
                                MapsVideoThumbnailView(
                                    moment = moment,
                                    cornerRadius = 0.dp,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                SubcomposeAsyncImage(
                                    model = moment.mapPreferredImageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    loading = {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
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

    selectedIndex?.let { index ->
        Dialog(
            onDismissRequest = { selectedIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            MomentDetailContainerView(
                context = MomentDetailContext.Map(
                    moments = moments,
                    initialIndex = index,
                    locationName = locationName,
                    momentAvailability = emptyMap(),
                    onDismiss = { selectedIndex = null },
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
