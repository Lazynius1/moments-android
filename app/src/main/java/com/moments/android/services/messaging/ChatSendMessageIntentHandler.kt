package com.moments.android.services.messaging

/**
 * N/A — stub documentando equivalencia iOS → Android.
 *
 * iOS: `ChatSendMessageIntentHandler` implementa `INSendMessageIntentHandling` para responder
 * desde Communication Notifications / Siri con `ChatService.sendTextMessage`.
 *
 * Android: las respuestas inline se implementan con:
 * - `NotificationCompat.Action` + `RemoteInput` en el handler de FCM/local notifications
 * - `ChatNotificationReplyReceiver` (declarado en el manifest) que llama a `ChatService.sendTextMessage`
 *
 * Es decir: la funcionalidad SÍ existe en Android, sólo que no vía Intents framework.
 *
 * No hay Intents framework equivalente; este archivo existe solo como ancla de documentación.
 */
object ChatSendMessageIntentHandler {
    const val NOT_APPLICABLE_ON_ANDROID =
        "INSendMessageIntent no existe en Android; usar RemoteInput en acciones de notificación."
}
