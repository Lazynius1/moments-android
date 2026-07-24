package com.moments.android.views.messaging.services

import com.google.firebase.functions.FirebaseFunctions

enum class ViewOnceConsumptionReason(val raw: String) {
    VIEW_ONCE("viewOnce"),
    REPLAY("replay"),
    ABANDON_REPLAY("abandonReplay");
}

/** Contrato Android de `consumeViewOnceMessage` en europe-southwest1. */
object ViewOnceConsumptionService {
    private val functions by lazy { FirebaseFunctions.getInstance("europe-southwest1") }

    fun consume(
        conversationId: String,
        messageId: String,
        reason: ViewOnceConsumptionReason,
        completion: (Exception?) -> Unit,
    ) {
        functions
            .getHttpsCallable("consumeViewOnceMessage")
            .call(mapOf("conversationId" to conversationId, "messageId" to messageId, "reason" to reason.raw))
            .addOnSuccessListener { completion(null) }
            .addOnFailureListener { completion(it as? Exception ?: Exception(it)) }
    }
}
