package com.moments.android.notifications.services

import android.content.Context
import com.google.firebase.FirebaseApp
import com.moments.android.R
import java.util.UUID
import kotlin.math.max

/**
 * Port de `InAppActionToast` en InAppNotificationService.swift.
 * Confirmaciones de acciones del usuario en el mismo canal que el banner in-app.
 */
data class InAppActionToast(
    val id: String = UUID.randomUUID().toString(),
    val prefix: String,
    val emphasis: String? = null,
    val suffix: String = "",
    val subtitle: String? = null,
    val durationMs: Long = STANDARD_DURATION_MS,
    val undo: (() -> Unit)? = null,
    val holds: Boolean = false,
    val showsProgress: Boolean = false,
    /** Tras el toast, el chrome hace morph a la pill Incognito. */
    val bridgesToIncognitoPill: Boolean = false,
    /** Toast de pausa: morph desde la pill. */
    val isIncognitoPaused: Boolean = false,
    /** Se ejecuta al acabar el timer / dismiss sin deshacer (p. ej. unfollow diferido). */
    val onExpire: (() -> Unit)? = null,
) {
    companion object {
        const val STANDARD_DURATION_MS = 2_000L
        const val UNDO_DURATION_MS = 3_500L
        const val INCOGNITO_BRIDGE_DURATION_MS = 1_550L

        fun create(
            prefix: String,
            emphasis: String? = null,
            suffix: String = "",
            subtitle: String? = null,
            durationMs: Long = STANDARD_DURATION_MS,
            undo: (() -> Unit)? = null,
            holds: Boolean = false,
            showsProgress: Boolean = false,
            bridgesToIncognitoPill: Boolean = false,
            isIncognitoPaused: Boolean = false,
            onExpire: (() -> Unit)? = null,
        ): InAppActionToast = InAppActionToast(
            prefix = prefix,
            emphasis = emphasis,
            suffix = suffix,
            subtitle = subtitle,
            durationMs = if (undo == null) durationMs else max(durationMs, UNDO_DURATION_MS),
            undo = undo,
            holds = holds,
            showsProgress = showsProgress,
            bridgesToIncognitoPill = bridgesToIncognitoPill,
            isIncognitoPaused = isIncognitoPaused,
            onExpire = onExpire,
        )

        private fun ctx(): Context = FirebaseApp.getInstance().applicationContext

        private fun str(resId: Int): String = ctx().getString(resId)

        private fun suffix(resId: Int): String = runCatching { str(resId) }.getOrDefault("")

        fun activityProgress(title: String): InAppActionToast = create(
            prefix = title,
            subtitle = str(R.string.user_activity_simple_recently_deleted_processing_subtitle),
            holds = true,
            showsProgress = true,
        )

        fun activityDone(message: String): InAppActionToast = create(prefix = message)

        fun commentPosted(): InAppActionToast =
            create(prefix = str(R.string.toast_action_comment_posted))

        fun momentSaved(): InAppActionToast =
            create(prefix = str(R.string.toast_action_moment_saved))

        fun momentDeleted(): InAppActionToast =
            create(prefix = str(R.string.toast_action_moment_deleted))

        fun momentArchived(
            undo: (() -> Unit)? = null,
            onExpire: (() -> Unit)? = null,
        ): InAppActionToast = create(
            prefix = str(R.string.toast_action_moment_archived),
            undo = undo,
            onExpire = onExpire,
        )

        fun storyDeleted(): InAppActionToast =
            create(prefix = str(R.string.toast_action_story_deleted))

        fun echoLeft(): InAppActionToast =
            create(prefix = str(R.string.toast_action_echo_left))

        fun echoDeleted(): InAppActionToast =
            create(prefix = str(R.string.toast_action_echo_deleted))

        fun messageRequestDeleted(): InAppActionToast =
            create(prefix = str(R.string.toast_action_message_request_deleted))

        fun messageRequestsDeleted(): InAppActionToast =
            create(prefix = str(R.string.toast_action_message_requests_deleted))

        fun messageRequestReported(): InAppActionToast =
            create(prefix = str(R.string.toast_action_message_request_reported))

        fun messageRequestAccepted(): InAppActionToast =
            create(prefix = str(R.string.toast_action_message_request_accepted))

        fun followed(username: String): InAppActionToast = personToast(
            prefix = str(R.string.toast_action_followed),
            username = username,
            suffix = suffix(R.string.toast_action_followed_suffix),
        )

        fun followRequested(username: String): InAppActionToast = personToast(
            prefix = str(R.string.toast_action_follow_requested),
            username = username,
            suffix = suffix(R.string.toast_action_follow_requested_suffix),
        )

        fun requestAccepted(username: String): InAppActionToast = personToast(
            prefix = str(R.string.toast_action_request_accepted),
            username = username,
            suffix = suffix(R.string.toast_action_request_accepted_suffix),
        )

        fun requestRejected(username: String): InAppActionToast = personToast(
            prefix = str(R.string.toast_action_request_rejected),
            username = username,
            suffix = suffix(R.string.toast_action_request_rejected_suffix),
        )

        fun unfollowed(
            username: String,
            undo: (() -> Unit)? = null,
            onExpire: (() -> Unit)? = null,
        ): InAppActionToast = personToast(
            prefix = str(R.string.toast_action_unfollowed),
            username = username,
            suffix = suffix(R.string.toast_action_unfollowed_suffix),
            undo = undo,
            onExpire = onExpire,
        )

        fun blocked(username: String): InAppActionToast = personToast(
            prefix = str(R.string.toast_action_blocked),
            username = username,
            suffix = suffix(R.string.toast_action_blocked_suffix),
        )

        fun muted(
            username: String,
            undo: () -> Unit,
            onExpire: (() -> Unit)? = null,
        ): InAppActionToast = personToast(
            prefix = str(R.string.toast_action_muted),
            username = username,
            suffix = suffix(R.string.toast_action_muted_suffix),
            undo = undo,
            onExpire = onExpire,
        )

        fun momentUnsaved(
            undo: () -> Unit,
            onExpire: (() -> Unit)? = null,
        ): InAppActionToast = create(
            prefix = str(R.string.toast_action_moment_unsaved),
            undo = undo,
            onExpire = onExpire,
        )

        fun commentDeleted(username: String?): InAppActionToast {
            val name = username?.trim().orEmpty()
            return if (name.isNotEmpty()) {
                personToast(
                    prefix = str(R.string.toast_action_comment_deleted_prefix),
                    username = name,
                    suffix = suffix(R.string.toast_action_comment_deleted_suffix),
                )
            } else {
                create(prefix = str(R.string.toast_action_comment_deleted))
            }
        }

        fun messageCopied(text: String): InAppActionToast = create(
            prefix = str(R.string.toast_action_message_copied),
            subtitle = text,
        )

        fun linkCopied(): InAppActionToast =
            create(prefix = str(R.string.toast_action_link_copied))

        fun momentUpdated(): InAppActionToast =
            create(prefix = str(R.string.toast_action_moment_updated))

        fun leftGroup(name: String): InAppActionToast = create(
            prefix = str(R.string.toast_action_left_group_prefix),
            emphasis = name,
            suffix = suffix(R.string.toast_action_left_group_suffix),
        )

        fun pinnedMoment(
            undo: () -> Unit,
            onExpire: (() -> Unit)? = null,
        ): InAppActionToast = create(
            prefix = str(R.string.context_menu_pin_moment_toast_pinned),
            undo = undo,
            onExpire = onExpire,
        )

        fun unpinnedMoment(
            undo: () -> Unit,
            onExpire: (() -> Unit)? = null,
        ): InAppActionToast = create(
            prefix = str(R.string.context_menu_pin_moment_toast_unpinned),
            undo = undo,
            onExpire = onExpire,
        )

        fun chatArchived(
            undo: () -> Unit,
            onExpire: (() -> Unit)? = null,
        ): InAppActionToast = create(
            prefix = str(R.string.messaging_toast_archived),
            undo = undo,
            onExpire = onExpire,
        )

        fun chatUnarchived(): InAppActionToast =
            create(prefix = str(R.string.messaging_toast_unarchived))

        fun chatMuted(
            undo: () -> Unit,
            onExpire: (() -> Unit)? = null,
        ): InAppActionToast = create(
            prefix = str(R.string.messaging_toast_muted),
            undo = undo,
            onExpire = onExpire,
        )

        fun chatUnmuted(): InAppActionToast =
            create(prefix = str(R.string.messaging_toast_unmuted))

        fun chatPinned(
            undo: () -> Unit,
            onExpire: (() -> Unit)? = null,
        ): InAppActionToast = create(
            prefix = str(R.string.messaging_toast_pinned),
            undo = undo,
            onExpire = onExpire,
        )

        fun chatUnpinned(): InAppActionToast =
            create(prefix = str(R.string.messaging_toast_unpinned))

        fun chatDeleted(): InAppActionToast =
            create(prefix = str(R.string.messaging_toast_deleted))

        fun notificationDeleted(
            count: Int,
            undo: () -> Unit,
            onExpire: (() -> Unit)? = null,
        ): InAppActionToast {
            val prefix = if (count > 1) {
                str(R.string.notifications_deleted_toast_plural)
            } else {
                str(R.string.notifications_deleted_toast)
            }
            return create(prefix = prefix, undo = undo, onExpire = onExpire)
        }

        /** Activar/reanudar: toast con copy → morph a la pill del timer. */
        fun incognitoActivated(): InAppActionToast = create(
            prefix = str(R.string.toast_action_incognito_activated),
            subtitle = str(R.string.toast_action_incognito_activated_subtitle),
            durationMs = INCOGNITO_BRIDGE_DURATION_MS,
            bridgesToIncognitoPill = true,
        )

        /** Pausar: la pill hace morph al toast y se descarta (sin subtítulo). */
        fun incognitoPaused(): InAppActionToast = create(
            prefix = str(R.string.toast_action_incognito_paused),
            durationMs = 1_800L,
            isIncognitoPaused = true,
        )

        private fun personToast(
            prefix: String,
            username: String,
            suffix: String = "",
            undo: (() -> Unit)? = null,
            onExpire: (() -> Unit)? = null,
        ): InAppActionToast {
            val name = username.trim()
            if (name.isEmpty()) {
                val stripped = prefix.trimEnd()
                val cleaned = if (stripped.endsWith(" a")) stripped.dropLast(2) else stripped
                return create(prefix = cleaned, undo = undo, onExpire = onExpire)
            }
            return create(prefix = prefix, emphasis = name, suffix = suffix, undo = undo, onExpire = onExpire)
        }
    }
}
