package com.moments.android.views.nova.ai

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionDeclaration as FirebaseFunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.moments.android.views.nova.agent.NovaToolRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Firebase AI Logic bridge used by the Nova agent. */
object NovaAIService {
    private val firebaseAi = Firebase.ai(backend = GenerativeBackend.vertexAI(NovaGenerationConfig.location))

    fun makeChatModel(systemInstruction: String): GenerativeModel = firebaseAi.generativeModel(
        modelName = NovaGenerationConfig.modelName,
        generationConfig = NovaGenerationConfig.chat,
        safetySettings = NovaGenerationConfig.safetySettings,
        tools = listOf(Tool.functionDeclarations(NovaToolRegistry.toolSet.map(::toFirebaseDeclaration)), Tool.googleSearch()),
        systemInstruction = content("system") { text(systemInstruction) },
    )

    fun makeUtilityModel(config: com.google.firebase.ai.type.GenerationConfig): GenerativeModel = firebaseAi.generativeModel(
        modelName = NovaGenerationConfig.modelName,
        generationConfig = config,
        safetySettings = NovaGenerationConfig.safetySettings,
    )

    fun startChat(systemInstruction: String, history: List<NovaModelContent> = emptyList()): ChatSession =
        ChatSession(makeChatModel(systemInstruction).startChat(history.map(::toFirebaseContent)))

    fun startChatWithNativeHistory(systemInstruction: String, history: List<Content>): ChatSession =
        ChatSession(makeChatModel(systemInstruction).startChat(history))

    suspend fun generateJson(prompt: String, schema: Schema): String {
        val model = firebaseAi.generativeModel(
            modelName = NovaGenerationConfig.modelName,
            generationConfig = generationConfig {
                temperature = 0.2f
                maxOutputTokens = 1024
                responseMimeType = "application/json"
                responseSchema = schema
                thinkingConfig = com.google.firebase.ai.type.thinkingConfig { thinkingBudget = 256 }
            },
            safetySettings = NovaGenerationConfig.safetySettings,
        )
        return model.generateContent(prompt).text ?: "{}"
    }

    suspend fun generateTitle(prompt: String): String =
        makeUtilityModel(NovaGenerationConfig.titleGeneration).generateContent(prompt).text?.trim().orEmpty()

    suspend fun compactHistory(transcript: String): String {
        val prompt = "${NovaPromptCatalog.historyCompactionPrompt}\n\n$transcript"
        return makeUtilityModel(NovaGenerationConfig.structuredJson).generateContent(prompt).text ?: transcript
    }

    fun userParts(text: String, image: Bitmap?, memoryContext: String? = null): List<NovaPart> = buildList {
        if (!memoryContext.isNullOrEmpty()) add(NovaPart.Text("[Additional relevant memory about the user for this message — use naturally, never mention this block:\n$memoryContext]"))
        add(NovaPart.Text(text))
        image?.let { add(NovaPart.Image(it)) }
    }

    class ChatSession internal constructor(private val chat: Chat) {
        val history: List<Content> get() = chat.history

        suspend fun sendMessage(messages: List<NovaModelContent>): NovaResponse =
            chat.sendMessage(messages.map(::toFirebaseContent).single()).toNovaResponse()

        fun sendMessageStream(messages: List<NovaModelContent>): Flow<NovaResponse> =
            chat.sendMessageStream(messages.map(::toFirebaseContent).single()).map { it.toNovaResponse() }

        suspend fun sendFunctionResponses(responses: List<FunctionResponse>): NovaResponse =
            chat.sendMessage(content("function") {
                responses.forEach { part(FunctionResponsePart(it.name, it.payload.toJsonObject(), it.id)) }
            }).toNovaResponse()
    }

    data class NovaModelContent(val role: String, val parts: List<NovaPart>) {
        companion object {
            fun user(parts: List<NovaPart>) = NovaModelContent("user", parts)
            fun userText(text: String) = user(listOf(NovaPart.Text(text)))
            fun modelText(text: String) = NovaModelContent("model", listOf(NovaPart.Text(text)))
        }
    }

    sealed interface NovaPart {
        data class Text(val value: String) : NovaPart
        data class Image(val bitmap: Bitmap) : NovaPart
    }

    data class FunctionDeclaration(val name: String, val description: String, val parameters: Map<String, FunctionParameter>, val optionalParameters: Set<String>)
    data class FunctionParameter(val type: ParameterType, val description: String)
    enum class ParameterType { STRING, INTEGER, BOOLEAN }
    data class FunctionCall(val name: String, val arguments: Map<String, Any?>, val id: String? = null)
    data class FunctionResponse(val name: String, val payload: Map<String, Any?>, val id: String? = null)
    data class GroundingChunk(val title: String?, val url: String?)
    data class GroundingMetadata(val chunks: List<GroundingChunk>, val searchEntryPointHtml: String?)
    data class NovaResponse(val text: String?, val functionCalls: List<FunctionCall>, val groundingMetadata: GroundingMetadata?)

    private fun toFirebaseDeclaration(declaration: FunctionDeclaration): FirebaseFunctionDeclaration = FirebaseFunctionDeclaration(
        declaration.name,
        declaration.description,
        declaration.parameters.mapValues { (_, parameter) ->
            when (parameter.type) {
                ParameterType.STRING -> Schema.string(parameter.description)
                ParameterType.INTEGER -> Schema.integer(parameter.description)
                ParameterType.BOOLEAN -> Schema.boolean(parameter.description)
            }
        },
        declaration.optionalParameters.toList(),
    )

    private fun toFirebaseContent(model: NovaModelContent): Content = content(model.role) {
        model.parts.forEach {
            when (it) {
                is NovaPart.Text -> text(it.value)
                is NovaPart.Image -> image(it.bitmap)
            }
        }
    }

    private fun GenerateContentResponse.toNovaResponse(): NovaResponse {
        val metadata = candidates.firstOrNull()?.groundingMetadata?.let { grounding ->
            GroundingMetadata(
                chunks = grounding.groundingChunks.map { GroundingChunk(it.web?.title, it.web?.uri) },
                searchEntryPointHtml = grounding.searchEntryPoint?.renderedContent,
            )
        }
        return NovaResponse(
            text = text,
            functionCalls = functionCalls.map { FunctionCall(it.name, it.args.toAnyMap(), it.id) },
            groundingMetadata = metadata,
        )
    }

    private fun Map<String, Any?>.toJsonObject() = JsonObject(mapValues { (_, value) -> value.toJsonElement() })
    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Map<*, *> -> JsonObject(entries.mapNotNull { (key, value) -> (key as? String)?.let { it to value.toJsonElement() } }.toMap())
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }
    private fun Map<String, JsonElement>.toAnyMap(): Map<String, Any?> = mapValues { (_, value) -> value.toAny() }
    private fun JsonElement.toAny(): Any? = when (this) {
        JsonNull -> null
        is JsonObject -> toAnyMap()
        is JsonArray -> map { it.toAny() }
        is JsonPrimitive -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
    }
}
