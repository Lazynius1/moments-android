package com.moments.android.views.shared

import android.app.Activity
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import java.util.WeakHashMap

/**
 * Port de `ScreenshotProtectedView.swift`.
 *
 * iOS: contenido dentro de `UITextField.isSecureTextEntry` (solo ese subárbol sale
 * negro en captura). Android no tiene equivalente per-view → `FLAG_SECURE` en el
 * window de la Activity mientras haya al menos un protegido montado (refcount).
 *
 * En uso normal el contenido se ve; en screenshot/grabación el OS bloquea la captura.
 */
@Composable
fun ScreenshotProtectedView(
    isProtected: Boolean,
    fillsContainer: Boolean = false,
    cornerRadius: Dp? = null,
    updateToken: Any? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity ?: view.context as? Activity

    DisposableEffect(isProtected, activity) {
        val window = activity?.window
        if (isProtected && window != null) {
            SecureFlagRegistry.acquire(window)
            onDispose { SecureFlagRegistry.release(window) }
        } else {
            onDispose { }
        }
    }

    val body: @Composable () -> Unit = {
        // ≡ iOS `updateToken` / `shouldRefreshContent` — fuerza remount al cambiar token.
        if (updateToken != null) {
            key(updateToken) { content() }
        } else {
            content()
        }
    }

    val clipped: @Composable () -> Unit = {
        if (cornerRadius != null) {
            Box(Modifier.clip(RoundedCornerShape(cornerRadius))) { body() }
        } else {
            body()
        }
    }

    if (fillsContainer) {
        Box(Modifier.fillMaxSize()) { clipped() }
    } else {
        clipped()
    }
}

/**
 * ≡ iOS `View.screenshotProtected(when:fillsContainer:cornerRadius:updateToken:)`.
 */
@Composable
fun ScreenshotProtected(
    isProtected: Boolean = true,
    fillsContainer: Boolean = false,
    cornerRadius: Dp? = null,
    updateToken: Any? = null,
    content: @Composable () -> Unit,
) {
    ScreenshotProtectedView(
        isProtected = isProtected,
        fillsContainer = fillsContainer,
        cornerRadius = cornerRadius,
        updateToken = updateToken,
        content = content,
    )
}

/**
 * Contador por window: varios `ScreenshotProtectedView` simultáneos no deben
 * quitar `FLAG_SECURE` al desmontar solo uno.
 */
private object SecureFlagRegistry {
    private val counts = WeakHashMap<Window, Int>()

    fun acquire(window: Window) {
        synchronized(counts) {
            val next = (counts[window] ?: 0) + 1
            counts[window] = next
            if (next == 1) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    fun release(window: Window) {
        synchronized(counts) {
            val next = (counts[window] ?: 0) - 1
            if (next <= 0) {
                counts.remove(window)
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                counts[window] = next
            }
        }
    }
}
