package com.moments.android.views.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VisibilityOff
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.searchUsers
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.creator.audienceselector.AudienceSelectionView
import com.moments.android.views.creator.audienceselector.ContentAudience
import com.moments.android.views.creator.audienceselector.CustomAudienceListsView
import com.moments.android.views.profile.core.sections.profileThumbnailUrl
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.settings.sections.SettingsDividerStart
import com.moments.android.views.settings.sections.SettingsSubsectionGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Port 1:1 de `ContentVisibilityView.swift` (853 líneas):
 * pantalla + interacciones + selectores + HiddenFrom + ViewModel.
 */
@Composable
fun ContentVisibilityView(onNavigateBack: () -> Unit = {}) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val sectionLabel = if (isDark) Color.White.copy(0.45f) else Color.Black.copy(0.35f)
    val viewModel = remember { ContentVisibilityViewModel() }

    var isLoading by remember { mutableStateOf(true) }
    var showingStoryAudience by remember { mutableStateOf(false) }
    var showingPostAudience by remember { mutableStateOf(false) }
    var showingStoryInteractions by remember { mutableStateOf(false) }
    var showingCustomLists by remember { mutableStateOf(false) }
    var showingHiddenFrom by remember { mutableStateOf(false) }
    var showingHiddenWords by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadSettings { isLoading = false }
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.content_visibility_title),
        onNavigateBack = onNavigateBack,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (isLoading) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    MomentsCircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.content_visibility_loading),
                        fontSize = 16.sp,
                        color = Color.Gray,
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // MARK: Stories
                    SettingsSubsectionGroup(
                        title = stringResource(R.string.content_visibility_stories_title),
                    ) {
                        Column {
                            Box(Modifier.clickable { showingStoryAudience = true }) {
                                CurrentAudienceRow(
                                    audience = viewModel.storyAudience,
                                    customListName = viewModel.storyCustomListName,
                                    customCount = viewModel.storyCustomUsers.size,
                                    primary = primary,
                                )
                            }
                            HorizontalDivider(
                                Modifier.padding(start = SettingsDividerStart),
                                color = primary.copy(alpha = 0.2f),
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showingStoryInteractions = true }
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    null,
                                    tint = primary,
                                    modifier = Modifier.width(28.dp),
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        stringResource(R.string.content_visibility_interactions_title),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primary,
                                    )
                                    Text(
                                        stringResource(interactionSummaryRes(viewModel)),
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            HorizontalDivider(
                                Modifier.padding(start = SettingsDividerStart),
                                color = primary.copy(alpha = 0.2f),
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showingHiddenWords = true }
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    Icons.Filled.TextFields,
                                    null,
                                    tint = primary,
                                    modifier = Modifier.width(28.dp),
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        stringResource(R.string.message_requests_hidden_words_title),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primary,
                                    )
                                    Text(
                                        stringResource(R.string.message_requests_hidden_words_description),
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    // MARK: Posts
                    SettingsSubsectionGroup(
                        title = stringResource(R.string.content_visibility_posts_title),
                    ) {
                        Box(Modifier.clickable { showingPostAudience = true }) {
                            CurrentAudienceRow(
                                audience = viewModel.postAudience,
                                customListName = viewModel.postCustomListName,
                                customCount = viewModel.postCustomUsers.size,
                                primary = primary,
                            )
                        }
                    }

                    // MARK: Additional restrictions
                    SettingsSubsectionGroup(
                        title = stringResource(R.string.content_visibility_additional_restrictions),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showingHiddenFrom = true }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(
                                Icons.Filled.VisibilityOff,
                                null,
                                tint = primary,
                                modifier = Modifier.width(28.dp),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(R.string.content_visibility_hide_from),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primary,
                                )
                                Text(
                                    stringResource(
                                        R.string.content_visibility_hidden_count,
                                        viewModel.hiddenFromUsers.size,
                                    ),
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                )
                            }
                        }
                    }

                    // MARK: Audience lists
                    SettingsSubsectionGroup(
                        title = stringResource(R.string.content_visibility_audience_lists),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showingCustomLists = true }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                                AudienceIconView(
                                    audience = ContentAudience.CUSTOM_LIST,
                                    size = AudienceIconMetrics.row,
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(R.string.content_visibility_manage_custom_lists),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primary,
                                )
                                Text(
                                    stringResource(R.string.content_visibility_create_edit_audience),
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showingStoryAudience) {
        MomentsModalSheet(
            onDismissRequest = { showingStoryAudience = false },
            largeOnly = true,
        ) { dismiss ->
            StoryAudienceSelector(
                viewModel = viewModel,
                onDone = {
                    viewModel.saveStorySettings()
                    dismiss()
                },
            )
        }
    }

    if (showingPostAudience) {
        MomentsModalSheet(
            onDismissRequest = { showingPostAudience = false },
            largeOnly = true,
        ) { dismiss ->
            PostAudienceSelector(
                viewModel = viewModel,
                onDone = {
                    viewModel.savePostSettings()
                    dismiss()
                },
            )
        }
    }

    if (showingStoryInteractions) {
        MomentsModalSheet(
            onDismissRequest = { showingStoryInteractions = false },
            largeOnly = false,
        ) { dismiss ->
            StoryInteractionSettingsView(
                viewModel = viewModel,
                onDismiss = dismiss,
            )
        }
    }

    if (showingCustomLists) {
        MomentsModalSheet(
            onDismissRequest = { showingCustomLists = false },
            largeOnly = false,
        ) { dismiss ->
            CustomAudienceListsView(onDismiss = dismiss)
        }
    }

    if (showingHiddenFrom) {
        MomentsModalSheet(
            onDismissRequest = { showingHiddenFrom = false },
            largeOnly = false,
        ) { dismiss ->
            HiddenFromView(
                viewModel = viewModel,
                onDismiss = dismiss,
            )
        }
    }

    if (showingHiddenWords) {
        MomentsModalSheet(
            onDismissRequest = { showingHiddenWords = false },
            largeOnly = false,
        ) { dismiss ->
            HiddenWordsSettingsView(onDismiss = dismiss)
        }
    }
}

