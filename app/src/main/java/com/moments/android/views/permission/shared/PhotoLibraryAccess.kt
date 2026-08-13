package com.moments.android.views.permission.shared

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Effective access available to Moments' in-app MediaStore gallery. */
enum class PhotoLibraryAccess {
    FULL,
    PARTIAL,
    DENIED,
}

fun photoLibraryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

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
