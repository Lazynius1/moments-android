package com.moments.android.services.network

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * POST autenticado a las Cloud Functions, con el mismo contrato que iOS: HTTP directo a
 * `https://{region}-{projectId}.cloudfunctions.net/{function}` con `Authorization: Bearer <idToken>`
 * (la app NO usa el SDK `httpsCallable`, ni en iOS ni aquí).
 *
 * Extraído para no seguir replicando el bloque `HttpURLConnection` + `getIdToken` en cada servicio.
 * Los servicios ya portados mantienen su copia; migrarlos es limpieza aparte.
 */
object CloudFunctionsClient {
    const val DEFAULT_REGION = "europe-southwest1"

    class NotAuthenticatedException : IllegalStateException("cloud_functions_not_authenticated")

    class BackendException(val statusCode: Int, message: String) : IllegalStateException(message)

    /** Respuesta cruda. Lanza [NotAuthenticatedException] o [BackendException] si no es 200. */
    suspend fun post(
        function: String,
        payload: JSONObject,
        region: String = DEFAULT_REGION,
        timeoutMs: Int = 20_000,
    ): String = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: throw NotAuthenticatedException()
        val token = user.getIdToken(false).await().token ?: throw NotAuthenticatedException()
        val projectId = FirebaseApp.getInstance().options.projectId
            ?: throw IllegalStateException("Missing Firebase project ID")

        val connection = URL("https://$region-$projectId.cloudfunctions.net/$function")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true
            connection.outputStream.use { it.write(payload.toString().toByteArray()) }

            val code = connection.responseCode
            val text = (if (code == HttpURLConnection.HTTP_OK) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code != HttpURLConnection.HTTP_OK) throw BackendException(code, "Backend error $code")
            text
        } finally {
            connection.disconnect()
        }
    }

    /** Igual que [post] pero devolviendo el JSON ya parseado. */
    suspend fun postJson(
        function: String,
        payload: JSONObject,
        region: String = DEFAULT_REGION,
        timeoutMs: Int = 20_000,
    ): JSONObject = JSONObject(post(function, payload, region, timeoutMs))

    /** Para endpoints cuya respuesta no se usa. */
    suspend fun postVoid(
        function: String,
        payload: JSONObject,
        region: String = DEFAULT_REGION,
        timeoutMs: Int = 20_000,
    ) {
        post(function, payload, region, timeoutMs)
    }
}
