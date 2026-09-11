package com.moments.android.services.firestore

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.moments.android.services.network.NetworkMonitor
import kotlinx.coroutines.tasks.await

/**
 * Lecturas Firestore tolerantes a offline.
 *
 * En Android, `get()` / `Source.DEFAULT` con el cliente offline **tira**
 * (`Failed to get document because the client is offline`), aunque haya caché.
 * iOS usa completions que ignoran el error. Aquí: intentar DEFAULT si hay red,
 * y si falla por offline (o ya estamos offline) → [Source.CACHE].
 */
suspend fun DocumentReference.getOfflineAware(): DocumentSnapshot? {
    if (!NetworkMonitor.isConnected) {
        return runCatching { get(Source.CACHE).await() }.getOrNull()
    }
    return runCatching { get(Source.DEFAULT).await() }.getOrElse { error ->
        if (error.isFirestoreOffline()) {
            runCatching { get(Source.CACHE).await() }.getOrNull()
        } else {
            null
        }
    }
}

suspend fun Query.getOfflineAware(): QuerySnapshot? {
    if (!NetworkMonitor.isConnected) {
        return runCatching { get(Source.CACHE).await() }.getOrNull()
    }
    return runCatching { get(Source.DEFAULT).await() }.getOrElse { error ->
        if (error.isFirestoreOffline()) {
            runCatching { get(Source.CACHE).await() }.getOrNull()
        } else {
            null
        }
    }
}

fun Throwable.isFirestoreOffline(): Boolean {
    val firestore = this as? FirebaseFirestoreException
        ?: (cause as? FirebaseFirestoreException)
    if (firestore != null) {
        val code = firestore.code
        if (code == FirebaseFirestoreException.Code.UNAVAILABLE) return true
    }
    val message = message.orEmpty()
    return message.contains("client is offline", ignoreCase = true) ||
        message.contains("Failed to get document from cache", ignoreCase = true)
}
