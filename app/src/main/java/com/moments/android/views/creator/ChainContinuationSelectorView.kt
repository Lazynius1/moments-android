package com.moments.android.views.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchCustomLists
import com.moments.android.views.creator.audienceselector.ContentAudience
import com.moments.android.views.creator.audienceselector.CustomAudienceList
import com.moments.android.views.creator.audienceselector.CustomAudienceListsView
import com.moments.android.views.creator.audienceselector.CustomAudienceSelector
import com.moments.android.views.creator.audienceselector.CustomListCard
import com.moments.android.views.creator.audienceselector.AudienceGridCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Port de `CustomUserSelectorView` que devuelve ids al contrato de cadenas. */
@Composable
fun CustomUserSelectorView(selectedUsers: List<String>, onSelectedUsersChange: (List<String>) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var users by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    CustomAudienceSelector(users, { users = it }, onComplete = { onSelectedUsersChange(users.map { it.id }); onDismiss() }, onBack = onDismiss, embeddedInFlow = true, modifier = modifier)
}

/** Port Compose de `ChainContinuationSelectorView.swift`. */
@Composable
fun ChainContinuationSelectorView(
    selectedAudience: ChainContinuationSetting,
    onSelectedAudienceChange: (ChainContinuationSetting) -> Unit,
    selectedListId: String?,
    onSelectedListIdChange: (String?) -> Unit,
    selectedListName: String?,
    onSelectedListNameChange: (String?) -> Unit,
    customSelectedUsers: List<String>,
    onCustomSelectedUsersChange: (List<String>) -> Unit,
    embeddedInFlow: Boolean = false,
    onBack: (() -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme(); val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6); val content = if (dark) Color.White else Color.Black
    var lists by remember { mutableStateOf<List<CustomAudienceList>>(emptyList()) }; var loading by remember { mutableStateOf(true) }
    var showPeople by remember { mutableStateOf(false) }; var showManage by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { val uid = FirebaseAuth.getInstance().currentUser?.uid; lists = if (uid == null) emptyList() else withContext(Dispatchers.IO) { runCatching { FirestoreService().fetchCustomLists(uid) }.getOrDefault(emptyList()) }; loading = false }
    if (showPeople) { CustomUserSelectorView(customSelectedUsers, { ids -> onCustomSelectedUsersChange(ids); onSelectedAudienceChange(ChainContinuationSetting.CUSTOM); onSelectedListIdChange(null); onSelectedListNameChange(null) }, { showPeople = false; onComplete?.invoke() }, modifier); return }
    if (showManage) { CustomAudienceListsView(embeddedInFlow = true, onBack = { showManage = false }, onListsChanged = { }, modifier = modifier); return }
    Column(modifier.fillMaxSize().background(canvas)) {
        if (embeddedInFlow) Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).momentsChromeGlass(androidx.compose.foundation.shape.CircleShape, true).clickable { onBack?.invoke() }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = content) }; Spacer(Modifier.weight(1f)); Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Continuation audience", color = content, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text("Choose who may continue", color = content.copy(.6f), fontSize = 13.sp) }; Spacer(Modifier.weight(1f)); Spacer(Modifier.size(40.dp)) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Predefined audiences", color = content.copy(.8f), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
            listOf(ChainContinuationSetting.EVERYONE, ChainContinuationSetting.MUTUALS, ChainContinuationSetting.BEST_FRIENDS).forEach { choice ->
                AudienceGridCard(choice.contentAudience, choice == selectedAudience, onTap = { onSelectedAudienceChange(choice); onSelectedListIdChange(null); onSelectedListNameChange(null); onCustomSelectedUsersChange(emptyList()); onComplete?.invoke() })
            }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text("Custom lists", color = content.copy(.8f), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text("Manage", color = Color(0xFF007AFF), modifier = Modifier.clickable { showManage = true }) }
            if (loading) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
            else if (lists.isEmpty()) Text("No custom lists yet", color = content.copy(.55f), modifier = Modifier.padding(vertical = 16.dp))
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) { items(lists, key = { it.id ?: it.name }) { list -> CustomListCard(list, selectedAudience == ChainContinuationSetting.CUSTOM_LIST && selectedListId == list.id, onTap = { onSelectedAudienceChange(ChainContinuationSetting.CUSTOM_LIST); onSelectedListIdChange(list.id); onSelectedListNameChange(list.name); onCustomSelectedUsersChange(emptyList()); onComplete?.invoke() }) } }
            Row(Modifier.fillMaxWidth().padding(top = 24.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(content.copy(.06f)).clickable { showPeople = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Add, null, tint = content); Column(Modifier.padding(start = 12.dp).weight(1f)) { Text("Custom people", color = content, fontWeight = FontWeight.SemiBold); Text(if (selectedAudience == ChainContinuationSetting.CUSTOM && customSelectedUsers.isNotEmpty()) "${customSelectedUsers.size} people" else "Choose specific people", color = content.copy(.55f), fontSize = 13.sp) }; if (selectedAudience == ChainContinuationSetting.CUSTOM) Icon(Icons.Filled.Check, null, tint = Color(0xFF007AFF)) }
        }
    }
}
