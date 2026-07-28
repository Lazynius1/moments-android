package com.moments.android.services.content

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.extensions.optStringOrNull
import com.moments.android.models.GroupedVisit
import com.moments.android.models.Visit
import com.moments.android.models.VisitGrouping
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchVisits
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Port de `ProfileVisitsService.swift`.
 * CF `getProfileVisitsPage` → fallback Firestore `users/{id}/visits`.
 */
object ProfileVisitsService {
    private val firestoreService = FirestoreService()

    suspend fun fetchGroupedVisits(userId: String, limit: Int = 1000): List<GroupedVisit> {
        fetchFromFunction(limit)?.let { return it }
        return fetchFromFirestore(userId)
    }

    private suspend fun fetchFromFunction(limit: Int): List<GroupedVisit>? = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
        runCatching {
            val token = user.getIdToken(false).await().token ?: return@runCatching null
            val projectId = FirebaseApp.getInstance().options.projectId ?: return@runCatching null
            val url = URL("https://europe-southwest1-$projectId.cloudfunctions.net/getProfileVisitsPage")
            val c = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                doOutput = true
                connectTimeout = 20_000
                readTimeout = 20_000
            }
            try {
                c.outputStream.use { it.write(JSONObject().put("limit", limit).toString().toByteArray()) }
                if (c.responseCode != 200) return@runCatching null
                val json = JSONObject(c.inputStream.bufferedReader().readText())
                val grouped = json.getJSONArray("groupedVisits")
                val visits = mutableListOf<Visit>()
                for (i in 0 until grouped.length()) {
                    val g = grouped.getJSONObject(i)
                    val visitorId = g.getString("visitorId")
                    val arr = g.getJSONArray("visits")
                    for (j in 0 until arr.length()) {
                        val v = arr.getJSONObject(j)
                        // iOS: Date(timeIntervalSince1970: timestamp / 1000) — epoch millis.
                        val epochMs = v.getDouble("timestamp")
                        visits += Visit(
                            id = v.optStringOrNull("id"),
                            visitorId = visitorId,
                            timestamp = Date(epochMs.toLong()),
                        )
                    }
                }
                buildGroupedVisits(visits)
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    private suspend fun fetchFromFirestore(userId: String): List<GroupedVisit> {
        return runCatching {
            val visits = firestoreService.fetchVisits(userId)
            buildGroupedVisits(visits)
        }.getOrDefault(emptyList())
    }

    private suspend fun buildGroupedVisits(visits: List<Visit>): List<GroupedVisit> {
        val ids = VisitGrouping.uniqueVisitorIds(visits)
        if (ids.isEmpty()) return emptyList()
        val users = firestoreService.fetchUsersAsync(ids)
        return VisitGrouping.build(visits, users)
    }
}
