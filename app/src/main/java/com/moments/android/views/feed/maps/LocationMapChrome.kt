package com.moments.android.views.feed.maps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.feed.rememberAdaptiveColors

/**
 * Chrome de `LocationMapView` en `Maps.swift`:
 * `modernLoadingView`, `modernErrorView`, `WeatherIndicatorView`, `WeatherAwareLocationPin`.
 */

fun WeatherCondition.icon(effectsEnabled: Boolean = true): ImageVector {
    if (!effectsEnabled) return Icons.Filled.CloudOff
    return when (this) {
        WeatherCondition.Clear -> Icons.Filled.WbSunny
        WeatherCondition.PartlyCloudy -> Icons.Filled.WbCloudy
        WeatherCondition.Cloudy -> Icons.Filled.Cloud
        WeatherCondition.Rain -> Icons.Filled.WaterDrop
        WeatherCondition.Snow -> Icons.Filled.AcUnit
        WeatherCondition.Thunderstorm -> Icons.Filled.Thunderstorm
        WeatherCondition.Unknown -> Icons.Filled.Help
    }
}

@Composable
fun WeatherCondition.displayName(): String = stringResource(
    when (this) {
        WeatherCondition.Clear -> R.string.weather_condition_clear
        WeatherCondition.PartlyCloudy -> R.string.weather_condition_partly_cloudy
        WeatherCondition.Cloudy -> R.string.weather_condition_cloudy
        WeatherCondition.Rain -> R.string.weather_condition_rain
        WeatherCondition.Snow -> R.string.weather_condition_snow
        WeatherCondition.Thunderstorm -> R.string.weather_condition_thunderstorm
        WeatherCondition.Unknown -> R.string.weather_condition_unknown
    },
)

fun WeatherCondition.accentColor(): Color = when (this) {
    WeatherCondition.Clear -> Color(0xFFFFCC00)
    WeatherCondition.PartlyCloudy -> Color(0xFFFF9500)
    WeatherCondition.Cloudy -> Color.Gray
    WeatherCondition.Rain -> Color(0xFF007AFF)
    WeatherCondition.Snow -> Color.White
    WeatherCondition.Thunderstorm -> Color(0xFFAF52DE)
    WeatherCondition.Unknown -> Color.Gray
}

fun shareLocation(context: Context, locationName: String, latitude: Double?, longitude: Double?) {
    val items = buildString {
        append(locationName)
        if (latitude != null && longitude != null) {
            append("\nhttps://maps.google.com/?q=$latitude,$longitude")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, items)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
fun ModernLocationLoadingView(
    locationName: String,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val colors = rememberAdaptiveColors()
    val primary = if (isDark) Color.White else FeedInk
    val secondary = primary.copy(alpha = 0.72f)
    Column(
        modifier
            .fillMaxSize()
            .background(if (isDark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(80.dp)
                .momentsChromeGlass(CircleShape, interactive = false)
                .border(
                    width = 2.dp,
                    // iOS: LinearGradient(colors: adaptiveColors.buttonStroke)
                    brush = Brush.linearGradient(colors.buttonStroke),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), color = colors.accent, strokeWidth = 2.5.dp)
        }
        Spacer(Modifier.size(20.dp))
        Text(
            stringResource(R.string.maps_loading_location),
            color = primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            locationName,
            color = secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
    }
}

@Composable
fun ModernLocationErrorView(
    message: String,
    locationPermissionGranted: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val colors = rememberAdaptiveColors()
    val primary = if (isDark) Color.White else FeedInk
    val tertiary = primary.copy(alpha = 0.55f)
    Column(
        modifier
            .fillMaxSize()
            .background(if (isDark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(100.dp)
                .momentsChromeGlass(CircleShape, interactive = false)
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.Red.copy(alpha = 0.6f), Color(0xFFFF2D55).copy(alpha = 0.6f)),
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.LocationOff,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.size(24.dp))
        Text(
            stringResource(R.string.maps_error_location_load_failed),
            color = primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
        Spacer(Modifier.size(12.dp))
        Text(
            message,
            color = tertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
        Spacer(Modifier.size(20.dp))
        Row(
            Modifier
                // iOS: LinearGradient([accent, accent.opacity(0.8)]) + buttonStroke + shadow(accent 0.3)
                .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = colors.accent)
                .background(
                    brush = Brush.linearGradient(
                        listOf(colors.accent, colors.accent.copy(alpha = 0.8f)),
                    ),
                    shape = RoundedCornerShape(20.dp),
                )
                .border(1.dp, Brush.linearGradient(colors.buttonStroke), RoundedCornerShape(20.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text(
                stringResource(R.string.maps_error_retry),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
        if (!locationPermissionGranted) {
            Spacer(Modifier.size(16.dp))
            Text(
                stringResource(R.string.maps_error_configure_permissions),
                color = colors.accent,
                fontSize = 12.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                },
            )
        }
    }
}

/** ≡ iOS `LocationMapView.WeatherIndicatorView` (nested en Maps.swift). */
@Composable
fun WeatherIndicatorView(
    weather: WeatherData,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else FeedInk
    val secondary = primary.copy(alpha = 0.72f)
    val pulse = rememberInfiniteTransition(label = "weatherIndicator")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "weatherIndicatorScale",
    )
    Row(
        modifier
            .momentsChromeGlass(RoundedCornerShape(16.dp), interactive = false)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            weather.condition.icon(),
            contentDescription = null,
            tint = weather.condition.accentColor(),
            modifier = Modifier.size(16.dp).scale(scale),
        )
        Column {
            Text(weather.temperatureFormatted, color = primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(weather.condition.displayName(), color = secondary, fontSize = 11.sp, maxLines = 1)
        }
        if (weather.isNight) {
            Icon(Icons.Filled.NightsStay, null, tint = Color(0xFF5856D6), modifier = Modifier.size(12.dp))
        }
    }
}

/** ≡ iOS `LocationMapView.WeatherAwareLocationPin` (nested en Maps.swift). */
@Composable
fun WeatherAwareLocationPin(
    locationName: String,
    weather: WeatherData?,
    effectsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val colors = rememberAdaptiveColors()
    val primary = if (isDark) Color.White else FeedInk
    val durationMs = when {
        weather == null || !effectsEnabled -> 1500
        weather.condition == WeatherCondition.Thunderstorm -> 800
        weather.condition == WeatherCondition.Rain -> 1200
        weather.condition == WeatherCondition.Snow -> 2000
        else -> 1500
    }
    val pulse = rememberInfiniteTransition(label = "weatherPin")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(durationMs), RepeatMode.Reverse),
        label = "weatherPinScale",
    )
    val strokeColors = if (weather != null && effectsEnabled) {
        listOf(weather.condition.accentColor(), weather.condition.accentColor().copy(alpha = 0.6f))
    } else {
        // iOS `getWeatherAwareGradient`: [accent, accent.opacity(0.8)]
        listOf(colors.accent, colors.accent.copy(alpha = 0.8f))
    }
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(45.dp)
                    .background(Color.Black.copy(alpha = 0.2f), CircleShape),
            )
            Box(
                Modifier
                    .size(40.dp)
                    .scale(scale)
                    .momentsChromeGlass(CircleShape, interactive = false)
                    .border(3.dp, Brush.linearGradient(strokeColors), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = strokeColors.first(),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            locationName,
            color = primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier
                .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = false)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
