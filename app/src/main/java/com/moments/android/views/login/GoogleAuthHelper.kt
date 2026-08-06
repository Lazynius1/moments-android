package com.moments.android.views.login

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.moments.android.ad.findActivity
import com.moments.android.services.auth.AuthService

private const val TAG = "GoogleAuthHelper"

// Web client (type 3) del proyecto Firebase de Moments, tomado de google-services.json.
private const val GOOGLE_WEB_CLIENT_ID =
    "543287427123-jqme1m9j9ubrnt7hc37hse2dd7ibl7hu.apps.googleusercontent.com"

/**
 * Obtiene idToken de Google vía Credential Manager y delega en [AuthService.signInWithGoogle]
 * (≡ flujo social iOS / Sign in with Apple → check perfil → login o onboarding).
 *
 * @return true si el usuario ya tenía perfil completo; false si requiere onboarding social.
 */
suspend fun signInWithGoogle(context: Context): Boolean {
    AuthService.initialize(context.applicationContext)
    val idToken = obtainGoogleIdToken(context)
    return AuthService.signInWithGoogle(idToken)
}

/** ≡ Sign in with Apple para vincular — obtiene idToken y llama [AuthService.linkWithGoogle]. */
suspend fun linkGoogleAccount(context: Context) {
    AuthService.initialize(context.applicationContext)
    AuthService.linkWithGoogle(obtainGoogleIdToken(context))
}

/** ≡ reauthenticateWithApple — Credential Manager + [AuthService.reauthenticateWithGoogle]. */
suspend fun reauthenticateWithGoogle(context: Context) {
    AuthService.initialize(context.applicationContext)
    AuthService.reauthenticateWithGoogle(obtainGoogleIdToken(context))
}

/**
 * Credential Manager exige [Activity]. Primero intenta el flujo silencioso/One Tap
 * ([GetGoogleIdOption]); si no hay cuentas autorizadas disponibles, cae al botón
 * clásico ([GetSignInWithGoogleOption]) — sin ese fallback la beta falla en muchos
 * dispositivos con [NoCredentialException].
 */
private suspend fun obtainGoogleIdToken(context: Context): String {
    val activity: Activity = context.findActivity()
        ?: error("Google Sign-In requiere una Activity")

    val credentialManager = CredentialManager.create(activity)
    val response = try {
        credentialManager.getCredential(
            context = activity,
            request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                        .setAutoSelectEnabled(false)
                        .build(),
                )
                .build(),
        )
    } catch (cancellation: GetCredentialCancellationException) {
        throw cancellation
    } catch (noCredential: NoCredentialException) {
        Log.i(TAG, "GetGoogleIdOption sin credenciales; fallback a Sign in with Google", noCredential)
        credentialManager.getCredential(
            context = activity,
            request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetSignInWithGoogleOption.Builder(GOOGLE_WEB_CLIENT_ID).build(),
                )
                .build(),
        )
    } catch (error: Exception) {
        // Algunos OEM envuelven NoCredentialException; reintentar el botón clásico.
        val isNoCredential = generateSequence(error as Throwable?) { it.cause }
            .any { it is NoCredentialException }
        if (!isNoCredential) {
            Log.e(TAG, "GetGoogleIdOption falló", error)
            throw error
        }
        Log.i(TAG, "GetGoogleIdOption sin credenciales (wrapped); fallback a Sign in with Google", error)
        credentialManager.getCredential(
            context = activity,
            request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetSignInWithGoogleOption.Builder(GOOGLE_WEB_CLIENT_ID).build(),
                )
                .build(),
        )
    }

    val credential = response.credential
    check(
        credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
    ) {
        "Credencial de Google inválida (${credential::class.java.simpleName})"
    }
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}

/** true si el usuario cerró el sheet de Google (no mostrar error genérico). */
fun isGoogleSignInUserCancellation(error: Throwable): Boolean =
    generateSequence(error as Throwable?) { it.cause }
        .any {
            it is GetCredentialCancellationException ||
                it.message?.contains("cancel", ignoreCase = true) == true
        }

