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
import com.moments.android.views.permission.location.LocationPermissionView

/** Port de `LocationPermissionGate.swift`. */
enum class LocationPermissionAccessLevel { WHEN_IN_USE, ALWAYS }

class LocationPermissionGate {
    var isPresenting by mutableStateOf(false); private set
    var stage by mutableStateOf(PermissionPrimerStage.PRIMER); private set
    var accessLevel by mutableStateOf(LocationPermissionAccessLevel.WHEN_IN_USE); private set
    private var onGranted: (() -> Unit)? = null
    fun requestAccess(context: Context, level: LocationPermissionAccessLevel = LocationPermissionAccessLevel.WHEN_IN_USE, onGranted: () -> Unit) {
        accessLevel = level; this.onGranted = onGranted
        if (hasForeground(context) && (level != LocationPermissionAccessLevel.ALWAYS || hasBackground(context))) { finish(true); return }
        stage = if (wasDenied(context)) PermissionPrimerStage.DENIED else PermissionPrimerStage.PRIMER; isPresenting = true
    }
    fun primaryAction(request: () -> Unit, context: Context) { if (stage == PermissionPrimerStage.DENIED) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))); isPresenting = false } else request() }
    fun onPermissionResult(context: Context) { if (!hasForeground(context)) { stage = PermissionPrimerStage.DENIED; return }; if (accessLevel == LocationPermissionAccessLevel.ALWAYS && !hasBackground(context)) { stage = PermissionPrimerStage.DENIED; return }; finish(true) }
    fun dismiss() { isPresenting = false; onGranted = null }
    private fun finish(granted: Boolean) { if (!granted) { stage = PermissionPrimerStage.DENIED; return }; isPresenting = false; onGranted?.invoke(); onGranted = null }
    private fun hasForeground(c: Context) = ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasBackground(c: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun wasDenied(c: Context) = !hasForeground(c)
}

@Composable fun LocationPermissionGateHost(gate: LocationPermissionGate = remember { LocationPermissionGate() }) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { gate.onPermissionResult(context) }
    if (gate.isPresenting) LocationPermissionView(gate.stage, gate.accessLevel, { gate.primaryAction({ launcher.launch(if (gate.accessLevel == LocationPermissionAccessLevel.ALWAYS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, context) }, gate::dismiss)
}
