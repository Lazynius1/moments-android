package com.moments.android.services.performance

/** Port de FeedVisibilityCoordinator.swift. */
object FeedVisibilityCoordinator {
    private const val PLAY_THRESHOLD = 0.55f

    @Volatile
    var activeVideoMomentId: String? = null
        private set

    private val visibilityByMomentId = mutableMapOf<String, Float>()

    fun update(all: Map<String, Float>) {
        visibilityByMomentId.clear()
        visibilityByMomentId.putAll(all)
        pickWinner()
    }

    fun report(momentId: String, fraction: Float) {
        visibilityByMomentId[momentId] = fraction
        pickWinner()
    }

    fun clear(momentId: String) {
        visibilityByMomentId.remove(momentId)
        pickWinner()
    }

    fun pinActiveVideo(momentId: String) {
        visibilityByMomentId.clear()
        visibilityByMomentId[momentId] = 1f
        activeVideoMomentId = momentId
    }

    fun isActive(momentId: String?): Boolean {
        val active = activeVideoMomentId ?: return false
        return momentId != null && active == momentId
    }

    private fun pickWinner() {
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
