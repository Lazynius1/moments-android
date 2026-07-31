package com.moments.android.views.shared

import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** AdaptiveColors canvas — dark `#0B1215` / light `#FAF9F6`. */
private val CanvasDark = Color(0xFF0B1215)
private val CanvasLight = Color(0xFFFAF9F6)

/**
 * Host tipo iOS `UITextField.isSecureTextEntry`: región con [SurfaceView.setSecure].
 * Fondo canvas AdaptiveColors (no negro) para disimular letterboxing si el blit falla parcial.
 */
@Composable
fun SecureComposeSurfaceHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val canvasArgb = remember(isDark) {
        (if (isDark) CanvasDark else CanvasLight).toArgb()
    }

    AndroidView(
        factory = { context ->
            SecureComposeSurfaceLayout(context).also { it.canvasColorArgb = canvasArgb }
        },
        update = { layout ->
            layout.canvasColorArgb = canvasArgb
            layout.updateContent(content)
        },
        modifier = modifier,
    )
}

private class SecureComposeSurfaceLayout(
    context: Context,
) : FrameLayout(context),
    SurfaceHolder.Callback,
    ViewTreeObserver.OnPreDrawListener {

    var canvasColorArgb: Int = CanvasDark.toArgb()
        set(value) {
            if (field == value) return
            field = value
            setBackgroundColor(value)
            blitSecureFrame()
        }

    private val secureSurface = SurfaceView(context).apply {
        setSecure(true)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this@SecureComposeSurfaceLayout)
    }

    private val composeView = ComposeView(context).apply {
        alpha = 0f
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private var surfaceReady = false
    private var latestContent: (@Composable () -> Unit)? = null

    init {
        setBackgroundColor(canvasColorArgb)
        addView(secureSurface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun updateContent(content: @Composable () -> Unit) {
        latestContent = content
        bindOwnersIfNeeded()
        composeView.setContent {
            latestContent?.invoke()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bindOwnersIfNeeded()
        viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(this)
        super.onDetachedFromWindow()
    }

    private fun bindOwnersIfNeeded() {
        val parent = parent as? View ?: return
        if (composeView.findViewTreeLifecycleOwner() == null) {
            parent.findViewTreeLifecycleOwner()?.let { composeView.setViewTreeLifecycleOwner(it) }
        }
        if (composeView.findViewTreeSavedStateRegistryOwner() == null) {
            parent.findViewTreeSavedStateRegistryOwner()?.let {
                composeView.setViewTreeSavedStateRegistryOwner(it)
            }
        }
    }

    override fun onPreDraw(): Boolean {
        blitSecureFrame()
        return true
    }

    private fun blitSecureFrame() {
        if (!surfaceReady || width <= 0 || height <= 0) return
        val surface = secureSurface.holder.surface
        if (!surface.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = secureSurface.holder.lockHardwareCanvas()
                ?: secureSurface.holder.lockCanvas()
                ?: return
            canvas.drawColor(canvasColorArgb)
            val previousAlpha = composeView.alpha
            composeView.alpha = 1f
            composeView.draw(canvas)
            composeView.alpha = previousAlpha
        } catch (_: Throwable) {
        } finally {
            try {
                canvas?.let { secureSurface.holder.unlockCanvasAndPost(it) }
            } catch (_: Throwable) {
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        composeView.dispatchTouchEvent(ev)

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        blitSecureFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceReady = true
        blitSecureFrame()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
    }
}
