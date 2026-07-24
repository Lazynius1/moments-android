package com.moments.android.views.permissions

import android.Manifest
import android.content.Context
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

/** Port de `CameraPermissionGate.swift`. */
class CameraPermissionGate {
    enum class Stage { PRIMER, DENIED }
    var isPresenting by mutableStateOf(false); private set
    var stage by mutableStateOf(Stage.PRIMER); private set
    private var onAuthorized: (() -> Unit)? = null
    fun requestCameraAccess(context: Context, onAuthorized: () -> Unit) { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) onAuthorized() else { this.onAuthorized = onAuthorized; stage = Stage.PRIMER; isPresenting = true } }
    fun onResult(granted: Boolean) { if (!granted) { stage = Stage.DENIED; return }; isPresenting = false; onAuthorized?.invoke(); onAuthorized = null }
    fun openSettings(context: Context) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))); isPresenting = false }
    fun dismiss() { isPresenting = false }
}

@Composable fun CameraPermissionGateHost(gate: CameraPermissionGate = remember { CameraPermissionGate() }) {
    val context = LocalContext.current; val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { gate.onResult(it) }
    if (!gate.isPresenting) return
    val denied = gate.stage == CameraPermissionGate.Stage.DENIED
    CameraPermissionsView(title = stringResource(if (denied) R.string.permission_camera_denied_title else R.string.permission_camera_primer_title), description = stringResource(if (denied) R.string.permission_camera_denied_subtitle else R.string.permission_camera_primer_subtitle), primaryActionTitle = stringResource(if (denied) R.string.permission_camera_denied_open_settings else R.string.permission_camera_primer_allow), secondaryActionTitle = stringResource(R.string.permission_camera_primer_not_now), showsShutterUI = !denied, isDenied = denied, primaryAction = { if (denied) gate.openSettings(context) else launcher.launch(Manifest.permission.CAMERA) }, secondaryAction = gate::dismiss) { Image(painterResource(R.drawable.pic1), null, contentScale = ContentScale.Crop) }
}
