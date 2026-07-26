package com.moments.android.models

import androidx.annotation.StringRes
import com.moments.android.R
import com.google.firebase.Timestamp
import java.util.Date

/**
 * Port de `Models/AccountHistoryItem.swift`.
 */
enum class AccountHistoryEventType(
    val raw: String,
    @StringRes val labelRes: Int,
    /** ≡ SF Symbol name; UI mapea a Material. */
    val iconName: String,
) {
    JOIN("join", R.string.user_activity_account_history_type_join, "person.badge.plus"),
    USERNAME("username", R.string.user_activity_account_history_type_username, "person.text.rectangle"),
    BIO("bio", R.string.user_activity_account_history_type_bio, "text.alignleft"),
    WEBSITE("website", R.string.user_activity_account_history_type_website, "link"),
    PRIVACY("privacy", R.string.user_activity_account_history_type_privacy, "lock");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw }
    }
}

data class AccountHistoryItem(
    val id: String? = null,
    val type: AccountHistoryEventType,
    val oldValue: String? = null,
    val newValue: String? = null,
    val timestamp: Date = Date(),
) {
    companion object {
        /** null si el tipo de evento es desconocido. */
        fun from(id: String?, data: Map<String, Any?>): AccountHistoryItem? {
            val type = AccountHistoryEventType.from(data["type"] as? String) ?: return null
            return AccountHistoryItem(
                id = id ?: data["id"] as? String,
                type = type,
                oldValue = data["oldValue"] as? String,
                newValue = data["newValue"] as? String,
                timestamp = MediaItem.anyToDate(data["timestamp"]) ?: Date(),
            )
        }
    }
}

fun AccountHistoryItem.toMap(): Map<String, Any> = buildMap {
    id?.let { put("id", it) }
    put("type", type.raw)
    oldValue?.let { put("oldValue", it) }
    newValue?.let { put("newValue", it) }
    put("timestamp", Timestamp(timestamp))
}
