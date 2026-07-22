package com.moments.android.services.social

import android.location.Location
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.moments.android.models.AppUser
import com.moments.android.models.Echo
import com.moments.android.models.EchoMomentRef
import com.moments.android.models.EchoParticipant
import com.moments.android.models.EchoParticipantStatus
import com.moments.android.models.EchoStatus
import com.moments.android.models.Moment
import com.moments.android.models.toMap
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.views.nova.tools.NovaEvents
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.privacy.filterVisibleContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import com.moments.android.services.firestore.fetchUser

/** Port de EchoService.swift. */
object EchoService {
    private val db = FirebaseFirestore.getInstance()
    private val firestoreService = FirestoreService()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val MAX_AUTHOR_IDS_PER_ECHO_QUERY = 30
    private const val MAX_CONCURRENT_ECHO_MOMENT_BATCH_QUERIES = 6

    private fun hasMinimumMomentPerspectives(echo: Echo): Boolean =
        echo.hasMinimumMomentParticipants

    fun checkForEchoOverlap(momentId: String, userId: String) {
        scope.launch {
            runCatching {
                val moment = firestoreService.fetchMoment(momentId, userId)
                checkForEchoOverlap(moment)
            }
        }
    }

    private suspend fun checkForEchoOverlap(newMoment: Moment) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val coordinate = newMoment.locationCoordinate ?: return

        val friendIds = getMutualIds(userId)
        if (friendIds.isEmpty()) return

