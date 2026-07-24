package com.moments.android.views.messaging.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.R

/** Port de `Views/Messaging/Components/AttachmentIconView.swift`. */
enum class AttachmentIcon(@DrawableRes val drawableRes: Int) {
    CAMERA(R.drawable.attachment_camera_icon),
    PHOTOS(R.drawable.attachment_photos_icon),
    GIF(R.drawable.attachment_gif_icon),
    LOCATION(R.drawable.attachment_location_icon),
    LIVE_LOCATION(R.drawable.attachment_live_location_icon),
    VOICE(R.drawable.attachment_voice_icon),
    EPHEMERAL(R.drawable.attachment_ephemeral_icon),
    BOOKMARK(R.drawable.attachment_bookmark_icon),
    TAGGED(R.drawable.attachment_tagged_icon),
    COMMENTS(R.drawable.attachment_comments_icon),
    SHARE(R.drawable.attachment_share_icon),
    HIDDEN_LAYER(R.drawable.attachment_hidden_layer_icon),
    BUZZ(R.drawable.attachment_buzz_icon),
    MUTUALS(R.drawable.audience_mutuals_icon),
}

object AttachmentIconMetrics {
    val attachmentMenu = 22.dp
    val locationSheetRow = 23.dp
    val locationBubbleInfo = 18.dp
    val locationDetailCard = 23.dp
    val whatsNew = 22.dp
    val stickerCatalogPill = 20.dp
    val stickerSectionHeader = 15.dp
    val storyLocationSticker = 20.dp
    val creatorCameraChip = 15.dp
    val storyReplyAction = 25.dp
    val permissionPromptMedium = 36.dp
    val permissionPromptLarge = 54.dp
    val messageRequestRow = 15.dp
    val locationPickerInline = 15.dp
    val stickerPolaroidButton = 19.dp
    const val stickerAccentPillFill = 0.50f
    const val selfiePlaceholderFill = 0.40f
    val rail = 24.dp
    val railBookmark = 26.dp
    val profilePillTab = 16.dp
    val profilePillTabBookmark = 16.dp
    val profileEmptyState = 28.dp
    val momentActionBar = 22.dp
    val settingsRow = 21.dp
    val overlayTaggedCompact = 22.dp
    val overlayTaggedGlass = 24.dp
    val inlineCommentsHeader = 22.dp
    val actionChip = 22.dp
    val reelsSidebar = 30.dp
    val creatorMetaRow = 24.dp
    val shareSheetRow = 24.dp
    val shareInline = 19.dp
    val novaShareInline = 14.dp
    val cameraEphemeral = 14.dp
    val cameraEphemeralBadge = 14.dp
    val gridSavedBadge = 12.dp
    val tagCountChip = 14.dp
    val inAppBanner = 20.dp
    val activityCategoryRow = 26.dp
    val activityEmptyState = 38.dp
    val commentsEmptyState = 30.dp
    val emptyStateHero = 56.dp
    val chatVoiceInput = 24.dp
    val storyEphemeral = 26.dp
    val viewOnceBubble = 30.dp
    val viewOnceBadge = 21.dp
    val chatEphemeralPlaceholder = 46.dp
    val activityReactionBadge = 16.dp
    val voiceEditor = 28.dp
    val voiceRecording = 32.dp
    val voiceStickerPrompt = 34.dp
    val buzzToast = 17.dp
    val buzzTimelineEvent = 15.dp
}

enum class AttachmentIconPreset {
    ATTACHMENT_MENU,
    LOCATION_SHEET_ROW,
    LOCATION_BUBBLE_INFO,
    LOCATION_DETAIL_CARD,
    STICKER_CATALOG_PILL,
    STICKER_SECTION_HEADER,
    STORY_LOCATION_STICKER,
    CREATOR_CAMERA_CHIP,
    STORY_REPLY_ACTION,
    PERMISSION_PROMPT_MEDIUM,
    PERMISSION_PROMPT_LARGE,
    MESSAGE_REQUEST_ROW,
    LOCATION_PICKER_INLINE,
    STICKER_POLAROID_BUTTON,
    RAIL,
    PROFILE_PILL_TAB,
    PROFILE_EMPTY_STATE,
    MOMENT_ACTION_BAR,
    SETTINGS_ROW,
    OVERLAY_TAGGED_COMPACT,
    OVERLAY_TAGGED_GLASS,
    INLINE_COMMENTS_HEADER,
    ACTION_CHIP,
    REELS_SIDEBAR,
    CREATOR_META_ROW,
    SHARE_SHEET_ROW,
    SHARE_INLINE,
    NOVA_SHARE_INLINE,
    CAMERA_EPHEMERAL,
    CAMERA_EPHEMERAL_BADGE,
    GRID_SAVED_BADGE,
    TAG_COUNT_CHIP,
    IN_APP_BANNER,
    ACTIVITY_CATEGORY_ROW,
    ACTIVITY_EMPTY_STATE,
    COMMENTS_EMPTY_STATE,
    EMPTY_STATE_HERO,
    CHAT_VOICE_INPUT,
    STORY_EPHEMERAL,
    VIEW_ONCE_BUBBLE,
    VIEW_ONCE_BADGE,
    CHAT_EPHEMERAL_PLACEHOLDER,
    ACTIVITY_REACTION_BADGE,
    VOICE_EDITOR,
    VOICE_RECORDING,
    VOICE_STICKER_PROMPT,
    WHATS_NEW,
    ;

