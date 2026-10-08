package com.moments.android.services.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Correos de cuenta con las plantillas propias (Cloud Function `sendAccountEmail`, en el idioma de la app).
 * Si la función falla, se envía el correo estándar de Firebase Auth para no dejar al usuario sin él (≡ iOS).
 */
object AccountEmailService {
    private val functions by lazy { FirebaseFunctions.getInstance("europe-southwest1") }

    private val locale: String
        get() = Locale.getDefault().toLanguageTag()

    suspend fun sendVerification(user: FirebaseUser, name: String? = null) {
        val payload = mutableMapOf<String, Any>("kind" to "verify", "locale" to locale)
        if (!name.isNullOrBlank()) payload["name"] = name
        try {
            functions.getHttpsCallable("sendAccountEmail").call(payload).await()
        } catch (_: Exception) {
            user.sendEmailVerification().await()
        }
    }

    suspend fun sendPasswordReset(email: String) {
        val clean = email.trim()
        try {
            functions.getHttpsCallable("sendAccountEmail")
                .call(mapOf("kind" to "reset", "email" to clean, "locale" to locale))
                .await()
        } catch (_: Exception) {
            FirebaseAuth.getInstance().sendPasswordResetEmail(clean).await()
        }
    }
}
