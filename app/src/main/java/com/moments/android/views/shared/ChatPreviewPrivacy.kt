package com.moments.android.views.shared

import android.content.Context
import com.moments.android.MomentsApplication

/**
 * Port de `Shared/ChatPreviewPrivacy.swift`.
 *
 * iOS: App Group `group.com.glowsyapp` + UserDefaults.
 * Android: SharedPreferences locales (mismo proceso que FCM; no hay App Group).
 *
 * Clave por conversación: `chat_show_message_preview_{id}` (default ON).
 * Vanish ≡ preview desactivada.
 */
object ChatPreviewPrivacy {
    private const val PREFS = "chat_preview_privacy"
    private const val KEY_PREFIX = "chat_show_message_preview_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(conversationId: String) = KEY_PREFIX + conversationId

    /** ≡ isUserPreviewEnabled(for:) */
    fun isUserPreviewEnabled(context: Context, conversationId: String): Boolean {
        if (conversationId.isBlank()) return true
        return prefs(context).getBoolean(key(conversationId), true)
    }

    fun isUserPreviewEnabled(conversationId: String): Boolean {
        val ctx = MomentsApplication.instance ?: return true
        return isUserPreviewEnabled(ctx, conversationId)
    }

    /**
     * Escritura usada por ajustes de conversación
     * (≡ ConversationSettingsView.toggleMessagePreview → App Group).
     */
    fun setUserPreviewEnabled(context: Context, conversationId: String, enabled: Boolean) {
        if (conversationId.isBlank()) return
        prefs(context).edit().putBoolean(key(conversationId), enabled).apply()
    }

    /** ≡ shouldRevealPreview(for:isVanishModeMessage:) */
    fun shouldRevealPreview(
        context: Context,
        conversationId: String,
        isVanishModeMessage: Boolean,
    ): Boolean = isUserPreviewEnabled(context, conversationId) && !isVanishModeMessage

    fun shouldRevealPreview(conversationId: String, isVanishModeMessage: Boolean): Boolean {
        val ctx = MomentsApplication.instance ?: return !isVanishModeMessage
        return shouldRevealPreview(ctx, conversationId, isVanishModeMessage)
    }

    /**
     * ≡ isVanishModeMessage(in userInfo:) + isVanishModeMessage(in data:).
     * Push: String `"1"` o Bool; Firestore: Bool.
     */
    fun isVanishModeMessage(payload: Map<String, Any?>): Boolean {
        return when (val raw = payload["isVanishModeMessage"]) {
            is Boolean -> raw
            is String -> raw == "1"
            else -> false
        }
    }
}
