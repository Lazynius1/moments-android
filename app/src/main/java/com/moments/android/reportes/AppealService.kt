package com.moments.android.reportes

import android.content.Context
import android.os.Build
import com.moments.android.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Port de AppealService.swift — apelaciones y revisiones de moderación vía admin panel HTTP API. */
class AppealService private constructor(
    private val appContext: Context,
) {
    private val baseUrl = "https://moment-admin-panel.vercel.app"

    private fun deviceInfo(): DeviceInfo = DeviceInfo(
        platform = "Android",
        version = Build.VERSION.RELEASE,
        model = Build.MODEL,
    )

    private fun appVersion(): String = try {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        info.versionName ?: "1.0"
    } catch (_: Exception) {
        "1.0"
    }

    suspend fun submitAppeal(
        userId: String,
        message: String,
        email: String,
        additionalInfo: String? = null,
    ): AppealResponse {
        if (userId.isEmpty()) throw AppealError.InvalidUserId
        if (message.isEmpty()) throw AppealError.EmptyMessage

        val trimmedMessage = message.trim()
        if (trimmedMessage.length < 50) {
            throw AppealError.MessageTooShort(trimmedMessage.length, 50)
        }
        if (trimmedMessage.length > 2000) {
            throw AppealError.MessageTooLong(trimmedMessage.length)
        }
        if (email.isEmpty() || !email.contains("@")) throw AppealError.InvalidEmail

        val url = URL("$baseUrl/api/appeals-create")
        val body = AppealRequest(
            userId = userId,
            appealMessage = trimmedMessage,
            additionalInfo = additionalInfo?.takeIf { it.isNotEmpty() },
            contactEmail = email,
            deviceInfo = deviceInfo(),
            appVersion = appVersion(),
        ).toJson()

        return postJson(url, body) { (code, json) ->
            val response = AppealResponse.fromJson(json)
            when (code) {
                201 -> response
                400 -> throw mapValidationError(response, trimmedMessage.length, 50)
                404 -> throw AppealError.UserNotFound
                409 -> throw AppealError.AppealAlreadyExists(
                    ticketNumber = response.existingAppeal?.ticketNumber ?: "Desconocido",
                    status = response.existingAppeal?.status ?: "unknown",
                )
                429 -> throw AppealError.RateLimited
                500 -> throw AppealError.ServerError
                else -> throw AppealError.HttpError(code)
            }
        }
    }

    suspend fun submitModerationReview(
        userId: String,
        contentType: String,
        contentId: String,
        moderationScope: String,
        message: String,
        email: String,
        additionalInfo: String? = null,
        notificationId: String? = null,
    ): ModerationReviewCreateResponse {
        if (userId.isEmpty()) throw AppealError.InvalidUserId
        if (contentId.isEmpty()) {
            throw AppealError.ValidationError(appContext.getString(R.string.appeal_errors_invalidContent))
        }

        val trimmedMessage = message.trim()
        if (trimmedMessage.length < 25) {
            throw AppealError.MessageTooShort(trimmedMessage.length, 25)
        }
        if (trimmedMessage.length > 2000) {
            throw AppealError.MessageTooLong(trimmedMessage.length)
        }
        if (email.isEmpty() || !email.contains("@")) throw AppealError.InvalidEmail

        val url = URL("$baseUrl/api/moderation-review-create")
        val body = ModerationReviewRequestPayload(
            userId = userId,
            contentType = contentType,
            contentId = contentId,
            moderationScope = moderationScope,
            reviewMessage = trimmedMessage,
            additionalInfo = additionalInfo?.takeIf { it.isNotEmpty() },
            contactEmail = email,
            notificationId = notificationId,
            deviceInfo = deviceInfo(),
            appVersion = appVersion(),
        ).toJson()

        return postJson(url, body) { (code, json) ->
            val response = ModerationReviewCreateResponse.fromJson(json)
            when (code) {
                201 -> response
                400 -> throw mapReviewValidationError(response, trimmedMessage.length)
                404 -> throw AppealError.UserNotFound
                409 -> throw AppealError.ReviewAlreadyExists(
                    ticketNumber = response.existingRequest?.ticketNumber ?: "Desconocido",
                    status = response.existingRequest?.status ?: "pending",
                )
                429 -> throw AppealError.RateLimited
                500 -> throw AppealError.ServerError
                else -> throw AppealError.HttpError(code)
            }
        }
    }

    suspend fun fetchUserAppeals(userId: String): List<AppealStatus> {
        if (userId.isEmpty()) throw AppealError.InvalidUserId
        val encoded = URLEncoder.encode(userId, Charsets.UTF_8.name())
        val url = URL("$baseUrl/api/appeals-status?userId=$encoded")
        return getJson(url) { (code, json) ->
            when (code) {
                200 -> AppealsListResponse.fromJson(json).appeals.map { AppealStatus.from(it) }
                404 -> emptyList()
                400 -> throw AppealError.ValidationError(
                    AppealResponse.fromJson(json).error ?: "Error de validación",
                )
                500 -> throw AppealError.ServerError
                else -> throw AppealError.HttpError(code)
            }
        }
    }

    suspend fun fetchAppealByTicket(ticketNumber: String): AppealStatus {
        if (ticketNumber.isEmpty()) {
            throw AppealError.ValidationError("Número de ticket requerido")
        }
        val encoded = URLEncoder.encode(ticketNumber, Charsets.UTF_8.name())
        val url = URL("$baseUrl/api/appeals-status?ticketNumber=$encoded")
        return getJson(url) { (code, json) ->
            when (code) {
                200 -> AppealStatus.from(SingleAppealResponse.fromJson(json).appeal)
                404 -> throw AppealError.ValidationError("Apelación no encontrada")
                400 -> throw AppealError.ValidationError(
                    AppealResponse.fromJson(json).error ?: "Error de validación",
                )
                500 -> throw AppealError.ServerError
                else -> throw AppealError.HttpError(code)
            }
        }
    }

    suspend fun fetchUserModerationReviews(userId: String): List<ModerationReviewStatus> {
        if (userId.isEmpty()) throw AppealError.InvalidUserId
        val encoded = URLEncoder.encode(userId, Charsets.UTF_8.name())
        val url = URL("$baseUrl/api/moderation-review-requests?userId=$encoded")
        return getJson(url) { (code, json) ->
            when (code) {
                200 -> ModerationReviewListResponse.fromJson(json).requests.map { ModerationReviewStatus.from(it) }
                404 -> emptyList()
                400 -> throw AppealError.ValidationError(
                    AppealResponse.fromJson(json).error ?: "Error de validación",
                )
                500 -> throw AppealError.ServerError
                else -> throw AppealError.HttpError(code)
            }
        }
    }

    private fun mapValidationError(response: AppealResponse, currentLength: Int, required: Int): AppealError {
        return when (response.code) {
            "MESSAGE_TOO_SHORT" -> AppealError.MessageTooShort(
                current = response.currentLength ?: currentLength,
                required = response.requiredLength ?: required,
            )
            "MESSAGE_TOO_LONG" -> AppealError.MessageTooLong(response.currentLength ?: currentLength)
            "USER_NOT_SUSPENDED" -> AppealError.UserNotSuspended
            "MISSING_REQUIRED_FIELDS" -> AppealError.ValidationError("Faltan campos requeridos")
            "TOO_MANY_ATTACHMENTS" -> AppealError.ValidationError("Demasiados archivos adjuntos")
            else -> AppealError.ValidationError(response.error ?: "Error de validación")
        }
    }

    private fun mapReviewValidationError(response: ModerationReviewCreateResponse, currentLength: Int): AppealError {
        return when (response.code) {
            "MESSAGE_TOO_SHORT" -> AppealError.MessageTooShort(currentLength, 25)
            "MESSAGE_TOO_LONG" -> AppealError.MessageTooLong(currentLength)
            else -> AppealError.ValidationError(response.error ?: "Error de validación")
        }
    }

    private inline fun <T> postJson(url: URL, body: JSONObject, block: (Pair<Int, JSONObject>) -> T): T =
        executeRequest(url, "POST", body, block)

    private inline fun <T> getJson(url: URL, block: (Pair<Int, JSONObject>) -> T): T =
        executeRequest(url, "GET", null, block)

    private inline fun <T> executeRequest(
        url: URL,
        method: String,
        body: JSONObject?,
        block: (Pair<Int, JSONObject>) -> T,
    ): T {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 30_000
            readTimeout = 30_000
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(if (text.isEmpty()) "{}" else text)
            } catch (_: Exception) {
                throw AppealError.DecodingError
            }
            block(code to json)
        } catch (error: AppealError) {
            throw error
        } catch (error: Exception) {
            throw AppealError.NetworkError(error.localizedMessage ?: error.toString())
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        @Volatile
        private var instance: AppealService? = null

        fun getInstance(context: Context): AppealService {
            return instance ?: synchronized(this) {
                instance ?: AppealService(context.applicationContext).also { instance = it }
            }
        }

        /** Alias equivalente a `AppealService.shared` en iOS. */
        val shared: AppealService
            get() = instance ?: error("AppealService not initialized — call getInstance(context) first")
    }
}
