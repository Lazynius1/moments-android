package com.moments.android.reportes

import org.json.JSONArray
import org.json.JSONObject

// MARK: - Data Models (AppealService.swift)

data class AppealRequest(
    val userId: String,
    val appealMessage: String,
    val additionalInfo: String?,
    val contactEmail: String,
    val deviceInfo: DeviceInfo,
    val appVersion: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("userId", userId)
        put("appealMessage", appealMessage)
        additionalInfo?.let { put("additionalInfo", it) }
        put("contactEmail", contactEmail)
        put("deviceInfo", deviceInfo.toJson())
        put("appVersion", appVersion)
    }
}

data class DeviceInfo(
    val platform: String,
    val version: String,
    val model: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("platform", platform)
        put("version", version)
        put("model", model)
    }
}

data class AppealResponse(
    val success: Boolean,
    val appealId: String? = null,
    val ticketNumber: String? = null,
    val message: String? = null,
    val estimatedResponseTime: String? = null,
    val priority: String? = null,
    val status: String? = null,
    val error: String? = null,
    val code: String? = null,
    val currentLength: Int? = null,
    val requiredLength: Int? = null,
    val existingAppeal: ExistingAppeal? = null,
    val nextSteps: List<String>? = null,
    val supportInfo: SupportInfo? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): AppealResponse = AppealResponse(
            success = json.optBoolean("success"),
            appealId = json.optStringOrNull("appealId"),
            ticketNumber = json.optStringOrNull("ticketNumber"),
            message = json.optStringOrNull("message"),
            estimatedResponseTime = json.optStringOrNull("estimatedResponseTime"),
            priority = json.optStringOrNull("priority"),
            status = json.optStringOrNull("status"),
            error = json.optStringOrNull("error"),
            code = json.optStringOrNull("code"),
            currentLength = json.optIntOrNull("currentLength"),
            requiredLength = json.optIntOrNull("requiredLength"),
            existingAppeal = json.optJSONObject("existingAppeal")?.let { ExistingAppeal.fromJson(it) },
            nextSteps = json.optJSONArray("nextSteps")?.toStringList(),
            supportInfo = json.optJSONObject("supportInfo")?.let { SupportInfo.fromJson(it) },
        )
    }
}

data class ModerationReviewRequestPayload(
    val userId: String,
    val contentType: String,
    val contentId: String,
    val moderationScope: String,
    val reviewMessage: String,
    val additionalInfo: String?,
    val contactEmail: String,
    val notificationId: String?,
    val deviceInfo: DeviceInfo,
    val appVersion: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("userId", userId)
        put("contentType", contentType)
        put("contentId", contentId)
        put("moderationScope", moderationScope)
        put("reviewMessage", reviewMessage)
        additionalInfo?.let { put("additionalInfo", it) }
        put("contactEmail", contactEmail)
        notificationId?.let { put("notificationId", it) }
        put("deviceInfo", deviceInfo.toJson())
        put("appVersion", appVersion)
    }
}

data class ModerationReviewCreateResponse(
    val success: Boolean,
    val reviewRequestId: String? = null,
    val ticketNumber: String? = null,
    val message: String? = null,
    val estimatedResponseTime: String? = null,
    val status: String? = null,
    val error: String? = null,
    val code: String? = null,
    val existingRequest: ExistingAppeal? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): ModerationReviewCreateResponse = ModerationReviewCreateResponse(
            success = json.optBoolean("success"),
            reviewRequestId = json.optStringOrNull("reviewRequestId"),
            ticketNumber = json.optStringOrNull("ticketNumber"),
            message = json.optStringOrNull("message"),
            estimatedResponseTime = json.optStringOrNull("estimatedResponseTime"),
            status = json.optStringOrNull("status"),
            error = json.optStringOrNull("error"),
            code = json.optStringOrNull("code"),
            existingRequest = json.optJSONObject("existingRequest")?.let { ExistingAppeal.fromJson(it) },
        )
    }
}

