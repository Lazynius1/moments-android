package com.moments.android.views.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.searchUsers
import com.moments.android.views.profile.core.sections.profileThumbnailUrl
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.delay

/**
 * Port 1:1 de `MuteSettingsView.swift` (629 líneas).
 * Sheets iOS → [MomentsModalSheet].
 */
@Composable
fun MuteSettingsView(
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val accent = SettingsProfileColors.accent(isDark)
    val viewModel = remember { MuteSettingsViewModel() }

    var isLoading by remember { mutableStateOf(true) }
    var showAddMutedUser by remember { mutableStateOf(false) }
    var showAddMutedWord by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadSettings { isLoading = false }
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.mute_settings_navigation_title),
        onNavigateBack = onNavigateBack,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (isLoading) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = textColor)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.mute_settings_loading),
                        fontSize = 16.sp,
                        color = Color.Gray,
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // Muted users
                    MuteSectionCard(isDark = isDark) {
                        MuteSectionHeader(
                            title = stringResource(R.string.mute_settings_muted_accounts_title),
                            description = stringResource(R.string.mute_settings_muted_accounts_description),
                            textColor = textColor,
                            accent = accent,
                            onAdd = { showAddMutedUser = true },
                        )
                        if (viewModel.mutedUsers.isEmpty()) {
                            MuteEmptyState(
                                icon = { Icon(Icons.Default.PersonOff, null, tint = Color.Gray, modifier = Modifier.size(30.dp)) },
                                message = stringResource(R.string.mute_settings_no_muted_users),
                            )
                        } else {
                            viewModel.mutedUsers.forEach { user ->
                                MutedUserRow(
                                    user = user,
                                    textColor = textColor,
                                    accent = accent,
                                    onUnmute = { viewModel.unmuteUser(user.id) },
                                )
                                HorizontalDivider(color = textColor.copy(alpha = 0.08f))
                            }
                        }
                    }

                    // Muted words
                    MuteSectionCard(isDark = isDark) {
                        MuteSectionHeader(
                            title = stringResource(R.string.mute_settings_muted_words_title),
                            description = stringResource(R.string.mute_settings_muted_words_description),
                            textColor = textColor,
                            accent = accent,
                            onAdd = { showAddMutedWord = true },
                        )
                        if (viewModel.mutedWords.isEmpty()) {
                            MuteEmptyState(
                                icon = { Icon(Icons.Default.TextFields, null, tint = Color.Gray, modifier = Modifier.size(30.dp)) },
                                message = stringResource(R.string.mute_settings_no_muted_words),
                            )
                        } else {
                            viewModel.mutedWords.forEach { word ->
                                MutedWordRow(
                                    word = word,
                                    textColor = textColor,
                                    onRemove = { viewModel.removeMutedWord(word) },
                                )
                                HorizontalDivider(color = textColor.copy(alpha = 0.08f))
                            }
                        }
                    }

                    // Additional options
                    Text(
                        stringResource(R.string.mute_settings_additional_options),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    MuteSectionCard(isDark = isDark) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.Settings, null, tint = textColor, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.mute_settings_configuration_title),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        MuteToggleRow(
                            title = stringResource(R.string.mute_settings_notifications_title),
                            description = stringResource(R.string.mute_settings_notifications_description),
                            checked = viewModel.muteNotifications,
                            textColor = textColor,
                            onCheckedChange = {
                                viewModel.muteNotifications = it
                                viewModel.saveSettings()
                            },
                        )
                        Spacer(Modifier.height(16.dp))
                        MuteToggleRow(
                            title = stringResource(R.string.mute_settings_hide_from_search_title),
                            description = stringResource(R.string.mute_settings_hide_from_search_description),
                            checked = viewModel.hideFromSearch,
                            textColor = textColor,
                            onCheckedChange = {
                                viewModel.hideFromSearch = it
                                viewModel.saveSettings()
                            },
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showAddMutedUser) {
        MomentsModalSheet(
            onDismissRequest = { showAddMutedUser = false },
            largeOnly = true,
        ) {
            AddMutedUserSheet(
                viewModel = viewModel,
                onDismiss = { showAddMutedUser = false },
            )
        }
    }
    if (showAddMutedWord) {
        MomentsModalSheet(
            onDismissRequest = { showAddMutedWord = false },
            largeOnly = false,
        ) {
            AddMutedWordSheet(
                viewModel = viewModel,
                onDismiss = { showAddMutedWord = false },
            )
        }
    }
}

@Composable
private fun MuteSectionCard(
    isDark: Boolean,
    content: @Composable () -> Unit,
) {
    val fill = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.04f)
    Column(
        Modifier
            .fillMaxWidth()
            .background(fill, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun MuteSectionHeader(
    title: String,
    description: String,
    textColor: Color,
    accent: Color,
    onAdd: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            Text(description, fontSize = 14.sp, color = Color.Gray)
        }
        Icon(
            Icons.Default.AddCircle,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onAdd),
        )
    }
}

@Composable
private fun MuteEmptyState(
    icon: @Composable () -> Unit,
    message: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(message, fontSize = 14.sp, color = Color.Gray)
    }
}

