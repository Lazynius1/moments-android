package com.moments.android.services.social

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.moments.android.services.firestore.fetchUser

/** Port de BestFriendsService.swift. */
class BestFriendsService(
    private val firestoreService: FirestoreService = FirestoreService(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val functionsRegion = "europe-southwest1"
    private val bestFriendsOptOutFunctionName = "optOutBestFriends"

    suspend fun addBestFriend(currentUserId: String, friendId: String) {
        db.collection("users").document(currentUserId)
            .update("bestFriends", FieldValue.arrayUnion(friendId)).await()
    }

    suspend fun removeBestFriend(currentUserId: String, friendId: String) {
        db.collection("users").document(currentUserId)
            .update("bestFriends", FieldValue.arrayRemove(friendId)).await()
    }

    suspend fun optOutFromBestFriends(ofOwnerId: String) {
        require(ofOwnerId.isNotEmpty()) { "ownerId vacío" }
        val currentUser = FirebaseAuth.getInstance().currentUser
            ?: error("Usuario no autenticado")
        val projectId = FirebaseApp.getInstance().options.projectId
            ?: error("No se pudo construir la URL de Cloud Function")
        val url = URL("https://$functionsRegion-$projectId.cloudfunctions.net/$bestFriendsOptOutFunctionName")
        val token = currentUser.getIdToken(false).await().token
            ?: error("No se pudo obtener ID token")

        withContextIo {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            try {
                connection.outputStream.use {
                    it.write(JSONObject(mapOf("ownerId" to ofOwnerId)).toString().toByteArray())
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val err = runCatching {
                        JSONObject(connection.errorStream.bufferedReader().readText()).optString("error")
                    }.getOrNull()
                    error(err?.takeIf { it.isNotEmpty() } ?: "Error del servidor ($code)")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun fetchBestFriends(userId: String): List<AppUser> = coroutineScope {
        val snap = db.collection("users").document(userId).get().await()
        if (!snap.exists()) return@coroutineScope emptyList()
        @Suppress("UNCHECKED_CAST")
        val data = snap.data as Map<String, Any?>
        val user = AppUser.from(snap.id, data)
        if (user.bestFriends.isEmpty()) return@coroutineScope emptyList()
        user.bestFriends.map { friendId ->
            async {
                runCatching { firestoreService.fetchUser(friendId) }.getOrNull()
                    ?.takeIf { it.isActive }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun <T> withContextIo(block: () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
}
