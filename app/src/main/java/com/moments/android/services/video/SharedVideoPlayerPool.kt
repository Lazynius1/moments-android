package com.moments.android.services.video

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Port de SharedVideoPlayerPool.swift.
 * Pool compartido de ExoPlayer para feed y reels (máx. 3 instancias activas).
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
    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (slots.isNotEmpty()) return
        appContext = context.applicationContext
        synchronized(lock) {
            if (slots.isNotEmpty()) return
            repeat(POOL_SIZE) {
                slots += Slot(
                    player = ExoPlayer.Builder(context.applicationContext).build().apply {
                        volume = 0f
                    },
                )
            }
        }
    }

    fun setEvictionHandler(consumerId: String, handler: () -> Unit) {
        evictionHandlers[consumerId] = handler
    }

    fun player(forConsumerId: String): ExoPlayer {
        ensureInitialized()
        var handler: (() -> Unit)? = null
        val player: ExoPlayer
        synchronized(lock) {
            val existing = slots.indexOfFirst { it.consumerId == forConsumerId }
            if (existing >= 0) {
                slots[existing].lastUsed = Date()
                return slots[existing].player
            }
            val free = slots.indexOfFirst { it.consumerId == null }
            if (free >= 0) {
                slots[free].consumerId = forConsumerId
                slots[free].lastUsed = Date()
                return slots[free].player
            }
            val lruIndex = slots.withIndex().minByOrNull { it.value.lastUsed.time }?.index ?: 0
            val evictedConsumer = slots[lruIndex].consumerId
            evictSlot(lruIndex)
            slots[lruIndex].consumerId = forConsumerId
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

    fun hasActiveItem(forConsumerId: String): Boolean {
        synchronized(lock) {
            val index = slots.indexOfFirst { it.consumerId == forConsumerId }
            if (index < 0) return false
            return slots[index].player.mediaItemCount > 0
        }
    }

    private fun evictSlot(index: Int) {
        val player = slots[index].player
        player.pause()
        player.clearMediaItems()
        slots[index].consumerId = null
        slots[index].lastUsed = Date(0)
    }

    private fun ensureInitialized() {
        check(slots.isNotEmpty()) {
            "SharedVideoPlayerPool.initialize(context) debe llamarse antes de usar el pool"
        }
    }
}
