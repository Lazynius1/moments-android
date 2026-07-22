package com.moments.android.moderation

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.UUID
import kotlin.coroutines.resume

/** Port de CommentsModerationService.swift */
class CommentsModerationService private constructor() {
    private val functionsRegion = "europe-southwest1"
    private val moderationFunctionName = "proxyOpenAIModeration"

    private var cachedSettings: ModerationSettings? = null
    private var lastFetchTime: Long? = null
    private val cacheValidityDurationMs = 300_000L

    suspend fun moderateComment(text: String): ModerationAction {
        val settings = loadModerationSettings()
        val request = createModerationRequest(text)
        val connection = (URL(request.first).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer ${request.second}")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            outputStream.use { it.write(request.third.toByteArray()) }
        }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code != 200) throw CommentsModerationError.ApiError
        val json = JSONObject(body)
        val result = ModerationResponse.fromJson(json).results.firstOrNull()
        return processModerationResult(result, settings)
    }

    suspend fun moderateAndHandle(
        content: String,
        onApproved: suspend () -> Unit,
        onWarning: suspend (String, String) -> Unit,
        onRejected: suspend (String, String) -> Unit,
        onError: suspend (Exception) -> Unit,
    ) {
        try {
            when (val result = moderateComment(content)) {
                ModerationAction.Approved -> onApproved()
                is ModerationAction.Warning -> onWarning(result.reason, result.category)
                is ModerationAction.Rejected -> onRejected(result.reason, result.category)
            }
        } catch (error: Exception) {
            onError(error)
        }
    }

    fun logModerationEvent(
        userId: String,
        content: String,
        action: String,
        reason: String,
        category: String,
        momentId: String,
    ) {
        val db = FirebaseFirestore.getInstance()
        val logData = mapOf(
            "userId" to userId,
            "content" to content,
            "action" to action,
            "reason" to reason,
            "category" to category,
            "momentId" to momentId,
            "timestamp" to Timestamp.now(),
            "platform" to "android",
            "moderationType" to "auto_comment",
            "moderationVersion" to "2.0",
            "apiProvider" to "openai",
        )
        db.collection("moderationLogs").add(logData)
    }

    fun logModerationEventWithDetails(
        userId: String,
        content: String,
        action: String,
        reason: String,
        category: String,
        momentId: String,
        moderationResult: ModerationResult? = null,
    ) {
        val db = FirebaseFirestore.getInstance()
        val logData = mutableMapOf<String, Any>(
            "userId" to userId,
            "content" to content,
            "action" to action,
            "reason" to reason,
            "category" to category,
            "momentId" to momentId,
            "timestamp" to Timestamp.now(),
            "platform" to "android",
            "moderationType" to "auto_comment",
            "moderationVersion" to "2.0",
            "apiProvider" to "openai",
        )
        moderationResult?.let { result ->
            logData["scores"] = mapOf(
                "flagged" to result.flagged,
                "harassment" to result.categoryScores.harassment,
                "harassmentThreatening" to result.categoryScores.harassmentThreatening,
                "hate" to result.categoryScores.hate,
                "hateThreatening" to result.categoryScores.hateThreatening,
                "selfHarm" to result.categoryScores.selfHarm,
                "selfHarmInstructions" to result.categoryScores.selfHarmInstructions,
                "selfHarmIntent" to result.categoryScores.selfHarmIntent,
                "sexual" to result.categoryScores.sexual,
                "sexualMinors" to result.categoryScores.sexualMinors,
                "violence" to result.categoryScores.violence,
                "violenceGraphic" to result.categoryScores.violenceGraphic,
            )
        }
        db.collection("moderationLogs").add(logData)
    }

    fun reloadSettings() {
        cachedSettings = null
        lastFetchTime = null
    }

    private suspend fun loadModerationSettings(): ModerationSettings {
        cachedSettings?.let { cached ->
            lastFetchTime?.let { last ->
                if (System.currentTimeMillis() - last < cacheValidityDurationMs) return cached
            }
        }
        return try {
            val document = FirebaseFirestore.getInstance()
                .collection("moderationSettings").document("comments").get().await()
            if (document.exists()) {
                val settings = ModerationSettings.from(document.data ?: emptyMap())
                cachedSettings = settings
                lastFetchTime = System.currentTimeMillis()
                settings
            } else {
                defaultSettings()
            }
        } catch (_: Exception) {
            defaultSettings()
        }
    }

    private fun defaultSettings(): ModerationSettings = ModerationSettings(
        deleteThresholds = ThresholdSettings(0.8, 0.8, 0.9, 0.8, 0.9, 0.1),
        warningThresholds = ThresholdSettings(0.5, 0.5, 0.6, 0.5, 0.7, 0.1),
        enableAutoApproval = true,
        enableDetailedLogging = true,
        moderationMode = "balanced",
        updatedAt = null,
        updatedBy = null,
    )

    private suspend fun createModerationRequest(text: String): Triple<String, String, String> {
        val projectId = com.google.firebase.FirebaseApp.getInstance().options.projectId
            ?: throw CommentsModerationError.InvalidResponse
        val url = "https://$functionsRegion-$projectId.cloudfunctions.net/$moderationFunctionName"
        val token = fetchIdToken()
        val body = JSONObject().put("input", text).toString()
        return Triple(url, token, body)
    }

    private suspend fun fetchIdToken(): String = suspendCancellableCoroutine { cont ->
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            cont.resumeWith(Result.failure(CommentsModerationError.NotAuthenticated))
            return@suspendCancellableCoroutine
        }
        user.getIdToken(false).addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result?.token ?: "")
            else cont.resumeWith(Result.failure(CommentsModerationError.NotAuthenticated))
        }
    }

    private fun processModerationResult(result: ModerationResult?, settings: ModerationSettings): ModerationAction {
        if (result == null || !result.flagged) return ModerationAction.Approved
        val categories = result.categories
        val scores = result.categoryScores

        if (categories.sexualMinors) {
            return ModerationAction.Rejected("Contenido relacionado con menores no permitido", "sexual/minors")
        }
        if (categories.hateThreatening || categories.harassmentThreatening) {
            return ModerationAction.Rejected("Amenazas no permitidas", "threats")
        }
        if (categories.selfHarmInstructions) {
            return ModerationAction.Rejected("Contenido de autolesión no permitido", "self-harm")
        }
        if (categories.violenceGraphic && scores.violenceGraphic > settings.deleteThresholds.violence) {
            return ModerationAction.Rejected("Violencia extrema no permitida", "violence")
        }
        if (scores.harassment > settings.deleteThresholds.harassment) {
            return ModerationAction.Rejected("Contenido de acoso detectado", "harassment")
        }
        if (scores.hate > settings.deleteThresholds.hate) {
            return ModerationAction.Rejected("Discurso de odio detectado", "hate")
        }
        if (scores.sexual > settings.deleteThresholds.sexual) {
            return ModerationAction.Rejected("Contenido sexual inapropiado", "sexual")
        }
        if (scores.violence > settings.deleteThresholds.violence) {
            return ModerationAction.Rejected("Contenido violento detectado", "violence")
        }
        if (scores.selfHarm > settings.deleteThresholds.selfHarm) {
            return ModerationAction.Rejected("Contenido de autolesión detectado", "self-harm")
        }
        if (scores.harassment > settings.warningThresholds.harassment) {
            return ModerationAction.Warning("Comentario detectado como potencialmente ofensivo", "harassment")
        }
        if (scores.hate > settings.warningThresholds.hate) {
            return ModerationAction.Warning("Posible discurso de odio detectado", "hate")
        }
        if (scores.sexual > settings.warningThresholds.sexual) {
            return ModerationAction.Warning("Contenido sexual inapropiado", "sexual")
        }
        if (scores.violence > settings.warningThresholds.violence) {
            return ModerationAction.Warning("Contenido violento detectado", "violence")
        }
        if (scores.selfHarm > settings.warningThresholds.selfHarm) {
            return ModerationAction.Warning("Contenido de autolesión detectado", "self-harm")
        }
        return ModerationAction.Approved
    }

    companion object {
        val shared: CommentsModerationService by lazy { CommentsModerationService() }
    }
}