@Composable
private fun CurrentAudienceRow(
    audience: ContentAudience,
    customListName: String?,
    customCount: Int,
    primary: Color,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            AudienceIconView(audience = audience, size = AudienceIconMetrics.row)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                audienceDisplayTitle(audience, customListName),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
            )
            Text(
                audienceDisplayDescription(audience, customCount),
                fontSize = 13.sp,
                color = Color.Gray,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun audienceDisplayTitle(audience: ContentAudience, customListName: String?): String {
    if (audience == ContentAudience.CUSTOM_LIST && !customListName.isNullOrBlank()) {
        return customListName
    }
    return stringResource(audience.titleRes)
}

@Composable
private fun audienceDisplayDescription(audience: ContentAudience, customCount: Int): String =
    when (audience) {
        ContentAudience.CUSTOM ->
            if (customCount > 0) {
                stringResource(R.string.content_visibility_custom_selected, customCount)
            } else {
                stringResource(R.string.content_visibility_custom_selection)
            }
        ContentAudience.CUSTOM_LIST -> stringResource(R.string.content_visibility_custom_list)
        else -> stringResource(audience.descriptionRes)
    }

@StringRes
private fun interactionSummaryRes(viewModel: ContentVisibilityViewModel): Int {
    val active = listOf(
        viewModel.allowStoryMessages,
        viewModel.allowStoryReactions,
        viewModel.allowStoryEphemeralPhotos,
    ).count { it }
    return when (active) {
        3 -> R.string.content_visibility_interactions_all_allowed
        2 -> R.string.content_visibility_interactions_some_allowed
        1 -> R.string.content_visibility_interactions_limited
        0 -> R.string.content_visibility_interactions_none
        else -> R.string.content_visibility_interactions_configure
    }
}

private val ContentAudience.titleRes: Int
    get() = when (this) {
        ContentAudience.EVERYONE -> R.string.audience_type_everyone
        ContentAudience.MUTUALS -> R.string.audience_type_mutuals
        ContentAudience.BEST_FRIENDS -> R.string.audience_type_best_friends
        ContentAudience.CUSTOM -> R.string.audience_type_custom
        ContentAudience.CUSTOM_LIST -> R.string.audience_type_custom_list
        ContentAudience.ONLY_ME -> R.string.audience_type_only_me
    }

private val ContentAudience.descriptionRes: Int
    get() = when (this) {
        ContentAudience.EVERYONE -> R.string.audience_description_everyone
        ContentAudience.MUTUALS -> R.string.audience_description_mutuals
        ContentAudience.BEST_FRIENDS -> R.string.audience_description_best_friends
        ContentAudience.CUSTOM -> R.string.audience_description_custom
        ContentAudience.CUSTOM_LIST -> R.string.audience_description_custom_list
        ContentAudience.ONLY_ME -> R.string.audience_description_only_me
    }

// MARK: - Story / Post audience selectors

@Composable
private fun StoryAudienceSelector(viewModel: ContentVisibilityViewModel, onDone: () -> Unit) {
    AudienceSelectorHost(
        title = stringResource(R.string.content_visibility_story_audience_nav),
        audience = viewModel.storyAudience,
        listId = viewModel.storyCustomListId,
        listName = viewModel.storyCustomListName,
        customUsers = viewModel.storyCustomUsers,
        onAudience = { viewModel.storyAudience = it },
        onListId = { viewModel.storyCustomListId = it },
        onListName = { viewModel.storyCustomListName = it },
        onCustomUsers = { viewModel.storyCustomUsers = it },
        onDone = onDone,
    )
}

@Composable
private fun PostAudienceSelector(viewModel: ContentVisibilityViewModel, onDone: () -> Unit) {
    AudienceSelectorHost(
        title = stringResource(R.string.content_visibility_post_audience_nav),
        audience = viewModel.postAudience,
        listId = viewModel.postCustomListId,
        listName = viewModel.postCustomListName,
        customUsers = viewModel.postCustomUsers,
        onAudience = { viewModel.postAudience = it },
        onListId = { viewModel.postCustomListId = it },
        onListName = { viewModel.postCustomListName = it },
        onCustomUsers = { viewModel.postCustomUsers = it },
        onDone = onDone,
    )
}

@Composable
private fun AudienceSelectorHost(
    title: String,
    audience: ContentAudience,
    listId: String?,
    listName: String?,
    customUsers: List<String>,
    onAudience: (ContentAudience) -> Unit,
    onListId: (String?) -> Unit,
    onListName: (String?) -> Unit,
    onCustomUsers: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val accent = SettingsProfileColors.accent(isDark)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color.Black,
            )
            TextButton(onClick = onDone) {
                Text(
                    stringResource(R.string.content_visibility_done),
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        AudienceSelectionView(
            selectedAudience = audience,
            selectedListId = listId,
            selectedListName = listName,
            customSelectedUsers = customUsers,
            onSelectedAudienceChange = onAudience,
            onSelectedListIdChange = onListId,
            onSelectedListNameChange = onListName,
            onCustomSelectedUsersChange = onCustomUsers,
            onDismiss = onDone,
            modifier = Modifier.weight(1f),
        )
    }
}

// MARK: - Story interactions sheet

@Composable
private fun StoryInteractionSettingsView(
    viewModel: ContentVisibilityViewModel,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 0.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.content_visibility_save),
                modifier = Modifier
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .clickable {
                        viewModel.saveStoryInteractionSettings()
                        onDismiss()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.content_visibility_interactions_config_description),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.content_visibility_interactions_config_subtitle),
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )
            }

            InteractionToggleRow(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.content_visibility_interactions_messages_title),
                description = stringResource(R.string.content_visibility_interactions_messages_description),
                isOn = viewModel.allowStoryMessages,
                onChange = { viewModel.allowStoryMessages = it },
                primary = primary,
            )
            InteractionToggleRow(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.content_visibility_interactions_reactions_title),
                description = stringResource(R.string.content_visibility_interactions_reactions_description),
                isOn = viewModel.allowStoryReactions,
                onChange = { viewModel.allowStoryReactions = it },
                primary = primary,
            )
            InteractionToggleRow(
                icon = Icons.Filled.CameraAlt,
                title = stringResource(R.string.content_visibility_interactions_ephemeral_title),
                description = stringResource(R.string.content_visibility_interactions_ephemeral_description),
                isOn = viewModel.allowStoryEphemeralPhotos,
                onChange = { viewModel.allowStoryEphemeralPhotos = it },
                primary = primary,
            )

            if (!viewModel.allowStoryMessages &&
                !viewModel.allowStoryReactions &&
                !viewModel.allowStoryEphemeralPhotos
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.content_visibility_view_only_mode),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                    )
                    Text(
                        stringResource(R.string.content_visibility_view_only_mode_description),
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractionToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    isOn: Boolean,
    onChange: (Boolean) -> Unit,
    primary: Color,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = primary, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primary)
            Text(description, fontSize = 13.sp, color = Color.Gray)
        }
        Switch(
            checked = isOn,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SettingsProfileColors.toggleTint,
            ),
        )
    }
}

