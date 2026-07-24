package com.moments.android.views.nova.memory

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/** Encrypted, user-scoped long-term Nova facts with a small in-memory cache. */
object NovaMemoryStore {
    private val db = FirebaseFirestore.getInstance()
    private val memoryCache = ConcurrentHashMap<String, NovaMemory>()
    private val updates = MutableSharedFlow<String>(extraBufferCapacity = 8)

    suspend fun loadMemory(userId: String): NovaMemory? {
        memoryCache[userId]?.let { return it }
        return try {
            val snapshot = memoryDocument(userId).get().await()
            val stored = snapshot.data?.toStringAnyMap()?.let(NovaMemory::fromFirestoreData)
            if (!snapshot.exists() || stored == null) {
                NovaMemory(userId = userId).also { memoryCache[userId] = it }
            } else {
                val decrypted = NovaMemoryCrypto.decryptMemory(stored, userId)
                val compacted = decrypted.compacted()
                memoryCache[userId] = compacted
                if (compacted.facts.size != decrypted.facts.size || NovaMemoryCrypto.memoryNeedsEncryptionMigration(stored)) {
                    runCatching { persistEncrypted(compacted, userId) }
                }
                compacted
            }
        } catch (error: Exception) {
            Log.e(TAG, "loadMemory failed", error)
            NovaMemory(userId = userId)
        }
    }

    suspend fun saveMemory(memory: NovaMemory) {
        persistEncrypted(memory, memory.userId)
        memoryCache[memory.userId] = memory
    }

    fun invalidateCache(userId: String) {
        memoryCache.remove(userId)
    }

    fun memory(userId: String): NovaMemory? = memoryCache[userId]

    /** Android counterpart of the Swift NovaMemoryDidUpdate notification. */
    fun observeUpdates(userId: String): Flow<Unit> = updates.filter { it == userId }.map { }

    fun notifyMemoryUpdated(userId: String) {
        updates.tryEmit(userId)
    }

    private fun memoryDocument(userId: String) = db.collection("users").document(userId)
        .collection("novaMemory").document("memory")

    private suspend fun persistEncrypted(memory: NovaMemory, userId: String) {
        val encrypted = NovaMemoryCrypto.encryptMemory(memory, userId)
        memoryDocument(userId).set(encrypted.toFirestoreData(), SetOptions.merge()).await()
    }

    private fun Any?.toStringAnyMap(): Map<String, Any?>? = (this as? Map<*, *>)?.entries
        ?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
        ?.toMap()

    private const val TAG = "NovaMemoryStore"
}
