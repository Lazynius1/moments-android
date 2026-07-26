package com.moments.android.views.permission.shared

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.moments.android.views.permission.microphone.MicrophonePermissionView
import com.moments.android.views.permission.notifications.NotificationsPermissionView
import com.moments.android.views.permission.photos.PhotosPermissionView

/** Port de `PermissionPrimerGate.swift`. */
class PermissionPrimerGate(val kind: Kind) {
    enum class Kind { MICROPHONE, PHOTOS, PHOTOS_SAVE, NOTIFICATIONS }

    var isPresenting by mutableStateOf(false)
        private set
    var stage by mutableStateOf(PermissionPrimerStage.PRIMER)
        private set

    private var onGranted: (() -> Unit)? = null

    fun requestAccess(context: Context, onGranted: () -> Unit) {
        if (authorized(context)) {
            onGranted()
            return
        }
        this.onGranted = onGranted
        // ≡ iOS: .notDetermined → .primer; .denied → .denied
        stage = if (isDenied(context)) PermissionPrimerStage.DENIED else PermissionPrimerStage.PRIMER
        isPresenting = true
    }

    fun primaryAction(context: Context, request: () -> Unit) {
        if (stage == PermissionPrimerStage.PRIMER) {
            request()
        } else {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
            isPresenting = false
        }
    }

    fun onResult(context: Context) {
        if (authorized(context)) {
            isPresenting = false
            onGranted?.invoke()
            onGranted = null
        } else {
            stage = PermissionPrimerStage.DENIED
        }
    }

    fun dismiss() {
        isPresenting = false
    }

    fun permissions(): Array<String> = when (kind) {
        Kind.MICROPHONE -> arrayOf(Manifest.permission.RECORD_AUDIO)
        Kind.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
        Kind.PHOTOS, Kind.PHOTOS_SAVE -> if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun authorized(c: Context): Boolean =
        permissions().all {
            ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED
        }

    /** Aprox. iOS `.denied`: ya se pidió y sigue sin conceder. */
    private fun isDenied(c: Context): Boolean {
        if (authorized(c)) return false
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val activity = c.findActivity() ?: return prefs.all.keys.any { it.startsWith("asked_") }
        return permissions().any { perm ->
            val asked = prefs.getBoolean(askedKey(perm), false)
            if (!asked) return@any false
            ContextCompat.checkSelfPermission(c, perm) != PackageManager.PERMISSION_GRANTED &&
                (
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm) ||
                        asked
                    )
        }
    }

    fun markAsked(context: Context) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        permissions().forEach { edit.putBoolean(askedKey(it), true) }
        edit.apply()
    }

    private fun askedKey(perm: String) = "asked_$perm"

    companion object {
        private const val PREFS = "moments_permission_primer"
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * ≡ `permissionPrimerGate` / `.fullScreenCover` de iOS.
 * Dialog a pantalla completa (no overlay inline dentro del host).
 */
@Composable
fun PermissionPrimerGateHost(
    gate: PermissionPrimerGate = remember { PermissionPrimerGate(PermissionPrimerGate.Kind.PHOTOS) },
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        gate.onResult(context)
    }
    if (!gate.isPresenting) return

    val primary = {
        gate.primaryAction(context) {
            gate.markAsked(context)
            launcher.launch(gate.permissions())
        }
    }

    // ≡ fullScreenCover
    Dialog(
        onDismissRequest = gate::dismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            when (gate.kind) {
                PermissionPrimerGate.Kind.MICROPHONE ->
                    MicrophonePermissionView(gate.stage, primary, gate::dismiss)
                PermissionPrimerGate.Kind.PHOTOS, PermissionPrimerGate.Kind.PHOTOS_SAVE ->
                    PhotosPermissionView(gate.stage, primary, gate::dismiss)
                PermissionPrimerGate.Kind.NOTIFICATIONS ->
                    NotificationsPermissionView(gate.stage, primary, gate::dismiss)
            }
        }
    }
}
