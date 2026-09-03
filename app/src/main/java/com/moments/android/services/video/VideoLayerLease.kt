package com.moments.android.services.video

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Port de `VideoLayerLease` (VideoPlayer.swift).
 * Un ExoPlayer solo pinta en un PlayerView: feed y Reels se turnan el enchufe.
 */
enum class VideoLayerRole {
    Feed,
    Reels,
}

object VideoLayerLease {
    private val main = Handler(Looper.getMainLooper())

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    var exclusiveConsumerId: String? = null
        private set
    var owner: VideoLayerRole = VideoLayerRole.Feed
        private set
    var generation: Long = 0L
        private set

    private var isTransitioning = false
    private var canClaimReels = false
    private var idleRunnable: Runnable? = null

    fun mayAttach(role: VideoLayerRole, consumerId: String): Boolean {
        if (consumerId.isEmpty()) return true
        val exclusive = exclusiveConsumerId ?: return true
        if (exclusive != consumerId) return true
        return owner == role
    }

    /** Feed en póster mientras Reels tiene el layer (ida y vuelta). */
    fun isFeedAsleep(consumerId: String): Boolean {
        return exclusiveConsumerId == consumerId && owner == VideoLayerRole.Reels
    }

    /**
     * El feed no debe marcar “Ver otra vez” mientras el mismo consumer está en handoff feed↔Reels.
     * Cubre la ventana entre `beginReels` y `markReelsFeedHandoff`, y el encoger al cerrar.
     */
    fun suppressesFeedPlaybackFinished(consumerId: String): Boolean {
        if (consumerId.isEmpty()) return false
        val exclusive = exclusiveConsumerId ?: return false
        if (exclusive != consumerId) return false
        return isTransitioning || owner == VideoLayerRole.Reels
    }

    fun isHandoffConsumer(consumerId: String): Boolean =
        !consumerId.isEmpty() && exclusiveConsumerId == consumerId

    fun beginReels(consumerId: String): Boolean {
        if (isTransitioning) return false
        isTransitioning = true
        canClaimReels = true
        cancelIdle()
        exclusiveConsumerId = consumerId
        owner = VideoLayerRole.Reels
        generation += 1
        bump()
        return true
    }

    fun claimReels(consumerId: String) {
        if (!canClaimReels) return
        if (exclusiveConsumerId != consumerId) return
        canClaimReels = false
        owner = VideoLayerRole.Reels
        bump()
    }

    fun returnToFeed() {
        if (!isTransitioning && exclusiveConsumerId == null) return
        canClaimReels = false
        owner = VideoLayerRole.Feed
        generation += 1
        bump()
        cancelIdle()
        val work = Runnable {
            isTransitioning = false
            idleRunnable = null
        }
        idleRunnable = work
        main.postDelayed(work, 450)
    }

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    private fun cancelIdle() {
        idleRunnable?.let { main.removeCallbacks(it) }
        idleRunnable = null
    }
}
