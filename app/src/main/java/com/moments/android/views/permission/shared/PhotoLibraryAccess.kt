package com.moments.android.views.permission.shared

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Effective access available to Moments' in-app MediaStore gallery. */
enum class PhotoLibraryAccess {
    FULL,
    PARTIAL,
    DENIED,
}

/**
 * Runtime permissions to request in the system dialog.
 * Do NOT request [READ_MEDIA_VISUAL_USER_SELECTED] — Android grants it when the user picks partial access.
 */
fun photoLibraryPermissionsToRequest(): Array<String> = when {
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** @deprecated Use [photoLibraryPermissionsToRequest] for requests; kept for call-site compatibility. */
fun photoLibraryPermissions(): Array<String> = photoLibraryPermissionsToRequest()

fun photoLibraryAccess(context: Context): PhotoLibraryAccess {
    fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    if (Build.VERSION.SDK_INT >= 33) {
        val images = granted(Manifest.permission.READ_MEDIA_IMAGES)
        val videos = granted(Manifest.permission.READ_MEDIA_VIDEO)
        if (images && videos) return PhotoLibraryAccess.FULL

        val selected = Build.VERSION.SDK_INT >= 34 &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        return if (images || videos || selected) {
            PhotoLibraryAccess.PARTIAL
        } else {
            PhotoLibraryAccess.DENIED
        }
    }

    return if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
        PhotoLibraryAccess.FULL
    } else {
        PhotoLibraryAccess.DENIED
    }
}

/** Re-read runtime permission when returning from Settings. */
@Composable
fun rememberRuntimePermissionGranted(permission: String): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember(permission) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    DisposableEffect(lifecycleOwner, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}
