package com.moments.android.services.performance

import android.content.Context
import android.provider.Settings

/**
 * Port de MotionPolicy.swift.
 * Reduce motion + límites de partículas. Springs/Transitions → capa UI Compose.
 */
object MotionPolicy {
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    val reduceMotion: Boolean
        get() {
            val ctx = appContext ?: return false
            val resolver = ctx.contentResolver
            val animatorScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            val transitionScale = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
            return animatorScale == 0f || transitionScale == 0f
        }

    fun revealParticleCount(width: Float, height: Float): Int {
        if (reduceMotion) return 0
        val area = maxOf(width * height, 1f)
        return minOf(maxOf((area / 90).toInt(), 80), 600)
    }

    val maxParticleCount: Int
        get() {
            if (reduceMotion) return 0
            return 140
        }

    const val canvasFPS: Double = 30.0
}
