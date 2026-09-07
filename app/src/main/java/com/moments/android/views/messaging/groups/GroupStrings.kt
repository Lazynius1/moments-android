package com.moments.android.views.messaging.groups

import com.moments.android.R

internal fun groupStringId(key: String): Int = when (key) {
    "title" -> R.string.groups_title
    "empty" -> R.string.groups_empty
    "emptyBody" -> R.string.groups_empty_body
    "memberCount" -> R.string.groups_member_count
    "create" -> R.string.groups_create
    "name" -> R.string.groups_name
    "pickerHint" -> R.string.groups_picker_hint
    "selectedCount" -> R.string.groups_selected_count
    "noConnections" -> R.string.groups_no_connections
    "add" -> R.string.groups_add
    "search" -> R.string.groups_search
    "details" -> R.string.groups_details
    "privacy" -> R.string.groups_privacy
    "older" -> R.string.groups_older
    "reply" -> R.string.groups_reply
    "photo" -> R.string.groups_photo
    "message" -> R.string.groups_message
    "cancel" -> R.string.groups_cancel
    "send" -> R.string.groups_send
    "save" -> R.string.groups_save
    "mute" -> R.string.groups_mute
    "members" -> R.string.groups_members
    "owner" -> R.string.groups_owner
    "admin" -> R.string.groups_admin
    "promote" -> R.string.groups_promote
    "demote" -> R.string.groups_demote
    "remove" -> R.string.groups_remove
    "manage" -> R.string.groups_manage
    "leave" -> R.string.groups_leave
    "leaveBody" -> R.string.groups_leave_body
    "removeBody" -> R.string.groups_remove_body
    "errorTitle" -> R.string.groups_error_title
    "ok" -> R.string.groups_ok
    "back" -> R.string.groups_back
    "error" -> R.string.groups_error
    "unavailable" -> R.string.groups_unavailable
    "keyError" -> R.string.groups_key_error
    "sendError" -> R.string.groups_send_error
    "photoError" -> R.string.groups_photo_error
    "manageError" -> R.string.groups_manage_error
    "notification" -> R.string.groups_notification
    "invitations" -> R.string.groups_invitations
    "accept" -> R.string.groups_accept
    "decline" -> R.string.groups_decline
    "invited" -> R.string.groups_invited
    "pendingInvitations" -> R.string.groups_pending_invitations
    else -> R.string.groups_error
}