data class ExistingAppeal(
    val ticketNumber: String,
    val status: String,
    val submittedAt: String,
) {
    companion object {
        fun fromJson(json: JSONObject): ExistingAppeal = ExistingAppeal(
            ticketNumber = json.optString("ticketNumber"),
            status = json.optString("status"),
            submittedAt = json.optString("submittedAt"),
        )
    }
}

data class SupportInfo(
    val email: String,
    val responseTime: String,
    val ticketNumber: String,
) {
    companion object {
        fun fromJson(json: JSONObject): SupportInfo = SupportInfo(
            email = json.optString("email"),
            responseTime = json.optString("responseTime"),
            ticketNumber = json.optString("ticketNumber"),
        )
    }
}

data class AppealResult(
    val success: Boolean,
    val ticketNumber: String?,
    val message: String,
    val estimatedResponseTime: String?,
    val priority: String?,
    val nextSteps: List<String>,
) {
    companion object {
        fun from(response: AppealResponse, defaultMessage: String): AppealResult = AppealResult(
            success = response.success,
            ticketNumber = response.ticketNumber,
            message = response.message ?: defaultMessage,
            estimatedResponseTime = response.estimatedResponseTime,
            priority = response.priority,
            nextSteps = response.nextSteps ?: emptyList(),
        )
    }
}

data class AppealStatus(
    val id: String,
    val ticketNumber: String,
    val status: String,
    val priority: String,
    val appealMessage: String,
    val additionalInfo: String?,
    val contactEmail: String,
    val suspensionReason: String?,
    val suspensionDate: String?,
    val suspensionExpiry: String?,
    val submittedAt: String,
    val reviewedAt: String?,
    val resolvedAt: String?,
    val moderatorId: String?,
    val moderatorNotes: String?,
    val estimatedResponseTime: String,
    val nextSteps: List<String>,
    val statusDescription: String,
) {
    companion object {
        fun from(response: AppealResponseData): AppealStatus = AppealStatus(
            id = response.id,
            ticketNumber = response.ticketNumber,
            status = response.status,
            priority = response.priority,
            appealMessage = response.appealMessage,
            additionalInfo = response.additionalInfo,
            contactEmail = response.contactEmail,
            suspensionReason = response.suspensionReason,
            suspensionDate = response.suspensionDate,
            suspensionExpiry = response.suspensionExpiry,
            submittedAt = response.submittedAt,
            reviewedAt = response.reviewedAt,
            resolvedAt = response.resolvedAt,
            moderatorId = response.moderatorId,
            moderatorNotes = response.moderatorNotes,
            estimatedResponseTime = response.estimatedResponseTime,
            nextSteps = response.nextSteps,
            statusDescription = response.statusDescription,
        )
    }
}

data class ModerationReviewStatus(
    val id: String,
    val ticketNumber: String,
    val status: String,
    val priority: String,
    val reviewMessage: String,
    val additionalInfo: String?,
    val contactEmail: String,
    val contentType: String,
    val contentId: String,
    val moderationScope: String,
    val moderationCategory: String?,
    val submittedAt: String,
    val reviewedAt: String?,
    val resolvedAt: String?,
    val moderatorId: String?,
    val moderatorNotes: String?,
    val estimatedResponseTime: String,
) {
    companion object {
        fun from(response: ModerationReviewStatusResponse): ModerationReviewStatus = ModerationReviewStatus(
            id = response.id,
            ticketNumber = response.ticketNumber,
            status = response.status,
            priority = response.priority ?: "medium",
            reviewMessage = response.reviewMessage,
            additionalInfo = response.additionalInfo,
            contactEmail = response.contactEmail,
            contentType = response.contentType,
            contentId = response.contentId,
            moderationScope = response.moderationScope,
            moderationCategory = response.moderationCategory,
            submittedAt = response.submittedAt ?: "",
            reviewedAt = response.reviewedAt,
            resolvedAt = response.resolvedAt,
            moderatorId = response.moderatorId,
            moderatorNotes = response.moderatorNotes,
            estimatedResponseTime = response.estimatedResponseTime ?: "24-72h",
        )
    }
}

