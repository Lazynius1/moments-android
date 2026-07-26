package com.moments.android.services.performance

/**
 * Port de `FeedVisibilityCoordinator.swift`.
 * Elige un único vídeo activo en el feed según la fracción visible de cada post.
 *
 * La parte SwiftUI (`MomentVisibilityPreference` / `feedMomentVisibility`) vive en la capa View.
 */
object FeedVisibilityCoordinator {
    private const val PLAY_THRESHOLD = 0.55f

    @Volatile
    var activeVideoMomentId: String? = null
        private set

    private val visibilityByMomentId = mutableMapOf<String, Float>()
    private val lock = Any()

    fun update(all: Map<String, Float>) {
        synchronized(lock) {
            visibilityByMomentId.clear()
            visibilityByMomentId.putAll(all)
            pickWinnerLocked()
        }
    }

    fun report(momentId: String, fraction: Float) {
        synchronized(lock) {
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
        }
    }

    fun isActive(momentId: String?): Boolean {
        val active = activeVideoMomentId ?: return false
        return momentId != null && active == momentId
    }

    private fun pickWinnerLocked() {
        PerformanceSignposts.begin("FeedPickActiveVideo")
        val candidate = visibilityByMomentId
            .filter { it.value >= PLAY_THRESHOLD }
            .maxByOrNull { it.value }
            ?.key
        if (activeVideoMomentId != candidate) {
            activeVideoMomentId = candidate
            PerformanceSignposts.event("FeedActiveVideoChanged")
        }
        PerformanceSignposts.end("FeedPickActiveVideo")
    }
}
