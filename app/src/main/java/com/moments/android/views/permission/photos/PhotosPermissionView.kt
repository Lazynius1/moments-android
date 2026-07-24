package com.moments.android.views.permission.photos

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.permission.shared.PermissionPhoneFrame
import com.moments.android.views.permission.shared.PermissionPrimerScaffold
import com.moments.android.views.permission.shared.PermissionPrimerStage

/** Port de `PhotosPermissionView.swift`. */
@Composable
fun PhotosPermissionView(stage: PermissionPrimerStage = PermissionPrimerStage.PRIMER, primaryAction: () -> Unit, secondaryAction: () -> Unit) {
    val denied = stage == PermissionPrimerStage.DENIED
    PermissionPrimerScaffold(stage, icon = { tint -> Icon(if (denied) Icons.Default.PhotoSizeSelectLarge else Icons.Default.PhotoLibrary, null, tint = tint, modifier = Modifier.fillMaxSize()) },
        title = stringResource(if (denied) R.string.permission_photos_denied_title else R.string.permission_photos_primer_title), description = stringResource(if (denied) R.string.permission_photos_denied_subtitle else R.string.permission_photos_primer_subtitle),
        primaryActionTitle = stringResource(if (denied) R.string.permission_photos_denied_open_settings else R.string.permission_photos_primer_allow), secondaryActionTitle = stringResource(R.string.permission_photos_primer_not_now), primaryAction = primaryAction, secondaryAction = secondaryAction) {
        PermissionPhoneFrame(screenBackground = Color(0xFF111318), animated = false, appliesDeniedChrome = denied, screen = { PhotoMosaicScreen(!denied) }, island = {})
    }
}

@Composable private fun PhotoMosaicScreen(active: Boolean) {
    val scroll = if (active) rememberInfiniteTransition(label = "photo-mosaic").animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse), label = "photo-mosaic-scroll").value else .35f
    Image(painterResource(R.drawable.permission_gallery_photos), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { translationY = -scroll * size.height * .8f }.then(if (active) Modifier else Modifier.blur(2.5.dp)))
}
