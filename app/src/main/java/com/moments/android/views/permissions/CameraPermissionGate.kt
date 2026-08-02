package com.moments.android.views.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.moments.android.R
import com.moments.android.views.permission.camera.helpers.CameraPermissionsView
import com.moments.android.views.permission.shared.PermissionPrimerFullScreenDialog

/** Port de `CameraPermissionGate.swift`. */
class CameraPermissionGate {
    enum class Stage { PRIMER, DENIED }

    var isPresenting by mutableStateOf(false)
        private set
    var stage by mutableStateOf(Stage.PRIMER)
        private set

    private var onAuthorized: (() -> Unit)? = null

    fun requestCameraAccess(context: Context, onAuthorized: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            onAuthorized()
            return
        }
        this.onAuthorized = onAuthorized
        // ≡ iOS: .notDetermined → .primer; else → .denied
        stage = if (wasAskedCamera(context)) Stage.DENIED else Stage.PRIMER
        isPresenting = true
    }

    fun onResult(context: Context, granted: Boolean) {
        markAskedCamera(context)
        if (!granted) {
            stage = Stage.DENIED
            return
        }
        isPresenting = false
        onAuthorized?.invoke()
        onAuthorized = null
    }

    fun openSettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
        isPresenting = false
    }

    fun dismiss() {
        isPresenting = false
    }
}

/**
 * ≡ `cameraPermissionGate` / `.fullScreenCover` de iOS.
 */
@Composable
fun CameraPermissionGateHost(
    gate: CameraPermissionGate = remember { CameraPermissionGate() },
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        gate.onResult(context, granted)
    }
    if (!gate.isPresenting) return

    val denied = gate.stage == CameraPermissionGate.Stage.DENIED

    PermissionPrimerFullScreenDialog(onDismissRequest = gate::dismiss) {
        CameraPermissionsView(
            title = stringResource(
                if (denied) R.string.permission_camera_denied_title
                else R.string.permission_camera_primer_title,
            ),
            description = stringResource(
                if (denied) R.string.permission_camera_denied_subtitle
                else R.string.permission_camera_primer_subtitle,
            ),
            primaryActionTitle = stringResource(
                if (denied) R.string.permission_camera_denied_open_settings
                else R.string.permission_camera_primer_allow,
            ),
            secondaryActionTitle = stringResource(R.string.permission_camera_primer_not_now),
            showsShutterUI = !denied,
            isDenied = denied,
            primaryAction = {
                if (denied) gate.openSettings(context)
                else launcher.launch(Manifest.permission.CAMERA)
            },
            secondaryAction = gate::dismiss,
        ) {
            Image(
                painterResource(R.drawable.pic1),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val PREFS = "moments_camera_permission_gate"

private fun askedKey() = "asked_${Manifest.permission.CAMERA}"

private fun wasAskedCamera(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(askedKey(), false)) return true
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}

private fun markAskedCamera(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(askedKey(), true)
        .apply()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
