package com.moments.android.views.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.services.messaging.EncryptionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class DataExportType {
    COMPLETE,
    TEXT_ONLY,
    MEDIA_ONLY,
    CONVERSATIONS_ONLY,
}

enum class DataExportFormat {
    JSON,
    CSV,
}

enum class DataExportStatus {
    PENDING,
    PROCESSING,
    READY,
    COMPLETED,
    FAILED,
    ;

    val color: Color
        get() = when (this) {
            PENDING -> Color(0xFFFF9500)
            PROCESSING -> Color(0xFF007AFF)
            READY -> Color(0xFF34C759)
            COMPLETED -> Color.Gray
            FAILED -> Color(0xFFFF3B30)
        }
}

data class DataExportRequest(
    val id: String,
    val requestDate: Date,
    val estimatedCompletion: Date?,
    val status: DataExportStatus,
    val progress: Double,
    val exportType: DataExportType,
    val format: DataExportFormat,
    val downloadURLs: List<String> = emptyList(),
)

/**
 * Port de `DataExportViewModel` en `DataExportView.swift`.
 * Colección: `users/{uid}/dataExportRequests`.
 */
class DataExportViewModel {
    var selectedExportType by mutableStateOf(DataExportType.COMPLETE)
    var selectedFormat by mutableStateOf(DataExportFormat.JSON)
    var recoveryPIN by mutableStateOf("")
    var currentRequest by mutableStateOf<DataExportRequest?>(null)
    var isProcessing by mutableStateOf(false)
    var showSuccess by mutableStateOf(false)
    var showError by mutableStateOf(false)
    var errorMessage by mutableStateOf("")
    var canRequestExport by mutableStateOf(true)
    var daysUntilNextRequest by mutableIntStateOf(0)

