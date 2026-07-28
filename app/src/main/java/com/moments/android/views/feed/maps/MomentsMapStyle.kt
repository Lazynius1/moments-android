package com.moments.android.views.feed.maps

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapboxMapComposable
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.ThemeValue
import com.mapbox.maps.extension.compose.style.standard.rememberStandardStyleState

/**
 * Estilo Moments para Mapbox Standard — equivalente a `MapStyle.standard` de MapKit:
 * - theme **default** (a todo color, como Apple Maps estándar)
 * - `realisticElevation` ≡ `.standard(elevation: .realistic)` de iOS (objetos 3D)
 * - lightPreset según sistema (day / night)
 *
 * Docs: https://docs.mapbox.com/map-styles/standard/api/
 *       https://docs.mapbox.com/android/maps/guides/styles/set-a-style/
 */
object MomentsMapStyle {
    /** pitch plano — sin tilt 3D. */
    const val CAMERA_PITCH = 0.0

    /** ≈ iOS `MapRegionStore.spainCenter`. */
    val DEFAULT_CENTER: Point get() = MapRegionStore.spainCenter

    /** ≈ span 0.08 → zoom ~12. */
    const val DEFAULT_ZOOM = 12.0
}

/**
 * @param realisticElevation ≡ iOS `.standard(elevation: .realistic)` (DiscoverMapView).
 *   Con `false` equivale a `.standard` a secas (LocationMapView).
 */
@Composable
@MapboxMapComposable
fun MomentsMapboxStandardStyle(realisticElevation: Boolean = false) {
    val isDark = isSystemInDarkTheme()
    MapboxStandardStyle(
        standardStyleState = rememberStandardStyleState {
            configurationsState.apply {
                theme = ThemeValue.DEFAULT
                show3dObjects = BooleanValue(realisticElevation)
                lightPreset = if (isDark) LightPresetValue.NIGHT else LightPresetValue.DAY
            }
        },
    )
}