data class AppealsListResponse(
    val success: Boolean,
    val appeals: List<AppealResponseData>,
    val total: Int,
    val summary: AppealsSummary,
    val timestamp: String,
) {
    companion object {
        fun fromJson(json: JSONObject): AppealsListResponse {
            val appealsArray = json.optJSONArray("appeals") ?: JSONArray()
            val appeals = buildList {
                for (i in 0 until appealsArray.length()) {
                    appealsArray.optJSONObject(i)?.let { add(AppealResponseData.fromJson(it)) }
                }
            }
            return AppealsListResponse(
                success = json.optBoolean("success"),
                appeals = appeals,
                total = json.optInt("total"),
                summary = json.optJSONObject("summary")?.let { AppealsSummary.fromJson(it) }
                    ?: AppealsSummary(0, 0, 0, 0, 0),
                timestamp = json.optString("timestamp"),
            )
        }
    }
}

data class SingleAppealResponse(
    val success: Boolean,
    val appeal: AppealResponseData,
    val message: String,
    val timestamp: String,
) {
    companion object {
        fun fromJson(json: JSONObject): SingleAppealResponse = SingleAppealResponse(
            success = json.optBoolean("success"),
            appeal = AppealResponseData.fromJson(json.getJSONObject("appeal")),
            message = json.optString("message"),
            timestamp = json.optString("timestamp"),
        )
    }
}

data class AppealResponseData(
    val id: String,
    val ticketNumber: String,
    val status: String,
    val priority: String,
    val appealMessage: String,
    val additionalInfo: String?,
    val contactEmail: String,
    val suspensionReason: String?,
    val suspensionDate: String?,
    val suspensionExpiry: String?,
    val submittedAt: String,
    val reviewedAt: String?,
    val resolvedAt: String?,
    val moderatorId: String?,
    val moderatorNotes: String?,
    val estimatedResponseTime: String,
    val nextSteps: List<String>,
    val statusDescription: String,
    val progress: AppealProgress? = null,
    val timeline: List<AppealTimelineEvent>? = null,
) {
    companion object {
        fun fromJson(json: JSONObject): AppealResponseData = AppealResponseData(
            id = json.optString("id"),
            ticketNumber = json.optString("ticketNumber"),
            status = json.optString("status"),
            priority = json.optString("priority"),
            appealMessage = json.optString("appealMessage"),
            additionalInfo = json.optStringOrNull("additionalInfo"),
            contactEmail = json.optString("contactEmail"),
            suspensionReason = json.optStringOrNull("suspensionReason"),
            suspensionDate = json.optStringOrNull("suspensionDate"),
            suspensionExpiry = json.optStringOrNull("suspensionExpiry"),
            submittedAt = json.optString("submittedAt"),
            reviewedAt = json.optStringOrNull("reviewedAt"),
            resolvedAt = json.optStringOrNull("resolvedAt"),
            moderatorId = json.optStringOrNull("moderatorId"),
            moderatorNotes = json.optStringOrNull("moderatorNotes"),
            estimatedResponseTime = json.optString("estimatedResponseTime"),
            nextSteps = json.optJSONArray("nextSteps")?.toStringList() ?: emptyList(),
            statusDescription = json.optString("statusDescription"),
            progress = json.optJSONObject("progress")?.let { AppealProgress.fromJson(it) },
            timeline = json.optJSONArray("timeline")?.toTimelineList(),
        )
    }
}

data class AppealsSummary(
    val pending: Int,
    val reviewing: Int,
    val approved: Int,
    val denied: Int,
    val requiresInfo: Int,
) {
    companion object {
        fun fromJson(json: JSONObject): AppealsSummary = AppealsSummary(
            pending = json.optInt("pending"),
            reviewing = json.optInt("reviewing"),
            approved = json.optInt("approved"),
            denied = json.optInt("denied"),
            requiresInfo = json.optInt("requires_info"),
        )
    }
}

