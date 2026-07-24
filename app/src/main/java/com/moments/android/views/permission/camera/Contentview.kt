package com.moments.android.views.permission.camera

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.moments.android.R
import com.moments.android.views.permission.camera.helpers.CameraPermissionsView

/** Port de `Contentview.swift`. */
@Composable
fun ContentView() {
    CameraPermissionsView(
        title = stringResource(R.string.permission_camera_primer_title),
        description = stringResource(R.string.permission_camera_primer_subtitle),
        primaryActionTitle = stringResource(R.string.permission_camera_primer_allow),
        secondaryActionTitle = stringResource(R.string.permission_camera_primer_not_now),
        showsShutterUI = true,
        primaryAction = {},
        secondaryAction = {},
    ) {
        Image(
            painter = painterResource(R.drawable.pic1),
            contentDescription = null,
            contentScale = ContentScale.Crop,
        )
    }
}
