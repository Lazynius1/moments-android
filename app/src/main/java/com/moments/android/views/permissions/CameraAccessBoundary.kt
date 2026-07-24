package com.moments.android.views.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.moments.android.R
import com.moments.android.views.permission.camera.helpers.CameraPermissionsView
import com.moments.android.views.permission.microphone.MicrophonePermissionView
import com.moments.android.views.permission.shared.PermissionPrimerStage

/** Port de `CameraAccessBoundary.swift`. */
@Composable fun CameraAccessBoundary(requiresMicrophone: Boolean = false, onCancel: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micGranted = it }
    fun settings() = context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    when {
        cameraGranted && (!requiresMicrophone || micGranted) -> content()
        !cameraGranted -> {
            val denied = (context as? android.app.Activity)?.let { androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } ?: false
            CameraPermissionsView(title = stringResource(if (denied) R.string.permission_camera_denied_title else R.string.permission_camera_primer_title), description = stringResource(if (denied) R.string.permission_camera_denied_subtitle else R.string.permission_camera_primer_subtitle), primaryActionTitle = stringResource(if (denied) R.string.permission_camera_denied_open_settings else R.string.permission_camera_primer_allow), secondaryActionTitle = stringResource(R.string.permission_camera_primer_not_now), showsShutterUI = !denied, isDenied = denied, primaryAction = { if (denied) settings() else cameraLauncher.launch(Manifest.permission.CAMERA) }, secondaryAction = onCancel) { Image(painterResource(R.drawable.pic1), null, contentScale = ContentScale.Crop) }
        }
        else -> MicrophonePermissionView(if ((context as? android.app.Activity)?.let { androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO) } == true) PermissionPrimerStage.DENIED else PermissionPrimerStage.PRIMER, primaryAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }, secondaryAction = onCancel)
    }
}
