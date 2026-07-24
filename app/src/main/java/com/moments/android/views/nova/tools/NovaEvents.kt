package com.moments.android.views.nova.tools

/** Android event counterpart of `NovaEvents.triggerEchoSpark`. */
object NovaEvents {
    fun triggerEchoSpark(echoId: String, userId: String) {
        android.util.Log.d("NovaEvents", "triggerEchoSpark echoId=$echoId userId=$userId")
    }
}
