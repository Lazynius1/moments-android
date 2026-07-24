package com.moments.android.views.creator.creatorscreens

import com.moments.android.views.creator.HiddenLayerDraft
import com.moments.android.views.creator.HiddenLayersEditorView
import com.moments.android.views.creator.PhotoTagSelectionView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.BackgroundMomentUploadService
import com.moments.android.views.creator.CreatorFlow
import com.moments.android.views.creator.CreatorMedia
import com.moments.android.views.creator.audienceselector.AudienceSelectionView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `CaptionAndDetailsView.swift`.
 * LocationPicker / AudienceSelection / PhotoTag / HiddenLayers (texto) cableados; schedule: pending.
 */
@Composable
fun CaptionAndDetailsView(
    selectedMediaItems: List<CreatorMedia>,
    onSelectedMediaItemsChange: (List<CreatorMedia>) -> Unit,
    onCurrentFlowChange: (CreatorFlow) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var captionText by remember { mutableStateOf("") }
    var locationName by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<Moment.LocationCoordinate?>(null) }
    var audience by remember { mutableStateOf(ContentAudience.EVERYONE) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var selectedListName by remember { mutableStateOf<String?>(null) }
    var customSelectedUsers by remember { mutableStateOf<List<String>>(emptyList()) }
    var disableComments by remember { mutableStateOf(false) }
    var hideLikeCounts by remember { mutableStateOf(false) }
    var allowSharing by remember { mutableStateOf(true) }
    var isSchedulingEnabled by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    var isLaunching by remember { mutableStateOf(false) }
    var isPreviewingMedia by remember { mutableStateOf(false) }
    var pendingSheet by remember { mutableStateOf<String?>(null) }
    var showingLocationPicker by remember { mutableStateOf(false) }
    var showingAudience by remember { mutableStateOf(false) }
    var showingTagSelector by remember { mutableStateOf(false) }
    var showingHiddenLayers by remember { mutableStateOf(false) }
    var hiddenLayerDrafts by remember { mutableStateOf<List<HiddenLayerDraft>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val canUseHiddenLayers = selectedMediaItems.size == 1 && selectedMediaItems.none { it.isVideo }
    val tagCount = selectedMediaItems.sumOf { it.tags.size }
    val readyHiddenCount = hiddenLayerDrafts.count { it.isReadyToPublish }

    if (showingLocationPicker) {
        LocationPickerView(
            selectedLocation = selectedLocation,
            locationName = locationName,
            onSelectedLocationChange = { selectedLocation = it },
            onLocationNameChange = { locationName = it },
            onDismiss = { showingLocationPicker = false },
            modifier = modifier,
        )
        return
    }
    if (showingAudience) {
        AudienceSelectionView(
            selectedAudience = audience,
            selectedListId = selectedListId,
            selectedListName = selectedListName,
            customSelectedUsers = customSelectedUsers,
            onSelectedAudienceChange = { audience = it },
            onSelectedListIdChange = { selectedListId = it },
            onSelectedListNameChange = { selectedListName = it },
            onCustomSelectedUsersChange = { customSelectedUsers = it },
            onDismiss = { showingAudience = false },
            modifier = modifier,
        )
        return
    }
    if (showingTagSelector) {
        val media = selectedMediaItems.firstOrNull()
        if (media == null || media.isVideo) {
            showingTagSelector = false
        } else {
            PhotoTagSelectionView(
                mediaItem = media,
                onMediaItemChange = { updated ->
                    onSelectedMediaItemsChange(
                        selectedMediaItems.map { if (it.id == updated.id) updated else it },
                    )
                },
                onDismiss = { showingTagSelector = false },
                modifier = modifier,
            )
            return
        }
    }
    if (showingHiddenLayers && canUseHiddenLayers) {
        val media = selectedMediaItems.first()
        HiddenLayersEditorView(
            mediaItem = media,
            layers = hiddenLayerDrafts,
            onLayersChange = { hiddenLayerDrafts = it },
            onDismiss = { showingHiddenLayers = false },
            modifier = modifier,
        )
        return
    }

    if (pendingSheet != null) {
        CreatorFlowPendingScreen(
            iosSource = pendingSheet!!,
            onBack = { pendingSheet = null },
            onDismiss = onDismiss,
            modifier = modifier,
        )
        return
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        selectedMediaItems.firstOrNull()?.let { first ->
            AsyncImage(
                model = first.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.35f),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)))
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable { onCurrentFlowChange(CreatorFlow.MEDIA_EDITING) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.creator_new_moment),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF9C27B0), Color(0xFFE91E63), Color(0xFFFF9800))),
                        )
                        .clickable(enabled = !isPublishing) {
                            if (FirebaseAuth.getInstance().currentUser == null) return@clickable
                            isPublishing = true
                            val aspect = preferredAspectRatioLabel(selectedMediaItems)
                            val spatialTagged = selectedMediaItems.flatMap { it.tags }.map { it.userId }
                            val allTagged = spatialTagged.distinct().takeIf { it.isNotEmpty() }
                            val actionId = BackgroundMomentUploadService.uploadMoment(
                                content = captionText,
                                mediaItems = selectedMediaItems,
                                taggedUsers = allTagged,
                                location = locationName.ifBlank { null },
                                locationCoordinate = selectedLocation,
                                audienceSetting = audience.raw,
                                customViewers = customSelectedUsers.takeIf { it.isNotEmpty() },
                                customListId = selectedListId,
                                aspectRatio = aspect,
                                disableComments = disableComments,
                                hideLikeCounts = hideLikeCounts,
                                allowSharing = allowSharing,
                                hiddenLayers = if (canUseHiddenLayers) {
                                    hiddenLayerDrafts.filter { it.isReadyToPublish }.map { it.toCached() }
                                        .takeIf { it.isNotEmpty() }
                                } else {
                                    null
                                },
                            )
                            scope.launch {
                                if (actionId != null) {
                                    isLaunching = true
                                    HapticManager.shared.success()
                                    delay(1200)
                                    isPublishing = false
                                    onDismiss()
                                } else {
                                    HapticManager.shared.warning()
                                    isPublishing = false
                                }
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isPublishing && !isLaunching) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Text(
                            stringResource(R.string.creator_share),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(30.dp),
                ) {
                    Box(
                        Modifier
                            .width(100.dp)
                            .height(150.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isPreviewingMedia = true
                                        tryAwaitRelease()
                                        isPreviewingMedia = false
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        MediaStackPreview(items = selectedMediaItems)
                        if (!isPreviewingMedia) {
                            Text(
                                stringResource(R.string.creator_media_preview_hint),
                                color = Color.White.copy(0.7f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                                    .background(Color.Black.copy(0.45f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                    TextField(
                        value = captionText,
                        onValueChange = { captionText = it },
                        placeholder = {
                            Text(stringResource(R.string.creator_caption_placeholder), color = Color.White.copy(0.6f))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                }

                Column(Modifier.padding(top = 10.dp)) {
                    MinimalOptionRow(
                        icon = Icons.Filled.PersonAdd,
                        title = stringResource(R.string.creator_tag_people),
                        value = if (tagCount > 0) {
                            stringResource(R.string.creator_tag_count, tagCount)
                        } else {
                            null
                        },
                        enabled = selectedMediaItems.any { !it.isVideo },
                        onClick = { showingTagSelector = true },
                    )
                    OptionDivider()
                    MinimalOptionRow(
                        icon = Icons.Filled.LocationOn,
                        title = stringResource(R.string.creator_add_location),
                        value = locationName.ifBlank { null },
                        onClick = { showingLocationPicker = true },
                    )
                    OptionDivider()
                    MinimalOptionRow(
                        icon = Icons.Filled.Layers,
                        title = stringResource(R.string.creator_hidden_layers),
                        value = when {
                            !canUseHiddenLayers -> stringResource(R.string.creator_hidden_layers_single_only)
                            readyHiddenCount > 0 -> stringResource(R.string.creator_hidden_layer_count, readyHiddenCount, 3)
                            else -> null
                        },
                        enabled = canUseHiddenLayers,
                        onClick = { showingHiddenLayers = true },
                    )
                    OptionDivider()
                    MinimalOptionRow(
                        icon = Icons.Filled.People,
                        title = stringResource(R.string.creator_audience),
                        value = when {
                            audience == ContentAudience.CUSTOM_LIST && !selectedListName.isNullOrBlank() -> selectedListName
                            else -> audienceLabel(audience)
                        },
                        onClick = { showingAudience = true },
                    )
                }

                Column(Modifier.padding(top = 25.dp)) {
                    SectionLabel(stringResource(R.string.creator_interactions_title))
                    MinimalToggleRow(
                        icon = Icons.Filled.ChatBubbleOutline,
                        title = stringResource(R.string.creator_disable_comments),
                        checked = disableComments,
                        onCheckedChange = { disableComments = it },
                    )
                    OptionDivider()
                    MinimalToggleRow(
                        icon = Icons.Filled.FavoriteBorder,
                        title = stringResource(R.string.creator_hide_reactions),
                        checked = hideLikeCounts,
                        onCheckedChange = { hideLikeCounts = it },
                    )
                    OptionDivider()
                    MinimalToggleRow(
                        icon = Icons.Filled.Share,
                        title = stringResource(R.string.creator_allow_sharing),
                        checked = allowSharing,
                        onCheckedChange = { allowSharing = it },
                    )
                }

                Column(Modifier.padding(top = 25.dp)) {
                    SectionLabel(stringResource(R.string.creator_scheduling_title))
                    MinimalToggleRow(
                        icon = Icons.Filled.CalendarMonth,
                        title = stringResource(R.string.creator_scheduling_enable),
                        checked = isSchedulingEnabled,
                        onCheckedChange = { isSchedulingEnabled = it },
                    )
                    if (isSchedulingEnabled) {
                        OptionDivider()
                        Text(
                            stringResource(R.string.creator_scheduling_pending),
                            color = Color.White.copy(0.6f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }

        if (isPublishing && !isLaunching) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.creator_publishing), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (isLaunching) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.creator_upload_success_fly),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }
        if (isPreviewingMedia) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = selectedMediaItems.firstOrNull()?.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(20.dp)),
                )
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val keep = onSelectedMediaItemsChange
}

@Composable
private fun MediaStackPreview(items: List<CreatorMedia>) {
    Box(Modifier.size(100.dp, 150.dp), contentAlignment = Alignment.Center) {
        items.take(3).asReversed().forEachIndexed { index, item ->
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(100.dp, 150.dp)
                    .rotate(index * 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(0.6f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun OptionDivider() {
    HorizontalDivider(
        Modifier.padding(start = 50.dp),
        color = Color.White.copy(0.1f),
    )
}

@Composable
private fun MinimalOptionRow(
    icon: ImageVector,
    title: String,
    value: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = Color.White, modifier = Modifier.weight(1f), fontSize = 15.sp)
        if (value != null) {
            Text(value, color = Color.White.copy(0.55f), fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MinimalToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = Color.White, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFE91E63),
            ),
        )
    }
}

@Composable
private fun audienceLabel(audience: ContentAudience): String = when (audience) {
    ContentAudience.EVERYONE -> stringResource(R.string.audience_type_everyone)
    ContentAudience.MUTUALS -> stringResource(R.string.audience_type_mutuals)
    ContentAudience.BEST_FRIENDS -> stringResource(R.string.audience_type_best_friends)
    ContentAudience.CUSTOM, ContentAudience.CUSTOM_LIST -> stringResource(R.string.audience_type_custom)
    ContentAudience.ONLY_ME -> stringResource(R.string.audience_type_only_me)
}

private fun preferredAspectRatioLabel(items: List<CreatorMedia>): String {
    if (items.isEmpty()) return "1:1"
    val preferred = items.map { it.recommendedAspectRatio ?: it.aspectRatio }
    return preferred.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.displayName ?: "1:1"
}
