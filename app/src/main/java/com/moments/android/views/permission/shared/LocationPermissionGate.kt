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

/** Port de `LocationPermissionAccessLevel` / `LocationPermissionGate.swift`. */
enum class LocationPermissionAccessLevel { WHEN_IN_USE, ALWAYS }

class LocationPermissionGate {
    var isPresenting by mutableStateOf(false)
        private set
    var stage by mutableStateOf(PermissionPrimerStage.PRIMER)
        private set
    var accessLevel by mutableStateOf(LocationPermissionAccessLevel.WHEN_IN_USE)
        private set

    private var onGranted: (() -> Unit)? = null
    private var awaitingResponse = false
    private var pendingAlwaysAfterWhenInUse = false

    fun requestAccess(
        context: Context,
        level: LocationPermissionAccessLevel = LocationPermissionAccessLevel.WHEN_IN_USE,
        onGranted: () -> Unit,
    ) {
        accessLevel = level
        pendingAlwaysAfterWhenInUse = false

        when (authorizationStatus(context)) {
            AuthStatus.ALWAYS -> onGranted()
            AuthStatus.WHEN_IN_USE -> {
                if (level == LocationPermissionAccessLevel.WHEN_IN_USE) {
                    onGranted()
                } else {
                    // Ya tiene foreground; primer para pedir Always / background.
                    this.onGranted = onGranted
                    stage = PermissionPrimerStage.PRIMER
                    isPresenting = true
                }
            }
            AuthStatus.NOT_DETERMINED -> {
                this.onGranted = onGranted
                stage = PermissionPrimerStage.PRIMER
                isPresenting = true
            }
            AuthStatus.DENIED -> {
                this.onGranted = onGranted
                stage = PermissionPrimerStage.DENIED
                isPresenting = true
            }
        }
    }

    /**
     * @param launchForeground pide FINE+COARSE
     * @param launchBackground pide ACCESS_BACKGROUND_LOCATION (solo si ya hay foreground; API 29+)
     */
    fun primaryAction(
        context: Context,
        launchForeground: () -> Unit,
        launchBackground: () -> Unit,
    ) {
        if (stage == PermissionPrimerStage.DENIED) {
            openSettings(context)
            return
        }

        awaitingResponse = true
        when (accessLevel) {
            LocationPermissionAccessLevel.WHEN_IN_USE -> launchForeground()
            LocationPermissionAccessLevel.ALWAYS -> when (authorizationStatus(context)) {
                AuthStatus.WHEN_IN_USE -> launchBackground()
                AuthStatus.NOT_DETERMINED -> {
                    // ≡ iOS: When In Use primero; Always nativo después.
                    pendingAlwaysAfterWhenInUse = true
                    launchForeground()
                }
                AuthStatus.ALWAYS -> finish(granted = true)
                AuthStatus.DENIED -> {
                    awaitingResponse = false
                    stage = PermissionPrimerStage.DENIED
                }
            }
        }
    }

    /** Resultado del request foreground (FINE/COARSE). */
    fun onForegroundResult(context: Context, launchBackground: () -> Unit) {
        if (!awaitingResponse) return
        markAsked(context)
        if (!hasForeground(context)) {
            finish(granted = false)
            return
        }
        if (accessLevel == LocationPermissionAccessLevel.ALWAYS && pendingAlwaysAfterWhenInUse) {
            pendingAlwaysAfterWhenInUse = false
            if (hasBackground(context)) {
                finish(granted = true)
            } else {
                launchBackground()
            }
            return
        }
        // When In Use basta para continuar.
        finish(granted = true)
    }

    /**
     * Resultado del request background.
     * ≡ iOS: si el usuario declina Always pero tiene When In Use → `finish(true)`.
     */
    fun onBackgroundResult(context: Context) {
        if (!awaitingResponse) return
        markAsked(context)
        finish(granted = hasForeground(context))
    }

    fun dismiss() {
        isPresenting = false
        awaitingResponse = false
        pendingAlwaysAfterWhenInUse = false
        onGranted = null
    }

    private fun finish(granted: Boolean) {
        awaitingResponse = false
        pendingAlwaysAfterWhenInUse = false
        if (!granted) {
            stage = PermissionPrimerStage.DENIED
            return
        }
        isPresenting = false
        val continuation = onGranted
        onGranted = null
        continuation?.invoke()
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

    private enum class AuthStatus { ALWAYS, WHEN_IN_USE, NOT_DETERMINED, DENIED }

    private fun authorizationStatus(context: Context): AuthStatus = when {
        hasForeground(context) && hasBackground(context) -> AuthStatus.ALWAYS
        hasForeground(context) -> AuthStatus.WHEN_IN_USE
        wasAsked(context) -> AuthStatus.DENIED
        else -> AuthStatus.NOT_DETERMINED
    }

    private fun hasForeground(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** API < Q: background implícito con foreground. */
    private fun hasBackground(c: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun wasAsked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ASKED_KEY, false)

    private fun markAsked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ASKED_KEY, true)
            .apply()
    }

    companion object {
        private const val PREFS = "moments_location_permission_gate"
        private const val ASKED_KEY = "asked_location"
    }
}

/** ≡ `locationPermissionGate` / `.fullScreenCover` de iOS. */
@Composable
fun LocationPermissionGateHost(
    gate: LocationPermissionGate = remember { LocationPermissionGate() },
) {
    val context = LocalContext.current

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        gate.onBackgroundResult(context)
    }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        gate.onForegroundResult(context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                gate.onBackgroundResult(context)
            }
        }
    }

    if (!gate.isPresenting) return

    val launchForeground = {
        foregroundLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }
    val launchBackground = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            gate.onBackgroundResult(context)
        }
    }

    PermissionPrimerFullScreenDialog(onDismissRequest = gate::dismiss) {
        LocationPermissionView(
            stage = gate.stage,
            accessLevel = gate.accessLevel,
            primaryAction = {
                gate.primaryAction(context, launchForeground, launchBackground)
            },
            secondaryAction = gate::dismiss,
        )
    }
}
