package com.moments.android.utilities

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.util.Size
import android.view.WindowManager

/**
 * Ventana activa / métricas de pantalla. Equivalente Android de
 * `UIApplication.activeKeyWindow`, `activeWindowSize` y `activeDisplayScale`.
 *
 * Sin androidx.window: usa [DisplayMetrics] y [WindowManager].
 */
object ActiveWindowMetrics {
    private const val FALLBACK_WIDTH_PX = 393
    private const val FALLBACK_HEIGHT_PX = 852
    private const val FALLBACK_DENSITY = 3f

    /** Tamaño usable de la ventana activa en px; fallback canónico si no hay Activity. */
    fun activeWindowSize(context: Context): Size {
        val metrics = displayMetrics(context) ?: return Size(FALLBACK_WIDTH_PX, FALLBACK_HEIGHT_PX)
        return Size(metrics.widthPixels, metrics.heightPixels)
    }

    fun activeWindowSize(activity: Activity): Size {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = activity.windowManager.currentWindowMetrics.bounds
            Size(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val size = Point()
            activity.windowManager.defaultDisplay.getSize(size)
            Size(size.x, size.y)
        }
    }

    /** Escala lógica (density) de la pantalla activa. */
    fun activeDisplayScale(context: Context): Float {
        return displayMetrics(context)?.density ?: FALLBACK_DENSITY
    }

    fun activeDisplayScale(activity: Activity): Float = activeDisplayScale(activity as Context)

    private fun displayMetrics(context: Context): DisplayMetrics? {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return null
        val metrics = DisplayMetrics()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            wm.defaultDisplay?.getRealMetrics(metrics)
            metrics
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            metrics
        }
    }
}
