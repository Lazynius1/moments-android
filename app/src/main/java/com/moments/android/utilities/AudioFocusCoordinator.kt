package com.moments.android.utilities

/** Ownership and generations prevent stale cleanup from releasing newer audio. */
internal class AudioFocusCoordinator(private val abandonFocus: () -> Unit) {
    private data class Claim(val generation: Long, val onLoss: () -> Unit)
    private val claims = linkedMapOf<Any, Claim>()

    @Synchronized
    fun acquire(owner: Any, generation: Long, isCurrent: () -> Boolean,
                requestFocus: () -> Boolean, onLoss: () -> Unit): Boolean {
        if (!isCurrent()) return false
        if (claims.isEmpty() && !requestFocus()) return false
        claims[owner] = Claim(generation, onLoss)
        return true
    }

    @Synchronized
    fun release(owner: Any, throughGeneration: Long) {
        val claim = claims[owner] ?: return
        if (claim.generation > throughGeneration) return
        claims.remove(owner)
        if (claims.isEmpty()) abandonFocus()
    }

    @Synchronized
    fun interrupt(): List<() -> Unit> {
        val callbacks = claims.values.map { it.onLoss }
        claims.clear()
        abandonFocus()
        return callbacks
    }
}
