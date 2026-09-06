package com.moments.android.services.content

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.services.incognito.IncognitoModeService
import com.moments.android.services.social.AffinityTracker
import com.moments.android.services.social.AffinityInteractionType
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ForYouPreferences {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val visibilityJobs = mutableMapOf<String, Job>()
    var revision by mutableIntStateOf(0)
        private set
    var isBusy by mutableStateOf(false)
        private set
    var undoMoment by mutableStateOf<FeedMoment?>(null)
        private set
    var notice by mutableStateOf<Int?>(null)
        private set
    private var noticeOwner: String? = null
    private var noticeDismissJob: Job? = null
    private const val NOTICE_DURATION_MS = 5_000L
    private fun owner() = FirebaseAuth.getInstance().currentUser?.uid
    private fun prefs(uid: String) = FirebaseApp.getInstance().applicationContext
        .getSharedPreferences("forYou.$uid", Context.MODE_PRIVATE)
    fun momentKey(moment: FeedMoment) = "${moment.authorId}/${moment.id}"
    fun hiddenKeys(): Set<String> {
        @Suppress("UNUSED_VARIABLE") val observed = revision
        return owner()?.let { prefs(it).getStringSet("hidden", emptySet())?.toSet() } ?: emptySet()
    }
    fun visibleNotice(): Int? = notice.takeIf { noticeOwner == owner() }
    fun seenMoments(): Map<String, Double> {
        val uid = owner() ?: return emptyMap()
        val json = runCatching { JSONObject(prefs(uid).getString("seen", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val cutoff = System.currentTimeMillis() - 30L * 86_400_000
        return json.keys().asSequence().associateWith { json.optDouble(it, 0.0) }.filterValues { it > cutoff }
    }
    fun updateVisibility(moments: List<FeedMoment>, fractions: Map<String, Float>, enabled: Boolean) {
        val uid = owner() ?: return
        val visible = if (enabled && !IncognitoModeService.isActiveSnapshot)
            moments.filter { (fractions[it.id] ?: 0f) >= 0.5f } else emptyList()
        val keys = visible.map(::momentKey).toSet()
        visibilityJobs.keys.toList().filterNot { it in keys }.forEach { visibilityJobs.remove(it)?.cancel() }
        visible.forEach { moment ->
            val key = momentKey(moment)
            if (moment.id.isBlank() || visibilityJobs.containsKey(key)) return@forEach
            visibilityJobs[key] = scope.launch {
                delay(1500)
                if (owner() != uid || IncognitoModeService.isActiveSnapshot) return@launch
                val previous = seenMoments()
                val now = System.currentTimeMillis().toDouble()
                if (now - (previous[key] ?: 0.0) > 86_400_000) {
                    AffinityTracker.trackInteraction(AffinityInteractionType.MOMENT_VIEW, moment.authorId)
                }
                val seen = (previous + (key to now))
                    .entries.sortedByDescending { it.value }.take(500).associate { it.key to it.value }
                prefs(uid).edit().putString("seen", JSONObject(seen).toString()).apply()
            }
        }
    }
    fun clearVisibility() {
        visibilityJobs.values.forEach { it.cancel() }
        visibilityJobs.clear()
    }
    fun hide(moment: FeedMoment) = send(moment, true)
    fun undo() { undoMoment?.let { send(it, false) } }
    fun dismissNotice() {
        if (isBusy) return
        noticeDismissJob?.cancel()
        noticeDismissJob = null
        notice = null
        undoMoment = null
    }

    private fun scheduleNoticeDismiss() {
        noticeDismissJob?.cancel()
        noticeDismissJob = scope.launch {
            delay(NOTICE_DURATION_MS)
            dismissNotice()
        }
    }

    private fun send(moment: FeedMoment, hiding: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (isBusy || moment.id.isBlank()) return
        val uid = user.uid
        val key = momentKey(moment)
        val original = prefs(uid).getStringSet("hidden", emptySet())!!.toSet()
        val updated = if (hiding) original + key else original - key
        prefs(uid).edit().putStringSet("hidden", updated).apply()
        revision++
        isBusy = true
        noticeOwner = uid
        noticeDismissJob?.cancel()
        notice = R.string.for_you_feedback_saving
        undoMoment = null
        scope.launch {
            try {
                val token = user.getIdToken(false).await().token ?: error("Authentication required")
                withContext(Dispatchers.IO) {
                    val project = FirebaseApp.getInstance().options.projectId
                    val connection = URL("https://europe-southwest1-$project.cloudfunctions.net/getFeedPage").openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 15_000
                        connection.readTimeout = 15_000
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        val body = JSONObject().put("action", "forYouFeedback").put("authorId", moment.authorId)
                            .put("momentId", moment.id).put("intent", if (hiding) "hide" else "undo")
                        connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
                        check(connection.responseCode == 200)
                        val result = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                        check(result.optBoolean("accepted", false))
                    } finally { connection.disconnect() }
                }
                if (owner() == uid) {
                    notice = if (hiding) R.string.for_you_feedback_hidden else R.string.for_you_feedback_restored
                    undoMoment = if (hiding) moment else null
                    scheduleNoticeDismiss()
                }
            } catch (error: Exception) {
                prefs(uid).edit().putStringSet("hidden", original).apply()
                revision++
                if (owner() == uid) {
                    notice = R.string.for_you_feedback_failed
                    undoMoment = if (hiding) null else moment
                    scheduleNoticeDismiss()
                }
            } finally { isBusy = false }
        }
    }
}

@Composable
fun ForYouFeedbackNotice(modifier: Modifier = Modifier) {
    val message = ForYouPreferences.visibleNotice() ?: return
    Surface(modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp), tonalElevation = 6.dp) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(message), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (ForYouPreferences.isBusy) CircularProgressIndicator(Modifier.size(20.dp))
            if (ForYouPreferences.undoMoment != null) {
                TextButton(onClick = { ForYouPreferences.undo() }, enabled = !ForYouPreferences.isBusy) {
                    Text(stringResource(R.string.for_you_feedback_undo))
                }
            }
            if (!ForYouPreferences.isBusy) {
                TextButton(onClick = { ForYouPreferences.dismissNotice() }) { Text(stringResource(R.string.for_you_feedback_dismiss)) }
            }
        }
    }
}