data class ModerationSettings(
    val deleteThresholds: ThresholdSettings,
    val warningThresholds: ThresholdSettings,
    val enableAutoApproval: Boolean,
    val enableDetailedLogging: Boolean,
    val moderationMode: String,
    val updatedAt: Date?,
    val updatedBy: String?,
) {
    companion object {
        fun from(data: Map<String, Any?>): ModerationSettings {
            @Suppress("UNCHECKED_CAST")
            val deleteMap = data["deleteThresholds"] as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val warningMap = data["warningThresholds"] as? Map<String, Any?> ?: emptyMap()
            return ModerationSettings(
                deleteThresholds = ThresholdSettings.from(deleteMap),
                warningThresholds = ThresholdSettings.from(warningMap),
                enableAutoApproval = data["enableAutoApproval"] as? Boolean ?: true,
                enableDetailedLogging = data["enableDetailedLogging"] as? Boolean ?: true,
                moderationMode = data["moderationMode"] as? String ?: "balanced",
                updatedAt = com.moments.android.models.MediaItem.anyToDate(data["updatedAt"]),
                updatedBy = data["updatedBy"] as? String,
            )
        }
    }
}

data class ThresholdSettings(
    val harassment: Double,
    val hate: Double,
    val sexual: Double,
    val violence: Double,
    val selfHarm: Double,
    val sexualMinors: Double,
) {
    companion object {
        fun from(data: Map<String, Any?>): ThresholdSettings = ThresholdSettings(
            harassment = (data["harassment"] as? Number)?.toDouble() ?: 0.0,
            hate = (data["hate"] as? Number)?.toDouble() ?: 0.0,
            sexual = (data["sexual"] as? Number)?.toDouble() ?: 0.0,
            violence = (data["violence"] as? Number)?.toDouble() ?: 0.0,
            selfHarm = (data["selfHarm"] as? Number)?.toDouble() ?: 0.0,
            sexualMinors = (data["sexualMinors"] as? Number)?.toDouble() ?: 0.0,
        )
    }
}

