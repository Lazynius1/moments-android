package com.moments.android.views.shared

import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import com.moments.android.ad.findActivity
import java.util.WeakHashMap

/**
 * Cómo proteger el contenido (paridad con iOS `ScreenshotProtectedView`).
 *
 * - [ContentSurface]: `SurfaceView.setSecure` + fondo AdaptiveColors.
 *   Default Moments/feed/chat. Historias usan [WindowFlag].
 * - [WindowFlag]: `FLAG_SECURE` en la Activity (fullscreen stories).
 */
enum class ScreenshotProtectionMode {
    ContentSurface,
    WindowFlag,
}

/**
 * Port de `ScreenshotProtectedView.swift`.
 *
 * Condición típica: `(audience?.lowercased() ?? "") != "everyone"`.
 */
@Composable
fun ScreenshotProtectedView(
    isProtected: Boolean,
    fillsContainer: Boolean = false,
    cornerRadius: Dp? = null,
    updateToken: Any? = null,
    mode: ScreenshotProtectionMode = ScreenshotProtectionMode.ContentSurface,
    content: @Composable () -> Unit,
) {
    val body: @Composable () -> Unit = {
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

    val wrapped: @Composable () -> Unit = {
        if (fillsContainer) {
            Box(Modifier.fillMaxSize()) { clipped() }
        } else {
            clipped()
        }
    }

    if (!isProtected) {
        wrapped()
        return
    }

    when (mode) {
        ScreenshotProtectionMode.WindowFlag -> {
            WindowFlagSecureEffect()
            wrapped()
        }
        ScreenshotProtectionMode.ContentSurface -> {
            val hostModifier = if (fillsContainer) Modifier.fillMaxSize() else Modifier
            SecureComposeSurfaceHost(modifier = hostModifier) {
                wrapped()
            }
        }
    }
}

@Composable
private fun WindowFlagSecureEffect() {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.findActivity() ?: view.context.findActivity()

    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            SecureFlagRegistry.acquire(window)
            onDispose { SecureFlagRegistry.release(window) }
        } else {
            onDispose { }
        }
    }

    SideEffect {
        activity?.window?.let(SecureFlagRegistry::ensureSecure)
    }
}

@Composable
fun ScreenshotProtected(
    isProtected: Boolean = true,
    fillsContainer: Boolean = false,
    cornerRadius: Dp? = null,
    updateToken: Any? = null,
    mode: ScreenshotProtectionMode = ScreenshotProtectionMode.ContentSurface,
    content: @Composable () -> Unit,
) {
    ScreenshotProtectedView(
        isProtected = isProtected,
        fillsContainer = fillsContainer,
        cornerRadius = cornerRadius,
        updateToken = updateToken,
        mode = mode,
        content = content,
    )
}

private object SecureFlagRegistry {
    private val counts = WeakHashMap<Window, Int>()

    fun acquire(window: Window) {
        synchronized(counts) {
            val next = (counts[window] ?: 0) + 1
            counts[window] = next
            applySecure(window)
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
                applySecure(window)
            }
        }
    }

    fun ensureSecure(window: Window) {
        synchronized(counts) {
            if ((counts[window] ?: 0) > 0) applySecure(window)
        }
    }

    private fun applySecure(window: Window) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }
}
