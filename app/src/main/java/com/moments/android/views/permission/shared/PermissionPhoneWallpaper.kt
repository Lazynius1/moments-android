package com.moments.android.views.permission.shared

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.moments.android.R

/** Port de `PermissionPhoneWallpaper.swift`. */
@Composable fun PermissionPhoneWallpaper(modifier: Modifier = Modifier) = Image(painterResource(R.drawable.pic1), null, modifier, contentScale = ContentScale.Crop)
