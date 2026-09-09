package com.moments.android.views.messaging.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.moments.android.services.messaging.CryptoHelpers
import com.moments.android.services.messaging.EncryptionService
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

internal data class GroupMember(val id: String, val name: String, val image: String)
internal data class GroupConversation(
    val id: String, val name: String, val members: List<GroupMember>, val admins: List<String>,
    val owner: String, val createdBy: String, val createdAt: Date?, val revision: Int, val keyVersion: Int, val wrappedKeys: Map<String, Map<String, Any?>>,
    val date: Date, val lastMessageId: String, val readStatus: Map<String, Boolean>, val muted: List<String>,
    val pendingNames: Map<String, String>,
    val allMemberNames: Map<String, String>,
    val image: String = "",
    val memberJoinedAt: Map<String, Date> = emptyMap(),
    val groupDescription: String = "",
    val sendPermission: String = "everyone",
    val linkRequiresApproval: Boolean = false,
    val pendingJoinNames: Map<String, String> = emptyMap(),
) {
    fun joinedDate(memberId: String): Date? =
        memberJoinedAt[memberId] ?: createdAt.takeIf { memberId == createdBy || memberId == owner }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(doc: DocumentSnapshot): GroupConversation {
            val data = doc.data.orEmpty()
            val details = data["participantData"] as? Map<String, Map<String, Any?>> ?: emptyMap()
            val ids = (data["participants"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            val owner = data["ownerId"] as? String ?: ""
            return GroupConversation(doc.id, data["groupName"] as? String ?: "",
                ids.map { GroupMember(it, details[it]?.get("username") as? String ?: "", details[it]?.get("profileImagePath") as? String ?: "") },
                (data["adminIds"] as? List<*>)?.filterIsInstance<String>().orEmpty(), owner,
                data["createdBy"] as? String ?: owner, doc.getTimestamp("createdAt")?.toDate(),
                (data["groupRevision"] as? Number)?.toInt() ?: 0, (data["conversationKeyVersion"] as? Number)?.toInt() ?: 1,
                data["wrappedKeys"] as? Map<String, Map<String, Any?>> ?: emptyMap(), doc.getTimestamp("timestamp")?.toDate() ?: Date(0),
                data["lastMessageId"] as? String ?: "", data["readStatus"] as? Map<String, Boolean> ?: emptyMap(),
                (data["mutedByUserIds"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                data["pendingInviteNames"] as? Map<String, String> ?: emptyMap(),
                details.mapValues { (_, value) -> value["username"] as? String ?: "" },
                data["groupImagePath"] as? String ?: "",
                timestampMap(data["memberJoinedAt"]),
                data["groupDescription"] as? String ?: "",
                if (data["sendPermission"] as? String == "admins") "admins" else "everyone",
                data["linkRequiresApproval"] as? Boolean ?: false,
                data["pendingJoinNames"] as? Map<String, String> ?: emptyMap(),
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun timestampMap(raw: Any?): Map<String, Date> {
            val values = raw as? Map<String, Any?> ?: return emptyMap()
            return values.mapNotNull { (key, value) ->
                val date = when (value) {
                    is com.google.firebase.Timestamp -> value.toDate()
                    is Date -> value
                    else -> null
                }
                date?.let { key to it }
            }.toMap()
        }
    }
}
internal data class GroupSkippedInvite(val id: String, val username: String)
internal data class GroupSaveResult(val id: String, val skipped: List<GroupSkippedInvite>)
private class GroupInviteForbidden(val skipped: List<GroupSkippedInvite>) : Exception("inviteForbidden")
internal object GroupDirectory { val groups = MutableStateFlow<Map<String, GroupConversation>>(emptyMap()) }
object GroupNavigation { val pendingId = MutableStateFlow<String?>(null) }

internal class GroupChatStore(private val scope: CoroutineScope) {
    var groups by mutableStateOf<List<GroupConversation>>(emptyList()); private set
    var active by mutableStateOf<GroupConversation?>(null); private set
    var candidates by mutableStateOf<List<GroupMember>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var skippedInvites by mutableStateOf<List<GroupSkippedInvite>>(emptyList())
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    val uid get() = auth.currentUser?.uid.orEmpty()
    private var inboxListener: ListenerRegistration? = null
    private var groupListener: ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var sessionId: String? = null
    private var activeId: String? = null
    private var creationId: String? = null
    private var creationSignature: String? = null

    fun start() {
        if (authListener != null) return
        listenInbox(auth.currentUser?.uid)
        authListener = FirebaseAuth.AuthStateListener { listenInbox(it.currentUser?.uid) }.also(auth::addAuthStateListener)
    }
    private fun listenInbox(userId: String?) {
        if (sessionId == userId && inboxListener != null) return
        inboxListener?.remove(); closeChat(); groups = emptyList(); candidates = emptyList(); sessionId = userId
        if (userId == null) return
        loading = true
        inboxListener = db.collection("groupConversations").whereArrayContains("participants", userId).addSnapshotListener { snapshot, failure ->
            if (sessionId != userId) return@addSnapshotListener
            loading = false
            if (failure != null) { error = "error"; return@addSnapshotListener }
            groups = snapshot?.documents.orEmpty().map(GroupConversation::from).sortedByDescending { it.date }
        }
    }
    fun stop() {
        inboxListener?.remove(); inboxListener = null
        authListener?.let(auth::removeAuthStateListener); authListener = null; sessionId = null
        closeChat(); groups = emptyList(); candidates = emptyList()
    }
    fun closeChat() {
        groupListener?.remove(); groupListener = null
        activeId = null; active = null
    }
    fun open(id: String) {
        if (activeId == id) return
        closeChat(); activeId = id; loading = true
        val userId = uid
        groupListener = db.collection("groupConversations").document(id).addSnapshotListener { snapshot, failure ->
            if (activeId != id || uid != userId) return@addSnapshotListener
            loading = false
            if (failure != null || snapshot == null || !snapshot.exists()) { closeChat(); error = "unavailable"; return@addSnapshotListener }
            val group = GroupConversation.from(snapshot)
            if (group.members.none { it.id == userId }) { closeChat(); error = "unavailable"; return@addSnapshotListener }
            active = group
        }
    }
    private fun key(group: GroupConversation): ByteArray = EncryptionService.unwrapGroupKey(group.wrappedKeys[uid] ?: error("Missing key"))
    suspend fun loadCandidates(query: String = "") {
        try {
            val userId = uid
            if (userId.isBlank()) { candidates = emptyList(); return }
            val search = query.trim().lowercase()
            var request: Query = db.collection("users")
            if (search.isNotEmpty()) request = request.whereGreaterThanOrEqualTo("username", search).whereLessThanOrEqualTo("username", search + "\uf8ff")
            val docs = request.limit(40).get().await()
            val members = docs.documents.filter { it.id != userId && it.getBoolean("isActive") != false }.map {
                GroupMember(it.id, it.getString("username").orEmpty(), it.getString("profileImagePath").orEmpty())
            }
            if (uid == userId) candidates = members.sortedBy { it.name.lowercase() }
        } catch (_: Exception) { error = "error" }
    }
    suspend fun saveMembers(name: String, ids: List<String>, group: GroupConversation?): GroupSaveResult? {
        if (busy) return null
        busy = true
        return try {
            val signature = JSONObject(mapOf("uid" to uid, "name" to name, "ids" to ids.sorted())).toString()
            if (group == null && creationSignature != signature) { creationId = null; creationSignature = signature }
            val userId = uid; val id = group?.id ?: (creationId ?: com.moments.android.services.messaging.GroupChatScope.newId().also { creationId = it })
            val key = group?.let(::key) ?: CryptoHelpers.randomBytes(32)
            val recipients = if (group == null) listOf(userId) + ids else ids
            val envelopes = EncryptionService.buildWrappedConversationKeys(recipients, key, userId).mapValues { (_, map) -> map.filterKeys { it != "wrappedAt" } }
            if (envelopes.size != recipients.size) { error = "keyError"; null }
            else {
                val payload = request("manageGroup", mapOf("action" to if (group == null) "create" else "add", "conversationId" to id,
                    "name" to name, "memberIds" to ids, "wrappedKeys" to envelopes, "revision" to (group?.revision ?: 0)))
                GroupSaveResult(id, payload.skippedInvites())
            }
        } catch (failure: GroupInviteForbidden) {
            skippedInvites = failure.skipped
            error = "inviteForbidden"
            null
        } catch (_: Exception) {
            skippedInvites = emptyList()
            this.error = "manageError"
            null
        } finally { busy = false }
    }
    suspend fun setPhoto(image: android.graphics.Bitmap, group: GroupConversation) {
        if (busy) return
        busy = true
        try {
            val path = com.moments.android.services.storage.StorageService.uploadGroupImage(group.id, image)
            request("manageGroup", mapOf("action" to "setPhoto", "conversationId" to group.id, "revision" to group.revision, "imagePath" to path))
        } catch (_: Exception) { error = "photoError" } finally { busy = false }
    }
    suspend fun command(action: String, group: GroupConversation, memberId: String = "", name: String = "", muted: Boolean = false, extra: Map<String, Any> = emptyMap()): Boolean {
        if (busy) return false
        busy = true
        return try {
            request("manageGroup", mapOf("action" to action, "conversationId" to group.id, "revision" to group.revision, "memberId" to memberId, "name" to name, "muted" to muted) + extra); true
        } catch (_: Exception) { error = "manageError"; false } finally { busy = false }
    }
    suspend fun invitationLink(group: GroupConversation, renew: Boolean = false): String? {
        if (busy) return null
        busy = true
        return try {
            val groupKey = key(group)
            val aad = group.id.toByteArray(Charsets.UTF_8)
            if (!renew) {
                val existing = request("manageGroup", mapOf("action" to "getLink", "conversationId" to group.id))
                if (existing.has("token")) {
                    val secret = CryptoHelpers.aesGcmOpen(android.util.Base64.decode(existing.getString("secretBox"), android.util.Base64.NO_WRAP), groupKey, aad)
                    return GroupInviteLink.url(group.id, existing.getString("token"), secret)
                }
            }
            val secret = CryptoHelpers.randomBytes(32)
            val token = CryptoHelpers.randomBytes(32).joinToString("") { "%02x".format(it) }
            val encryptedKey = android.util.Base64.encodeToString(CryptoHelpers.aesGcmSeal(groupKey, secret, aad), android.util.Base64.NO_WRAP)
            val secretBox = android.util.Base64.encodeToString(CryptoHelpers.aesGcmSeal(secret, groupKey, aad), android.util.Base64.NO_WRAP)
            request("manageGroup", mapOf("action" to "setLink", "conversationId" to group.id, "revision" to group.revision,
                "token" to token, "encryptedKey" to encryptedKey, "secretBox" to secretBox))
            GroupInviteLink.url(group.id, token, secret)
        } catch (_: Exception) { error = "linkError"; null } finally { busy = false }
    }
    private fun linkKey(link: GroupInviteLink, result: JSONObject): ByteArray =
        CryptoHelpers.aesGcmOpen(android.util.Base64.decode(result.getString("encryptedKey"), android.util.Base64.NO_WRAP),
            link.secret, link.groupId.toByteArray(Charsets.UTF_8)).also { require(it.size == 32) }

    suspend fun previewLink(link: GroupInviteLink): Triple<String, String, Boolean>? = try {
        val result = request("manageGroup", mapOf("action" to "previewLink", "conversationId" to link.groupId, "token" to link.token))
        linkKey(link, result)
        val name = result.optString("name")
        if (name.isBlank()) null else Triple(name, result.optString("image"), result.optBoolean("requiresApproval"))
    } catch (_: Exception) { error = "linkError"; null }

    suspend fun joinLink(link: GroupInviteLink): Boolean? {
        if (busy) return null
        busy = true
        return try {
            val result = request("manageGroup", mapOf("action" to "previewLink", "conversationId" to link.groupId, "token" to link.token))
            val userId = uid
            val key = linkKey(link, result)
            val envelope = EncryptionService.buildWrappedConversationKeys(listOf(userId), key, userId)[userId]
                ?.filterKeys { it != "wrappedAt" } ?: error("Missing key")
            val joined = request("manageGroup", mapOf("action" to "joinLink", "conversationId" to link.groupId, "token" to link.token, "wrappedKey" to envelope))
            if (joined.optBoolean("pending")) false else true
        } catch (_: Exception) { error = "linkError"; null } finally { busy = false }
    }
    private suspend fun request(endpoint: String, body: Map<String, Any>): JSONObject {
        val user = auth.currentUser ?: error("Unauthenticated")
        val token = user.getIdToken(false).await().token ?: error("Unauthenticated")
        val project = FirebaseApp.getInstance().options.projectId ?: error("Missing project")
        return withContext(Dispatchers.IO) {
            val connection = URL("https://europe-southwest1-$project.cloudfunctions.net/$endpoint").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"; connection.connectTimeout = 20000; connection.readTimeout = 60000; connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token"); connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(JSONObject(body).toString().toByteArray(Charsets.UTF_8)) }
                val payload = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (connection.responseCode != 200 || uid != user.uid) {
                    val json = runCatching { JSONObject(payload) }.getOrNull()
                    val code = json?.optString("error").orEmpty()
                    if (code == "inviteForbidden") throw GroupInviteForbidden(json.skippedInvites())
                    error("failed")
                }
                JSONObject(payload)
            } finally { connection.disconnect() }
        }
    }
}

private fun JSONObject?.skippedInvites(): List<GroupSkippedInvite> {
    val array = this?.optJSONArray("skipped") ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val row = array.optJSONObject(index) ?: return@mapNotNull null
        val id = row.optString("id")
        if (id.isBlank()) null else GroupSkippedInvite(id, row.optString("username"))
    }
}

internal data class GroupInviteLink(val groupId: String, val token: String, val secret: ByteArray) {
    companion object {
        fun parse(uri: android.net.Uri): GroupInviteLink? {
            val fragment = uri.fragment.orEmpty()
            if (!fragment.matches(Regex("[a-f0-9]{64}"))) return null
            val parts = uri.pathSegments
            val scheme = uri.scheme.orEmpty()
            val host = uri.host.orEmpty().lowercase()
            val pair = when {
                scheme in listOf("moments", "glowsy") && host == "group" && parts.size == 2 ->
                    parts[0] to parts[1]
                scheme == "https" && host in setOf("momentsapp.app", "www.momentsapp.app") &&
                    parts.size == 3 && parts[0] == "g" ->
                    parts[1] to parts[2]
                scheme == "https" && host.contains("cloudfunctions.net") &&
                    parts.size >= 4 && parts[parts.size - 3] == "join" ->
                    parts[parts.size - 2] to parts[parts.size - 1]
                else -> null
            } ?: return null
            if (!com.moments.android.services.messaging.GroupChatScope.isGroup(pair.first) ||
                !pair.second.matches(Regex("[a-f0-9]{64}"))) return null
            return GroupInviteLink(pair.first, pair.second, fragment.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        }
        fun url(groupId: String, token: String, secret: ByteArray): String =
            "https://momentsapp.app/g/$groupId/$token#${secret.joinToString("") { "%02x".format(it) }}"
    }
}
internal object GroupLinkNavigation { val pending = MutableStateFlow<GroupInviteLink?>(null) }

internal data class GroupPendingRequest(
    val id: String, val groupId: String, val name: String, val image: String,
    val inviter: String = "", val createdAt: Date? = null,
)

internal class GroupRequestsStore(private val scope: CoroutineScope) {
    var received by mutableStateOf<List<GroupPendingRequest>>(emptyList()); private set
    var sent by mutableStateOf<List<GroupPendingRequest>>(emptyList()); private set
    var receivedLoading by mutableStateOf(true); private set
    var sentLoading by mutableStateOf(true); private set
    var receivedFailed by mutableStateOf(false); private set
    var sentFailed by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var actionFailed by mutableStateOf(false)
    val count get() = received.size + sent.size
    private val auth = FirebaseAuth.getInstance()
    private var sessionId: String? = null
    private var generation = 0
    private var sentGeneration = 0
    private var receivedListener: ListenerRegistration? = null
    private var sentListener: ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    fun start() {
        if (authListener != null) return
        listen(auth.currentUser?.uid)
        authListener = FirebaseAuth.AuthStateListener {
            if (sessionId != it.currentUser?.uid) listen(it.currentUser?.uid)
        }.also(auth::addAuthStateListener)
    }
    fun stop() {
        generation++; sentGeneration++
        receivedListener?.remove(); sentListener?.remove()
        authListener?.let(auth::removeAuthStateListener); authListener = null
        sessionId = null; received = emptyList(); sent = emptyList()
    }
    fun retry() = listen(auth.currentUser?.uid)
    private fun listen(uid: String?) {
        receivedListener?.remove(); sentListener?.remove()
        generation++; sentGeneration++
        val version = generation
        if (sessionId != uid) { received = emptyList(); sent = emptyList(); busy = false; actionFailed = false }
        sessionId = uid
        receivedLoading = true; sentLoading = true; receivedFailed = false; sentFailed = false
        if (uid == null) { receivedLoading = false; sentLoading = false; return }
        val db = FirebaseFirestore.getInstance()
        receivedListener = db.collection("groupInvitations").whereEqualTo("recipientId", uid).addSnapshotListener { snapshot, failure ->
            if (generation != version || sessionId != uid) return@addSnapshotListener
            receivedLoading = false; receivedFailed = failure != null
            if (snapshot != null && failure == null) received = snapshot.documents.map { doc ->
                GroupPendingRequest(doc.id, doc.getString("groupId").orEmpty(), doc.getString("groupName").orEmpty(),
                    doc.getString("groupImagePath").orEmpty(), doc.getString("inviterName").orEmpty(), doc.getTimestamp("createdAt")?.toDate())
            }.sortedByDescending { it.createdAt?.time ?: 0 }
        }
        sentListener = db.collection("groupJoinRequests").whereEqualTo("recipientId", uid).addSnapshotListener { snapshot, failure ->
            if (generation != version || sessionId != uid) return@addSnapshotListener
            sentGeneration++
            val requestVersion = sentGeneration
            if (failure != null) { sentLoading = false; sentFailed = true; return@addSnapshotListener }
            if (snapshot?.isEmpty == true) { sent = emptyList(); sentLoading = false; sentFailed = false; return@addSnapshotListener }
            scope.launch {
                try {
                    val result = com.moments.android.services.messaging.GroupChatAPI.request("manageGroup", mapOf("action" to "listJoinRequests"))
                    if (generation != version || sentGeneration != requestVersion || sessionId != uid) return@launch
                    val rows = result.optJSONArray("requests")
                    sent = (0 until (rows?.length() ?: 0)).mapNotNull { index ->
                        val row = rows?.optJSONObject(index) ?: return@mapNotNull null
                        val millis = row.optLong("createdAt")
                        GroupPendingRequest(row.getString("id"), row.getString("groupId"), row.optString("name"), row.optString("image"),
                            createdAt = millis.takeIf { it > 0 }?.let(::Date))
                    }
                    sentLoading = false; sentFailed = false
                } catch (_: Exception) {
                    if (generation == version && sentGeneration == requestVersion) { sentLoading = false; sentFailed = true }
                }
            }
        }
    }
    suspend fun respond(row: GroupPendingRequest, accept: Boolean): com.moments.android.views.messaging.core.Conversation? {
        val uid = sessionId ?: return null
        if (busy || auth.currentUser?.uid != uid) return null
        busy = true
        return try {
            com.moments.android.services.messaging.GroupChatAPI.request("manageGroup", mapOf("action" to if (accept) "acceptInvite" else "declineInvite", "conversationId" to row.groupId))
            if (sessionId != uid) return null
            received = received.filterNot { it.id == row.id }
            if (!accept) null else {
                val doc = FirebaseFirestore.getInstance().collection("groupConversations").document(row.groupId).get().await()
                if (sessionId != uid) null else com.moments.android.views.messaging.services.ChatService.parseConversation(doc.id, doc.data.orEmpty(), uid)
            }
        } catch (_: Exception) { if (sessionId == uid) actionFailed = true; null }
        finally { if (sessionId == uid) busy = false }
    }
    suspend fun cancel(row: GroupPendingRequest) {
        val uid = sessionId ?: return
        if (busy || auth.currentUser?.uid != uid) return
        busy = true
        try {
            com.moments.android.services.messaging.GroupChatAPI.request("manageGroup", mapOf("action" to "cancelJoin", "conversationId" to row.groupId))
            if (sessionId == uid) sent = sent.filterNot { it.id == row.id }
        } catch (_: Exception) { if (sessionId == uid) actionFailed = true }
        finally { if (sessionId == uid) busy = false }
    }
}
