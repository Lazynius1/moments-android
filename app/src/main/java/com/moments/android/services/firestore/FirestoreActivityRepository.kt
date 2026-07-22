package com.moments.android.services.firestore

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.moments.android.models.AppUser
import com.moments.android.models.Visit
import com.moments.android.services.incognito.IncognitoModeService
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

/** Port de FirestoreActivityRepository.swift. */
suspend fun FirestoreService.updateLastAppOpenAt(userId: String? = null) {
    val resolvedUserId = userId ?: FirebaseAuth.getInstance().currentUser?.uid
        ?: error("Usuario no autenticado")
    updateUserActivityMetadata(resolvedUserId, mapOf("lastAppOpenAt" to FieldValue.serverTimestamp()))
}

suspend fun FirestoreService.updateLastMomentCreatedAt(userId: String) {
    updateUserActivityMetadata(userId, mapOf("lastMomentCreatedAt" to FieldValue.serverTimestamp()))
}

suspend fun FirestoreService.fetchVisits(userId: String): List<Visit> {
    val snap = db.collection("users").document(userId).collection("visits")
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .get()
        .await()
    return snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
        Visit(
            id = doc.id,
            visitorId = data["visitorId"] as? String ?: return@mapNotNull null,
            timestamp = (data["timestamp"] as? Timestamp)?.toDate() ?: Date(),
        )
    }
}

suspend fun FirestoreService.fetchVisitsWithUsers(userId: String): List<Pair<AppUser, Visit>> {
    val snap = db.collection("users").document(userId).collection("visits")
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .limit(50)
        .get()
        .await()
    val visits = snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
        Visit(
            id = doc.id,
            visitorId = data["visitorId"] as? String ?: return@mapNotNull null,
            timestamp = (data["timestamp"] as? Timestamp)?.toDate() ?: Date(),
        )
    }
    if (visits.isEmpty()) return emptyList()
    val visitorIds = visits.map { it.visitorId }.distinct()
    val users = fetchUsersAsync(visitorIds)
    val userDict = users.associateBy { it.id }
    return visits.mapNotNull { visit ->
        val user = userDict[visit.visitorId] ?: return@mapNotNull null
        user to visit
    }
}

suspend fun FirestoreService.registerVisit(visitorId: String, targetUserId: String) {
    if (visitorId == targetUserId) return
    if (IncognitoModeService.isActiveSnapshot) return

    val blockCheck = checkIfBlocked(visitorId, targetUserId)
    if (blockCheck.isBlockedByCurrentUser || blockCheck.isCurrentUserBlocked) return

    val visitsRef = db.collection("users").document(targetUserId).collection("visits")
    val fiveMinutesAgo = Calendar.getInstance().apply { add(Calendar.MINUTE, -5) }.time
    val recent = visitsRef
        .whereEqualTo("visitorId", visitorId)
        .whereGreaterThan("timestamp", Timestamp(fiveMinutesAgo))
        .limit(1)
        .get()
        .await()
    if (!recent.isEmpty) return

    val visitData = mapOf(
        "visitorId" to visitorId,
        "timestamp" to Timestamp(Date()),
    )
    visitsRef.add(visitData).await()
    updateVisitSummary(targetUserId, visitorId)
}

private suspend fun FirestoreService.updateVisitSummary(targetUserId: String, visitorId: String) {
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(Date())
    val visitSummaryRef = db.collection("users").document(targetUserId)
        .collection("visitSummaries").document(today)
    runCatching {
        visitSummaryRef.set(
            mapOf(
                "date" to today,
                "visitorIds" to FieldValue.arrayUnion(visitorId),
                "visitCount" to FieldValue.increment(1),
                "timestamp" to Timestamp(Date()),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }
}