@Composable
private fun MuteToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    textColor: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
            Text(description, fontSize = 13.sp, color = Color.Gray)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SettingsProfileColors.toggleTint,
            ),
        )
    }
}

@Composable
private fun MutedUserRow(
    user: AppUser,
    textColor: Color,
    accent: Color,
    onUnmute: () -> Unit,
) {
    var showUnmuteAlert by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MuteAvatar(user.profileImagePath)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(user.username, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
            val bio = user.bio
            if (!bio.isNullOrBlank()) {
                Text(bio, fontSize = 13.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            stringResource(R.string.mute_settings_activate),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = accent,
            modifier = Modifier
                .border(1.dp, accent, RoundedCornerShape(16.dp))
                .clickable { showUnmuteAlert = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }

    if (showUnmuteAlert) {
        AlertDialog(
            onDismissRequest = { showUnmuteAlert = false },
            title = { Text(stringResource(R.string.mute_settings_alert_activate_user_title)) },
            text = {
                Text(stringResource(R.string.mute_settings_alert_activate_user_message, user.username))
            },
            confirmButton = {
                TextButton(onClick = {
                    showUnmuteAlert = false
                    onUnmute()
                }) {
                    Text(stringResource(R.string.mute_settings_activate), color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnmuteAlert = false }) {
                    Text(stringResource(R.string.mute_settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun MutedWordRow(
    word: String,
    textColor: Color,
    onRemove: () -> Unit,
) {
    var showRemoveAlert by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.TextFields, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(word, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor, modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.RemoveCircle,
            contentDescription = null,
            tint = Color(0xFFFF3B30),
            modifier = Modifier
                .size(22.dp)
                .clickable { showRemoveAlert = true },
        )
    }

    if (showRemoveAlert) {
        AlertDialog(
            onDismissRequest = { showRemoveAlert = false },
            title = { Text(stringResource(R.string.mute_settings_alert_remove_word_title)) },
            text = {
                Text(stringResource(R.string.mute_settings_alert_remove_word_message, word))
            },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveAlert = false
                    onRemove()
                }) {
                    Text(stringResource(R.string.mute_settings_remove), color = Color(0xFFFF3B30))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveAlert = false }) {
                    Text(stringResource(R.string.mute_settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun MuteAvatar(path: String?) {
    if (!path.isNullOrBlank()) {
        AsyncImage(
            model = profileThumbnailUrl(path),
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
            Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AddMutedUserSheet(
    viewModel: MuteSettingsViewModel,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val accent = SettingsProfileColors.accent(isDark)
    val firestore = remember { FirestoreService() }

    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchText) {
        if (searchText.isEmpty()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(280)
        searchResults = runCatching { firestore.searchUsers(searchText, limit = 10) }.getOrDefault(emptyList())
        isSearching = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.mute_settings_cancel), color = accent)
            }
            Text(
                stringResource(R.string.mute_settings_mute_user_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(64.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Gray.copy(0.1f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Search, null, tint = Color.Gray)
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text(stringResource(R.string.mute_settings_search_placeholder)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                ),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        when {
            isSearching -> {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = textColor)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.mute_settings_searching), color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
            searchResults.isEmpty() && searchText.isNotEmpty() -> {
                Text(
                    stringResource(R.string.mute_settings_no_users_found),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    searchResults.forEach { user ->
                        val isMuted = viewModel.mutedUsers.any { it.id == user.id }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MuteAvatar(user.profileImagePath)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(user.username, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textColor)
                                val bio = user.bio
                                if (!bio.isNullOrBlank()) {
                                    Text(bio, fontSize = 13.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            val label = if (isMuted) {
                                stringResource(R.string.mute_settings_activate)
                            } else {
                                stringResource(R.string.mute_settings_navigation_title)
                            }
                            val stroke = if (isMuted) accent else Color(0xFFFF3B30)
                            Text(
                                label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = stroke,
                                modifier = Modifier
                                    .border(1.dp, stroke, RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (isMuted) viewModel.unmuteUser(user.id)
                                        else viewModel.muteUser(user)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                        HorizontalDivider(color = textColor.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMutedWordSheet(
    viewModel: MuteSettingsViewModel,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val accent = SettingsProfileColors.accent(isDark)
    var newWord by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.mute_settings_cancel), color = accent)
            }
            Text(
                stringResource(R.string.mute_settings_muted_word_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(64.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.mute_settings_add_word_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
            Text(
                stringResource(R.string.mute_settings_add_word_description),
                fontSize = 14.sp,
                color = Color.Gray,
            )
            TextField(
                value = newWord,
                onValueChange = { newWord = it },
                placeholder = { Text(stringResource(R.string.mute_settings_text_field_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(0.1f), RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                ),
            )
            val enabled = newWord.trim().isNotEmpty()
            Text(
                stringResource(R.string.mute_settings_add),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (enabled) accent else Color.Gray,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable(enabled = enabled) {
                        viewModel.addMutedWord(newWord.trim())
                        onDismiss()
                    }
                    .padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
