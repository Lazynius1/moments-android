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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.moments.android.views.permission.microphone.MicrophonePermissionView
import com.moments.android.views.permission.notifications.NotificationsPermissionView
import com.moments.android.views.permission.photos.PhotosPermissionView

/** Port de `PermissionPrimerGate.swift`. */
class PermissionPrimerGate(val kind: Kind) {
    enum class Kind { MICROPHONE, PHOTOS, PHOTOS_SAVE, NOTIFICATIONS }

    /** ≡ private enum State */
    enum class State { AUTHORIZED, NOT_DETERMINED, DENIED }

    var isPresenting by mutableStateOf(false)
        private set
    var stage by mutableStateOf(PermissionPrimerStage.PRIMER)
        private set

    private var onGranted: (() -> Unit)? = null

    fun requestAccess(context: Context, onGranted: () -> Unit) {
        when (currentState(context)) {
            State.AUTHORIZED -> onGranted()
            State.NOT_DETERMINED -> {
                this.onGranted = onGranted
                stage = PermissionPrimerStage.PRIMER
                isPresenting = true
            }
            State.DENIED -> {
                this.onGranted = onGranted
                stage = PermissionPrimerStage.DENIED
                isPresenting = true
            }
        }
    }

    fun primaryAction(context: Context, requestNative: () -> Unit) {
        if (stage == PermissionPrimerStage.PRIMER) {
            requestNative()
        } else {
            openSettings(context)
        }
    }

    /** ≡ `finish(granted:)` */
    fun finish(granted: Boolean) {
        if (!granted) {
            stage = PermissionPrimerStage.DENIED
            return
        }
        isPresenting = false
        val continuation = onGranted
        onGranted = null
        continuation?.invoke()
    }

    /** Resultado del launcher nativo → finish. */
    fun onNativeResult(context: Context) {
        markAsked(context)
        finish(granted = isAuthorized(context))
    }

    fun dismiss() {
        isPresenting = false
    }

    /**
     * ≡ `refreshNotificationStatus` (en iOS no hay callers; se mantiene por paridad API).
     * En Android relee el estado live vía [currentState].
     */
    fun refreshNotificationStatus(context: Context, completion: () -> Unit) {
        // Forzar relectura de NotificationManager / runtime perms en el próximo currentState.
        @Suppress("UNUSED_VARIABLE")
        val ignored = currentState(context)
        completion()
    }

    fun currentState(context: Context): State {
        if (isAuthorized(context)) return State.AUTHORIZED
        return if (wasAsked(context)) State.DENIED else State.NOT_DETERMINED
    }

    fun permissions(): Array<String> = when (kind) {
        Kind.MICROPHONE -> arrayOf(Manifest.permission.RECORD_AUDIO)
        Kind.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
        // ≡ PHPhotoLibrary .readWrite
        Kind.PHOTOS -> photoLibraryPermissionsToRequest()
        // ≡ PHPhotoLibrary .addOnly — API 29+ MediaStore insert sin runtime perm.
        Kind.PHOTOS_SAVE -> if (Build.VERSION.SDK_INT >= 29) {
            emptyArray()
        } else {
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun isAuthorized(context: Context): Boolean {
        val perms = permissions()
        if (kind == Kind.PHOTOS_SAVE && Build.VERSION.SDK_INT >= 29) {
            // MediaStore insert on Q+ — no runtime permission; do not tie to SharedPreferences.
            return true
        }
        if (kind == Kind.PHOTOS) {
            return photoLibraryAccess(context) != PhotoLibraryAccess.DENIED
        }
        if (kind == Kind.NOTIFICATIONS) {
            val runtimeOk = perms.isEmpty() || perms.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            return runtimeOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        if (perms.isEmpty()) {
            // Sin runtime perm (p.ej. photosSave API 29+): “autorizado” tras haber pasado el primer.
            return wasAsked(context)
        }
        return perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun wasAsked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val perms = permissions()
        if (perms.isEmpty()) {
            return prefs.getBoolean(kindAskedKey(), false)
        }
        return perms.any { prefs.getBoolean(askedKey(it), false) }
    }

    fun markAsked(context: Context) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        val perms = permissions()
        if (perms.isEmpty()) {
            edit.putBoolean(kindAskedKey(), true)
        } else {
            perms.forEach { edit.putBoolean(askedKey(it), true) }
        }
        edit.apply()
    }

    private fun openSettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
        isPresenting = false
    }

    private fun askedKey(perm: String) = "asked_$perm"
    private fun kindAskedKey() = "asked_kind_${kind.name}"

    companion object {
        private const val PREFS = "moments_permission_primer"
    }
}

/**
 * ≡ `permissionPrimerGate` / `.fullScreenCover` de iOS.
 */
@Composable
fun PermissionPrimerGateHost(
    gate: PermissionPrimerGate = remember { PermissionPrimerGate(PermissionPrimerGate.Kind.PHOTOS) },
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        gate.onNativeResult(context)
    }

    DisposableEffect(lifecycleOwner, gate.isPresenting) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && gate.isPresenting) {
                if (gate.currentState(context) == PermissionPrimerGate.State.AUTHORIZED) {
                    gate.finish(granted = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!gate.isPresenting) return

    val primary = {
        gate.primaryAction(context) {
            val perms = gate.permissions()
            if (perms.isEmpty()) {
                // Sin diálogo del SO (photosSave API 29+ / notif < 33): finish como granted.
                gate.markAsked(context)
                gate.finish(granted = true)
            } else {
                launcher.launch(perms)
            }
        }
    }

    PermissionPrimerFullScreenDialog(onDismissRequest = gate::dismiss) {
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
