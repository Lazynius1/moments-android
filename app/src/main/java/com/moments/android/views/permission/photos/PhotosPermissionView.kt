package com.moments.android.views.permission.photos

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.permission.shared.PermissionPhoneFrame
import com.moments.android.views.permission.shared.PermissionPrimerScaffold
import com.moments.android.views.permission.shared.PermissionPrimerStage
import com.moments.android.views.permission.shared.permissionMockOverflowSize
import com.moments.android.views.permission.shared.rememberPermissionLoopTimeSeconds
import kotlin.math.sin

/** Port de `PhotosPermissionView.swift`. */
@Composable
fun PhotosPermissionView(
    stage: PermissionPrimerStage = PermissionPrimerStage.PRIMER,
    primaryAction: () -> Unit,
    secondaryAction: () -> Unit,
) {
    val denied = stage == PermissionPrimerStage.DENIED
    PermissionPrimerScaffold(
        stage = stage,
        icon = { tint ->
            Icon(
                if (denied) Icons.Default.PhotoSizeSelectLarge else Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize(),
            )
        },
        title = stringResource(
            if (denied) R.string.permission_photos_denied_title
            else R.string.permission_photos_primer_title,
        ),
        description = stringResource(
            if (denied) R.string.permission_photos_denied_subtitle
            else R.string.permission_photos_primer_subtitle,
        ),
        primaryActionTitle = stringResource(
            if (denied) R.string.permission_photos_denied_open_settings
            else R.string.permission_photos_primer_allow,
        ),
        secondaryActionTitle = stringResource(R.string.permission_photos_primer_not_now),
        primaryAction = primaryAction,
        secondaryAction = secondaryAction,
    ) {
        PermissionPhoneFrame(
            screenBackground = Color(0xFF111318),
            animated = false,
            appliesDeniedChrome = denied,
            screen = { size, _ -> PhotoMosaicScreen(size = size, isActive = !denied) },
            island = { _, _ -> },
        )
    }
}

/**
 * ≡ PhotoMosaicScreen —
 * frame w × 1.8h + fill + offset y: −scroll; scroll = (sin(t·0.35)+1)/2 · 0.8h.
 * Overflow size: sin esto el padre comprime a 1×h y se ve el hueco al hacer pan.
 */
@Composable
private fun PhotoMosaicScreen(size: DpSize, isActive: Boolean) {
    val density = LocalDensity.current
    val travelPx = with(density) { size.height.toPx() * 0.8f }
    val t = rememberPermissionLoopTimeSeconds(isActive)
    val scrollPx = if (isActive) {
        ((sin(t * 0.35) + 1.0) / 2.0).toFloat() * travelPx
    } else {
        travelPx * 0.35f
    }

    Box(Modifier.fillMaxSize().clipToBounds()) {
        Image(
            painter = painterResource(R.drawable.permission_gallery_photos),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .permissionMockOverflowSize(
                    width = size.width,
                    height = size.height * 1.8f,
                    alignment = Alignment.TopCenter,
                )
                .graphicsLayer { translationY = -scrollPx }
                .then(if (isActive) Modifier else Modifier.blur(2.5.dp)),
        )
    }
}
