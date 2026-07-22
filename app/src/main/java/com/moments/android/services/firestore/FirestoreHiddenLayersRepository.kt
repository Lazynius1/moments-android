package com.moments.android.services.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.moments.android.models.HiddenLayerDiscovery
import com.moments.android.models.HiddenLayerMetricsSnapshot
import com.moments.android.models.MomentHiddenLayer
import com.moments.android.models.toMap
import com.moments.android.services.cache.UserCacheService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.Date

/** Port de FirestoreHiddenLayersRepository.swift. */
suspend fun FirestoreService.saveHiddenLayers(
    userId: String,
    momentId: String,
    layers: List<MomentHiddenLayer>,
) {
    if (layers.isEmpty()) {
        updateMomentHiddenLayerSummary(userId, momentId, 0)
        return
    }
    val momentRef = db.collection("users").document(userId).collection("moments").document(momentId)
    val visibleCount = layers.count { it.isVisibleInViewer }
    db.runBatch { batch ->
        for (layer in layers) {
            val layerData = layer.toMap().toMutableMap()
            layerData["id"] = layer.id
            batch.set(momentRef.collection("hiddenLayers").document(layer.id), layerData, com.google.firebase.firestore.SetOptions.merge())
        }
        batch.update(momentRef, mapOf(
            "hasHiddenLayers" to (visibleCount > 0),
            "hiddenLayerCount" to visibleCount,
        ))
    }.await()
}

suspend fun FirestoreService.updateMomentHiddenLayerSummary(userId: String, momentId: String, count: Int) {
    db.collection("users").document(userId).collection("moments").document(momentId)
        .update(mapOf("hasHiddenLayers" to (count > 0), "hiddenLayerCount" to count)).await()
}

suspend fun FirestoreService.fetchHiddenLayers(userId: String, momentId: String): List<MomentHiddenLayer> {
    val snap = db.collection("users").document(userId).collection("moments")
        .document(momentId).collection("hiddenLayers")
        .orderBy("zIndex", Query.Direction.ASCENDING)
        .get().await()
    return snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        runCatching { MomentHiddenLayer.from(doc.data as Map<String, Any?>) }.getOrNull()
    }
}

suspend fun FirestoreService.recordHiddenLayerDiscovery(
    ownerUserId: String,
    momentId: String,
    layerId: String,
    viewerId: String,
) {
    val cached = UserCacheService.getCachedUser(viewerId)
    val resolvedUser = cached ?: runCatching { fetchUser(viewerId) }.getOrNull()
    val username = resolvedUser?.username
    val profileImagePath = resolvedUser?.profileImagePath
    val now = Timestamp(Date())
    val discoveryData = buildMap<String, Any?> {
        put("viewerId", viewerId)
        put("discoveredAt", now)
        username?.let { put("username", it) }
        profileImagePath?.let { put("profileImagePath", it) }
    }
    val momentRef = db.collection("users").document(ownerUserId)
        .collection("moments").document(momentId)
    db.runBatch { batch ->
        batch.set(
            momentRef.collection("hiddenLayers").document(layerId)
                .collection("discoveries").document(viewerId),
            discoveryData,
            com.google.firebase.firestore.SetOptions.merge(),
        )
        batch.set(
            momentRef.collection("hiddenLayerDiscoverers").document(viewerId),
            buildMap {
                put("viewerId", viewerId)
                put("lastDiscoveredAt", now)
                username?.let { put("username", it) }
                profileImagePath?.let { put("profileImagePath", it) }
            },
            com.google.firebase.firestore.SetOptions.merge(),
        )
    }.await()
}

suspend fun FirestoreService.fetchHiddenLayerMetrics(
    userId: String,
    momentId: String,
): HiddenLayerMetricsSnapshot {
    val momentRef = db.collection("users").document(userId).collection("moments").document(momentId)
    val layersSnap = momentRef.collection("hiddenLayers").get().await()
    val layers = layersSnap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        runCatching { MomentHiddenLayer.from(doc.data as Map<String, Any?>) }.getOrNull()
    }.sortedWith(compareByDescending<MomentHiddenLayer> { it.discoverCount ?: 0 }
        .thenByDescending { it.createdAt.time })

    val discoverersSnap = momentRef.collection("hiddenLayerDiscoverers").get().await()
    val uniquePeopleCount = discoverersSnap.size()

    val recentDiscoveriesByLayer = coroutineScope {
        layers.map { layer ->
            async {
                val snap = momentRef.collection("hiddenLayers").document(layer.id)
                    .collection("discoveries")
                    .orderBy("discoveredAt", Query.Direction.DESCENDING)
                    .limit(3)
                    .get().await()
                layer.id to snap.documents.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    runCatching { HiddenLayerDiscovery.from(doc.data as Map<String, Any?>) }.getOrNull()
                }
            }
        }.awaitAll().toMap()
    }

    return HiddenLayerMetricsSnapshot(layers, uniquePeopleCount, recentDiscoveriesByLayer)
}

data class HiddenLayerDiscoveriesPage(
    val discoveries: List<HiddenLayerDiscovery>,
    val lastDocument: DocumentSnapshot?,
    val hasMore: Boolean,
)

suspend fun FirestoreService.fetchHiddenLayerDiscoveriesPage(
    userId: String,
    momentId: String,
    layerId: String,
    pageSize: Int = 8,
    startAfter: DocumentSnapshot? = null,
): HiddenLayerDiscoveriesPage {
    var query: Query = db.collection("users").document(userId).collection("moments")
        .document(momentId).collection("hiddenLayers").document(layerId)
        .collection("discoveries")
        .orderBy("discoveredAt", Query.Direction.DESCENDING)
        .limit(pageSize.toLong())
    if (startAfter != null) query = query.startAfter(startAfter)
    val snap = query.get().await()
    val discoveries = snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        runCatching { HiddenLayerDiscovery.from(doc.data as Map<String, Any?>) }.getOrNull()
    }
    return HiddenLayerDiscoveriesPage(discoveries, snap.documents.lastOrNull(), snap.size() == pageSize)
}

suspend fun FirestoreService.hideHiddenLayer(
    userId: String,
    momentId: String,
    layerId: String,
    reason: String? = null,
    category: String? = null,
) {
    val data = buildMap<String, Any> {
        put("moderationState", MomentHiddenLayer.ModerationState.HIDDEN.raw)
        put("moderatedAt", Timestamp(Date()))
        reason?.let { put("moderationReason", it) }
        category?.let { put("moderationCategory", it) }
    }
    db.collection("users").document(userId).collection("moments")
        .document(momentId).collection("hiddenLayers").document(layerId)
        .update(data).await()
    rebuildHiddenLayerSummary(userId, momentId)
}

suspend fun FirestoreService.markHiddenLayerVisible(userId: String, momentId: String, layerId: String) {
    db.collection("users").document(userId).collection("moments")
        .document(momentId).collection("hiddenLayers").document(layerId)
        .update(mapOf(
            "moderationState" to MomentHiddenLayer.ModerationState.VISIBLE.raw,
            "moderatedAt" to Timestamp(Date()),
            "moderationReason" to FieldValue.delete(),
            "moderationCategory" to FieldValue.delete(),
        )).await()
    rebuildHiddenLayerSummary(userId, momentId)
}

suspend fun FirestoreService.rebuildHiddenLayerSummary(userId: String, momentId: String) {
    val layers = fetchHiddenLayers(userId, momentId)
    val visibleCount = layers.count { it.isVisibleInViewer }
    updateMomentHiddenLayerSummary(userId, momentId, visibleCount)
}
