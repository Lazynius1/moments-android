package com.moments.android.views.permission.location

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.permission.shared.LocationPermissionAccessLevel
import com.moments.android.views.permission.shared.PermissionPhoneFrame
import com.moments.android.views.permission.shared.PermissionPhoneIslandPlacement
import com.moments.android.views.permission.shared.PermissionPrimerScaffold
import com.moments.android.views.permission.shared.PermissionPrimerStage
import com.moments.android.views.permission.shared.permissionMockOverflowSize
import com.moments.android.views.permission.shared.rememberPermissionLoopTimeSeconds
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

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
            Icon(
                when {
                    denied -> Icons.Default.LocationOff
                    always -> Icons.Default.MyLocation
                    else -> Icons.Default.LocationOn
                },
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize(),
            )
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
            // ≡ iOS animated: !isDenied — aquí sí se mueve el teléfono
            animated = !denied,
            islandPlacement = PermissionPhoneIslandPlacement.LEADING,
            showsIslandIndicators = !denied,
            appliesDeniedChrome = denied,
            screen = { size, _ ->
                LocationMapScreen(
                    size = size,
                    emphasizesAlways = always,
                    isActive = !denied,
                )
            },
            island = { ratio, _ ->
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF4C8DFF),
                    modifier = Modifier.size((14f * ratio).dp),
                )
            },
        )
    }
}

/**
 * ≡ LocationMapScreen — pan sin/cos + pulse como TimelineView iOS.
 * Imagen w×2 · h×1.4 con overflow (si no, el padre la aplasta a 1×w y se ven bordes).
 */
@Composable
private fun LocationMapScreen(
    size: DpSize,
    emphasizesAlways: Boolean,
    isActive: Boolean,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { size.width.toPx() }
    val heightPx = with(density) { size.height.toPx() }

    val tSec = rememberPermissionLoopTimeSeconds(isActive)

    val panX = if (isActive) sin(tSec * 0.4) * widthPx * 0.5f else 0f
    val panY = if (isActive) cos(tSec * 0.32) * heightPx * 0.1f else 0f
    val pulse = if (isActive) ((sin(tSec * 2.2) + 1.0) / 2.0).toFloat() else 0.35f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.permission_map),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .permissionMockOverflowSize(
                    width = size.width * 2f,
                    height = size.height * 1.4f,
                    alignment = Alignment.Center,
                )
                .graphicsLayer {
                    translationX = panX.toFloat()
                    translationY = panY.toFloat()
                },
        )
        if (emphasizesAlways) {
            LiveLocationDot(pulse = pulse, widthPx = widthPx)
        } else {
            LocationPin(pulse = pulse, widthPx = widthPx)
        }
    }
}

/** ≡ locationPin — halo + mappin.circle.fill */
@Composable
private fun LocationPin(pulse: Float, widthPx: Float) {
    val density = LocalDensity.current
    val halo = with(density) { (widthPx * (0.32f + 0.14f * pulse)).toDp() }
    val pin = with(density) { (widthPx * 0.2f).toDp() }
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(halo)
                .clip(CircleShape)
                .background(Color(0xFF4C8DFF).copy(alpha = 0.22f)),
        )
        Icon(
            Icons.Default.Place,
            contentDescription = null,
            tint = Color(0xFF4C8DFF),
            modifier = Modifier
                .size(pin)
                .shadow(3.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f)),
        )
    }
}

/** ≡ liveLocationDot — accuracy ring + white-stroked blue core */
@Composable
private fun LiveLocationDot(pulse: Float, widthPx: Float) {
    val density = LocalDensity.current
    val accuracy = with(density) { (widthPx * (0.38f + 0.16f * pulse)).toDp() }
    val core = with(density) { (widthPx * 0.13f).toDp() }
    val stroke = with(density) { max(2f, widthPx * 0.018f).toDp() }
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(accuracy)
                .clip(CircleShape)
                .background(Color(0xFF4C8DFF).copy(alpha = 0.18f)),
        )
        Box(
            Modifier
                .size(core)
                .shadow(6.dp, CircleShape, ambientColor = Color(0xFF4C8DFF).copy(alpha = 0.45f))
                .clip(CircleShape)
                .background(Color(0xFF4C8DFF))
                .border(stroke, Color.White.copy(alpha = 0.9f), CircleShape),
        )
    }
}