data class ModerationReviewListResponse(
    val success: Boolean,
    val requests: List<ModerationReviewStatusResponse>,
    val stats: ModerationReviewSummary?,
) {
    companion object {
        fun fromJson(json: JSONObject): ModerationReviewListResponse {
            val array = json.optJSONArray("requests") ?: JSONArray()
            val requests = buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { add(ModerationReviewStatusResponse.fromJson(it)) }
                }
            }
            return ModerationReviewListResponse(
                success = json.optBoolean("success"),
                requests = requests,
                stats = json.optJSONObject("stats")?.let { ModerationReviewSummary.fromJson(it) },
            )
        }
    }
}

data class ModerationReviewSummary(
    val total: Int,
    val pending: Int,
    val reviewing: Int,
    val approved: Int,
    val denied: Int,
    val requiresInfo: Int,
) {
    companion object {
        fun fromJson(json: JSONObject): ModerationReviewSummary = ModerationReviewSummary(
            total = json.optInt("total"),
            pending = json.optInt("pending"),
            reviewing = json.optInt("reviewing"),
            approved = json.optInt("approved"),
            denied = json.optInt("denied"),
            requiresInfo = json.optInt("requires_info"),
        )
    }
}

data class ModerationReviewStatusResponse(
    val id: String,
    val ticketNumber: String,
    val status: String,
    val priority: String?,
    val reviewMessage: String,
    val additionalInfo: String?,
    val contactEmail: String,
    val contentType: String,
    val contentId: String,
    val moderationScope: String,
    val moderationCategory: String?,
    val submittedAt: String?,
    val reviewedAt: String?,
    val resolvedAt: String?,
    val moderatorId: String?,
    val moderatorNotes: String?,
    val estimatedResponseTime: String?,
) {
    companion object {
        fun fromJson(json: JSONObject): ModerationReviewStatusResponse = ModerationReviewStatusResponse(
            id = json.optString("id"),
            ticketNumber = json.optString("ticketNumber"),
            status = json.optString("status"),
            priority = json.optStringOrNull("priority"),
            reviewMessage = json.optString("reviewMessage"),
            additionalInfo = json.optStringOrNull("additionalInfo"),
            contactEmail = json.optString("contactEmail"),
            contentType = json.optString("contentType"),
            contentId = json.optString("contentId"),
            moderationScope = json.optString("moderationScope"),
            moderationCategory = json.optStringOrNull("moderationCategory"),
            submittedAt = json.optStringOrNull("submittedAt"),
            reviewedAt = json.optStringOrNull("reviewedAt"),
            resolvedAt = json.optStringOrNull("resolvedAt"),
            moderatorId = json.optStringOrNull("moderatorId"),
            moderatorNotes = json.optStringOrNull("moderatorNotes"),
            estimatedResponseTime = json.optStringOrNull("estimatedResponseTime"),
        )
    }
}

data class AppealProgress(
    val percentage: Int,
    val currentStage: String,
    val totalStages: Int,
) {
    companion object {
        fun fromJson(json: JSONObject): AppealProgress = AppealProgress(
            percentage = json.optInt("percentage"),
            currentStage = json.optString("currentStage"),
            totalStages = json.optInt("totalStages"),
        )
    }
}

data class AppealTimelineEvent(
    val date: String,
    val event: String,
    val description: String,
    val moderator: String?,
) {
    companion object {
        fun fromJson(json: JSONObject): AppealTimelineEvent = AppealTimelineEvent(
            date = json.optString("date"),
            event = json.optString("event"),
            description = json.optString("description"),
            moderator = json.optStringOrNull("moderator"),
        )
    }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key)
    return value.takeIf { it.isNotEmpty() }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (i in 0 until length()) add(optString(i))
}

private fun JSONArray.toTimelineList(): List<AppealTimelineEvent> = buildList {
    for (i in 0 until length()) {
        optJSONObject(i)?.let { add(AppealTimelineEvent.fromJson(it)) }
    }
}
