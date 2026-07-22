package com.moments.android.views.shared

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Port de `ScreenshotProtectedView.swift`.
 * iOS: UITextField.isSecureTextEntry. Android: FLAG_SECURE en la Activity
 * mientras el contenido protegido esté visible.
 */
@Composable
fun ScreenshotProtectedView(
    isProtected: Boolean,
    fillsContainer: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity ?: view.context as? Activity

    DisposableEffect(isProtected, activity) {
        val window = activity?.window
        if (isProtected && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (isProtected && window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    if (fillsContainer) {
        Box(Modifier.fillMaxSize()) { content() }
    } else {
        content()
    }
}
