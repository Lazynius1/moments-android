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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.moments.android.R
import com.moments.android.views.permission.camera.helpers.CameraPermissionsView
import com.moments.android.views.permission.microphone.MicrophonePermissionView
import com.moments.android.views.permission.shared.PermissionPrimerStage

/**
 * Port de `CameraAccessBoundary.swift`.
 * Fase cámara → micrófono (si `requiresMicrophone`) → content.
 * Resume ≡ `UIApplication.didBecomeActiveNotification`.
 */
@Composable
fun CameraAccessBoundary(
    requiresMicrophone: Boolean = false,
    onCancel: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraGranted by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.CAMERA))
    }
    var micGranted by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO))
    }

    fun refresh() {
        cameraGranted = hasPermission(context, Manifest.permission.CAMERA)
        micGranted = hasPermission(context, Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        markAsked(context, Manifest.permission.CAMERA)
        cameraGranted = granted
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        markAsked(context, Manifest.permission.RECORD_AUDIO)
        micGranted = granted
    }

    fun openSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }

    when {
        cameraGranted && (!requiresMicrophone || micGranted) -> content()
        !cameraGranted -> {
            // ≡ iOS: .notDetermined → primer; else → denied + Settings
            val showPrimer = !wasAsked(context, Manifest.permission.CAMERA)
            CameraPermissionsView(
                title = stringResource(
                    if (showPrimer) R.string.permission_camera_primer_title
                    else R.string.permission_camera_denied_title,
                ),
                description = stringResource(
                    if (showPrimer) R.string.permission_camera_primer_subtitle
                    else R.string.permission_camera_denied_subtitle,
                ),
                primaryActionTitle = stringResource(
                    if (showPrimer) R.string.permission_camera_primer_allow
                    else R.string.permission_camera_denied_open_settings,
                ),
                secondaryActionTitle = stringResource(R.string.permission_camera_primer_not_now),
                showsShutterUI = showPrimer,
                isDenied = !showPrimer,
                primaryAction = {
                    if (showPrimer) cameraLauncher.launch(Manifest.permission.CAMERA)
                    else openSettings()
                },
                secondaryAction = onCancel,
            ) {
                Image(painterResource(R.drawable.pic1), null, contentScale = ContentScale.Crop)
            }
        }
        else -> {
            // ≡ iOS mic: .undetermined → primer; else → denied + Settings
            val showPrimer = !wasAsked(context, Manifest.permission.RECORD_AUDIO)
            MicrophonePermissionView(
                stage = if (showPrimer) PermissionPrimerStage.PRIMER else PermissionPrimerStage.DENIED,
                primaryAction = {
                    if (showPrimer) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    else openSettings()
                },
                secondaryAction = onCancel,
            )
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun wasAsked(context: Context, permission: String): Boolean {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(askedKey(permission), false)) return true
    // Ya denegó y el sistema muestra rationale → se pidió al menos una vez.
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

private fun markAsked(context: Context, permission: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(askedKey(permission), true)
        .apply()
}

private fun askedKey(permission: String) = "asked_$permission"

private const val PREFS = "moments_camera_access_boundary"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
