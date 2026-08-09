package com.moments.android.coordinators.nav3

import android.net.Uri

/**
 * Parsea `moments://` / `glowsy://` / universal links → [MomentsNavKey].
 *
 * Paridad con `DeepLinkHandler` en TabBarView; la resolución async de username
 * (profile/{username}) queda fuera — el handler sigue haciendo el fetch.
 *
 * Skill navigation-3 / recipe deeplinks-basic: Intent URI → NavKey.
 */
object MomentsDeepLinkParser {

    private val universalHosts = setOf(
        "moments.app",
        "www.moments.app",
        "momentsapp.app",
        "www.momentsapp.app",
    )

    /**
     * @return key síncrona, o null si no aplica / requiere resolución async (username).
     */
    fun parse(uri: Uri): MomentsNavKey? {
        return when (uri.scheme?.lowercase()) {
            "moments", "glowsy" -> parseCustomScheme(uri)
            "https" -> parseUniversalLink(uri)
            else -> null
        }
    }

    private fun parseCustomScheme(uri: Uri): MomentsNavKey? {
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path.orEmpty()
        val segments = uri.pathSegments

        return when {
            host == "moment" && segments.size > 1 ->
                MomentsNavKey.Moment(id = segments[1], authorId = "")
            host == "story" && path == "/create" ->
                MomentsNavKey.Creator
            host == "profile" && path == "/visits" ->
                MomentsNavKey.ShowProfileVisits
            host == "profile" && segments.size > 1 ->
                // Username → requiere Firestore; TabBar DeepLinkHandler lo resuelve.
                null
            host == "messages" -> MomentsNavKey.ShowMessages
            host == "nova" -> MomentsNavKey.ShowNova
            host == "notifications" -> MomentsNavKey.ShowNotifications
            host == "stories" -> MomentsNavKey.ShowStories
            host == "echoes" || host == "echo" ->
                segments.getOrNull(0)?.takeIf { it.isNotBlank() }?.let { MomentsNavKey.Echo(it) }
            else -> null
        }
    }

    private fun parseUniversalLink(uri: Uri): MomentsNavKey? {
        val host = uri.host?.lowercase().orEmpty()
        if (host !in universalHosts) return null
        val segments = uri.pathSegments
        if (segments.size >= 2 && segments[0] == "moment") {
            return MomentsNavKey.Moment(id = segments[1], authorId = "")
        }
        return null
    }
}
