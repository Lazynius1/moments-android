package com.moments.android.views.login

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// Web client (type 3) del proyecto Firebase de Moments, tomado de google-services.json.
private const val GOOGLE_WEB_CLIENT_ID =
    "543287427123-jqme1m9j9ubrnt7hc37hse2dd7ibl7hu.apps.googleusercontent.com"

/**
 * Inicia sesión con Google vía Credential Manager y crea el perfil en Firestore
 * si es la primera vez que el usuario entra (equivalente simplificado al flujo
 * de Sign in with Apple + completado de perfil social en iOS).
 */
suspend fun signInWithGoogle(context: Context) {
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
    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
    val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
    val user = authResult.user ?: return
    ensureUserProfile(uid = user.uid, displayName = googleIdTokenCredential.displayName, email = user.email)
}

private suspend fun ensureUserProfile(uid: String, displayName: String?, email: String?) {
    val firestore = FirebaseFirestore.getInstance()
    val userDoc = firestore.collection("users").document(uid)
    if (userDoc.get().await().exists()) return

    val baseUsername = (displayName ?: email?.substringBefore("@") ?: "user")
        .lowercase()
        .filter { it.isLetterOrDigit() || it == '_' }
        .ifBlank { "user" }
        .take(20)

    val usernames = firestore.collection("usernames")
    var candidate = baseUsername
    var suffix = 0
    while (usernames.document(candidate).get().await().exists()) {
        suffix += 1
        candidate = "$baseUsername$suffix"
    }

    val now = FieldValue.serverTimestamp()
    val profile = hashMapOf<String, Any>(
        "id" to uid,
        "username" to candidate,
        "email" to (email ?: ""),
        "interests" to emptyList<String>(),
        "isActive" to true,
        "isSuspended" to false,
        "isPrivate" to false,
        "blockedUsers" to emptyList<String>(),
        "bestFriends" to emptyList<String>(),
        "createdAt" to now,
        "updatedAt" to now,
    )
    val index = hashMapOf<String, Any>(
        "userId" to uid,
        "email" to (email ?: ""),
        "createdAt" to now,
        "updatedAt" to now,
    )
    firestore.runBatch { batch ->
        batch.set(userDoc, profile)
        batch.set(usernames.document(candidate), index)
    }.await()
}