        val searchWindow = Date(System.currentTimeMillis() - 86_400_000L)
        val recentMoments = fetchRecentMomentsFromAuthors(friendIds, searchWindow)
        val allNearby = recentMoments.filter { moment ->
            val friendCoord = moment.locationCoordinate ?: return@filter false
            calculateDistanceMeters(coordinate, friendCoord) <= 500.0
        }
        val visible = PrivacyService.filterVisibleContent(allNearby, userId)
        if (visible.isEmpty()) return
        proposeEcho(hostMoment = newMoment, nearbyMoments = visible)
    }

    private suspend fun getMutualIds(userId: String): List<String> {
        val following = db.collection("users").document(userId).collection("following").get().await()
        val followers = db.collection("users").document(userId).collection("followers").get().await()
        val followingIds = following.documents.map { it.id }.toSet()
        val followerIds = followers.documents.map { it.id }.toSet()
        return followingIds.intersect(followerIds).toList()
    }

    private suspend fun fetchRecentMomentsFromAuthors(
        authorIds: List<String>,
        since: Date,
    ): List<Moment> {
        if (authorIds.isEmpty()) return emptyList()
        val chunks = authorIds.chunked(MAX_AUTHOR_IDS_PER_ECHO_QUERY)
        val allDocuments = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()

        var chunkStart = 0
        while (chunkStart < chunks.size) {
            val chunkEnd = minOf(chunkStart + MAX_CONCURRENT_ECHO_MOMENT_BATCH_QUERIES, chunks.size)
            val wave = chunks.subList(chunkStart, chunkEnd)
            coroutineScope {
                val results = wave.flatMap { chunk ->
                    chunk.filter { it.isNotEmpty() }.map { authorId ->
                        async {
                            runCatching {
                                db.collection("users").document(authorId).collection("moments")
                                    .whereGreaterThan("timestamp", Timestamp(since))
                                    .orderBy("timestamp", Query.Direction.DESCENDING)
                                    .limit(50)
                                    .get()
                                    .await()
                                    .documents
                            }.getOrDefault(emptyList())
                        }
                    }
                }.awaitAll()
                results.forEach { allDocuments.addAll(it) }
            }
            chunkStart = chunkEnd
        }

        val seen = mutableSetOf<String>()
        val moments = mutableListOf<Moment>()
        for (doc in allDocuments) {
            val path = doc.reference.path
            if (!seen.add(path)) continue
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: continue
            val moment = Moment.from(doc.id, data)
            if (moment.isArchived == true) continue
            moments.add(moment)
        }
        return moments
    }

    private suspend fun proposeEcho(hostMoment: Moment, nearbyMoments: List<Moment>) {
        val hostId = hostMoment.authorId
        val coordinate = hostMoment.locationCoordinate ?: return

        val existing = findExistingEcho(near = coordinate)
        if (existing != null && existing.id != null) {
            mergeWithExistingEcho(existing.id!!, hostMoment)
            return
        }

        val hostUser = runCatching { firestoreService.fetchUser(hostId) }.getOrNull() ?: return
        val participants = mutableListOf(
            EchoParticipant(
                userId = hostId,
                username = hostUser.username,
                profileImagePath = hostUser.profileImagePath,
                status = EchoParticipantStatus.PENDING,
            ),
        )
        val addedParticipantIds = mutableSetOf(hostId)
        val momentRefs = mutableListOf<EchoMomentRef>()

        val hostMedia = hostMoment.mediaItems
        if (!hostMedia.isNullOrEmpty()) {
            hostMedia.forEach { momentRefs.add(EchoMomentRef.fromMediaItem(it, hostMoment)) }
        } else {
            momentRefs.add(EchoMomentRef.fromMoment(hostMoment))
        }

        for (m in nearbyMoments) {
            if (addedParticipantIds.add(m.authorId)) {
                participants.add(
                    EchoParticipant(
                        userId = m.authorId,
                        username = m.username,
                        profileImagePath = m.profileImagePath,
                        status = EchoParticipantStatus.PENDING,
                    ),
                )
            }
            val media = m.mediaItems
            if (!media.isNullOrEmpty()) {
                media.forEach { momentRefs.add(EchoMomentRef.fromMediaItem(it, m)) }
            } else {
                momentRefs.add(EchoMomentRef.fromMoment(m))
            }
        }

        val newEcho = Echo.create(
            hostId = hostId,
            participants = participants,
            location = coordinate,
            locationName = hostMoment.location,
            moments = momentRefs,
        )
        try {
            val docRef = db.collection("echoes").add(newEcho.toMap()).await()
            sendEchoSuggestions(docRef.id, participants, hostId)
        } catch (_: Exception) {
            // mirror iOS: log only
        }
    }

    private suspend fun findExistingEcho(near: Moment.LocationCoordinate): Echo? {
        val searchWindow = Date(System.currentTimeMillis() - 86_400_000L)
        return try {
            val snapshot = db.collection("echoes")
                .whereGreaterThan("createdAt", Timestamp(searchWindow))
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
                Echo.from(doc.id, data)
            }.firstOrNull { echo ->
                calculateDistanceMeters(near, echo.location) <= 500.0
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun mergeWithExistingEcho(echoId: String, hostMoment: Moment) {
        val hostId = hostMoment.authorId
        val hostUser = runCatching { firestoreService.fetchUser(hostId) }.getOrNull() ?: return
        val echoRef = db.collection("echoes").document(echoId)
        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(echoRef)
                @Suppress("UNCHECKED_CAST")
                val data = snapshot.data as? Map<String, Any?> ?: return@runTransaction null
                var echo = Echo.from(snapshot.id, data) ?: return@runTransaction null

                val participants = echo.participants.toMutableList()
                val participantIds = echo.participantIds.toMutableList()
                val moments = echo.moments.toMutableList()

                if (hostId !in participantIds) {
                    participants.add(
                        EchoParticipant(
                            userId = hostId,
                            username = hostUser.username,
                            profileImagePath = hostUser.profileImagePath,
                            status = EchoParticipantStatus.PENDING,
                        ),
                    )
                    participantIds.add(hostId)
                }

                val existingUrls = moments.map { it.mediaUrl }.toSet()
                val hostMedia = hostMoment.mediaItems
                if (!hostMedia.isNullOrEmpty()) {
                    for (item in hostMedia) {
                        if (item.url !in existingUrls) {
                            moments.add(EchoMomentRef.fromMediaItem(item, hostMoment))
                        }
                    }
                } else {
                    val url = hostMoment.videoUrl ?: hostMoment.imagePath
                    if (url != null && url !in existingUrls) {
                        moments.add(EchoMomentRef.fromMoment(hostMoment))
                    }
                }

                echo = echo.copy(
                    participants = participants,
                    participantIds = participantIds,
                    moments = moments,
                )
                transaction.set(echoRef, echo.toMap())
                null
            }.await()
        } catch (_: Exception) {
        }
    }

    private fun calculateDistanceMeters(
        from: Moment.LocationCoordinate,
        to: Moment.LocationCoordinate,
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
        return results[0].toDouble()
    }

    suspend fun acceptEcho(echoId: String, userId: String) {
        val docRef = db.collection("echoes").document(echoId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            @Suppress("UNCHECKED_CAST")
            val data = snapshot.data as? Map<String, Any?> ?: error("Echo not found")
            var echo = Echo.from(snapshot.id, data) ?: error("Echo not found")
            val participants = echo.participants.toMutableList()
            val index = participants.indexOfFirst { it.userId == userId }
            if (index < 0) return@runTransaction null
            if (participants[index].status == EchoParticipantStatus.ACCEPTED) return@runTransaction null
            participants[index] = participants[index].copy(status = EchoParticipantStatus.ACCEPTED)
            val acceptedCount = participants.count { it.status == EchoParticipantStatus.ACCEPTED }
            var status = echo.status
            if (acceptedCount >= 2 && status == EchoStatus.PENDING) {
                status = EchoStatus.ACTIVE
            }
            echo = echo.copy(participants = participants, status = status)
            transaction.set(docRef, echo.toMap())
            null
        }.await()
    }

    suspend fun declineEcho(echoId: String, userId: String) {
        val docRef = db.collection("echoes").document(echoId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            @Suppress("UNCHECKED_CAST")
            val data = snapshot.data as? Map<String, Any?> ?: return@runTransaction null
            var echo = Echo.from(snapshot.id, data) ?: return@runTransaction null
            val participants = echo.participants.toMutableList()
            val index = participants.indexOfFirst { it.userId == userId }
            if (index < 0) return@runTransaction null
            if (participants[index].status == EchoParticipantStatus.DECLINED) return@runTransaction null
            participants[index] = participants[index].copy(status = EchoParticipantStatus.DECLINED)
            echo = echo.copy(participants = participants)
            transaction.set(docRef, echo.toMap())
            null
        }.await()
    }

    private fun sendEchoSuggestions(
        echoId: String,
        participants: List<EchoParticipant>,
        @Suppress("UNUSED_PARAMETER") hostId: String,
    ) {
        for (recipient in participants) {
            NovaEvents.triggerEchoSpark(echoId, recipient.userId)
        }
    }

    fun fetchPendingEchoes(userId: String, onUpdate: (List<Echo>) -> Unit): ListenerRegistration {
        return db.collection("echoes")
            .whereArrayContains("participantIds", userId)
            .whereIn("status", listOf("pending", "active"))
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val echoes = snapshot.documents.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
                    val echo = Echo.from(doc.id, data) ?: return@mapNotNull null
                    if (echo.expiresAt.before(Date())) {
                        if (echo.status != EchoStatus.EXPIRED) markEchoAsExpired(doc.id)
                        return@mapNotNull null
                    }
                    val pending = echo.participants.any {
                        it.userId == userId && it.status == EchoParticipantStatus.PENDING
                    }
                    if (!pending) return@mapNotNull null
                    if (Date().before(echo.expiresAt) && !hasMinimumMomentPerspectives(echo)) {
                        return@mapNotNull null
                    }
                    echo
                }
                onUpdate(echoes)
            }
    }

    private fun markEchoAsExpired(echoId: String) {
        db.collection("echoes").document(echoId)
            .update("status", EchoStatus.EXPIRED.raw)
    }

    fun fetchEchoHistory(userId: String, onUpdate: (List<Echo>) -> Unit): ListenerRegistration {
        return db.collection("echoes")
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val echoes = snapshot.documents.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
                    val echo = Echo.from(doc.id, data) ?: return@mapNotNull null
                    if (Date().before(echo.expiresAt) && !hasMinimumMomentPerspectives(echo)) {
                        return@mapNotNull null
                    }
                    echo
                }
                echoes.filter { it.participantIds.isEmpty() }.forEach { repairEcho(it) }
                onUpdate(echoes.sortedByDescending { it.createdAt })
            }
    }

    suspend fun fetchEchoHistoryOnce(userId: String): List<Echo> {
        return try {
            val snapshot = db.collection("echoes")
                .whereArrayContains("participantIds", userId)
                .get()
                .await()
            val echoes = snapshot.documents.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
                val echo = Echo.from(doc.id, data) ?: return@mapNotNull null
                if (Date().before(echo.expiresAt) && !hasMinimumMomentPerspectives(echo)) {
                    return@mapNotNull null
                }
                echo
            }
            echoes.filter { it.participantIds.isEmpty() }.forEach { repairEcho(it) }
            echoes.sortedByDescending { it.createdAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun leaveEcho(echoId: String, userId: String) {
        val echoRef = db.collection("echoes").document(echoId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(echoRef)
            @Suppress("UNCHECKED_CAST")
            val data = snapshot.data as? Map<String, Any?>
                ?: error("Unable to fetch echo")
            var echo = Echo.from(snapshot.id, data) ?: error("Unable to fetch echo")
            if (Date().before(echo.expiresAt)) {
                error("echo.leave.locked")
            }
            val participants = echo.participants.filterNot { it.userId == userId }
            val participantIds = echo.participantIds.filterNot { it == userId }
            val moments = echo.moments.filterNot { it.authorId == userId }
            if (participants.isEmpty()) {
                transaction.delete(echoRef)
            } else {
                echo = echo.copy(
                    participants = participants,
                    participantIds = participantIds,
                    moments = moments,
                )
                transaction.set(echoRef, echo.toMap())
            }
            null
        }.await()
    }

    fun repairEcho(echo: Echo) {
        val echoId = echo.id ?: return
        if (echo.participantIds.isNotEmpty()) return
        val ids = echo.participants.map { it.userId }
        db.collection("echoes").document(echoId).update("participantIds", ids)
    }
}
