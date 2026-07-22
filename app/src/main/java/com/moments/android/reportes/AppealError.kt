package com.moments.android.reportes

import android.content.Context
import com.moments.android.R

/** Port de AppealError (LocalizedError) en AppealService.swift */
sealed class AppealError : Exception() {
    data object InvalidUserId : AppealError()
    data object EmptyMessage : AppealError()
    data class MessageTooShort(val current: Int, val required: Int) : AppealError()
    data class MessageTooLong(val current: Int) : AppealError()
    data object InvalidEmail : AppealError()
    data object InvalidUrl : AppealError()
    data object EncodingError : AppealError()
    data object DecodingError : AppealError()
    data object InvalidResponse : AppealError()
    data object UserNotFound : AppealError()
    data object UserNotSuspended : AppealError()
    data class AppealAlreadyExists(val ticketNumber: String, val status: String) : AppealError()
    data class ReviewAlreadyExists(val ticketNumber: String, val status: String) : AppealError()
    data object RateLimited : AppealError()
    data object ServerError : AppealError()
    data class NetworkError(val detail: String) : AppealError()
    data class ValidationError(val detail: String) : AppealError()
    data class HttpError(val statusCode: Int) : AppealError()

    fun localizedMessage(context: Context): String = when (this) {
        InvalidUserId -> context.getString(R.string.appeal_errors_invalidUserId)
        EmptyMessage -> context.getString(R.string.appeal_errors_emptyMessage)
        is MessageTooShort -> context.getString(R.string.appeal_errors_messageTooShort, current, required)
        is MessageTooLong -> context.getString(R.string.appeal_errors_messageTooLong, current)
        InvalidEmail -> context.getString(R.string.appeal_errors_invalidEmail)
        InvalidUrl -> context.getString(R.string.appeal_errors_invalidURL)
        EncodingError -> context.getString(R.string.appeal_errors_encodingError)
        DecodingError -> context.getString(R.string.appeal_errors_decodingError)
        InvalidResponse -> context.getString(R.string.appeal_errors_invalidResponse)
        UserNotFound -> context.getString(R.string.appeal_errors_userNotFound)
        UserNotSuspended -> context.getString(R.string.appeal_errors_userNotSuspended)
        is AppealAlreadyExists -> context.getString(R.string.appeal_errors_appealAlreadyExists, status, ticketNumber)
        is ReviewAlreadyExists -> context.getString(R.string.appeal_errors_reviewAlreadyExists, status, ticketNumber)
        RateLimited -> context.getString(R.string.appeal_errors_rateLimited)
        ServerError -> context.getString(R.string.appeal_errors_serverError)
        is NetworkError -> context.getString(R.string.appeal_errors_networkError, detail)
        is ValidationError -> detail
        is HttpError -> context.getString(R.string.appeal_errors_httpError, statusCode)
    }

    fun recoverySuggestion(context: Context): String? = when (this) {
        is MessageTooShort -> context.getString(R.string.appeal_recovery_messageTooShort)
        is MessageTooLong -> context.getString(R.string.appeal_recovery_messageTooLong)
        InvalidEmail -> context.getString(R.string.appeal_recovery_invalidEmail)
        is AppealAlreadyExists -> context.getString(R.string.appeal_recovery_appealAlreadyExists)
        is ReviewAlreadyExists -> context.getString(R.string.appeal_recovery_reviewAlreadyExists)
        RateLimited -> context.getString(R.string.appeal_recovery_rateLimited)
        is NetworkError -> context.getString(R.string.appeal_recovery_networkError)
        ServerError -> context.getString(R.string.appeal_recovery_serverError)
        else -> context.getString(R.string.appeal_recovery_default)
    }
}
