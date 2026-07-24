package com.moments.android.views.nova.agent

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.moments.android.R
import com.moments.android.models.NotificationType
import com.moments.android.views.nova.tools.NovaMomentAudienceResolver
import java.util.UUID

data class NovaPendingAction(
    val id: String = UUID.randomUUID().toString(),
    val kind: Kind,
    val toolName: String,
    val title: String,
    val detail: String,
    val audienceLabel: String? = null,
    val previewImage: Bitmap? = null,
    val args: Map<String, Any?>,
) {
    enum class Kind {
        CREATE_MOMENT,
        REMEMBER_FACT,
        UPDATE_PREFERENCE,
        SEND_FOLLOW_REQUEST,
        UPDATE_PRIVACY_SETTINGS,
        UPDATE_PROFILE_BIO,
        UPDATE_PROFILE_WEBSITE,
        UPDATE_ACTIVE_HOURS,
        UPDATE_NOTIFICATION_PREFERENCES,
    }

    companion object {
        fun from(context: Context, toolName: String, args: Map<String, Any?>, previewImage: Bitmap? = null): NovaPendingAction? = when (toolName) {
            "create_moment" -> createMoment(context, toolName, args, previewImage)
            "remember_fact" -> (args["content"] as? String)?.takeIf { it.isNotEmpty() }?.let {
                NovaPendingAction(kind = Kind.REMEMBER_FACT, toolName = toolName, title = localizedTitle(context, Kind.REMEMBER_FACT), detail = it, args = args)
            }
            "update_user_preference" -> {
                val key = args["key"] as? String
                val value = args["value"] as? String
                if (key.isNullOrEmpty() || value.isNullOrEmpty()) null else NovaPendingAction(
                    kind = Kind.UPDATE_PREFERENCE, toolName = toolName, title = localizedTitle(context, Kind.UPDATE_PREFERENCE), detail = "$key: $value", args = args,
                )
            }
            "send_follow_request" -> (args["username"] as? String)?.takeIf { it.isNotEmpty() }?.let { username ->
                NovaPendingAction(
                    kind = Kind.SEND_FOLLOW_REQUEST,
                    toolName = toolName,
                    title = localizedTitle(context, Kind.SEND_FOLLOW_REQUEST),
                    detail = "@${username.trimStart('@')}",
                    args = args,
                )
            }
            "update_profile_privacy_settings" -> NovaPendingAction(
                kind = Kind.UPDATE_PRIVACY_SETTINGS, toolName = toolName, title = localizedTitle(context, Kind.UPDATE_PRIVACY_SETTINGS), detail = describePrivacyArgs(context, args), args = args,
            )
            "update_profile_bio" -> (args["bio"] as? String)?.let { bio ->
                NovaPendingAction(kind = Kind.UPDATE_PROFILE_BIO, toolName = toolName, title = localizedTitle(context, Kind.UPDATE_PROFILE_BIO), detail = bio.ifEmpty { clearField(context) }, args = args)
            }
            "update_profile_website" -> (args["website"] as? String)?.let { website ->
                NovaPendingAction(kind = Kind.UPDATE_PROFILE_WEBSITE, toolName = toolName, title = localizedTitle(context, Kind.UPDATE_PROFILE_WEBSITE), detail = website.ifEmpty { clearField(context) }, args = args)
            }
            "update_active_hours" -> NovaPendingAction(
                kind = Kind.UPDATE_ACTIVE_HOURS, toolName = toolName, title = localizedTitle(context, Kind.UPDATE_ACTIVE_HOURS), detail = describeActiveHoursArgs(context, args), args = args,
            )
            "update_notification_preferences" -> NovaPendingAction(
                kind = Kind.UPDATE_NOTIFICATION_PREFERENCES, toolName = toolName, title = localizedTitle(context, Kind.UPDATE_NOTIFICATION_PREFERENCES), detail = describeNotificationPreferenceArgs(context, args), args = args,
            )
            else -> null
        }

        private fun createMoment(context: Context, toolName: String, args: Map<String, Any?>, previewImage: Bitmap?): NovaPendingAction? {
            previewImage ?: return null
            val content = (args["content"] as? String)?.takeIf { it.isNotBlank() } ?: context.getString(R.string.nova_confirm_photo_only)
            val audienceRaw = args["audience"] as? String ?: "everyone"
            val audience = NovaMomentAudienceResolver.audienceSummary(
                context = context,
                audienceRaw = audienceRaw,
                targetUsername = args["target_username"] as? String,
                customListName = args["custom_list_name"] as? String,
            )
            val detail = listOf(content, context.getString(R.string.nova_confirm_audience_line, audience)).joinToString("\n\n")
            return NovaPendingAction(
                kind = Kind.CREATE_MOMENT,
                toolName = toolName,
                title = localizedTitle(context, Kind.CREATE_MOMENT),
                detail = detail,
                audienceLabel = audience,
                previewImage = previewImage,
                args = args,
            )
        }

        private fun localizedTitle(context: Context, kind: Kind): String = context.getString(
            when (kind) {
                Kind.CREATE_MOMENT -> R.string.nova_confirm_create_moment_title
                Kind.REMEMBER_FACT -> R.string.nova_confirm_remember_fact_title
                Kind.UPDATE_PREFERENCE -> R.string.nova_confirm_update_preference_title
                Kind.SEND_FOLLOW_REQUEST -> R.string.nova_confirm_send_follow_request_title
                Kind.UPDATE_PRIVACY_SETTINGS -> R.string.nova_confirm_update_privacy_settings_title
                Kind.UPDATE_PROFILE_BIO -> R.string.nova_confirm_update_profile_bio_title
                Kind.UPDATE_PROFILE_WEBSITE -> R.string.nova_confirm_update_profile_website_title
                Kind.UPDATE_ACTIVE_HOURS -> R.string.nova_confirm_update_active_hours_title
                Kind.UPDATE_NOTIFICATION_PREFERENCES -> R.string.nova_confirm_update_notification_preferences_title
            },
        )

        private fun clearField(context: Context) = context.getString(R.string.nova_confirm_clear_field)

        private fun describePrivacyArgs(context: Context, args: Map<String, Any?>): String = buildList {
            appendBoolLine(context, args["is_private"], R.string.nova_confirm_field_private_account)?.let(::add)
            appendBoolLine(context, args["show_mutual_connections"], R.string.nova_confirm_field_show_mutuals)?.let(::add)
            appendBoolLine(context, args["show_following"], R.string.nova_confirm_field_show_following)?.let(::add)
            appendBoolLine(context, args["show_followers"], R.string.nova_confirm_field_show_followers)?.let(::add)
        }.joinToString("\n")

        private fun describeActiveHoursArgs(context: Context, args: Map<String, Any?>): String {
            if (args["clear"] == true) return context.getString(R.string.nova_confirm_active_hours_clear)
            val start = args["start_hour"] as? String ?: "--:--"
            val end = args["end_hour"] as? String ?: "--:--"
            return context.getString(R.string.nova_confirm_active_hours_range, start, end)
        }

        private fun describeNotificationPreferenceArgs(context: Context, args: Map<String, Any?>): String {
            val values = (args["preferences"] as? Map<*, *>)?.entries
                ?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
                ?.toMap()
                ?: args.filterValues { it is Boolean }
            return values.mapNotNull { (key, value) ->
                (value as? Boolean)?.let { "${notificationLabel(context, key)}: ${toggleState(context, it)}" }
            }
                .sorted()
                .joinToString("\n")
        }

        private fun appendBoolLine(context: Context, value: Any?, @StringRes label: Int): String? =
            (value as? Boolean)?.let { "${context.getString(label)}: ${yesNoState(context, it)}" }

        private fun yesNoState(context: Context, value: Boolean): String = context.getString(
            if (value) R.string.nova_confirm_state_yes else R.string.nova_confirm_state_no,
        )

        private fun toggleState(context: Context, value: Boolean): String = context.getString(
            if (value) R.string.nova_confirm_state_enabled else R.string.nova_confirm_state_disabled,
        )

        private fun notificationLabel(context: Context, key: String): String {
            val resId = when (key) {
                NotificationType.LIKE.raw -> R.string.nova_notification_like
                NotificationType.REACTION.raw -> R.string.nova_notification_reaction
                NotificationType.COMMENT.raw -> R.string.nova_notification_comment
                NotificationType.MENTION.raw -> R.string.nova_notification_mention
                NotificationType.NEW_FOLLOWER.raw -> R.string.nova_notification_new_follower
                NotificationType.FOLLOW_REQUEST.raw -> R.string.nova_notification_follow_request
                NotificationType.REQUEST_ACCEPTED.raw -> R.string.nova_notification_request_accepted
                NotificationType.MUTUAL_CONNECTION.raw -> R.string.nova_notification_mutual_connection
                NotificationType.STORY_REACTION.raw -> R.string.nova_notification_story_reaction
                NotificationType.MESSAGE.raw -> R.string.nova_notification_message
                NotificationType.PHOTO_TAG.raw -> R.string.nova_notification_photo_tag
                NotificationType.ECHO_SUGGESTION.raw -> R.string.nova_notification_echo_suggestion
                NotificationType.DATA_EXPORT_READY.raw -> R.string.nova_notification_data_export
                NotificationType.STORY_CHAIN_CONTINUED.raw -> R.string.nova_notification_story_chain
                NotificationType.MEDIA_MODERATION.raw -> R.string.nova_notification_moderation
                "gentleReminders" -> R.string.nova_notification_daily_reminders
                "commentsMutualsOnly" -> R.string.nova_notification_mutual_comments_only
                "muteOldPostReactions" -> R.string.nova_notification_mute_old_reactions
                else -> return key
            }
            return context.getString(resId)
        }
    }
}
