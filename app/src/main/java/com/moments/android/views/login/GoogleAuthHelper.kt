package com.moments.android.views.login

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.moments.android.services.auth.AuthService

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

private suspend fun obtainGoogleIdToken(context: Context): String {
    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(GOOGLE_WEB_CLIENT_ID)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
    val response = credentialManager.getCredential(context, request)

    val credential = response.credential
    check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        "Credencial de Google inválida"
    }
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}
