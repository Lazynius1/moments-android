package com.moments.android.views.nova.tools

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Port de `NovaEvents` (final de `NovaActivityTools.swift`).
 * ≡ NotificationCenter `NovaEchoSparkTriggered` con userInfo echoId/userId.
 */
object NovaEvents {
    data class EchoSpark(val echoId: String, val userId: String)

    private val _echoSpark = MutableSharedFlow<EchoSpark>(extraBufferCapacity = 8)
    val echoSpark: SharedFlow<EchoSpark> = _echoSpark.asSharedFlow()

    fun triggerEchoSpark(echoId: String, userId: String) {
        _echoSpark.tryEmit(EchoSpark(echoId = echoId, userId = userId))
    }
}
