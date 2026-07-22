package com.moments.android.services.messaging

import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.models.AcceptMessageRequestResult
import com.moments.android.models.MessageRequest
import com.moments.android.models.MessageRequestPolicy
import com.moments.android.models.MessageType
import com.moments.android.services.firestore.FirestoreService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import com.moments.android.services.firestore.fetchUser

/** Port de MessageRequestService.swift. */
class MessageRequestService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val firestoreService: FirestoreService = FirestoreService(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pendingRequests = MutableStateFlow<List<MessageRequest>>(emptyList())
    val pendingRequests: StateFlow<List<MessageRequest>> = _pendingRequests.asStateFlow()

    private val _outgoingPendingRequests = MutableStateFlow<List<MessageRequest>>(emptyList())
    val outgoingPendingRequests: StateFlow<List<MessageRequest>> = _outgoingPendingRequests.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val listeners = mutableMapOf<String, ListenerRegistration>()

    private enum class AcceptRequestServerErrorCode(val raw: String) {
        REQUEST_NOT_FOUND("REQUEST_NOT_FOUND"),
        REQUEST_FORBIDDEN("REQUEST_FORBIDDEN"),
        REQUEST_UNTRUSTED("REQUEST_UNTRUSTED"),
        REQUEST_NOT_PENDING("REQUEST_NOT_PENDING"),
        USER_NOT_FOUND("USER_NOT_FOUND"),
        INACTIVE_USER("INACTIVE_USER"),
        BLOCKED_RELATIONSHIP("BLOCKED_RELATIONSHIP"),
        REQUEST_ACCEPT_FAILED("REQUEST_ACCEPT_FAILED");

        companion object {
            fun from(raw: String?) = entries.firstOrNull { it.raw == raw }
        }
    }

    init {
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser == null) {
                scope.launch {
                    removeAllListeners()
                    _pendingRequests.value = emptyList()
                    _outgoingPendingRequests.value = emptyList()
                    _isLoading.value = false
                    _errorMessage.value = null
                }
            }
        }
    }

    fun removeAllListeners() {
        listeners.values.forEach { it.remove() }
        listeners.clear()
    }

    fun listenToPendingRequests(userId: String) {
        listeners["pendingRequests"]?.remove()
        listeners["pendingRequests"] = db.collection("messageRequests")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("status", MessageRequest.RequestStatus.PENDING.raw)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (FirebaseAuth.getInstance().currentUser == null) {
                        _pendingRequests.value = emptyList()
                        _errorMessage.value = null
                    } else {
                        _errorMessage.value = error.message
                    }
                    return@addSnapshotListener
                }
                val requests = snapshot?.documents?.mapNotNull { doc ->
                    MessageRequest.fromFirestoreData(doc.data ?: emptyMap(), doc.id)
                } ?: emptyList()
                _pendingRequests.value = requests
            }
    }

    fun listenToOutgoingPendingRequests(userId: String) {
        listeners["outgoingPendingRequests"]?.remove()
        listeners["outgoingPendingRequests"] = db.collection("messageRequests")
            .whereEqualTo("senderId", userId)
            .whereEqualTo("status", MessageRequest.RequestStatus.PENDING.raw)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (FirebaseAuth.getInstance().currentUser == null) {
                        _outgoingPendingRequests.value = emptyList()
                    }
                    return@addSnapshotListener
                }
                _outgoingPendingRequests.value = snapshot?.documents?.mapNotNull { doc ->
                    MessageRequest.fromFirestoreData(doc.data ?: emptyMap(), doc.id)
                } ?: emptyList()
            }
    }

    fun sendMessageRequest(
        receiverId: String,
        message: String,
        messageType: MessageType = MessageType.TEXT,
        mediaUrl: String? = null,
        thumbnailUrl: String? = null,
        onComplete: (Result<Unit>) -> Unit,
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            onComplete(Result.failure(IllegalStateException("Usuario no autenticado")))
            return
        }
        _isLoading.value = true
        scope.launch {
            runCatching {
                val allowed = verifyReceiverAcceptsRequests(currentUser.uid, receiverId)
                if (!allowed) error("El usuario no acepta solicitudes de mensaje")
                val existing = checkExistingRequest(currentUser.uid, receiverId)
                if (existing != null) error("Ya existe una solicitud pendiente")
                createNewRequest(
                    senderId = currentUser.uid,
                    receiverId = receiverId,
                    message = message,
                    messageType = messageType,
                    mediaUrl = mediaUrl,
                    thumbnailUrl = thumbnailUrl,
                )
            }.onSuccess {
                _isLoading.value = false
                onComplete(Result.success(Unit))
            }.onFailure { err ->
                _isLoading.value = false
                onComplete(Result.failure(err))
            }
        }
    }

    fun acceptRequest(request: MessageRequest, onComplete: (Result<AcceptMessageRequestResult>) -> Unit) {
        val requestId = request.id
        if (requestId.isNullOrEmpty()) {
            onComplete(Result.failure(IllegalStateException("Solicitud no disponible")))
            return
        }
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            onComplete(Result.failure(IllegalStateException("Usuario no autenticado")))
            return
        }
        if (currentUser.uid != request.receiverId) {
            onComplete(Result.failure(IllegalStateException("No autorizado")))
            return
        }
        _isLoading.value = true
        scope.launch {
            runCatching {
                val idToken = currentUser.getIdToken(false).await().token
                    ?: error("Token no disponible")
                val projectId = FirebaseApp.getInstance().options.projectId ?: error("Proyecto no configurado")
                val region = "europe-southwest1"
                val url = URL("https://$region-$projectId.cloudfunctions.net/acceptMessageRequest")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $idToken")
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }
                conn.outputStream.use {
                    it.write(JSONObject(mapOf("requestId" to requestId)).toString().toByteArray())
                }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.readBytes()?.decodeToString().orEmpty()
                if (code != 200) {
                    val errorCode = runCatching {
                        JSONObject(body).optString("errorCode").takeIf { it.isNotEmpty() }
                    }.getOrNull()?.let(AcceptRequestServerErrorCode::from)
                    throw IllegalStateException(localizedAcceptRequestError(errorCode, code))
                }
                val json = JSONObject(body)
                AcceptMessageRequestResult(
                    conversationId = json.getString("conversationId"),
                    messageId = json.getString("messageId"),
                )
            }.onSuccess { result ->
                _isLoading.value = false
                onComplete(Result.success(result))
            }.onFailure { err ->
                _isLoading.value = false
                onComplete(Result.failure(err))
            }
        }
    }

    fun rejectRequest(request: MessageRequest, onComplete: (Result<Unit>) -> Unit) {
        val requestId = request.id ?: return onComplete(Result.failure(IllegalStateException("ID inválido")))
        scope.launch {
            runCatching {
                db.collection("messageRequests").document(requestId).delete().await()
            }.fold(onSuccess = { onComplete(Result.success(Unit)) }, onFailure = { onComplete(Result.failure(it)) })
        }
    }

    fun cancelRequest(request: MessageRequest, onComplete: (Result<Unit>) -> Unit) {
        val requestId = request.id ?: return onComplete(Result.failure(IllegalStateException("ID inválido")))
        if (FirebaseAuth.getInstance().currentUser?.uid != request.senderId) {
            return onComplete(Result.failure(IllegalStateException("No autorizado")))
        }
        rejectRequest(request, onComplete)
    }

    fun blockUser(request: MessageRequest, onComplete: (Result<Unit>) -> Unit) = rejectRequest(request, onComplete)

    suspend fun getPendingRequestCount(userId: String): Int = runCatching {
        db.collection("messageRequests")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("status", MessageRequest.RequestStatus.PENDING.raw)
            .get().await().size()
    }.getOrDefault(0)

    fun canSendRequest(senderId: String, receiverId: String, onComplete: (Boolean) -> Unit) {
        scope.launch {
            val existing = runCatching { checkExistingRequest(senderId, receiverId) }.getOrNull()
            onComplete(existing == null)
        }
    }

    private suspend fun verifyReceiverAcceptsRequests(senderId: String, receiverId: String): Boolean {
        val snap = db.collection("users").document(receiverId).get().await()
        val policy = MessageRequestPolicy.from(snap.data?.get("messageRequestPolicy") as? String)
        return when (policy) {
            MessageRequestPolicy.EVERYONE -> true
            MessageRequestPolicy.NOBODY -> false
            MessageRequestPolicy.FOLLOWING -> {
                db.collection("users").document(receiverId)
                    .collection("following").document(senderId).get().await().exists()
            }
        }
    }

    private suspend fun checkExistingRequest(senderId: String, receiverId: String): MessageRequest? {
        val snap = db.collection("messageRequests")
            .whereEqualTo("senderId", senderId)
            .whereEqualTo("receiverId", receiverId)
            .whereEqualTo("status", MessageRequest.RequestStatus.PENDING.raw)
            .get().await()
        val doc = snap.documents.firstOrNull() ?: return null
        return MessageRequest.fromFirestoreData(doc.data ?: emptyMap(), doc.id)
    }

    private suspend fun createNewRequest(
        senderId: String,
        receiverId: String,
        message: String,
        messageType: MessageType,
        mediaUrl: String?,
        thumbnailUrl: String?,
    ) {
        val user = firestoreService.fetchUser(senderId)
        val request = MessageRequest(
            senderId = senderId,
            senderUsername = user.username,
            senderProfileImagePath = user.profileImagePath,
            receiverId = receiverId,
            message = message,
            timestamp = Date(),
            status = MessageRequest.RequestStatus.PENDING,
            messageType = messageType,
            mediaUrl = mediaUrl,
            thumbnailUrl = thumbnailUrl,
        )
        db.collection("messageRequests").add(request.encode()).await()
    }

    private fun localizedAcceptRequestError(code: AcceptRequestServerErrorCode?, statusCode: Int): String =
        when (code) {
            AcceptRequestServerErrorCode.REQUEST_FORBIDDEN,
            AcceptRequestServerErrorCode.REQUEST_UNTRUSTED -> "No puedes aceptar esta solicitud"
            AcceptRequestServerErrorCode.REQUEST_NOT_FOUND,
            AcceptRequestServerErrorCode.REQUEST_NOT_PENDING,
            AcceptRequestServerErrorCode.USER_NOT_FOUND,
            AcceptRequestServerErrorCode.INACTIVE_USER,
            AcceptRequestServerErrorCode.BLOCKED_RELATIONSHIP -> "La solicitud ya no está disponible"
            AcceptRequestServerErrorCode.REQUEST_ACCEPT_FAILED -> "Error al aceptar la solicitud"
            null -> if (statusCode == 401) "No autenticado" else "Error del servidor"
        }
}
