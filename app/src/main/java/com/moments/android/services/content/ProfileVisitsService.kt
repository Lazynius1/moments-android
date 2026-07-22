package com.moments.android.services.content

import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.AppUser
import com.moments.android.models.GroupedVisit
import com.moments.android.models.Visit
import com.moments.android.models.VisitGrouping
import com.moments.android.services.firestore.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import com.moments.android.services.firestore.fetchUser

/** Port de ProfileVisitsService.swift. */
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
                        visits += Visit(
                            id = v.optString("id").takeIf { it.isNotEmpty() },
                            visitorId = visitorId,
                            timestamp = Date(v.getDouble("timestamp").toLong()),
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
            val visits = fetchVisits(userId)
            buildGroupedVisits(visits)
        }.getOrDefault(emptyList())
    }

    private suspend fun buildGroupedVisits(visits: List<Visit>): List<GroupedVisit> = coroutineScope {
        val ids = VisitGrouping.uniqueVisitorIds(visits)
        if (ids.isEmpty()) return@coroutineScope emptyList()
        val users = ids.map { id ->
            async { runCatching { firestoreService.fetchUser(id) }.getOrNull() }
        }.awaitAll().filterNotNull()
        VisitGrouping.build(visits, users)
    }

    private suspend fun fetchVisits(userId: String): List<Visit> {
        val snap = FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("visits")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1000)
            .get()
            .await()
        return snap.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val visitorId = data["visitorId"] as? String ?: return@mapNotNull null
            val ts = (data["timestamp"] as? Timestamp)?.toDate() ?: Date()
            Visit(id = doc.id, visitorId = visitorId, timestamp = ts)
        }
    }
}
