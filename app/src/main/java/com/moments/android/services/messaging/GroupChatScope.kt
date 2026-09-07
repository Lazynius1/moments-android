package com.moments.android.services.messaging

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.Query
import java.util.UUID

/** Group identity survives offline queues and never depends on participant count. */
object GroupChatScope {
    fun isGroup(id: String?): Boolean = id?.takeIf { it.startsWith("group-") }?.let {
        runCatching { UUID.fromString(it.removePrefix("group-")).toString().equals(it.removePrefix("group-"), ignoreCase = true) }.getOrDefault(false)
    } ?: false
    fun recipientMetadata(raw: Map<String, Any?>, conversationId: String): Map<String, Any?> {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return raw
        if (!isGroup(conversationId) || raw["senderId"] == uid || raw["isViewOnce"] != true) return raw
        val viewed = (raw["viewedBy"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        val eligible = uid in (raw["recipientIds"] as? List<*>).orEmpty()
        val resolved = if (eligible) viewed else (viewed + uid).distinct()
        return raw + mapOf("viewedBy" to resolved, "isViewed" to (uid in resolved))
    }
    fun newId(): String = "group-" + UUID.randomUUID().toString()
    fun reactions(id: String): String = if (isGroup(id)) "groupMessageReactions" else "messageReactions"
}
fun FirebaseFirestore.messagingThread(id: String): DocumentReference =
    collection(if (GroupChatScope.isGroup(id)) "groupConversations" else "conversations").document(id)
val DocumentReference.messagingMessages: CollectionReference
    get() = collection(if (parent.id == "groupConversations") "groupMessages" else "messages")

fun Query.applyingHistoryCutoff(cutoff: com.moments.android.views.messaging.core.MessageHistoryCutoff?): Query {
    cutoff ?: return this
    val timestamp = com.google.firebase.Timestamp(cutoff.date)
    return if (cutoff.inclusive) whereGreaterThanOrEqualTo("timestamp", timestamp)
    else whereGreaterThan("timestamp", timestamp)
}

internal fun groupNoticeText(context: android.content.Context, content: String): String? {
    val data = runCatching { org.json.JSONObject(content) }.getOrNull() ?: return null
    val resource = when (data.optString("groupNotice")) {
        "joined" -> com.moments.android.R.string.groups_notice_joined
        "left" -> com.moments.android.R.string.groups_notice_left
        "removed" -> com.moments.android.R.string.groups_notice_removed
        else -> return null
    }
    return context.getString(resource, data.optString("name"))
}
