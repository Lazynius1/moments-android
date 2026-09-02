package com.moments.android.services.performance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Port de `FeedVisibilityCoordinator.swift`.
 * Elige un único vídeo activo en el feed según la fracción visible de cada post.
 *
 * La parte SwiftUI (`MomentVisibilityPreference` / `feedMomentVisibility`) vive en la capa View.
 */
object FeedVisibilityCoordinator {
    private const val PLAY_THRESHOLD = 0.32f
    private const val WARM_THRESHOLD = 0.16f
    /** Ignora micro-cambios de fracción durante el scroll. */
    private const val REPORT_EPSILON = 0.05f

    private val _activeVideoMomentId = MutableStateFlow<String?>(null)
    /** Observable para Compose (`ModernVideoPlayer` onChange). */
    val activeVideoMomentIdFlow: StateFlow<String?> = _activeVideoMomentId.asStateFlow()

    private val _warmingVideoMomentId = MutableStateFlow<String?>(null)
    val warmingVideoMomentIdFlow: StateFlow<String?> = _warmingVideoMomentId.asStateFlow()

    var activeVideoMomentId: String?
        get() = _activeVideoMomentId.value
        private set(value) {
            _activeVideoMomentId.value = value
        }

    var warmingVideoMomentId: String?
        get() = _warmingVideoMomentId.value
        private set(value) {
            _warmingVideoMomentId.value = value
        }

    private val visibilityByMomentId = mutableMapOf<String, Float>()
    private val lock = Any()

    fun update(all: Map<String, Float>) {
        if (all.isEmpty()) return
        synchronized(lock) {
            var changed = false
            for ((id, fraction) in all) {
                val previous = visibilityByMomentId[id] ?: -1f
                if (kotlin.math.abs(previous - fraction) >= REPORT_EPSILON ||
                    (fraction == 0f) != (previous == 0f)
                ) {
                    visibilityByMomentId[id] = fraction
                    changed = true
                }
            }
            val incoming = all.keys
            val stale = visibilityByMomentId.keys.filter { it !in incoming }
            if (stale.isNotEmpty()) {
                stale.forEach { visibilityByMomentId.remove(it) }
                changed = true
            }
            if (changed) pickWinnerLocked()
        }
    }

    fun report(momentId: String, fraction: Float) {
        synchronized(lock) {
            val previous = visibilityByMomentId[momentId] ?: -1f
            if (kotlin.math.abs(previous - fraction) < REPORT_EPSILON &&
                (fraction == 0f) == (previous == 0f)
            ) {
                return
            }
            visibilityByMomentId[momentId] = fraction
            pickWinnerLocked()
        }
    }

    fun clear(momentId: String) {
        synchronized(lock) {
            visibilityByMomentId.remove(momentId)
            pickWinnerLocked()
        }
    }

    /** Fija un único vídeo activo (p. ej. durante hero → detalle). */
    fun pinActiveVideo(momentId: String) {
        synchronized(lock) {
            visibilityByMomentId.clear()
            visibilityByMomentId[momentId] = 1f
            activeVideoMomentId = momentId
            warmingVideoMomentId = null
        }
    }

    fun isActive(momentId: String?): Boolean {
        val active = activeVideoMomentId ?: return false
        return momentId != null && active == momentId
    }

    private fun pickWinnerLocked() {
        PerformanceSignposts.begin("FeedPickActiveVideo")
        val ranked = visibilityByMomentId.entries.sortedByDescending { it.value }
        val playCandidate = ranked.firstOrNull { it.value >= PLAY_THRESHOLD }?.key
        if (playCandidate != null && activeVideoMomentId != playCandidate) {
            activeVideoMomentId = playCandidate
            PerformanceSignposts.event("FeedActiveVideoChanged")
        }
        val warmCandidate = ranked.firstOrNull {
            it.value >= WARM_THRESHOLD && it.key != activeVideoMomentId
        }?.key
        if (warmingVideoMomentId != warmCandidate) {
            warmingVideoMomentId = warmCandidate
        }
        PerformanceSignposts.end("FeedPickActiveVideo")
    }
}
