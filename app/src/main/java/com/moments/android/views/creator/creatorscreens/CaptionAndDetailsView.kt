package com.moments.android.views.creator.creatorscreens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MentionDraftToken
import com.moments.android.utilities.MentionParsing
import com.moments.android.utilities.MomentMentionResolver
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.comments.CommentMentionDraft
import com.moments.android.views.comments.CommentMentionSearchOverlay
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.creator.BackgroundMomentUploadService
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorFlow
import com.moments.android.views.creator.CreatorMedia
import com.moments.android.views.creator.GlowSharePill
import com.moments.android.views.creator.HiddenLayerDraft
import com.moments.android.views.creator.HiddenLayersEditorView
import com.moments.android.views.creator.PhotoTagSelectionView
import com.moments.android.views.creator.audienceselector.AudienceSelectionView
import com.moments.android.views.creator.audienceselector.ContentAudience
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

/**
 * Port de `CaptionAndDetailsView.swift`.
 *
 * Fondo: iOS usa mosaic blur/transparencias; Android = canvas sólido
 * AdaptiveColors (`#0B1215` / `#FAF9F6`) — decisión de plataforma.
 */
@Composable
fun CaptionAndDetailsView(
    selectedMediaItems: List<CreatorMedia>,
    onSelectedMediaItemsChange: (List<CreatorMedia>) -> Unit,
    onCurrentFlowChange: (CreatorFlow) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(CREATOR_INTERACTION_PREFS, Context.MODE_PRIVATE)
    }
    val isDark = isSystemInDarkTheme()
    val canvas = rememberAdaptiveColors().surfaceBackground
    val primary = MomentsChromeGlass.contentColor(isDark)
    val secondary = primary.copy(alpha = 0.60f)
    val muted = primary.copy(alpha = 0.55f)
    val divider = primary.copy(alpha = 0.10f)

    var captionText by remember { mutableStateOf("") }
    var taggedUsers by remember { mutableStateOf<List<String>>(emptyList()) }
    var locationName by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<Moment.LocationCoordinate?>(null) }
    var audience by remember { mutableStateOf(ContentAudience.EVERYONE) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var selectedListName by remember { mutableStateOf<String?>(null) }
    var customSelectedUsers by remember { mutableStateOf<List<String>>(emptyList()) }
    var disableComments by remember {
        mutableStateOf(prefs.getBoolean("disableComments", false))
    }
    var hideLikeCounts by remember {
        mutableStateOf(prefs.getBoolean("hideLikeCounts", false))
    }
    var allowSharing by remember {
        mutableStateOf(prefs.getBoolean("allowSharing", true))
    }
    var isSchedulingEnabled by remember { mutableStateOf(false) }
    var scheduledMillis by remember {
        mutableLongStateOf(System.currentTimeMillis() + 3_600_000L)
    }
    var isPublishing by remember { mutableStateOf(false) }
    var isLaunching by remember { mutableStateOf(false) }
    var isPreviewingMedia by remember { mutableStateOf(false) }
    var showingLocationPicker by remember { mutableStateOf(false) }
    var showingAudience by remember { mutableStateOf(false) }
    var showingTagSelector by remember { mutableStateOf(false) }
    var showingHiddenLayers by remember { mutableStateOf(false) }
    var hiddenLayerDrafts by remember { mutableStateOf<List<HiddenLayerDraft>>(emptyList()) }
    var activeCaptionMention by remember { mutableStateOf<MentionDraftToken?>(null) }
    val scope = rememberCoroutineScope()

    val canUseHiddenLayers = selectedMediaItems.size == 1 && selectedMediaItems.none { it.isVideo }
    val totalTagsCount = selectedMediaItems.sumOf { it.tags.size } + taggedUsers.size

    LaunchedEffect(Unit) {
        loadDefaultPostAudience(
            onAudience = { audience = it },
            onListId = { selectedListId = it },
            onListName = { selectedListName = it },
            onCustomUsers = { customSelectedUsers = it },
        )
    }
    LaunchedEffect(selectedMediaItems.map { it.id }) {
        if (!canUseHiddenLayers) hiddenLayerDrafts = emptyList()
    }
    LaunchedEffect(captionText) {
        activeCaptionMention = MentionParsing.detectActiveToken(captionText)
    }

    fun persistInteraction(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    Box(modifier.fillMaxSize().background(canvas)) {
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
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        null,
                        tint = primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.creator_new_moment),
                    color = primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.weight(1f))
                GlowSharePill(
                    titleRes = R.string.creator_share,
                    isLoading = isPublishing && !isLaunching,
                    onClick = {
                        if (FirebaseAuth.getInstance().currentUser == null) return@GlowSharePill
                        isPublishing = true
                        val aspect = preferredMomentAspectRatio(selectedMediaItems)
                        val spatialTagged = selectedMediaItems.flatMap { it.tags }.map { it.userId }
                        val captionSnapshot = captionText
                        val mediaSnapshot = selectedMediaItems
                        val audienceSnapshot = audience
                        val customSnapshot = customSelectedUsers.takeIf { it.isNotEmpty() }
                        val listIdSnapshot = selectedListId
                        val locationSnapshot = locationName.ifBlank { null }
                        val coordinateSnapshot = selectedLocation
                        val disableSnapshot = disableComments
                        val hideSnapshot = hideLikeCounts
                        val shareSnapshot = allowSharing
                        val scheduleSnapshot =
                            if (isSchedulingEnabled) Date(scheduledMillis) else null
                        val hiddenSnapshot = if (canUseHiddenLayers) {
                            hiddenLayerDrafts.filter { it.isReadyToPublish }
                        } else {
                            emptyList()
                        }
                        val manualTagged = taggedUsers
                        scope.launch {
                            val captionMentionIds = withContext(Dispatchers.IO) {
                                MomentMentionResolver.resolveUserIds(captionSnapshot)
                            }
                            val allTagged =
                                (manualTagged + spatialTagged).toSet().toList()
                            val uploading = BackgroundMomentUploadService.uploadMoment(
                                content = captionSnapshot,
                                mediaItems = mediaSnapshot,
                                taggedUsers = allTagged.takeIf { it.isNotEmpty() },
                                mentionedUsers = captionMentionIds.takeIf { it.isNotEmpty() },
                                location = locationSnapshot,
                                locationCoordinate = coordinateSnapshot,
                                audienceSetting = audienceSnapshot.raw,
                                customViewers = customSnapshot,
                                customListId = listIdSnapshot,
                                aspectRatio = aspect,
                                disableComments = disableSnapshot,
                                hideLikeCounts = hideSnapshot,
                                allowSharing = shareSnapshot,
                                scheduledDate = scheduleSnapshot,
                                hiddenLayers = hiddenSnapshot.takeIf { it.isNotEmpty() },
                            )
                            if (uploading != null) {
                                isLaunching = true
                                HapticManager.shared.success()
                                delay(1_200)
                                isPublishing = false
                                onDismiss()
                            } else {
                                HapticManager.shared.warning()
                                isLaunching = false
                                isPublishing = false
                            }
                        }
                    },
                )
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
                                color = Color.White.copy(alpha = 0.70f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                    TextField(
                        value = captionText,
                        onValueChange = { captionText = it },
                        placeholder = {
                            Text(
                                stringResource(R.string.creator_caption_placeholder),
                                color = secondary,
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = primary,
                            unfocusedTextColor = primary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = primary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                }

                Column(Modifier.padding(top = 10.dp)) {
                    MinimalOptionRow(
                        icon = Icons.Filled.PersonAdd,
                        title = stringResource(R.string.creator_tag_people),
                        value = if (totalTagsCount > 0) {
                            stringResource(R.string.creator_tag_count, totalTagsCount)
                        } else {
                            null
                        },
                        primary = primary,
                        muted = muted,
                        enabled = selectedMediaItems.any { !it.isVideo },
                        onClick = { showingTagSelector = true },
                    )
                    OptionDivider(divider)
                    MinimalOptionRow(
                        icon = Icons.Filled.LocationOn,
                        title = stringResource(R.string.creator_add_location),
                        value = locationName.ifBlank { null },
                        primary = primary,
                        muted = muted,
                        onClick = { showingLocationPicker = true },
                    )
                    OptionDivider(divider)
                    MinimalOptionRow(
                        icon = Icons.Filled.Layers,
                        title = stringResource(R.string.creator_hidden_layers),
                        value = when {
                            !canUseHiddenLayers -> stringResource(R.string.creator_hidden_layers_single_only)
                            hiddenLayerDrafts.isNotEmpty() ->
                                stringResource(
                                    R.string.creator_hidden_layers_count,
                                    hiddenLayerDrafts.size,
                                )
                            else -> null
                        },
                        primary = primary,
                        muted = muted,
                        enabled = canUseHiddenLayers,
                        onClick = { showingHiddenLayers = true },
                    )
                    OptionDivider(divider)
                    MinimalOptionRow(
                        icon = null,
                        audience = audience,
                        title = stringResource(R.string.creator_audience),
                        value = when {
                            audience == ContentAudience.CUSTOM_LIST &&
                                !selectedListName.isNullOrBlank() -> selectedListName
                            audience == ContentAudience.CUSTOM &&
                                customSelectedUsers.isNotEmpty() ->
                                stringResource(
                                    R.string.audience_people_count,
                                    customSelectedUsers.size,
                                )
                            else -> audienceLabel(audience)
                        },
                        primary = primary,
                        muted = muted,
                        onClick = { showingAudience = true },
                    )
                }

                Column(Modifier.padding(top = 25.dp)) {
                    SectionLabel(stringResource(R.string.creator_interactions_title), secondary)
                    MinimalToggleRow(
                        icon = Icons.Filled.ChatBubbleOutline,
                        title = stringResource(R.string.creator_disable_comments),
                        checked = disableComments,
                        primary = primary,
                        onCheckedChange = {
                            disableComments = it
                            persistInteraction("disableComments", it)
                        },
                    )
                    OptionDivider(divider)
                    MinimalToggleRow(
                        icon = Icons.Filled.FavoriteBorder,
                        title = stringResource(R.string.creator_hide_reactions),
                        checked = hideLikeCounts,
                        primary = primary,
                        onCheckedChange = {
                            hideLikeCounts = it
                            persistInteraction("hideLikeCounts", it)
                        },
                    )
                    OptionDivider(divider)
                    MinimalToggleRow(
                        icon = Icons.Filled.Share,
                        title = stringResource(R.string.creator_allow_sharing),
                        checked = allowSharing,
                        primary = primary,
                        onCheckedChange = {
                            allowSharing = it
                            persistInteraction("allowSharing", it)
                        },
                    )
                }

                Column(Modifier.padding(top = 25.dp)) {
                    SectionLabel(stringResource(R.string.creator_scheduling_title), secondary)
                    MinimalToggleRow(
                        icon = Icons.Filled.CalendarMonth,
                        title = stringResource(R.string.creator_scheduling_enable),
                        checked = isSchedulingEnabled,
                        primary = primary,
                        onCheckedChange = { isSchedulingEnabled = it },
                    )
                    if (isSchedulingEnabled) {
                        OptionDivider(divider)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pickScheduleDateTime(context, scheduledMillis) {
                                        scheduledMillis = it
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Schedule,
                                null,
                                tint = secondary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                MomentsFormat.smartDate(
                                    from = Date(scheduledMillis),
                                    context = MomentsFormat.DateContext.MEDIUM_DATE_TIME,
                                ),
                                color = secondary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }

        if (isPublishing && !isLaunching) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.60f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.creator_publishing),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        if (isLaunching) {
            Box(
                Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.creator_upload_success_fly),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }
        if (isPreviewingMedia) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.60f)),
                contentAlignment = Alignment.Center,
            ) {
                val pager = rememberPagerState { selectedMediaItems.size.coerceAtLeast(1) }
                HorizontalPager(
                    state = pager,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp),
                ) { page ->
                    val item = selectedMediaItems.getOrNull(page) ?: return@HorizontalPager
                    AsyncImage(
                        model = item.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(20.dp)),
                    )
                }
            }
        }

        activeCaptionMention?.let { token ->
            CommentMentionSearchOverlay(
                query = token.query,
                showsSearchField = false,
                onSelect = { user ->
                    val (newText, _) = CommentMentionDraft.insertMention(user, token, captionText)
                    captionText = newText
                    if (user.id !in taggedUsers) taggedUsers = taggedUsers + user.id
                    activeCaptionMention = null
                    HapticManager.shared.selection()
                },
                onCancel = { activeCaptionMention = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        // Sheets ≡ iOS `.sheet` (no full-screen replace)
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
        if (showingAudience) {
            // ≡ iOS `.presentationDetents([.medium, .large])`
            MomentsModalSheet(
                onDismissRequest = {
                    showingAudience = false
                    scope.launch {
                        updateAudienceSetting(
                            audience = audience,
                            selectedListId = selectedListId,
                            selectedListName = selectedListName,
                            customSelectedUsers = customSelectedUsers,
                        )
                    }
                },
                largeOnly = false,
            ) {
                AudienceSelectionView(
                    selectedAudience = audience,
                    selectedListId = selectedListId,
                    selectedListName = selectedListName,
                    customSelectedUsers = customSelectedUsers,
                    onSelectedAudienceChange = { audience = it },
                    onSelectedListIdChange = { selectedListId = it },
                    onSelectedListNameChange = { selectedListName = it },
                    onCustomSelectedUsersChange = { customSelectedUsers = it },
                    onDismiss = {
                        showingAudience = false
                        scope.launch {
                            updateAudienceSetting(
                                audience = audience,
                                selectedListId = selectedListId,
                                selectedListName = selectedListName,
                                customSelectedUsers = customSelectedUsers,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
        if (showingTagSelector) {
            val media = selectedMediaItems.firstOrNull { !it.isVideo }
            if (media == null) {
                LaunchedEffect(Unit) { showingTagSelector = false }
            } else {
                MomentsModalSheet(onDismissRequest = { showingTagSelector = false }) {
                    PhotoTagSelectionView(
                        mediaItem = media,
                        onMediaItemChange = { updated ->
                            onSelectedMediaItemsChange(
                                selectedMediaItems.map { if (it.id == updated.id) updated else it },
                            )
                        },
                        onDismiss = { showingTagSelector = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
        if (showingHiddenLayers && canUseHiddenLayers) {
            MomentsModalSheet(
                onDismissRequest = { showingHiddenLayers = false },
                largeOnly = true,
                showDragHandle = false,
            ) {
                HiddenLayersEditorView(
                    mediaItem = selectedMediaItems.first(),
                    layers = hiddenLayerDrafts,
                    onLayersChange = { hiddenLayerDrafts = it },
                    onDismiss = { showingHiddenLayers = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

private const val CREATOR_INTERACTION_PREFS = "moments_creator_interactions"

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
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun OptionDivider(color: Color) {
    HorizontalDivider(Modifier.padding(start = 50.dp), color = color)
}

@Composable
private fun MinimalOptionRow(
    icon: ImageVector?,
    title: String,
    value: String?,
    primary: Color,
    muted: Color,
    enabled: Boolean = true,
    audience: ContentAudience? = null,
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
        if (audience != null) {
            AudienceIconView(audience = audience, size = AudienceIconMetrics.creatorRow)
        } else if (icon != null) {
            Icon(icon, null, tint = primary.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = primary, modifier = Modifier.weight(1f), fontSize = 15.sp)
        if (value != null) {
            Text(value, color = muted, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = primary.copy(alpha = 0.40f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun MinimalToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    primary: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = primary.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = primary, modifier = Modifier.weight(1f), fontSize = 15.sp)
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
    ContentAudience.CUSTOM, ContentAudience.CUSTOM_LIST ->
        stringResource(R.string.audience_type_custom)
    ContentAudience.ONLY_ME -> stringResource(R.string.audience_type_only_me)
}

/** ≡ `preferredMomentAspectRatio` — ratio más vertical. */
private fun preferredMomentAspectRatio(items: List<CreatorMedia>): String {
    if (items.isEmpty()) return "1:1"
    val preferred = items.map { it.recommendedAspectRatio ?: it.aspectRatio }
    val mostVertical = preferred.minByOrNull { it.ratio } ?: CreatorAspectRatio.SQUARE
    return mostVertical.displayName
}

private fun pickScheduleDateTime(context: Context, currentMillis: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = currentMillis }
    DatePickerDialog(
        context,
        { _, y, m, d ->
            cal.set(Calendar.YEAR, y)
            cal.set(Calendar.MONTH, m)
            cal.set(Calendar.DAY_OF_MONTH, d)
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)
                    cal.set(Calendar.SECOND, 0)
                    val min = System.currentTimeMillis()
                    onPicked(cal.timeInMillis.coerceAtLeast(min))
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true,
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH),
    ).apply {
        datePicker.minDate = System.currentTimeMillis()
    }.show()
}

/** ≡ `loadDefaultPostAudience`. */
private suspend fun loadDefaultPostAudience(
    onAudience: (ContentAudience) -> Unit,
    onListId: (String?) -> Unit,
    onListName: (String?) -> Unit,
    onCustomUsers: (List<String>) -> Unit,
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    runCatching {
        val snap = FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .await()
        val visibility = snap.get("contentVisibilitySettings") as? Map<*, *> ?: return
        val raw = visibility["postAudience"] as? String
        val content = ContentAudience.entries.firstOrNull { it.raw == raw } ?: return
        when (content) {
            ContentAudience.CUSTOM -> {
                onAudience(ContentAudience.CUSTOM)
                onCustomUsers(
                    (visibility["postCustomUsers"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                )
            }
            ContentAudience.CUSTOM_LIST -> {
                onAudience(ContentAudience.CUSTOM_LIST)
                onListId(visibility["postCustomListId"] as? String)
                onListName(visibility["postCustomListName"] as? String)
            }
            else -> onAudience(content)
        }
    }
}

/** ≡ `updateAudienceSetting`. */
private suspend fun updateAudienceSetting(
    audience: ContentAudience,
    selectedListId: String?,
    selectedListName: String?,
    customSelectedUsers: List<String>,
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val update = mutableMapOf<String, Any>(
        "contentVisibilitySettings.postAudience" to audience.raw,
    )
    if (selectedListId != null) {
        update["contentVisibilitySettings.postCustomListId"] = selectedListId
        update["contentVisibilitySettings.postCustomListName"] = selectedListName.orEmpty()
    }
    if (customSelectedUsers.isNotEmpty()) {
        update["contentVisibilitySettings.postCustomUsers"] = customSelectedUsers
    }
    runCatching {
        FirebaseFirestore.getInstance().collection("users").document(uid).update(update).await()
    }
}
