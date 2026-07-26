package com.moments.android.services.messaging

import android.content.Context
import com.moments.android.views.messaging.core.MessageSyncCursor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Port de `Moments/Shared/MessageIngestQueue.swift` + `MessageSyncCursorStore`.
 * App Group iOS → `filesDir/moments_shared` + SharedPreferences (misma app UID).
 */
data class PendingMessageIngest(
    val conversationId: String,
    val messageId: String,
    val enqueuedAt: Date = Date(),
)

object MessageIngestQueue {
    const val SHARED_DIR_NAME = "moments_shared"
    private const val FILE_NAME = "pending_message_ingest.json"
    private val lock = ReentrantLock()

    @Volatile private var queueFile: File? = null

    fun initialize(context: Context) {
        if (queueFile != null) return
        val dir = File(context.applicationContext.filesDir, SHARED_DIR_NAME).apply { mkdirs() }
        queueFile = File(dir, FILE_NAME)
    }

    fun enqueue(conversationId: String, messageId: String) {
        val conv = conversationId.trim()
        val msg = messageId.trim()
        if (conv.isEmpty() || msg.isEmpty()) return

        lock.withLock {
            val pending = readPendingUnsafe().toMutableList()
            val item = PendingMessageIngest(conv, msg)
            if (pending.any { it.conversationId == item.conversationId && it.messageId == item.messageId }) return
            pending.add(item)
            writePendingUnsafe(pending)
        }
    }

    fun drainAll(): List<PendingMessageIngest> = lock.withLock {
        val pending = readPendingUnsafe()
        writePendingUnsafe(emptyList())
        pending
    }

    fun remove(processed: List<PendingMessageIngest>) {
        if (processed.isEmpty()) return
        lock.withLock {
            val keys = processed.map { "${it.conversationId}:${it.messageId}" }.toSet()
            val remaining = readPendingUnsafe().filter { "${it.conversationId}:${it.messageId}" !in keys }
            writePendingUnsafe(remaining)
        }
    }

    fun clear() = lock.withLock { writePendingUnsafe(emptyList()) }

    private fun readPendingUnsafe(): List<PendingMessageIngest> {
        val file = queueFile ?: return emptyList()
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PendingMessageIngest(
                    conversationId = obj.getString("conversationId"),
                    messageId = obj.getString("messageId"),
                    enqueuedAt = Date(obj.optLong("enqueuedAt", System.currentTimeMillis())),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Escritura atómica ≡ iOS `data.write(to:options: .atomic)`. */
    private fun writePendingUnsafe(pending: List<PendingMessageIngest>) {
        val file = queueFile ?: return
        val arr = JSONArray().apply {
            pending.forEach { item ->
                put(JSONObject().apply {
                    put("conversationId", item.conversationId)
                    put("messageId", item.messageId)
                    put("enqueuedAt", item.enqueuedAt.time)
                })
            }
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }.onFailure {
            tmp.delete()
        }
    }
}

object MessageSyncCursorStore {
    private const val PREFS = "message_sync_cursors"
    private const val LEGACY_PREFIX = "messageSyncCursor_"
    private const val TIMESTAMP_PREFIX = "messageSyncCursor_ts_"
    private const val MESSAGE_ID_PREFIX = "messageSyncCursor_id_"

    @Volatile private var prefsProvider: (() -> android.content.SharedPreferences)? = null

    fun initialize(context: Context) {
        if (prefsProvider != null) return
        val app = context.applicationContext
        prefsProvider = {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    private fun prefs() = prefsProvider?.invoke() ?: error("MessageSyncCursorStore.initialize required")

    fun cursor(conversationId: String): MessageSyncCursor? {
        val p = prefs()
        val tsKey = TIMESTAMP_PREFIX + conversationId
        val idKey = MESSAGE_ID_PREFIX + conversationId
        val storedTs = p.getLong(tsKey, 0L)
        if (storedTs > 0L) {
            return MessageSyncCursor(
                timestamp = Date(storedTs),
                messageId = p.getString(idKey, "") ?: "",
            )
        }
        val legacyTs = p.getLong(LEGACY_PREFIX + conversationId, 0L)
        if (legacyTs > 0L) {
            return MessageSyncCursor(Date(legacyTs), "")
        }
        return null
    }

    fun updateCursor(conversationId: String, timestamp: Date, messageId: String) {
        updateCursor(conversationId, MessageSyncCursor(timestamp, messageId))
    }

    fun updateCursor(conversationId: String, cursor: MessageSyncCursor) {
        prefs().edit()
            .putLong(TIMESTAMP_PREFIX + conversationId, cursor.timestamp.time)
            .putString(MESSAGE_ID_PREFIX + conversationId, cursor.messageId)
            .remove(LEGACY_PREFIX + conversationId)
            .apply()
    }

    fun clearAll() {
        val p = prefs()
        val editor = p.edit()
        p.all.keys.filter {
            it.startsWith(LEGACY_PREFIX) || it.startsWith(TIMESTAMP_PREFIX) || it.startsWith(MESSAGE_ID_PREFIX)
        }.forEach { editor.remove(it) }
        editor.apply()
    }
}