// MARK: - Hidden words

@Composable
private fun HiddenWordsSettingsView(onDismiss: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val surface = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val service = remember { MessageRequestService() }
    val scope = rememberCoroutineScope()
    var automaticFilterEnabled by remember { mutableStateOf(true) }
    var wordsText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { service.loadHiddenWordsPreferences() }
            .onSuccess { preferences ->
                automaticFilterEnabled = preferences.first
                wordsText = preferences.second.joinToString("\n")
            }
            .onFailure { errorMessage = it.localizedMessage }
        isLoading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.message_requests_hidden_words_title),
                modifier = Modifier.weight(1f),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
            )
            TextButton(
                enabled = !isLoading && !isSaving,
                onClick = {
                    val words = wordsText
                        .split(',', '\n')
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                    isSaving = true
                    errorMessage = null
                    scope.launch {
                        runCatching { service.saveHiddenWords(words, automaticFilterEnabled) }
                            .onSuccess { onDismiss() }
                            .onFailure {
                                errorMessage = it.localizedMessage
                                isSaving = false
                            }
                    }
                },
            ) {
                Text(
                    stringResource(R.string.content_visibility_save),
                    fontWeight = FontWeight.SemiBold,
                    color = SettingsProfileColors.accent(isDark),
                )
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MomentsCircularProgressIndicator()
            }
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.message_requests_hidden_words_automatic),
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                    )
                    Switch(
                        checked = automaticFilterEnabled,
                        onCheckedChange = { automaticFilterEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SettingsProfileColors.toggleTint,
                        ),
                    )
                }
                Text(
                    stringResource(R.string.message_requests_hidden_words_automatic_description),
                    fontSize = 13.sp,
                    color = Color.Gray,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.message_requests_hidden_words_custom),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                )
                BasicTextField(
                    value = wordsText,
                    onValueChange = { wordsText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(surface, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    textStyle = TextStyle(fontSize = 15.sp, color = primary),
                    cursorBrush = SolidColor(SettingsProfileColors.accent(isDark)),
                )
                Text(
                    stringResource(R.string.message_requests_hidden_words_custom_description),
                    fontSize = 13.sp,
                    color = Color.Gray,
                )
            }

            errorMessage?.takeIf(String::isNotBlank)?.let { message ->
                Text(message, fontSize = 13.sp, color = Color.Red)
            }
        }
    }
}

