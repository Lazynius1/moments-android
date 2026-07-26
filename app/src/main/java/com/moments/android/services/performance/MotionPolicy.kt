package com.moments.android.services.performance

import android.content.Context
import android.provider.Settings
import com.moments.android.utilities.ActiveWindowMetrics

/**
 * Port de `MotionPolicy.swift`.
 * Reduce motion + LOD de partículas. Springs/Transitions: constantes para Compose.
 */
object MotionPolicy {
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** `UIAccessibility.isReduceMotionEnabled` → escalas de animación del sistema en 0. */
    val reduceMotion: Boolean
        get() {
            val ctx = appContext ?: return false
            val resolver = ctx.contentResolver
            val animatorScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            val transitionScale = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
            return animatorScale == 0f || transitionScale == 0f
        }

    /** Densidad original del reveal noise (calidad visual prioritaria). */
    fun revealParticleCount(width: Float, height: Float): Int {
        if (reduceMotion) return 0
        val area = maxOf(width * height, 1f)
        return minOf(maxOf((area / 90).toInt(), 80), 600)
    }

    /** Cap más bajo solo para efectos secundarios del feed, no reveal. */
    val maxParticleCount: Int
        get() {
            if (reduceMotion) return 0
            val ctx = appContext ?: return 140
            val size = ActiveWindowMetrics.activeWindowSize(ctx)
            val area = size.width.toFloat() * size.height.toFloat()
            return when {
                area < 350_000f -> 80
                area < 450_000f -> 140
                else -> 220
            }
        }

    const val canvasFPS: Double = 30.0

    /**
     * Presets de spring alineados con iOS `MotionPolicy.Spring`.
     * Compose: `spring(dampingRatio = …, stiffness ≈ mapping de response)`.
     */
    object Spring {
        const val PRESS_RESPONSE = 0.28
        const val PRESS_DAMPING = 0.72
        const val TIMESTAMP_RETURN_RESPONSE = 0.2
        const val TIMESTAMP_RETURN_DAMPING = 0.94
        const val TOGGLE_RESPONSE = 0.32
        const val TOGGLE_DAMPING = 0.78
        const val SHEET_DURATION = 0.18
        const val HEADER_RESPONSE = 0.32
        const val HEADER_DAMPING = 0.86
        const val ROW_RESPONSE = 0.3
        const val ROW_DAMPING = 0.8
        const val ONBOARDING_RESPONSE = 0.55
        const val ONBOARDING_DAMPING = 0.82
        const val DELIGHT_RESPONSE = 0.45
        const val DELIGHT_DAMPING = 0.8
        const val TOAST_DURATION = 0.2
        const val PULSE_DURATION = 1.2
    }

    /** Escalas de pop (iOS Transition.enterPop / badgePop). */
    object Transition {
        const val ENTER_POP_SCALE = 0.95f
        const val BADGE_POP_SCALE = 0.9f
    }
}
