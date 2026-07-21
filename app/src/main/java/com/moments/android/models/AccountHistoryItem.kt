package com.moments.android.models

import java.util.Date

// MARK: - AccountHistoryEventType
enum class AccountHistoryEventType(val raw: String) {
    JOIN("join"),
    USERNAME("username"),
    BIO("bio"),
    WEBSITE("website"),
    PRIVACY("privacy");

    companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } }
    // localizedName / icon (localizado + SF Symbols) → capa de UI.
}

// MARK: - AccountHistoryItem (evento del historial de la cuenta)
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
