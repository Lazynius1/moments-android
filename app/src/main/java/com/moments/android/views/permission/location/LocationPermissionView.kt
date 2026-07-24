package com.moments.android.views.permission.location

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.permission.shared.LocationPermissionAccessLevel
import com.moments.android.views.permission.shared.PermissionPhoneFrame
import com.moments.android.views.permission.shared.PermissionPhoneIslandPlacement
import com.moments.android.views.permission.shared.PermissionPrimerScaffold
import com.moments.android.views.permission.shared.PermissionPrimerStage

/** Port de `LocationPermissionView.swift`. */
@Composable
fun LocationPermissionView(
    stage: PermissionPrimerStage = PermissionPrimerStage.PRIMER,
    accessLevel: LocationPermissionAccessLevel = LocationPermissionAccessLevel.WHEN_IN_USE,
    primaryAction: () -> Unit,
    secondaryAction: () -> Unit,
) {
    val denied = stage == PermissionPrimerStage.DENIED
    val always = accessLevel == LocationPermissionAccessLevel.ALWAYS
    val title = stringResource(
        when {
            denied && always -> R.string.permission_location_always_denied_title
            denied -> R.string.permission_location_denied_title
            always -> R.string.permission_location_always_primer_title
            else -> R.string.permission_location_primer_title
        },
    )
    val description = stringResource(
        when {
            denied && always -> R.string.permission_location_always_denied_subtitle
            denied -> R.string.permission_location_denied_subtitle
            always -> R.string.permission_location_always_primer_subtitle
            else -> R.string.permission_location_primer_subtitle
        },
    )
    val primaryTitle = stringResource(
        when {
            denied -> R.string.permission_location_denied_open_settings
            always -> R.string.permission_location_always_primer_allow
            else -> R.string.permission_location_primer_allow
        },
    )
    PermissionPrimerScaffold(
        stage = stage,
        icon = { tint ->
            Icon(if (denied) Icons.Default.LocationOff else if (always) Icons.Default.MyLocation else Icons.Default.LocationOn, null, tint = tint, modifier = Modifier.fillMaxSize())
        },
        title = title,
        description = description,
        primaryActionTitle = primaryTitle,
        secondaryActionTitle = stringResource(R.string.permission_location_primer_not_now),
        primaryAction = primaryAction,
        secondaryAction = secondaryAction,
    ) {
        PermissionPhoneFrame(
            screenBackground = Color(0xFF1B2A24),
            animated = !denied,
            islandPlacement = PermissionPhoneIslandPlacement.LEADING,
            showsIslandIndicators = !denied,
            appliesDeniedChrome = denied,
            screen = { LocationMapScreen(emphasizesAlways = always, isActive = !denied) },
            island = { Icon(Icons.Default.LocationOn, null, tint = Color(0xFF4C8DFF), modifier = Modifier.fillMaxSize()) },
        )
    }
}

@Composable
private fun LocationMapScreen(emphasizesAlways: Boolean, isActive: Boolean) {
    val transition = rememberInfiniteTransition(label = "location-permission-map")
    val progress = if (isActive) transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse), label = "location-map-pan").value else 0f
    val pulse = if (isActive) transition.animateFloat(.35f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "location-map-pulse").value else .35f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.permission_map), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { translationX = progress * maxWidth.toPx() * .5f; translationY = -progress * maxHeight.toPx() * .1f })
        if (emphasizesAlways) LiveLocationDot(pulse, maxWidth, Modifier.align(Alignment.Center)) else LocationPin(pulse, maxWidth, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun LocationPin(pulse: Float, width: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) = Box(modifier) {
    Box(Modifier.size(width * (.32f + .14f * pulse)).clip(CircleShape).background(Color(0xFF4C8DFF).copy(alpha = .22f)))
    Icon(Icons.Default.LocationOn, null, tint = Color(0xFF4C8DFF), modifier = Modifier.align(Alignment.Center).size(width * .2f).shadow(3.dp, CircleShape))
}

@Composable
private fun LiveLocationDot(pulse: Float, width: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) = Box(modifier) {
    Box(Modifier.size(width * (.38f + .16f * pulse)).clip(CircleShape).background(Color(0xFF4C8DFF).copy(alpha = .18f)))
    Box(Modifier.align(Alignment.Center).size(width * .13f).clip(CircleShape).background(Color(0xFF4C8DFF)).shadow(6.dp, CircleShape))
}
