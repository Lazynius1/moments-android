package com.moments.android.shared

import android.content.Context
import com.moments.android.MomentsApplication

/**
 * Port de Shared/ChatPreviewPrivacy.swift — preferencias de vista previa en chat.
 */
object ChatPreviewPrivacy {
    private const val PREFS = "chat_preview_privacy"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isUserPreviewEnabled(context: Context, conversationId: String): Boolean {
        if (conversationId.isBlank()) return true
        return prefs(context).getBoolean("chat_show_message_preview_$conversationId", true)
    }

    fun shouldRevealPreview(
        context: Context,
        conversationId: String,
        isVanishModeMessage: Boolean,
    ): Boolean = isUserPreviewEnabled(context, conversationId) && !isVanishModeMessage

    fun shouldRevealPreview(conversationId: String, isVanishModeMessage: Boolean): Boolean {
        val ctx = MomentsApplication.instance ?: return !isVanishModeMessage
        return shouldRevealPreview(ctx, conversationId, isVanishModeMessage)
    }

    fun isVanishModeMessage(payload: Map<String, Any?>): Boolean {
        when (val raw = payload["isVanishModeMessage"]) {
            is Boolean -> return raw
            is String -> return raw == "1" || raw.equals("true", ignoreCase = true)
        }
        return false
    }
}
