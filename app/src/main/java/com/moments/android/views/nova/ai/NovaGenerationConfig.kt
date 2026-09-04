package com.moments.android.views.nova.ai

import com.google.firebase.ai.type.HarmBlockThreshold
import com.google.firebase.ai.type.HarmCategory
import com.google.firebase.ai.type.SafetySetting
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig

/** Port de `Views/Nova/AI/NovaGenerationConfig.swift`. */
object NovaGenerationConfig {
    const val modelName = "gemini-3.1-flash-lite"
    const val location = "global"

    val chat = generationConfig {
        temperature = 0.7f
        topP = 0.95f
        topK = 40
        maxOutputTokens = 2048
        thinkingConfig = thinkingConfig {
            thinkingBudget = 512
        }
    }

    /** ≡ iOS `structuredJSON`. */
    val structuredJson = generationConfig {
        temperature = 0.2f
        maxOutputTokens = 1024
        responseMimeType = "application/json"
        thinkingConfig = thinkingConfig {
            thinkingBudget = 0
        }
    }

    val titleGeneration = generationConfig {
        temperature = 0.3f
        maxOutputTokens = 32
        thinkingConfig = thinkingConfig {
            thinkingBudget = 0
        }
    }

    val welcomeSpark = generationConfig {
        temperature = 1.25f
        topP = 0.95f
        topK = 64
        maxOutputTokens = 64
        thinkingConfig = thinkingConfig {
            thinkingBudget = 0
        }
    }

    val safetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.MEDIUM_AND_ABOVE),
        SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.MEDIUM_AND_ABOVE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.MEDIUM_AND_ABOVE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.MEDIUM_AND_ABOVE),
    )
}
