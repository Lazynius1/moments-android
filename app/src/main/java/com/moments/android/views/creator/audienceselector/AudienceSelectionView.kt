package com.moments.android.views.creator.audienceselector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.CustomAudienceList
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchCustomLists
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.views.creator.creatorscreens.CreatorFlowPendingScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Port v1 de `AudienceSelectionView.swift` — predefinidas + listas custom.
 * Custom people / manage lists: pendientes de sus archivos iOS.
 */
@Composable
fun AudienceSelectionView(
    selectedAudience: ContentAudience,
    selectedListId: String?,
    selectedListName: String?,
    customSelectedUsers: List<String>,
    onSelectedAudienceChange: (ContentAudience) -> Unit,
    onSelectedListIdChange: (String?) -> Unit,
    onSelectedListNameChange: (String?) -> Unit,
    onCustomSelectedUsersChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val canvas = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val content = if (isDark) Color.White else Color.Black
    val secondary = content.copy(0.55f)

    var customLists by remember { mutableStateOf<List<CustomAudienceList>>(emptyList()) }
    var isLoadingLists by remember { mutableStateOf(true) }
    var showSaved by remember { mutableStateOf(false) }
    var pendingCustomPeople by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        customLists = if (uid != null) {
            withContext(Dispatchers.IO) {
                runCatching { FirestoreService().fetchCustomLists(uid) }.getOrDefault(emptyList())
            }
        } else {
            emptyList()
        }
        isLoadingLists = false
    }

    if (pendingCustomPeople) {
        CreatorFlowPendingScreen(
            iosSource = "CustomAudienceSelector / AudienceSelectionView customPeople",
            onBack = { pendingCustomPeople = false },
            onDismiss = onDismiss,
            modifier = modifier,
        )
        return
    }

    fun selectPredefined(audience: ContentAudience) {
        onSelectedAudienceChange(audience)
        onSelectedListIdChange(null)
        onSelectedListNameChange(null)
        onCustomSelectedUsersChange(emptyList())
        showSaved = true
    }

    Column(modifier.fillMaxSize().background(canvas)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = content, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.audience_selection_title),
                color = content,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color(0xFF007AFF), modifier = Modifier.size(18.dp))
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                stringResource(R.string.audience_selection_subtitle),
                color = secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Text(
                stringResource(R.string.audience_predefined),
                color = secondary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            val predefined = listOf(
                Triple(ContentAudience.EVERYONE, Icons.Filled.Public, R.string.audience_type_everyone),
                Triple(ContentAudience.MUTUALS, Icons.Filled.People, R.string.audience_type_mutuals),
                Triple(ContentAudience.BEST_FRIENDS, Icons.Filled.Star, R.string.audience_type_best_friends),
                Triple(ContentAudience.ONLY_ME, Icons.Filled.Lock, R.string.audience_type_only_me),
            )
            predefined.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { (audience, icon, titleRes) ->
                        AudienceGridCard(
                            title = stringResource(titleRes),
                            icon = icon,
                            selected = selectedAudience == audience && selectedListId == null,
                            modifier = Modifier.weight(1f),
                            onClick = { selectPredefined(audience) },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.audience_custom_lists),
                color = secondary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            if (isLoadingLists) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(24.dp)
                        .align(Alignment.CenterHorizontally),
                    color = Color(0xFF007AFF),
                    strokeWidth = 2.dp,
                )
            } else if (customLists.isEmpty()) {
                Text(
                    stringResource(R.string.audience_custom_lists_empty),
                    color = secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(customLists, key = { it.id ?: it.name }) { list ->
                        val selected = selectedAudience == ContentAudience.CUSTOM_LIST && selectedListId == list.id
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Color(0xFF007AFF).copy(0.18f) else content.copy(0.06f))
                                .border(
                                    1.dp,
                                    if (selected) Color(0xFF007AFF) else content.copy(0.1f),
                                    RoundedCornerShape(14.dp),
                                )
                                .clickable {
                                    onSelectedAudienceChange(ContentAudience.CUSTOM_LIST)
                                    onSelectedListIdChange(list.id)
                                    onSelectedListNameChange(list.name)
                                    onCustomSelectedUsersChange(emptyList())
                                    showSaved = true
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Column {
                                Icon(Icons.Filled.Group, null, tint = if (selected) Color(0xFF007AFF) else content)
                                Text(
                                    list.name,
                                    color = content,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                Text(
                                    stringResource(R.string.audience_people_count, list.members.size),
                                    color = secondary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(content.copy(0.06f))
                    .clickable {
                        onSelectedAudienceChange(ContentAudience.CUSTOM)
                        onSelectedListIdChange(null)
                        onSelectedListNameChange(null)
                        pendingCustomPeople = true
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Person, null, tint = content)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(stringResource(R.string.audience_type_custom), color = content, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (customSelectedUsers.isEmpty()) {
                            stringResource(R.string.audience_custom_people_hint)
                        } else {
                            stringResource(R.string.audience_people_count, customSelectedUsers.size)
                        },
                        color = secondary,
                        fontSize = 12.sp,
                    )
                }
                if (selectedAudience == ContentAudience.CUSTOM) {
                    Icon(Icons.Filled.Check, null, tint = Color(0xFF007AFF))
                }
            }

            if (showSaved) {
                Text(
                    stringResource(R.string.audience_saved),
                    color = Color(0xFF007AFF),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }

            // Keep selectedListName visible for compile unused suppress via usage above
            @Suppress("UNUSED_VARIABLE")
            val keep = selectedListName
        }
    }
}

@Composable
private fun AudienceGridCard(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val content = if (isDark) Color.White else Color.Black
    Box(
        modifier
            .height(88.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF007AFF).copy(0.18f) else content.copy(0.06f))
            .border(
                1.dp,
                if (selected) Color(0xFF007AFF) else content.copy(0.1f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column {
            Icon(icon, null, tint = if (selected) Color(0xFF007AFF) else content)
            Spacer(Modifier.weight(1f))
            Text(title, color = content, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                null,
                tint = Color(0xFF007AFF),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp),
            )
        }
    }
}