    private val db = FirebaseFirestore.getInstance()
    private var currentRequestListener: ListenerRegistration? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun checkExistingRequests() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val thirtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }.time

        db.collection("users").document(userId).collection("dataExportRequests")
            .whereGreaterThan("requestDate", Timestamp(thirtyDaysAgo))
            .orderBy("requestDate", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val document = snapshot.documents.firstOrNull() ?: return@addOnSuccessListener
                val data = document.data ?: return@addOnSuccessListener
                val requestDate = (data["requestDate"] as? Timestamp)?.toDate()
                    ?: return@addOnSuccessListener
                val daysSinceRequest = TimeUnit.MILLISECONDS.toDays(
                    Date().time - requestDate.time,
                ).toInt()

                if (daysSinceRequest < 30) {
                    canRequestExport = false
                    daysUntilNextRequest = 30 - daysSinceRequest

                    val statusString = data["status"] as? String
                    if (statusString != null &&
                        statusString != "completed" &&
                        statusString != "failed"
                    ) {
                        loadCurrentRequest(data, document.id)
                        observeRequestProgress(document.id, userId)
                    }
                }
            }
    }

    private fun loadCurrentRequest(data: Map<String, Any?>, documentId: String) {
        val requestDate = (data["requestDate"] as? Timestamp)?.toDate() ?: return
        val statusString = data["status"] as? String ?: return

        val status = when (statusString) {
            "pending" -> DataExportStatus.PENDING
            "processing", "uploading" -> DataExportStatus.PROCESSING
            "ready" -> DataExportStatus.READY
            "completed" -> DataExportStatus.COMPLETED
            "failed" -> DataExportStatus.FAILED
            else -> DataExportStatus.PENDING
        }

        val progress = (data["progress"] as? Number)?.toDouble() ?: 0.0
        val estimatedCompletion = (data["estimatedCompletion"] as? Timestamp)?.toDate()
        @Suppress("UNCHECKED_CAST")
        val downloadURLs = (data["downloadParts"] as? List<Map<String, Any?>>)
            ?.mapNotNull { it["downloadURL"] as? String }
            ?: emptyList()

        currentRequest = DataExportRequest(
            id = documentId,
            requestDate = requestDate,
            estimatedCompletion = estimatedCompletion,
            status = status,
            progress = progress,
            exportType = selectedExportType,
            format = selectedFormat,
            downloadURLs = downloadURLs,
        )
    }

    fun requestDataExport(
        pinRequiredMessage: String,
        userNotAuthMessage: String,
        pinIncorrectMessage: String,
        submitErrorPrefix: String,
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            showErrorAlert(userNotAuthMessage)
            return
        }

        val trimmedPIN = recoveryPIN.trim()
        if (selectedExportType == DataExportType.CONVERSATIONS_ONLY && trimmedPIN.isEmpty()) {
            showErrorAlert(pinRequiredMessage)
            return
        }

        isProcessing = true
        recoveryPIN = ""

        scope.launch {
            if (trimmedPIN.isNotEmpty()) {
                val isValid = withContext(Dispatchers.IO) {
                    EncryptionService.verifyRecoveryPIN(trimmedPIN)
                }
                if (!isValid) {
                    isProcessing = false
                    showErrorAlert(pinIncorrectMessage)
                    return@launch
                }
            }
            submitExportRequest(
                userId = userId,
                pin = trimmedPIN.ifEmpty { null },
                submitErrorPrefix = submitErrorPrefix,
            )
        }
    }

    private fun submitExportRequest(userId: String, pin: String?, submitErrorPrefix: String) {
        val requestId = UUID.randomUUID().toString()
        val requestDate = Date()
        val estimatedCompletion = Calendar.getInstance().apply {
            time = requestDate
            add(Calendar.DAY_OF_YEAR, 2)
        }.time

        val requestData = mutableMapOf<String, Any>(
            "id" to requestId,
            "requestDate" to Timestamp(requestDate),
            "estimatedCompletion" to Timestamp(estimatedCompletion),
            "status" to "pending",
            "progress" to 0.0,
            "exportType" to exportTypeString(selectedExportType),
            "format" to formatString(selectedFormat),
            "userEmail" to (FirebaseAuth.getInstance().currentUser?.email ?: ""),
        )
        if (pin != null) {
            requestData["pin"] = pin
        }

        db.collection("users").document(userId).collection("dataExportRequests")
            .document(requestId)
            .set(requestData)
            .addOnCompleteListener { task ->
                isProcessing = false
                if (!task.isSuccessful) {
                    val msg = task.exception?.localizedMessage ?: task.exception?.message ?: ""
                    showErrorAlert("$submitErrorPrefix$msg")
                } else {
                    showSuccess = true
                    canRequestExport = false
                    daysUntilNextRequest = 30
                    currentRequest = DataExportRequest(
                        id = requestId,
                        requestDate = requestDate,
                        estimatedCompletion = estimatedCompletion,
                        status = DataExportStatus.PENDING,
                        progress = 0.0,
                        exportType = selectedExportType,
                        format = selectedFormat,
                    )
                    observeRequestProgress(requestId, userId)
                }
            }
    }

    private fun observeRequestProgress(requestId: String, userId: String) {
        currentRequestListener?.remove()
        currentRequestListener = db.collection("users")
            .document(userId)
            .collection("dataExportRequests")
            .document(requestId)
            .addSnapshotListener { snapshot, _ ->
                val data = snapshot?.data ?: return@addSnapshotListener
                loadCurrentRequest(data, requestId)
                val statusString = data["status"] as? String
                if (statusString == "ready" || statusString == "completed" || statusString == "failed") {
                    currentRequestListener?.remove()
                    currentRequestListener = null
                }
            }
    }

    private fun exportTypeString(type: DataExportType): String = when (type) {
        DataExportType.COMPLETE -> "complete"
        DataExportType.TEXT_ONLY -> "textOnly"
        DataExportType.MEDIA_ONLY -> "mediaOnly"
        DataExportType.CONVERSATIONS_ONLY -> "conversationsOnly"
    }

    private fun formatString(format: DataExportFormat): String = when (format) {
        DataExportFormat.JSON -> "json"
        DataExportFormat.CSV -> "csv"
    }

    private fun showErrorAlert(message: String) {
        errorMessage = message
        showError = true
    }

    fun clear() {
        currentRequestListener?.remove()
        currentRequestListener = null
    }
}
