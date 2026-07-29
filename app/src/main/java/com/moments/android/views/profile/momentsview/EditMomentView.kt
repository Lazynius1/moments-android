package com.moments.android.views.profile.momentsview

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MediaItem
import com.moments.android.models.PhotoTag
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchCustomListDetails
import com.moments.android.services.firestore.getCustomAudience
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.utilities.MomentMentionResolver
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorMedia
import com.moments.android.views.creator.PhotoTagSelectionView
import com.moments.android.views.creator.audienceselector.AudienceSelectionView
import com.moments.android.views.creator.audienceselector.contentAudienceDescription
import com.moments.android.views.creator.creatorscreens.LocationPickerView
import com.moments.android.views.feed.core.EditMomentPayload
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.launch

/**
 * Port de `EditMomentView.swift`.
 * Sheets: AudienceSelectionView / LocationPickerView / EditMomentPhotoTagSheet
 * vía MomentsModalSheet (iOS `.sheet` + detents).
 */
@Composable
fun EditMomentView(
    moment: FeedMoment,
    onSave: (EditMomentPayload) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val colors = rememberAdaptiveColors()
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }

    var editedContent by remember { mutableStateOf(moment.content) }
    var selectedAudience by remember {
        mutableStateOf(ContentAudience.from(moment.audience))
    }
    var selectedListId by remember { mutableStateOf(moment.customListId) }
    var selectedListName by remember { mutableStateOf<String?>(null) }
    var customSelectedUsers by remember { mutableStateOf<List<String>>(emptyList()) }
    var initialCustomSelectedUsers by remember { mutableStateOf<List<String>>(emptyList()) }
    var taggedUsers by remember { mutableStateOf(moment.taggedUsers) }
    var editedMediaItems by remember(moment.id) {
        mutableStateOf(moment.mediaItems.map { it.toMediaItem() })
    }
    var locationName by remember { mutableStateOf(moment.location.orEmpty()) }
    var selectedLocation by remember {
        mutableStateOf(moment.locationCoordinate)
    }
    var isSaving by remember { mutableStateOf(false) }
    var showingAudiencePicker by remember { mutableStateOf(false) }
    var showingLocationPicker by remember { mutableStateOf(false) }
    var showingTagPicker by remember { mutableStateOf(false) }

    val isAudienceLocked = moment.isModerationHidden
    val normalizedLocation = locationName.trim()
    val normalizedMomentLocation = moment.location.orEmpty().trim()
    val initialTaggedUsers = remember(moment.id) { moment.taggedUsers }
    val normalizedPhotoTags = remember(editedMediaItems) {
        editedMediaItems.firstOrNull { it.type == MediaItem.MediaType.IMAGE }?.tags.orEmpty()
    }
    val normalizedMomentPhotoTags = remember(moment.id) {
        moment.mediaItems.firstOrNull { it.type == "image" }?.tags.orEmpty()
    }

    val hasChanges = remember(
        editedContent, selectedAudience, selectedListId, customSelectedUsers,
        initialCustomSelectedUsers, taggedUsers, initialTaggedUsers, normalizedLocation,
        selectedLocation, normalizedPhotoTags, normalizedMomentPhotoTags,
    ) {
        editedContent != moment.content ||
            selectedAudience.raw != (moment.audience ?: ContentAudience.EVERYONE.raw) ||
            selectedListId != moment.customListId ||
            (selectedAudience == ContentAudience.CUSTOM &&
                customSelectedUsers.toSet() != initialCustomSelectedUsers.toSet()) ||
            taggedUsers.toSet() != initialTaggedUsers.toSet() ||
            !photoTagsEqual(normalizedPhotoTags, normalizedMomentPhotoTags) ||
            normalizedLocation != normalizedMomentLocation ||
            selectedLocation?.latitude != moment.locationCoordinate?.latitude ||
            selectedLocation?.longitude != moment.locationCoordinate?.longitude
    }

    LaunchedEffect(selectedAudience, selectedListId) {
        if (selectedAudience == ContentAudience.CUSTOM) {
            runCatching {
                firestore.getCustomAudience("moment", moment.authorId)
            }.onSuccess { viewers ->
                customSelectedUsers = viewers
                initialCustomSelectedUsers = viewers
            }
        }
        if (selectedAudience == ContentAudience.CUSTOM_LIST &&
            !selectedListId.isNullOrBlank() &&
            selectedListName == null
        ) {
            runCatching {
                firestore.fetchCustomListDetails(selectedListId!!, moment.authorId)
            }.onSuccess { list ->
                selectedListName = list.name
            }
        }
    }

    val audienceEveryone = stringResource(R.string.audience_everyone)
    val audienceMutuals = stringResource(R.string.audience_mutuals)
    val audienceBestFriends = stringResource(R.string.audience_best_friends)
    val audienceCustom = stringResource(R.string.audience_custom)
    val audienceCustomList = stringResource(R.string.audience_custom_list)
    val audienceOnlyMe = stringResource(R.string.audience_only_me)
    val audienceLocked = stringResource(R.string.edit_moment_audience_locked)
    val peopleCountFmt = stringResource(R.string.audience_people_count, customSelectedUsers.size)

    val audienceLabel = when (selectedAudience) {
        ContentAudience.CUSTOM_LIST -> selectedListName ?: audienceCustomList
        ContentAudience.CUSTOM ->
            if (customSelectedUsers.isEmpty()) audienceCustom else peopleCountFmt
        ContentAudience.EVERYONE -> audienceEveryone
        ContentAudience.MUTUALS -> audienceMutuals
        ContentAudience.BEST_FRIENDS -> audienceBestFriends
        ContentAudience.ONLY_ME -> audienceOnlyMe
    }
    val audienceSubtitle =
        if (isAudienceLocked) audienceLocked else contentAudienceDescription(selectedAudience)

    val bg = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF071118), Color(0xFF0F1822), Color(0xFF121A25)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFF5F7FB), Color.White, Color(0xFFEDF1F7)))
    }
    val primary = if (isDark) Color.White else Color.Black

    Box(
        modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = primary)
                }
                Text(
                    stringResource(R.string.edit_moment_title),
                    Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = primary,
                )
                TextButton(
                    onClick = {
                        if (!hasChanges || isSaving) return@TextButton
                        isSaving = true
                        scope.launch {
                            val mentions = MomentMentionResolver.resolveUserIds(editedContent)
                            val payload = EditMomentPayload(
                                content = editedContent,
                                audience = selectedAudience.raw,
                                customListId = if (selectedAudience == ContentAudience.CUSTOM_LIST) {
                                    selectedListId
                                } else {
                                    null
                                },
                                customViewers = if (selectedAudience == ContentAudience.CUSTOM) {
                                    customSelectedUsers
                                } else {
                                    emptyList()
                                },
                                taggedUsers = taggedUsers,
                                mentionedUsers = mentions,
                                locationName = normalizedLocation,
                                locationLatitude = selectedLocation?.latitude,
                                locationLongitude = selectedLocation?.longitude,
                                mediaItems = editedMediaItems,
                            )
                            onSave(payload)
                            isSaving = false
                            onDismiss()
                        }
                    },
                    enabled = hasChanges && !isSaving,
                ) {
                    Text(
                        stringResource(R.string.edit_moment_save),
                        fontWeight = FontWeight.SemiBold,
                        color = primary.copy(alpha = if (hasChanges && !isSaving) 1f else 0.4f),
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                EditMomentPreviewCard(moment)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.edit_moment_section_text),
                        subtitle = stringResource(R.string.edit_moment_placeholder),
                        color = primary,
                    )
                    TextField(
                        value = editedContent,
                        onValueChange = { editedContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 130.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.04f)),
                        placeholder = {
                            Text(
                                stringResource(R.string.edit_moment_placeholder),
                                color = primary.copy(0.35f),
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = primary,
                            unfocusedTextColor = primary,
                        ),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.edit_moment_section_details),
                        subtitle = stringResource(R.string.edit_moment_section_details_subtitle),
                        color = primary,
                    )
                    DetailRow(
                        title = stringResource(R.string.audience_title),
                        value = audienceLabel,
                        subtitle = audienceSubtitle,
                        locked = isAudienceLocked,
                        leading = {
                            AudienceIconView(
                                audience = selectedAudience,
                                size = AudienceIconMetrics.row,
                                isDark = isDark,
                            )
                        },
                        onClick = { if (!isAudienceLocked) showingAudiencePicker = true },
                        primary = primary,
                    )
                    DetailRow(
                        title = stringResource(R.string.edit_moment_location_title),
                        value = if (normalizedLocation.isEmpty()) {
                            stringResource(R.string.edit_moment_location_add)
                        } else {
                            locationName
                        },
                        subtitle = if (selectedLocation == null) {
                            stringResource(R.string.edit_moment_location_subtitle_empty)
                        } else {
                            stringResource(R.string.edit_moment_location_subtitle_set)
                        },
                        leading = {
                            Icon(Icons.Filled.LocationOn, null, Modifier.size(20.dp), tint = primary)
                        },
                        onClick = { showingLocationPicker = true },
                        primary = primary,
                    )
                    DetailRow(
                        title = stringResource(R.string.edit_moment_tags_title),
                        value = if (taggedUsers.isEmpty()) {
                            stringResource(R.string.edit_moment_tags_add)
                        } else {
                            stringResource(R.string.edit_moment_tags_count, taggedUsers.size)
                        },
                        subtitle = stringResource(R.string.edit_moment_tags_subtitle),
                        leading = {
                            Icon(Icons.Filled.PersonAdd, null, Modifier.size(20.dp), tint = primary)
                        },
                        onClick = { showingTagPicker = true },
                        primary = primary,
                    )
                }

                if (isAudienceLocked) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.04f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(14.dp), tint = primary.copy(0.7f))
                        Text(
                            stringResource(R.string.edit_moment_audience_locked_explainer),
                            fontSize = 12.sp,
                            color = primary.copy(0.65f),
                        )
                    }
                }
            }
        }

        if (isSaving) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.24f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceBackground)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = primary,
                        strokeWidth = 2.dp,
                    )
                    Text(stringResource(R.string.edit_moment_saving), color = primary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showingAudiencePicker) {
        MomentsModalSheet(
            onDismissRequest = { showingAudiencePicker = false },
            largeOnly = false,
        ) {
            AudienceSelectionView(
                selectedAudience = selectedAudience,
                selectedListId = selectedListId,
                selectedListName = selectedListName,
                customSelectedUsers = customSelectedUsers,
                onSelectedAudienceChange = { selectedAudience = it },
                onSelectedListIdChange = { selectedListId = it },
                onSelectedListNameChange = { selectedListName = it },
                onCustomSelectedUsersChange = { customSelectedUsers = it },
                onDismiss = { showingAudiencePicker = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }

    if (showingLocationPicker) {
        MomentsModalSheet(onDismissRequest = { showingLocationPicker = false }) {
            LocationPickerView(
                selectedLocation = selectedLocation,
                locationName = locationName,
                onSelectedLocationChange = { selectedLocation = it },
                onLocationNameChange = { locationName = it },
                onDismiss = { showingLocationPicker = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }

    if (showingTagPicker) {
        MomentsModalSheet(onDismissRequest = { showingTagPicker = false }) {
            EditMomentPhotoTagSheet(
                moment = moment,
                mediaItems = editedMediaItems,
                onMediaItemsChange = { editedMediaItems = it },
                taggedUsers = taggedUsers,
                onTaggedUsersChange = { taggedUsers = it },
                onDismiss = { showingTagPicker = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/**
 * Port de `EditMomentPhotoTagSheet` (EditMomentView.swift).
 * Android: CreatorMedia usa Uri (Coil carga la imagen); iOS precarga UIImage con Kingfisher.
 */
@Composable
private fun EditMomentPhotoTagSheet(
    moment: FeedMoment,
    mediaItems: List<MediaItem>,
    onMediaItemsChange: (List<MediaItem>) -> Unit,
    taggedUsers: List<String>,
    onTaggedUsersChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val secondary = primary.copy(if (isDark) 0.78f else 0.66f)

    val editableImage = remember(mediaItems, moment.id) {
        mediaItems.firstOrNull {
            it.type == MediaItem.MediaType.IMAGE && it.url.trim().isNotEmpty()
        } ?: moment.mediaItems.firstOrNull {
            it.type == "image" && it.url.trim().isNotEmpty()
        }?.toMediaItem()
    }

    val preservedLegacyTaggedUsers = remember(moment.id) {
        val spatial = moment.mediaItems.flatMap { it.tags.orEmpty() }.map { it.userId }.toSet()
        moment.taggedUsers.filterNot { it in spatial }
    }

    var creatorMedia by remember(editableImage?.id) {
        mutableStateOf<CreatorMedia?>(null)
    }
    var loadFailed by remember(editableImage?.id) { mutableStateOf(false) }

    LaunchedEffect(editableImage?.id) {
        val item = editableImage
        if (item == null) {
            loadFailed = true
            creatorMedia = null
            return@LaunchedEffect
        }
        val ratio = item.resolvedAspectRatioValue?.takeIf { it > 0f } ?: 0.8f
        val aspect = CreatorAspectRatio.fromRatio(ratio)
        creatorMedia = CreatorMedia(
            id = item.id,
            uri = Uri.parse(item.url),
            isVideo = false,
            thumbnailUri = item.thumbnailUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse),
            aspectRatio = aspect,
            recommendedAspectRatio = aspect,
            hasEdits = false,
            tags = item.tags.orEmpty(),
        )
        loadFailed = false
    }

    fun syncBackIfNeeded() {
        val media = creatorMedia ?: return
        val index = mediaItems.indexOfFirst { it.id == media.id }
        if (index < 0) return
        val current = mediaItems[index]
        val updated = mediaItems.toMutableList().also {
            it[index] = current.copy(tags = media.tags)
        }
        onMediaItemsChange(updated)
        val spatial = updated.flatMap { it.tags.orEmpty() }.map { it.userId }
        onTaggedUsersChange((spatial + preservedLegacyTaggedUsers).toSet().toList())
    }

    DisposableEffect(Unit) {
        onDispose { syncBackIfNeeded() }
    }

    Box(modifier.fillMaxSize()) {
        val media = creatorMedia
        when {
            media != null -> {
                PhotoTagSelectionView(
                    mediaItem = media,
                    onMediaItemChange = { creatorMedia = it },
                    onDismiss = {
                        syncBackIfNeeded()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            loadFailed || editableImage == null -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (loadFailed) Icons.Filled.Warning else Icons.Filled.Photo,
                        null,
                        Modifier.size(28.dp),
                        tint = primary.copy(if (isDark) 0.7f else 0.55f),
                    )
                    Text(
                        stringResource(R.string.edit_moment_tags_unavailable),
                        Modifier.padding(top = 14.dp),
                        color = primary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.common_ok),
                        Modifier
                            .padding(top = 14.dp)
                            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        color = primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }
            else -> {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = primary,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        stringResource(R.string.edit_moment_tags_loading),
                        Modifier.padding(top = 14.dp),
                        color = secondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

private fun FeedMediaItem.toMediaItem(): MediaItem = MediaItem(
    id = id,
    type = MediaItem.MediaType.from(type),
    url = url,
    aspectRatio = aspectRatio,
    thumbnailUrl = thumbnailUrl,
    videoDuration = videoDuration,
    tags = tags,
)

private fun photoTagsEqual(a: List<PhotoTag>, b: List<PhotoTag>): Boolean {
    if (a.size != b.size) return false
    return a.zip(b).all { (lhs, rhs) ->
        lhs.id == rhs.id &&
            lhs.userId == rhs.userId &&
            lhs.username == rhs.username &&
            lhs.x == rhs.x &&
            lhs.y == rhs.y
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, color: Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = color)
        Text(subtitle, fontSize = 12.sp, color = color.copy(0.55f))
    }
}

@Composable
private fun DetailRow(
    title: String,
    value: String,
    subtitle: String,
    locked: Boolean = false,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    primary: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !locked, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { leading() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = primary.copy(0.65f))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primary, maxLines = 2)
            Text(subtitle, fontSize = 12.sp, color = primary.copy(0.5f), maxLines = 2)
        }
        Icon(
            if (locked) Icons.Filled.Lock else Icons.Filled.KeyboardArrowRight,
            null,
            Modifier.size(16.dp),
            tint = primary.copy(0.4f),
        )
    }
}

@Composable
private fun EditMomentPreviewCard(moment: FeedMoment) {
    val url = moment.visibleMediaItems.firstOrNull()?.url
        ?: moment.mediaItems.firstOrNull()?.url
    val ratio = moment.aspectRatio?.toFloatOrNull()?.takeIf { it > 0f } ?: (4f / 5f)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Gray.copy(0.12f))
            .padding(10.dp),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Gray.copy(0.18f)),
            )
        }
    }
}
