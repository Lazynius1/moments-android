package com.moments.android.services.messaging

import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

class GroupChatAPIException(val serverCode: String) : Exception(serverCode)

object GroupChatAPI {
    suspend fun request(endpoint: String, body: Map<String, Any?>): JSONObject {
        val user = FirebaseAuth.getInstance().currentUser ?: error("Unauthenticated")
        val token = user.getIdToken(false).await().token ?: error("Unauthenticated")
        val project = FirebaseApp.getInstance().options.projectId ?: error("Missing project")
        return withContext(Dispatchers.IO) {
            val connection = URL("https://europe-southwest1-$project.cloudfunctions.net/$endpoint").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"; connection.connectTimeout = 20000; connection.readTimeout = 60000; connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token"); connection.setRequestProperty("Content-Type", "application/json")
                @Suppress("UNCHECKED_CAST") val json = JSONObject(jsonValue(body) as Map<String, Any?>)
                connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                check(FirebaseAuth.getInstance().currentUser?.uid == user.uid)
                val status = connection.responseCode
                val payload = (if (status == 200) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status != 200) {
                    val code = runCatching { JSONObject(payload).optString("error", "failed") }.getOrDefault("failed")
                    throw GroupChatAPIException(code)
                }
                JSONObject(payload)
            } finally { connection.disconnect() }
        }
    }
    private fun jsonValue(value: Any?): Any? = when (value) {
        is FieldValue -> null
        is ByteArray -> android.util.Base64.encodeToString(value, android.util.Base64.NO_WRAP)
        is com.google.firebase.firestore.Blob -> android.util.Base64.encodeToString(value.toBytes(), android.util.Base64.NO_WRAP)
        is Timestamp -> value.toDate().time
        is Date -> value.time
        is Map<*, *> -> value.entries.mapNotNull { (key, entry) -> jsonValue(entry)?.let { key.toString() to it } }.toMap()
        is List<*> -> value.mapNotNull(::jsonValue)
        else -> value
    }
}
