package com.moments.android.views.messaging.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.services.messaging.CryptoHelpers
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.messaging.GroupChatAPI
import com.moments.android.services.messaging.GroupChatAPIException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

internal enum class GroupJoinSheetPhase { LOADING, DIRECT, APPROVAL, SENDING, SENT, PENDING, ALREADY_MEMBER, ERROR, UNAVAILABLE, FULL }

internal class GroupJoinSheetStore(private val scope: CoroutineScope) {
    var phase by mutableStateOf(GroupJoinSheetPhase.LOADING); private set
    var name by mutableStateOf(""); private set
    var image by mutableStateOf(""); private set
    var requiresApproval by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var joined by mutableStateOf(false); private set
    var errorTitle by mutableStateOf("sheet.errorLoadTitle"); private set
    var errorBody by mutableStateOf("sheet.errorBody"); private set
    private enum class Operation { LOAD, SUBMIT, CANCEL }
    private var retryOperation = Operation.LOAD
    private var link: GroupInviteLink? = null
    private var userId: String? = null
    private var generation = 0
    private var pendingListener: ListenerRegistration? = null

    fun stop() {
        generation++
        pendingListener?.remove(); pendingListener = null
        busy = false
    }
    suspend fun load(link: GroupInviteLink) {
        if (busy) return
        stop(); this.link = link; userId = FirebaseAuth.getInstance().currentUser?.uid
        joined = false; phase = GroupJoinSheetPhase.LOADING; busy = true; retryOperation = Operation.LOAD
        val version = generation
        try {
            val result = preview(link)
            if (!valid(version)) return
            updateMetadata(result)
            if (!applyResolvedStatus(result)) {
                decodedKey(link, result)
                phase = if (requiresApproval) GroupJoinSheetPhase.APPROVAL else GroupJoinSheetPhase.DIRECT
            }
        } catch (error: Exception) { if (valid(version)) present(error) }
        finally { if (valid(version)) busy = false }
    }
    suspend fun submit() {
        val link = link ?: return
        val userId = userId ?: return
        if (busy || FirebaseAuth.getInstance().currentUser?.uid != userId) return
        busy = true; phase = GroupJoinSheetPhase.SENDING; retryOperation = Operation.SUBMIT
        val version = generation
        val expectedApproval = requiresApproval
        try {
            val result = preview(link)
            if (!valid(version)) return
            updateMetadata(result)
            if (applyResolvedStatus(result)) return
            if (requiresApproval != expectedApproval) {
                phase = if (requiresApproval) GroupJoinSheetPhase.APPROVAL else GroupJoinSheetPhase.DIRECT
                return
            }
            val key = decodedKey(link, result)
            val wrapped = EncryptionService.buildWrappedConversationKeys(listOf(userId), key, userId)
            if (!valid(version)) return
            val envelope = wrapped[userId] ?: throw GroupChatAPIException("keyUnavailable")
            val response = GroupChatAPI.request("manageGroup", mapOf(
                "action" to "joinLink", "conversationId" to link.groupId, "token" to link.token,
                "requiresApproval" to expectedApproval, "wrappedKey" to envelope,
            ))
            if (!valid(version)) return
            if (response.optBoolean("pending")) {
                phase = if (response.optBoolean("alreadyPending")) GroupJoinSheetPhase.PENDING else GroupJoinSheetPhase.SENT
                watchPending(link, version)
            } else joined = true
        } catch (error: Exception) {
            if (!valid(version)) return
            if ((error as? GroupChatAPIException)?.serverCode == "approvalChanged") {
                busy = false
                load(link)
            } else present(error)
        } finally { if (valid(version)) busy = false }
    }
    suspend fun cancel() {
        val link = link ?: return
        val userId = userId ?: return
        if (busy || FirebaseAuth.getInstance().currentUser?.uid != userId) return
        busy = true; phase = GroupJoinSheetPhase.PENDING; retryOperation = Operation.CANCEL
        val version = generation
        try {
            val result = GroupChatAPI.request("manageGroup", mapOf("action" to "cancelJoin", "conversationId" to link.groupId))
            if (!valid(version)) return
            if (result.optBoolean("isMember")) { phase = GroupJoinSheetPhase.ALREADY_MEMBER; return }
            busy = false
            load(link)
        } catch (error: Exception) { if (valid(version)) present(error) }
        finally { if (valid(version)) busy = false }
    }
    suspend fun retry() {
        when (retryOperation) {
            Operation.LOAD -> link?.let { load(it) }
            Operation.SUBMIT -> submit()
            Operation.CANCEL -> cancel()
        }
    }
    suspend fun refreshPending() {
        if (!busy && phase in listOf(GroupJoinSheetPhase.PENDING, GroupJoinSheetPhase.SENT)) link?.let { load(it) }
    }
    private suspend fun preview(link: GroupInviteLink): JSONObject = GroupChatAPI.request("manageGroup",
        mapOf("action" to "previewLink", "conversationId" to link.groupId, "token" to link.token))
    private fun updateMetadata(data: JSONObject) {
        name = data.optString("name"); image = data.optString("image"); requiresApproval = data.optBoolean("requiresApproval")
    }
    private fun applyResolvedStatus(data: JSONObject): Boolean {
        if (data.optBoolean("isMember")) { phase = GroupJoinSheetPhase.ALREADY_MEMBER; return true }
        if (data.optBoolean("pending")) {
            phase = GroupJoinSheetPhase.PENDING
            link?.let { watchPending(it, generation) }
            return true
        }
        if (data.optBoolean("isFull")) { phase = GroupJoinSheetPhase.FULL; return true }
        return false
    }
    private fun decodedKey(link: GroupInviteLink, data: JSONObject): ByteArray = try {
        CryptoHelpers.aesGcmOpen(android.util.Base64.decode(data.getString("encryptedKey"), android.util.Base64.NO_WRAP),
            link.secret, link.groupId.toByteArray(Charsets.UTF_8)).also { require(it.size == 32) }
    } catch (_: Exception) { throw GroupChatAPIException("linkUnavailable") }
    private fun valid(version: Int) = generation == version && userId != null && FirebaseAuth.getInstance().currentUser?.uid == userId
    private fun present(error: Exception) {
        val code = (error as? GroupChatAPIException)?.serverCode
        phase = when (code) {
            "linkUnavailable", "unavailable", "invalidGroup" -> GroupJoinSheetPhase.UNAVAILABLE
            "groupFull", "invalidMembers" -> GroupJoinSheetPhase.FULL
            else -> {
                errorTitle = when (retryOperation) {
                    Operation.LOAD -> "sheet.errorLoadTitle"
                    Operation.SUBMIT -> if (requiresApproval) "sheet.errorSendTitle" else "sheet.errorJoinTitle"
                    Operation.CANCEL -> "sheet.errorCancelTitle"
                }
                errorBody = if (code == "keyUnavailable") "keyError" else "sheet.errorBody"
                GroupJoinSheetPhase.ERROR
            }
        }
    }
    private fun watchPending(link: GroupInviteLink, version: Int) {
        pendingListener?.remove()
        val userId = userId ?: return
        pendingListener = FirebaseFirestore.getInstance().collection("groupJoinRequests").whereEqualTo("recipientId", userId)
            .addSnapshotListener { doc, _ ->
                if (valid(version) && !busy && doc != null && !doc.metadata.isFromCache && doc.documents.none { it.id == "${link.groupId}_$userId" }
                    && phase in listOf(GroupJoinSheetPhase.PENDING, GroupJoinSheetPhase.SENT)) {
                    scope.launch { load(link) }
                }
            }
    }
}
