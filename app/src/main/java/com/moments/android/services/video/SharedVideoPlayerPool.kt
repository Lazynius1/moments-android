package com.moments.android.services.video

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Port de SharedVideoPlayerPool.swift.
 * Pool compartido de ExoPlayer para feed y reels (máx. 3 instancias activas).
 *
 * Lazy: no crea los 3 ExoPlayers en Application.onCreate — el primer `player()`
 * materializa slots bajo demanda (cold start más ligero).
 */
object SharedVideoPlayerPool {

    private data class Slot(
        val player: ExoPlayer,
        var consumerId: String? = null,
        var lastUsed: Date = Date(0),
    )

    private const val POOL_SIZE = 3
    private val slots = mutableListOf<Slot>()
    private val lock = Any()
    private val evictionHandlers = ConcurrentHashMap<String, () -> Unit>()
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        // No crear players aquí: se materializan en ensureSlotCapacity / player().
    }

    fun setEvictionHandler(consumerId: String, handler: () -> Unit) {
        evictionHandlers[consumerId] = handler
    }

    fun player(consumerId: String): ExoPlayer {
        ensureContext()
        var handler: (() -> Unit)? = null
        val player: ExoPlayer
        synchronized(lock) {
            ensureSlotCapacityLocked()
            val existing = slots.indexOfFirst { it.consumerId == consumerId }
            if (existing >= 0) {
                slots[existing].lastUsed = Date()
                return slots[existing].player
            }
            val free = slots.indexOfFirst { it.consumerId == null }
            if (free >= 0) {
                slots[free].consumerId = consumerId
                slots[free].lastUsed = Date()
                return slots[free].player
            }
            // Ampliar hasta POOL_SIZE si aún no está lleno.
            if (slots.size < POOL_SIZE) {
                val ctx = appContext!!
                val slot = Slot(player = buildAdaptiveExoPlayer(ctx), consumerId = consumerId, lastUsed = Date())
                slots += slot
                return slot.player
            }
            val lruIndex = slots.withIndex().minByOrNull { it.value.lastUsed.time }?.index ?: 0
            val evictedConsumer = slots[lruIndex].consumerId
            evictSlot(lruIndex)
            slots[lruIndex].consumerId = consumerId
            slots[lruIndex].lastUsed = Date()
            player = slots[lruIndex].player
            handler = evictedConsumer?.let { evictionHandlers[it] }
        }
        handler?.invoke()
        return player
    }

    fun release(consumerId: String) {
        synchronized(lock) {
            val index = slots.indexOfFirst { it.consumerId == consumerId }
            if (index < 0) {
                evictionHandlers.remove(consumerId)
                return
            }
            evictSlot(index)
            evictionHandlers.remove(consumerId)
        }
    }

    fun hasPlayer(consumerId: String): Boolean {
        synchronized(lock) {
            return slots.any { it.consumerId == consumerId }
        }
    }

    fun hasActiveItem(consumerId: String): Boolean {
        synchronized(lock) {
            val index = slots.indexOfFirst { it.consumerId == consumerId }
            if (index < 0) return false
            return slots[index].player.mediaItemCount > 0
        }
    }

    /** Port de pausar todos los AVPlayer del pool (GlobalVideoManager.pauseAllVideos). */
    fun pauseAll() {
        synchronized(lock) {
            slots.forEach { it.player.pause() }
        }
    }

    fun setAllVolumes(volume: Float) {
        synchronized(lock) {
            slots.forEach { it.player.volume = volume }
        }
    }

    private fun evictSlot(index: Int) {
        val player = slots[index].player
        player.pause()
        player.clearMediaItems()
        slots[index].consumerId = null
        slots[index].lastUsed = Date(0)
    }

    private fun ensureContext() {
        check(appContext != null) {
            "SharedVideoPlayerPool.initialize(context) debe llamarse antes de usar el pool"
        }
    }

    /** Crea el primer slot si el pool aún está vacío (primer vídeo del feed). */
    private fun ensureSlotCapacityLocked() {
        if (slots.isNotEmpty()) return
        val ctx = appContext ?: return
        slots += Slot(player = buildAdaptiveExoPlayer(ctx))
    }
}
