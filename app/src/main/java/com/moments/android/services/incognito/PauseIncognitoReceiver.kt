package com.moments.android.services.incognito

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moments.android.activities.IncognitoLiveUpdateNotificationHelper

/**
 * ≡ PauseIncognitoIntent (LiveActivityIntent iOS).
 * Acción de la notificación Live Update → pauseFromLiveActivity().
 */
class PauseIncognitoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_PAUSE) return
        IncognitoModeService.initialize(context.applicationContext)
        // Cerrar Live Update de inmediato (≡ activity.end dismissalPolicy: .immediate).
        IncognitoLiveUpdateNotificationHelper.cancel(context.applicationContext)
        IncognitoModeService.pauseFromLiveActivity()
    }

    companion object {
        const val ACTION_PAUSE = "com.moments.android.action.PAUSE_INCOGNITO_LIVE"
    }
}