data class ModerationResponse(val id: String, val model: String, val results: List<ModerationResult>) {
    companion object {
        fun fromJson(json: JSONObject): ModerationResponse {
            val resultsArray = json.optJSONArray("results") ?: org.json.JSONArray()
            val results = buildList {
                for (i in 0 until resultsArray.length()) {
                    resultsArray.optJSONObject(i)?.let { add(ModerationResult.fromJson(it)) }
                }
            }
            return ModerationResponse(json.optString("id"), json.optString("model"), results)
        }
    }
}

data class ModerationResult(
    val flagged: Boolean,
    val categories: ModerationCategories,
    val categoryScores: ModerationCategoryScores,
) {
    companion object {
        fun fromJson(json: JSONObject): ModerationResult {
            val categoriesJson = json.optJSONObject("categories") ?: JSONObject()
            val scoresJson = json.optJSONObject("category_scores") ?: JSONObject()
            return ModerationResult(
                flagged = json.optBoolean("flagged"),
                categories = ModerationCategories.fromJson(categoriesJson),
                categoryScores = ModerationCategoryScores.fromJson(scoresJson),
            )
        }
    }
}

data class ModerationCategories(
    val harassment: Boolean,
    val harassmentThreatening: Boolean,
    val hate: Boolean,
    val hateThreatening: Boolean,
    val selfHarm: Boolean,
    val selfHarmInstructions: Boolean,
    val selfHarmIntent: Boolean,
    val sexual: Boolean,
    val sexualMinors: Boolean,
    val violence: Boolean,
    val violenceGraphic: Boolean,
) {
    companion object {
        fun fromJson(json: JSONObject) = ModerationCategories(
            harassment = json.optBoolean("harassment"),
            harassmentThreatening = json.optBoolean("harassment/threatening"),
            hate = json.optBoolean("hate"),
            hateThreatening = json.optBoolean("hate/threatening"),
            selfHarm = json.optBoolean("self-harm"),
            selfHarmInstructions = json.optBoolean("self-harm/instructions"),
            selfHarmIntent = json.optBoolean("self-harm/intent"),
            sexual = json.optBoolean("sexual"),
            sexualMinors = json.optBoolean("sexual/minors"),
            violence = json.optBoolean("violence"),
            violenceGraphic = json.optBoolean("violence/graphic"),
        )
    }
}

data class ModerationCategoryScores(
    val harassment: Double,
    val harassmentThreatening: Double,
    val hate: Double,
    val hateThreatening: Double,
    val selfHarm: Double,
    val selfHarmInstructions: Double,
    val selfHarmIntent: Double,
    val sexual: Double,
    val sexualMinors: Double,
    val violence: Double,
    val violenceGraphic: Double,
) {
    companion object {
        fun fromJson(json: JSONObject) = ModerationCategoryScores(
            harassment = json.optDouble("harassment"),
            harassmentThreatening = json.optDouble("harassment/threatening"),
            hate = json.optDouble("hate"),
            hateThreatening = json.optDouble("hate/threatening"),
            selfHarm = json.optDouble("self-harm"),
            selfHarmInstructions = json.optDouble("self-harm/instructions"),
            selfHarmIntent = json.optDouble("self-harm/intent"),
            sexual = json.optDouble("sexual"),
            sexualMinors = json.optDouble("sexual/minors"),
            violence = json.optDouble("violence"),
            violenceGraphic = json.optDouble("violence/graphic"),
        )
    }
}

sealed class ModerationAction {
    data object Approved : ModerationAction()
    data class Rejected(val reason: String, val category: String) : ModerationAction()
    data class Warning(val reason: String, val category: String) : ModerationAction()
}

enum class CommentModerationStatus(val raw: String) {
    APPROVED("approved"),
    PENDING("pending"),
    REJECTED("rejected"),
    MANUAL_REVIEW("manual_review"),
}

sealed class CommentsModerationError(message: String) : Exception(message) {
    data object ApiError : CommentsModerationError("Error en la API de moderación")
    data object InvalidResponse : CommentsModerationError("Respuesta inválida del servidor")
    data object NetworkError : CommentsModerationError("Error de conexión")
    data object NotAuthenticated : CommentsModerationError("Usuario no autenticado")
}
