package com.moments.android.services.auth

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Consultas de login sin sesión vía Cloud Functions (`resolveLoginEmail`,
 * `checkEmailAvailable`) en vez de leer el email del índice público `usernames`.
 * Si la función no responde (sin desplegar, caída), se usa el índice como respaldo
 * mientras siga guardando el email y admitiendo consultas. Port de iOS `AuthService`.
 */
object AuthLookupService {
    sealed interface EmailLookup {
        data class Found(val email: String) : EmailLookup
        data object NotFound : EmailLookup
        data object RateLimited : EmailLookup
    }

    private val functions by lazy { FirebaseFunctions.getInstance("europe-southwest1") }

    suspend fun resolveLoginEmail(username: String): EmailLookup {
        val clean = username.trim().lowercase()
        try {
            val data = functions.getHttpsCallable("resolveLoginEmail")
                .call(mapOf("username" to clean))
                .await()
                .getData() as? Map<*, *>
            val email = data?.get("email") as? String
            if (!email.isNullOrEmpty()) return EmailLookup.Found(email)
        } catch (e: FirebaseFunctionsException) {
            when (e.code) {
                FirebaseFunctionsException.Code.NOT_FOUND,
                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> return EmailLookup.NotFound
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> return EmailLookup.RateLimited
                else -> Unit
            }
        } catch (_: Exception) {
            // Respaldo con el índice.
        }

        val legacyEmail = FirebaseFirestore.getInstance()
            .collection("usernames")
            .document(clean)
            .get()
            .await()
            .getString("email")
        return if (legacyEmail.isNullOrEmpty()) EmailLookup.NotFound else EmailLookup.Found(legacyEmail)
    }

    /** ¿Hay ya una cuenta con este correo? Ante cualquier fallo devuelve false (no bloquea el alta). */
    suspend fun isEmailRegistered(email: String): Boolean {
        val clean = email.trim()
        if (clean.isEmpty()) return false
        try {
            val data = functions.getHttpsCallable("checkEmailAvailable")
                .call(mapOf("email" to clean))
                .await()
                .getData() as? Map<*, *>
            val available = data?.get("available") as? Boolean
            if (available != null) return !available
        } catch (_: Exception) {
            // Respaldo con el índice.
        }
        return runCatching {
            !FirebaseFirestore.getInstance()
                .collection("usernames")
                .whereEqualTo("email", clean)
                .limit(1)
                .get()
                .await()
                .isEmpty
        }.getOrDefault(false)
    }
}
