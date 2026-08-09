package com.moments.android.coordinators.nav3

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.TaskStackBuilder
import androidx.navigation3.runtime.NavKey

/**
 * Up/Back sintético para deep links — skill `navigation-3` / [deeplink-guide](
 * https://github.com/android/nav3-recipes/blob/main/docs/deeplink-guide.md).
 *
 * Cada destino declara el padre “más natural” si el usuario hubiera navegado a mano.
 * [buildSyntheticBackStack] recorre padres hasta un root de tab.
 */

/** Padre lógico para Up/Back (null = root de tab / no aplica). */
fun MomentsNavKey.deepLinkParent(): NavKey? = when (this) {
    is MomentsNavKey.Profile,
    is MomentsNavKey.ShowUserProfile,
    is MomentsNavKey.UserProfileInFeed,
    -> MomentsTabNavKey.Feed

    is MomentsNavKey.Moment -> MomentsTabNavKey.Feed

    /** Conversación encima del tab Mensajes (paridad iOS). */
    is MomentsNavKey.Conversation -> MomentsTabNavKey.Messages
    MomentsNavKey.ShowMessages -> MomentsTabNavKey.Messages
    MomentsNavKey.ShowNova -> MomentsTabNavKey.Feed

    MomentsNavKey.ShowNotifications,
    is MomentsNavKey.Notifications,
    -> MomentsTabNavKey.Feed

    MomentsNavKey.ShowStories,
    is MomentsNavKey.Story,
    is MomentsNavKey.StoryChain,
    -> MomentsTabNavKey.Feed

    MomentsNavKey.Creator -> MomentsTabNavKey.Feed

    MomentsNavKey.ShowProfileVisits -> MomentsTabNavKey.Profile
    MomentsNavKey.OwnProfileTab -> null

    is MomentsNavKey.FollowRequests -> MomentsTabNavKey.Profile

    is MomentsNavKey.Echo,
    is MomentsNavKey.EchoSuggestion,
    -> MomentsTabNavKey.Feed

    MomentsNavKey.ShowExplore -> MomentsTabNavKey.Explore
    MomentsNavKey.ScrollFeedToTop -> null
}

/**
 * Camino sintético root→destino (incluidos).
 * Ej. Conversation → `[Feed, ShowMessages, Conversation]`.
 */
fun buildSyntheticBackStack(target: MomentsNavKey): List<NavKey> {
    val path = ArrayDeque<NavKey>()
    val seen = HashSet<NavKey>()
    var node: NavKey? = target
    while (node != null && seen.add(node)) {
        path.addFirst(node)
        node = when (node) {
            is MomentsNavKey -> node.deepLinkParent()
            is MomentsTabNavKey -> null
            else -> null
        }
    }
    if (path.firstOrNull() !is MomentsTabNavKey) {
        path.addFirst(MomentsTabNavKey.Feed)
    }
    return path.toList()
}

/**
 * Reinicia la Activity en un Task nuevo apuntando al padre del deep link
 * (principio: Up nunca sale de la app — recipe *deeplinks-advanced*).
 */
fun createDeepLinkUpTaskStack(
    context: Context,
    activity: Activity?,
    parentDeepLinkUri: Uri?,
): TaskStackBuilder {
    val intent = if (activity != null) {
        Intent(context, activity.javaClass)
    } else {
        context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
    }
    if (parentDeepLinkUri != null) {
        intent.data = parentDeepLinkUri
        intent.action = Intent.ACTION_VIEW
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    return TaskStackBuilder.create(context).addNextIntentWithParentStack(intent)
}

/** URI moments:// para reconstruir el padre en Up (Task restart). */
fun NavKey.toMomentsDeepLinkUri(): Uri? = when (this) {
    MomentsTabNavKey.Feed -> Uri.parse("moments://home")
    MomentsTabNavKey.Profile -> Uri.parse("moments://profile")
    MomentsTabNavKey.Explore -> Uri.parse("moments://explore")
    MomentsTabNavKey.Messages -> Uri.parse("moments://messages")
    MomentsNavKey.ShowMessages -> Uri.parse("moments://messages")
    MomentsNavKey.ShowNova -> Uri.parse("moments://nova")
    MomentsNavKey.ShowNotifications -> Uri.parse("moments://notifications")
    MomentsNavKey.ShowStories -> Uri.parse("moments://stories")
    is MomentsNavKey.Profile -> Uri.parse("moments://user/${userId}")
    is MomentsNavKey.Moment -> Uri.parse("moments://moment/${id}")
    is MomentsNavKey.Conversation -> Uri.parse("moments://messages")
    else -> null
}
