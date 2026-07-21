package com.moments.android.models

import com.google.firebase.Timestamp
import java.util.Date

// MARK: - Estados
enum class EchoStatus(val raw: String) {
    PENDING("pending"), ACTIVE("active"), EXPIRED("expired"), COMPLETED("completed");
    companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: PENDING }
}

enum class EchoParticipantStatus(val raw: String) {
    PENDING("pending"), ACCEPTED("accepted"), DECLINED("declined");
    companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: PENDING }
}

// MARK: - Participante
data class EchoParticipant(
    val userId: String,
    val username: String,
    val profileImagePath: String? = null,
    val status: EchoParticipantStatus,
) {
    val id: String get() = userId

    companion object {
        fun from(data: Map<String, Any?>): EchoParticipant = EchoParticipant(
            userId = data["userId"] as? String ?: "",
            username = data["username"] as? String ?: "",
            profileImagePath = data["profileImagePath"] as? String,
            status = EchoParticipantStatus.from(data["status"] as? String),
        )
    }
}

// MARK: - Referencia a un momento del Echo
data class EchoMomentRef(
    val momentId: String,
    val authorId: String,
    val username: String,
    val timestamp: Date,
    val mediaType: String,
    val mediaUrl: String,
    val aspectRatio: String? = null,
    val thumbnailUrl: String? = null,
    val audience: String? = null,
    val customListId: String? = null,
) {
    companion object {
        fun fromMoment(moment: Moment): EchoMomentRef = EchoMomentRef(
            momentId = moment.id ?: "",
            authorId = moment.authorId,
            username = moment.username,
            timestamp = moment.timestamp,
            mediaType = if (moment.videoUrl != null) "video" else "image",
            mediaUrl = moment.videoUrl ?: moment.imagePath ?: "",
            aspectRatio = moment.aspectRatio,
            thumbnailUrl = moment.thumbnailUrl,
            audience = moment.audience,
            customListId = moment.customListId,
        )

        fun fromMediaItem(mediaItem: MediaItem, author: Moment): EchoMomentRef = EchoMomentRef(
            momentId = author.id ?: "",
            authorId = author.authorId,
            username = author.username,
            timestamp = author.timestamp,
            mediaType = if (mediaItem.type == MediaItem.MediaType.VIDEO) "video" else "image",
            mediaUrl = mediaItem.url,
            aspectRatio = author.aspectRatio,
            thumbnailUrl = mediaItem.thumbnailUrl,
            audience = author.audience,
            customListId = author.customListId,
        )

        fun from(data: Map<String, Any?>): EchoMomentRef = EchoMomentRef(
            momentId = data["momentId"] as? String ?: "",
            authorId = data["authorId"] as? String ?: "",
            username = data["username"] as? String ?: "",
            timestamp = MediaItem.anyToDate(data["timestamp"]) ?: Date(),
            mediaType = data["mediaType"] as? String ?: "image",
            mediaUrl = data["mediaUrl"] as? String ?: "",
            aspectRatio = data["aspectRatio"] as? String,
            thumbnailUrl = data["thumbnailUrl"] as? String,
            audience = data["audience"] as? String,
            customListId = data["customListId"] as? String,
        )
    }
}

// MARK: - Echo
data class Echo(
    val id: String? = null,
    val hostId: String,
    val participants: List<EchoParticipant>,
    val location: Moment.LocationCoordinate,
    val locationName: String? = null,
    val createdAt: Date = Date(),
    val expiresAt: Date,
    val status: EchoStatus = EchoStatus.PENDING,
    val moments: List<EchoMomentRef> = emptyList(),
    val vibeSummary: String? = null,
    val participantIds: List<String> = emptyList(),
) {
    /** Momentos visibles: de participantes que han aceptado. */
    val visibleMoments: List<EchoMomentRef>
        get() {
            val accepted = participants.filter { it.status == EchoParticipantStatus.ACCEPTED }.map { it.userId }.toSet()
            return moments.filter { accepted.contains(it.authorId) }
        }

    val momentParticipantIds: List<String> get() = moments.map { it.authorId }.toSet().sorted()

    val hasMinimumMomentParticipants: Boolean get() = momentParticipantIds.size >= 2

    companion object {
        fun create(
            hostId: String,
            participants: List<EchoParticipant>,
            location: Moment.LocationCoordinate,
            locationName: String?,
            moments: List<EchoMomentRef> = emptyList(),
        ): Echo {
            val now = Date()
            return Echo(
                id = null,
                hostId = hostId,
                participants = participants,
                location = location,
                locationName = locationName,
                createdAt = now,
                expiresAt = Date(now.time + 86_400_000L), // 24h
                status = EchoStatus.PENDING,
                moments = moments,
                participantIds = participants.map { it.userId },
            )
        }

        fun from(id: String?, data: Map<String, Any?>): Echo? {
            val location = Moment.LocationCoordinate.from(data["location"] as? Map<String, Any?>) ?: return null
            return Echo(
                id = id ?: data["id"] as? String,
                hostId = data["hostId"] as? String ?: "",
                participants = (data["participants"] as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let(EchoParticipant::from) } ?: emptyList(),
                location = location,
                locationName = data["locationName"] as? String,
                createdAt = MediaItem.anyToDate(data["createdAt"]) ?: Date(),
                expiresAt = MediaItem.anyToDate(data["expiresAt"]) ?: Date(),
                status = EchoStatus.from(data["status"] as? String),
                moments = (data["moments"] as? List<*>)?.mapNotNull { (it as? Map<String, Any?>)?.let(EchoMomentRef::from) } ?: emptyList(),
                vibeSummary = data["vibeSummary"] as? String,
                participantIds = (data["participantIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            )
        }
    }
}

// MARK: - Serialización a Firestore (equivalente a encode(to:) de iOS)

fun EchoParticipant.toMap(): Map<String, Any> = buildMap {
    put("userId", userId); put("username", username); put("status", status.raw)
    profileImagePath?.let { put("profileImagePath", it) }
}

fun EchoMomentRef.toMap(): Map<String, Any> = buildMap {
    put("momentId", momentId); put("authorId", authorId); put("username", username)
    put("timestamp", Timestamp(timestamp)); put("mediaType", mediaType); put("mediaUrl", mediaUrl)
    aspectRatio?.let { put("aspectRatio", it) }
    thumbnailUrl?.let { put("thumbnailUrl", it) }
    audience?.let { put("audience", it) }
    customListId?.let { put("customListId", it) }
}

fun Echo.toMap(): Map<String, Any> = buildMap {
    // id NO se serializa (@DocumentID en iOS).
    put("hostId", hostId)
    put("participants", participants.map { it.toMap() })
    put("location", location.toMap())
    locationName?.let { put("locationName", it) }
    put("createdAt", Timestamp(createdAt)); put("expiresAt", Timestamp(expiresAt))
    put("status", status.raw)
    put("moments", moments.map { it.toMap() })
    vibeSummary?.let { put("vibeSummary", it) }
    put("participantIds", participantIds)
}
