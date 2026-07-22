package com.moments.android.utilities

import androidx.compose.ui.graphics.Color
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUserByUsername
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MentionDraftToken(
    val query: String,
    val fullRange: IntRange,
)

object MomentMentionParser {
    val mentionColor = Color(0xFF007AFF)

    data class Match(
        val range: IntRange,
        val username: String,
    )

    // Require a non-word / non-dot boundary before @ so emails like foo@bar.com
    // do not become social mentions.
    private val mentionPattern = Regex("(?<![\\w.])@(\\w+)")

    fun matchesIn(content: String): List<Match> {
        return mentionPattern.findAll(content).mapNotNull { result ->
            val username = result.groupValues.getOrNull(1) ?: return@mapNotNull null
            Match(range = result.range, username = username)
        }.toList()
    }
}

object MomentMentionLink {
    fun urlFor(username: String): String {
        return "mention://open?value=${username}"
    }

    fun usernameFrom(url: String): String? {
        if (!url.startsWith("mention://")) return null
        val valueParam = Regex("[?&]value=([^&]+)").find(url)?.groupValues?.getOrNull(1)
        if (!valueParam.isNullOrEmpty()) return valueParam
        return url.removePrefix("mention://").substringBefore("?").takeIf { it.isNotEmpty() }
    }
}

object MentionParsing {
    fun extractUsernames(text: String): List<String> {
        val seen = linkedSetOf<String>()
        MomentMentionParser.matchesIn(text).forEach { match ->
            val username = match.username.lowercase()
            if (username.isNotEmpty()) seen.add(username)
        }
        return seen.toList()
    }

    fun detectActiveToken(text: String): MentionDraftToken? {
        if (text.isEmpty()) return null

        val tokenStart = text.lastIndexOfAny(charArrayOf(' ', '\n', '\t')).let { idx ->
            if (idx < 0) 0 else idx + 1
        }
        val token = text.substring(tokenStart)
        if (!token.startsWith("@") || token.length <= 1) return null

        val query = token.drop(1)
        if (query.length > 30) return null
        if (!query.all { it.isLetterOrDigit() || it == '_' }) return null

        return MentionDraftToken(
            query = query,
            fullRange = tokenStart until text.length,
        )
    }
}

/** Bridge de navegación UI; registrar implementación desde capa de presentación. */
fun interface MomentMentionProfileNavigator {
    fun openProfile(userId: String)
}

object MomentMentionNavigation {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val firestoreService = FirestoreService()

    /** Registrado desde [com.moments.android.coordinators.LegacyNavigationBridge.wireMentionNavigation]. */
    var profileNavigator: MomentMentionProfileNavigator? = null

    fun openProfile(username: String) {
        val clean = username.trim()
        if (clean.isEmpty()) return

        scope.launch {
            val user = runCatching {
                withContext(Dispatchers.IO) {
                    firestoreService.fetchUserByUsername(clean)
                }
            }.getOrNull() ?: return@launch
            profileNavigator?.openProfile(user.id)
        }
    }
}

/**
 * Resolves @username tokens in caption text for **mention notifications only**.
 * Must not be written to `Moment.taggedUsers` — that field bypasses audience privacy checks.
 */
object MomentMentionResolver {
    private val firestoreService = FirestoreService()

    suspend fun resolveUserIds(text: String): List<String> {
        val usernames = MentionParsing.extractUsernames(text)
        if (usernames.isEmpty()) return emptyList()

        return usernames.mapNotNull { username ->
            resolveUserId(username)
        }.distinct()
    }

    private suspend fun resolveUserId(username: String): String? {
        return runCatching {
            withContext(Dispatchers.IO) {
                firestoreService.fetchUserByUsername(username).id
            }
        }.getOrNull()
    }
}