// MARK: - Hidden from

@Composable
private fun HiddenFromView(
    viewModel: ContentVisibilityViewModel,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val sectionLabel = if (isDark) Color.White.copy(0.45f) else Color.Black.copy(0.35f)
    val firestore = remember { FirestoreService() }

    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchText) {
        if (searchText.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(250)
        searchResults = withContext(Dispatchers.IO) {
            runCatching { firestore.searchUsers(searchText, limit = 10) }.getOrDefault(emptyList())
        }
        isSearching = false
    }

    fun persistHidden() {
        viewModel.saveStorySettings()
        viewModel.savePostSettings()
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 0.dp, bottom = 8.dp),
        ) {
            Text(
                stringResource(R.string.content_visibility_hide_content_nav),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.content_visibility_info_description),
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            SettingsSearchField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = stringResource(R.string.audience_picker_searchPlaceholder),
            )

            if (isSearching) {
                MomentsCircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp),
                )
            } else {
                if (viewModel.hiddenFromUsers.isNotEmpty()) {
                    Text(
                        stringResource(R.string.content_visibility_hidden_users_section).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = sectionLabel,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    viewModel.hiddenFromUsers.forEach { user ->
                        ContentVisibilityUserRow(
                            user = user,
                            isSelected = true,
                            primary = primary,
                            onTap = {
                                viewModel.hiddenFromUsers =
                                    viewModel.hiddenFromUsers.filter { it.id != user.id }
                                persistHidden()
                            },
                        )
                    }
                }

                if (searchResults.isNotEmpty()) {
                    Text(
                        stringResource(R.string.content_visibility_search_results_section).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = sectionLabel,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    searchResults.forEach { user ->
                        val isHidden = viewModel.hiddenFromUsers.any { it.id == user.id }
                        ContentVisibilityUserRow(
                            user = user,
                            isSelected = isHidden,
                            primary = primary,
                            onTap = {
                                viewModel.hiddenFromUsers = if (isHidden) {
                                    viewModel.hiddenFromUsers.filter { it.id != user.id }
                                } else {
                                    viewModel.hiddenFromUsers + user
                                }
                                persistHidden()
                            },
                        )
                    }
                } else if (viewModel.hiddenFromUsers.isEmpty() && searchText.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.VisibilityOff,
                            null,
                            tint = Color.Gray,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            stringResource(R.string.content_visibility_no_hidden_users_title),
                            fontSize = 16.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.content_visibility_no_hidden_users_description),
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentVisibilityUserRow(
    user: AppUser,
    isSelected: Boolean,
    primary: Color,
    onTap: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val image = user.profileImagePath
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = profileThumbnailUrl(image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .background(Color.Gray.copy(0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(user.username, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primary)
            val bio = user.bio
            if (!bio.isNullOrBlank()) {
                Text(bio, fontSize = 13.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) primary else Color.Gray,
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onTap),
        )
    }
}
