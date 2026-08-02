package com.moments.android.views.permission.shared

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.moments.android.R

/**
 * Port de `PermissionPhoneWallpaper.swift`.
 * iOS: `PermissionWallpaper` asset → fallback `pic1`.
 */
@Composable
fun PermissionPhoneWallpaper(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.permission_wallpaper),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