    val size: Dp
        get() = when (this) {
            ATTACHMENT_MENU -> AttachmentIconMetrics.attachmentMenu
            LOCATION_SHEET_ROW -> AttachmentIconMetrics.locationSheetRow
            LOCATION_BUBBLE_INFO -> AttachmentIconMetrics.locationBubbleInfo
            LOCATION_DETAIL_CARD -> AttachmentIconMetrics.locationDetailCard
            STICKER_CATALOG_PILL -> AttachmentIconMetrics.stickerCatalogPill
            STICKER_SECTION_HEADER -> AttachmentIconMetrics.stickerSectionHeader
            STORY_LOCATION_STICKER -> AttachmentIconMetrics.storyLocationSticker
            CREATOR_CAMERA_CHIP -> AttachmentIconMetrics.creatorCameraChip
            STORY_REPLY_ACTION -> AttachmentIconMetrics.storyReplyAction
            PERMISSION_PROMPT_MEDIUM -> AttachmentIconMetrics.permissionPromptMedium
            PERMISSION_PROMPT_LARGE -> AttachmentIconMetrics.permissionPromptLarge
            MESSAGE_REQUEST_ROW -> AttachmentIconMetrics.messageRequestRow
            LOCATION_PICKER_INLINE -> AttachmentIconMetrics.locationPickerInline
            STICKER_POLAROID_BUTTON -> AttachmentIconMetrics.stickerPolaroidButton
            RAIL -> AttachmentIconMetrics.rail
            PROFILE_PILL_TAB -> AttachmentIconMetrics.profilePillTab
            PROFILE_EMPTY_STATE -> AttachmentIconMetrics.profileEmptyState
            MOMENT_ACTION_BAR -> AttachmentIconMetrics.momentActionBar
            SETTINGS_ROW -> AttachmentIconMetrics.settingsRow
            OVERLAY_TAGGED_COMPACT -> AttachmentIconMetrics.overlayTaggedCompact
            OVERLAY_TAGGED_GLASS -> AttachmentIconMetrics.overlayTaggedGlass
            INLINE_COMMENTS_HEADER -> AttachmentIconMetrics.inlineCommentsHeader
            ACTION_CHIP -> AttachmentIconMetrics.actionChip
            REELS_SIDEBAR -> AttachmentIconMetrics.reelsSidebar
            CREATOR_META_ROW -> AttachmentIconMetrics.creatorMetaRow
            SHARE_SHEET_ROW -> AttachmentIconMetrics.shareSheetRow
            SHARE_INLINE -> AttachmentIconMetrics.shareInline
            NOVA_SHARE_INLINE -> AttachmentIconMetrics.novaShareInline
            CAMERA_EPHEMERAL -> AttachmentIconMetrics.cameraEphemeral
            CAMERA_EPHEMERAL_BADGE -> AttachmentIconMetrics.cameraEphemeralBadge
            GRID_SAVED_BADGE -> AttachmentIconMetrics.gridSavedBadge
            TAG_COUNT_CHIP -> AttachmentIconMetrics.tagCountChip
            IN_APP_BANNER -> AttachmentIconMetrics.inAppBanner
            ACTIVITY_CATEGORY_ROW -> AttachmentIconMetrics.activityCategoryRow
            ACTIVITY_EMPTY_STATE -> AttachmentIconMetrics.activityEmptyState
            COMMENTS_EMPTY_STATE -> AttachmentIconMetrics.commentsEmptyState
            EMPTY_STATE_HERO -> AttachmentIconMetrics.emptyStateHero
            CHAT_VOICE_INPUT -> AttachmentIconMetrics.chatVoiceInput
            STORY_EPHEMERAL -> AttachmentIconMetrics.storyEphemeral
            VIEW_ONCE_BUBBLE -> AttachmentIconMetrics.viewOnceBubble
            VIEW_ONCE_BADGE -> AttachmentIconMetrics.viewOnceBadge
            CHAT_EPHEMERAL_PLACEHOLDER -> AttachmentIconMetrics.chatEphemeralPlaceholder
            ACTIVITY_REACTION_BADGE -> AttachmentIconMetrics.activityReactionBadge
            VOICE_EDITOR -> AttachmentIconMetrics.voiceEditor
            VOICE_RECORDING -> AttachmentIconMetrics.voiceRecording
            VOICE_STICKER_PROMPT -> AttachmentIconMetrics.voiceStickerPrompt
            WHATS_NEW -> AttachmentIconMetrics.whatsNew
        }

    fun resolvedSizeFor(icon: AttachmentIcon): Dp = when (this) {
        RAIL -> if (icon == AttachmentIcon.BOOKMARK) AttachmentIconMetrics.railBookmark else AttachmentIconMetrics.rail
        PROFILE_PILL_TAB -> if (icon == AttachmentIcon.BOOKMARK) AttachmentIconMetrics.profilePillTabBookmark else AttachmentIconMetrics.profilePillTab
        ACTION_CHIP, MOMENT_ACTION_BAR -> if (icon == AttachmentIcon.BOOKMARK) AttachmentIconMetrics.actionChip + 2.dp else AttachmentIconMetrics.actionChip
        else -> when (icon) {
            AttachmentIcon.BOOKMARK, AttachmentIcon.VOICE, AttachmentIcon.TAGGED -> size * 1.04f
            else -> size
        }
    }
}

@Composable
fun AttachmentIconView(
    icon: AttachmentIcon,
    size: Dp,
    tintColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(icon.drawableRes),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = tintColor.takeUnless { it == Color.Unspecified }?.let(ColorFilter::tint),
        modifier = modifier.then(Modifier.size(size)),
    )
}

@Composable
fun AttachmentIconView(
    icon: AttachmentIcon,
    preset: AttachmentIconPreset,
    tintColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) = AttachmentIconView(icon, preset.resolvedSizeFor(icon), tintColor, modifier)
