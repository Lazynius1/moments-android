package com.moments.android.views.login

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Registro por email: crea la cuenta en Auth, sube la foto a Storage (misma ruta/campo que iOS)
 * y escribe el perfil completo en Firestore. Equivalente a AuthService.register(...) de iOS.
 */
suspend fun completeEmailRegistration(
    context: Context,
    username: String,
    email: String,
    password: String,
    interests: List<String>,
    photoUri: Uri?,
) {
    val firestore = FirebaseFirestore.getInstance()
    val usernameLower = username.lowercase()
    val usernameRef = firestore.collection("usernames").document(usernameLower)
    if (usernameRef.get().await().exists()) throw UsernameTakenException()

    val authResult = FirebaseAuth.getInstance()
        .createUserWithEmailAndPassword(email.trim(), password).await()
    val user = authResult.user ?: throw ProfileCreationException()

    try {
        val profileImagePath = photoUri?.let { runCatching { uploadProfileImage(context, user.uid, it) }.getOrNull() }

        val now = FieldValue.serverTimestamp()
        val profile = buildProfileMap(user.uid, usernameLower, email.trim(), interests, profileImagePath, now)
        val index = hashMapOf<String, Any>(
            "userId" to user.uid,
            "email" to email.trim(),
            "createdAt" to now,
            "updatedAt" to now,
        )
        firestore.runBatch { batch ->
            batch.set(firestore.collection("users").document(user.uid), profile)
            batch.set(usernameRef, index)
        }.await()
        user.sendEmailVerification()
    } catch (error: Exception) {
        runCatching { user.delete().await() }
        throw ProfileCreationException()
    }
}

private fun buildProfileMap(
    uid: String,
    usernameLower: String,
    email: String,
    interests: List<String>,
    profileImagePath: String?,
    now: Any,
): HashMap<String, Any> {
    val notificationPreferences = hashMapOf(
        "like" to true,
        "newFollower" to true,
        "followRequest" to true,
        "mutualConnection" to true,
        "comment" to true,
        "storyReaction" to true,
        "gentleReminders" to true,
        "commentsMutualsOnly" to false,
        "muteOldPostReactions" to false,
    )
    val profile = hashMapOf<String, Any>(
        "id" to uid,
        "username" to usernameLower,
        "email" to email,
        "interests" to interests,
        "isPlusSubscriber" to false,
        "blockedUsers" to emptyList<String>(),
        "isPrivate" to false,
        "showMutuals" to true,
        "showFollowing" to true,
        "showFollowers" to true,
        "notificationPreferences" to notificationPreferences,
        "bestFriends" to emptyList<String>(),
        "isActive" to true,
        "isSuspended" to false,
        "ownedBadges" to emptyList<String>(),
        "showBadge" to true,
        "showPlusBadge" to true,
        "isVerified" to false,
        "onlineStatus" to "offline",
        "isOnline" to false,
        "createdAt" to now,
        "updatedAt" to now,
    )
    if (profileImagePath != null) profile["profileImagePath"] = profileImagePath
    return profile
}

private suspend fun uploadProfileImage(context: Context, uid: String, uri: Uri): String = withContext(Dispatchers.IO) {
    val jpeg = jpegBytes(context, uri) ?: throw ProfileCreationException()
    val path = "users/$uid/profile/avatar/${UUID.randomUUID()}.jpg"
    val ref = FirebaseStorage.getInstance().reference.child(path)
    val metadata = storageMetadata {
        contentType = "image/jpeg"
        setCustomMetadata("ownerId", uid)
        setCustomMetadata("type", "profile_picture")
    }
    ref.putBytes(jpeg, metadata).await()
    ref.downloadUrl.await().toString()
}

/** Decodifica, reescala a máx. 1080px y comprime a JPEG 0.75 — igual que iOS. */
private fun jpegBytes(context: Context, uri: Uri): ByteArray? {
    val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
    val maxDim = 1080
    val scale = maxOf(original.width, original.height).let { if (it > maxDim) maxDim.toFloat() / it else 1f }
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
    } else {
        original
    }
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
        out.toByteArray()
    }
}
