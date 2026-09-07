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
    val owner: String, val revision: Int, val keyVersion: Int, val wrappedKeys: Map<String, Map<String, Any?>>,
    val date: Date, val lastMessageId: String, val readStatus: Map<String, Boolean>, val muted: List<String>,
    val pendingNames: Map<String, String>,
    val allMemberNames: Map<String, String>,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(doc: DocumentSnapshot): GroupConversation {
            val data = doc.data.orEmpty()
            val details = data["participantData"] as? Map<String, Map<String, Any?>> ?: emptyMap()
            val ids = (data["participants"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            return GroupConversation(doc.id, data["groupName"] as? String ?: "",
                ids.map { GroupMember(it, details[it]?.get("username") as? String ?: "", details[it]?.get("profileImagePath") as? String ?: "") },
                (data["adminIds"] as? List<*>)?.filterIsInstance<String>().orEmpty(), data["ownerId"] as? String ?: "",
                (data["groupRevision"] as? Number)?.toInt() ?: 0, (data["conversationKeyVersion"] as? Number)?.toInt() ?: 1,
                data["wrappedKeys"] as? Map<String, Map<String, Any?>> ?: emptyMap(), doc.getTimestamp("timestamp")?.toDate() ?: Date(0),
                data["lastMessageId"] as? String ?: "", data["readStatus"] as? Map<String, Boolean> ?: emptyMap(),
                (data["mutedByUserIds"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                data["pendingInviteNames"] as? Map<String, String> ?: emptyMap(),
                details.mapValues { (_, value) -> value["username"] as? String ?: "" })
        }
    }
}
internal object GroupDirectory { val groups = MutableStateFlow<Map<String, GroupConversation>>(emptyMap()) }
object GroupNavigation { val pendingId = MutableStateFlow<String?>(null) }

internal class GroupChatStore(private val scope: CoroutineScope) {
    var groups by mutableStateOf<List<GroupConversation>>(emptyList()); private set
    var active by mutableStateOf<GroupConversation?>(null); private set
    var candidates by mutableStateOf<List<GroupMember>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
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
    suspend fun saveMembers(name: String, ids: List<String>, group: GroupConversation?): String? {
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
                request("manageGroup", mapOf("action" to if (group == null) "create" else "add", "conversationId" to id,
                    "name" to name, "memberIds" to ids, "wrappedKeys" to envelopes, "revision" to (group?.revision ?: 0)))
                id
            }
        } catch (error: Exception) {
            this.error = if (error.message == "inviteForbidden") "inviteForbidden" else "manageError"
            null
        } finally { busy = false }
    }
    suspend fun command(action: String, group: GroupConversation, memberId: String = "", name: String = "", muted: Boolean = false): Boolean {
        if (busy) return false
        busy = true
        return try {
            request("manageGroup", mapOf("action" to action, "conversationId" to group.id, "revision" to group.revision, "memberId" to memberId, "name" to name, "muted" to muted)); true
        } catch (_: Exception) { error = "manageError"; false } finally { busy = false }
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
                    val code = runCatching { JSONObject(payload).optString("error") }.getOrDefault("")
                    error(if (code == "inviteForbidden") "inviteForbidden" else "failed")
                }
                JSONObject(payload)
            } finally { connection.disconnect() }
        }
    }
}
