package com.moments.android.views.messaging.services

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

enum class ViewOnceConsumptionReason(val raw: String) {
    VIEW_ONCE("viewOnce"),
    REPLAY("replay"),
    ABANDON_REPLAY("abandonReplay");
}

/** Contrato Android de `consumeViewOnceMessage` en europe-southwest1. */
object ViewOnceConsumptionService {
    private const val TAG = "ViewOnceConsume"
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
            .addOnFailureListener { error ->
                Log.e(TAG, "consumeViewOnceMessage failed reason=${reason.raw} msg=$messageId", error)
                completion(error as? Exception ?: Exception(error))
            }
    }

    /** Suspend wrapper — permite await de [markViewOnceAsViewed] antes de replay/abandon. */
    suspend fun consumeAwait(
        conversationId: String,
        messageId: String,
        reason: ViewOnceConsumptionReason,
    ): Exception? = suspendCancellableCoroutine { cont ->
        consume(conversationId, messageId, reason) { error ->
            if (cont.isActive) cont.resume(error)
        }
    }
}
