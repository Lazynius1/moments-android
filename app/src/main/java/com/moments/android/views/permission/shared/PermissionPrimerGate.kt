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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.moments.android.views.permission.microphone.MicrophonePermissionView
import com.moments.android.views.permission.notifications.NotificationsPermissionView
import com.moments.android.views.permission.photos.PhotosPermissionView

/** Port de `PermissionPrimerGate.swift`. */
class PermissionPrimerGate(val kind: Kind) {
    enum class Kind { MICROPHONE, PHOTOS, PHOTOS_SAVE, NOTIFICATIONS }
    var isPresenting by mutableStateOf(false); private set
    var stage by mutableStateOf(PermissionPrimerStage.PRIMER); private set
    private var onGranted: (() -> Unit)? = null
    fun requestAccess(context: Context, onGranted: () -> Unit) { if (authorized(context)) { onGranted(); return }; this.onGranted = onGranted; stage = PermissionPrimerStage.PRIMER; isPresenting = true }
    fun primaryAction(context: Context, request: () -> Unit) { if (stage == PermissionPrimerStage.PRIMER) request() else { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))); isPresenting = false } }
    fun onResult(context: Context) { if (authorized(context)) { isPresenting = false; onGranted?.invoke(); onGranted = null } else stage = PermissionPrimerStage.DENIED }
    fun dismiss() { isPresenting = false }
    fun permissions(): Array<String> = when (kind) { Kind.MICROPHONE -> arrayOf(Manifest.permission.RECORD_AUDIO); Kind.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray(); Kind.PHOTOS, Kind.PHOTOS_SAVE -> if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE) }
    private fun authorized(c: Context) = permissions().all { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }
}

@Composable fun PermissionPrimerGateHost(gate: PermissionPrimerGate = remember { PermissionPrimerGate(PermissionPrimerGate.Kind.PHOTOS) }) {
    val context = LocalContext.current; val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { gate.onResult(context) }
    if (!gate.isPresenting) return
    val primary = { gate.primaryAction(context) { launcher.launch(gate.permissions()) } }
    when (gate.kind) { PermissionPrimerGate.Kind.MICROPHONE -> MicrophonePermissionView(gate.stage, primary, gate::dismiss); PermissionPrimerGate.Kind.PHOTOS, PermissionPrimerGate.Kind.PHOTOS_SAVE -> PhotosPermissionView(gate.stage, primary, gate::dismiss); PermissionPrimerGate.Kind.NOTIFICATIONS -> NotificationsPermissionView(gate.stage, primary, gate::dismiss) }
}
