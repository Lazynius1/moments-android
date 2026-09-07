package com.moments.android.views.messaging.groups

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.feed.rememberAdaptiveColors
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormat

@Composable private fun g(key: String) = stringResource(groupStringId(key))

@Composable private fun GroupHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, g("back")) }
        Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1)
        actions()
    }
}
@Composable private fun GroupAvatar() {
    Icon(Icons.Default.Groups, null, Modifier.size(52.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), CircleShape).padding(13.dp))
}
@Composable private fun GroupPersonRow(member: GroupMember, detail: String = "", modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null)
            if (member.image.isNotBlank()) AsyncImage(member.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(member.name, fontWeight = FontWeight.Medium, maxLines = 1)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = rememberAdaptiveColors().secondary)
        }
    }
}
@Composable private fun GroupMemberPicker(store: GroupChatStore, group: GroupConversation?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(true) }
    val candidates = store.candidates.filter { person -> group?.members?.none { it.id == person.id } != false && group?.pendingNames?.containsKey(person.id) != true && (search.isBlank() || person.name.contains(search, true)) }
    val valid = (group != null || (name.isNotBlank() && name.codePointCount(0, name.length) <= 60)) &&
        selected.size >= (if (group == null) 2 else 1) && selected.size + ((group?.members?.size ?: 1) + (group?.pendingNames?.size ?: 0)) <= 50
    LaunchedEffect(search) { loading = true; kotlinx.coroutines.delay(250); store.loadCandidates(search); loading = false }
    GroupHeader(g(if (group == null) "create" else "add"), onBack) {
        TextButton(enabled = valid && !store.busy, onClick = { scope.launch { store.saveMembers(name, selected.sorted(), group)?.let(onSaved) } }) { Text(g(if (group == null) "create" else "add")) }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (group == null) item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text(g("name")) }, singleLine = true) }
        item { Text(g("pickerHint"), Modifier.padding(20.dp), color = rememberAdaptiveColors().secondary) }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text(g("search")) }, singleLine = true) }
        item { Text(stringResource(R.string.groups_selected_count, selected.size), Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall) }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp)) }
        if (candidates.isEmpty() && !loading) item { Text(g("noConnections"), Modifier.padding(20.dp)) }
        items(candidates, key = { it.id }) { member ->
            Row(Modifier.fillMaxWidth().clickable(enabled = !store.busy) {
                selected = if (member.id in selected) selected - member.id else if (selected.size + ((group?.members?.size ?: 1) + (group?.pendingNames?.size ?: 0)) < 50) selected + member.id else selected
            }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                GroupPersonRow(member, modifier = Modifier.weight(1f))
                Checkbox(member.id in selected, onCheckedChange = null)
            }
        }
    }
}

@Composable private fun GroupDetailsView(store: GroupChatStore, onBack: () -> Unit, onAdd: () -> Unit, onLeft: () -> Unit) {
    val scope = rememberCoroutineScope()
    val group = store.active
    var name by remember { mutableStateOf(group?.name.orEmpty()) }
    var leaving by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<GroupMember?>(null) }
    GroupHeader(g("details"), onBack)
    if (group == null) { Text(g("unavailable"), Modifier.padding(20.dp)); return }
    val admin = store.uid in group.admins
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { GroupAvatar(); Text(group.name, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.groups_member_count, group.members.size)) } }
        if (admin) {
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text(g("name")) }, enabled = !store.busy)
                TextButton(onClick = { scope.launch { store.command("rename", group, name = name) } }, enabled = !store.busy && name.isNotBlank() && name != group.name && name.codePointCount(0, name.length) <= 60) { Text(g("save")) }
            } }
            item { TextButton(onClick = onAdd, enabled = group.members.size < 50 && !store.busy) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(g("add")) } }
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g("mute"), Modifier.weight(1f))
            Switch(store.uid in group.muted, { value -> scope.launch { store.command("mute", group, muted = value) } }, enabled = !store.busy)
        } }
        if (admin && group.pendingNames.isNotEmpty()) {
            item { Text(g("pendingInvitations"), style = MaterialTheme.typography.titleMedium) }
            items(group.pendingNames.keys.sorted(), key = { "invite-$it" }) { id ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.pendingNames[id].orEmpty(), Modifier.weight(1f))
                    TextButton(enabled = !store.busy, onClick = { scope.launch { store.command("cancelInvite", group, memberId = id) } }) { Text(g("cancel")) }
                }
            }
        }
        item { Text(g("members"), style = MaterialTheme.typography.titleMedium) }
        items(group.members, key = { it.id }) { member ->
            var menu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupPersonRow(member, if (member.id == group.owner) g("owner") else if (member.id in group.admins) g("admin") else "", Modifier.weight(1f))
                if (admin && member.id != group.owner && member.id != store.uid) Box {
                    IconButton(onClick = { menu = true }, enabled = !store.busy) { Icon(Icons.Default.MoreHoriz, g("manage")) }
                    DropdownMenu(menu, { menu = false }) {
                        if (member.id !in group.admins) DropdownMenuItem(text = { Text(g("promote")) }, onClick = { menu = false; scope.launch { store.command("promote", group, member.id) } })
                        else if (store.uid == group.owner) DropdownMenuItem(text = { Text(g("demote")) }, onClick = { menu = false; scope.launch { store.command("demote", group, member.id) } })
                        if (member.id !in group.admins || store.uid == group.owner) DropdownMenuItem(text = { Text(g("remove")) }, onClick = { menu = false; removing = member })
                    }
                }
            }
        }
        item { TextButton(onClick = { leaving = true }, enabled = !store.busy) { Text(g("leave"), color = MaterialTheme.colorScheme.error) } }
    }
    if (leaving || removing != null) AlertDialog(onDismissRequest = { leaving = false; removing = null }, title = { Text(g(if (leaving) "leave" else "remove")) }, text = { Text(g(if (leaving) "leaveBody" else "removeBody")) },
        dismissButton = { TextButton(onClick = { leaving = false; removing = null }) { Text(g("cancel")) } },
        confirmButton = { TextButton(onClick = {
            val isLeaving = leaving; val member = removing
            leaving = false; removing = null
            scope.launch { if (store.command(if (isLeaving) "leave" else "remove", group, memberId = member?.id.orEmpty()) && isLeaving) onLeft() }
        }) { Text(g(if (leaving) "leave" else "remove")) } })
}

@Composable
fun NewGroupView(onBack: () -> Unit, onCreated: (com.moments.android.views.messaging.core.Conversation) -> Unit) {
    val scope = rememberCoroutineScope()
    val store = remember { GroupChatStore(scope) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(rememberAdaptiveColors().surfaceBackground).statusBarsPadding()) {
        GroupMemberPicker(store, null, onBack) { id -> scope.launch {
            try {
                val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("groupConversations").document(id).get().await()
                com.moments.android.views.messaging.services.ChatService.parseConversation(doc.id, doc.data.orEmpty(), store.uid)?.let(onCreated)
            } catch (_: Exception) { store.error = "error" }
        } }
    }
    GroupError(store)
}
@Composable
fun GroupManagementView(groupId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val store = remember { GroupChatStore(scope) }
    var adding by remember { mutableStateOf(false) }
    DisposableEffect(groupId) { store.open(groupId); onDispose { store.stop() } }
    BackHandler { if (adding) adding = false else onBack() }
    Column(Modifier.fillMaxSize().background(rememberAdaptiveColors().surfaceBackground).statusBarsPadding()) {
        if (adding) GroupMemberPicker(store, store.active, { adding = false }) { adding = false }
        else GroupDetailsView(store, onBack, { adding = true }, onBack)
    }
    GroupError(store)
}
@Composable private fun GroupError(store: GroupChatStore) {
    store.error?.let { error -> AlertDialog(onDismissRequest = { store.error = null }, title = { Text(g("errorTitle")) },
        text = { Text(g(error)) }, confirmButton = { TextButton(onClick = { store.error = null }) { Text(g("ok")) } }) }
}

@Composable
fun GroupInvitationRows(onAccepted: (com.moments.android.views.messaging.core.Conversation) -> Unit) {
    val scope = rememberCoroutineScope()
    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var invitations by remember { mutableStateOf<List<com.google.firebase.firestore.DocumentSnapshot>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(uid) {
        invitations = emptyList()
        val listener = if (uid.isBlank()) null else com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("groupInvitations").whereEqualTo("recipientId", uid).addSnapshotListener { snapshot, error ->
                if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid == uid) {
                    if (error != null) failed = true else invitations = snapshot?.documents.orEmpty()
                }
            }
        onDispose { listener?.remove() }
    }
    if (invitations.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(g("invitations"), style = MaterialTheme.typography.titleMedium)
        invitations.forEach { invitation ->
            val id = invitation.getString("groupId").orEmpty()
            Text(invitation.getString("groupName").orEmpty(), fontWeight = FontWeight.SemiBold)
            Text(invitation.getString("inviterName").orEmpty(), style = MaterialTheme.typography.bodySmall)
            Text(g("privacy"), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(false, true).forEach { accept ->
                    TextButton(enabled = !busy, onClick = { scope.launch {
                        busy = true
                        try {
                            com.moments.android.services.messaging.GroupChatAPI.request("manageGroup", mapOf("action" to if (accept) "acceptInvite" else "declineInvite", "conversationId" to id))
                            if (accept) {
                                val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("groupConversations").document(id).get().await()
                                com.moments.android.views.messaging.services.ChatService.parseConversation(doc.id, doc.data.orEmpty(), uid)?.let(onAccepted)
                            }
                        } catch (_: Exception) { failed = true } finally { busy = false }
                    } }) { Text(g(if (accept) "accept" else "decline")) }
                }
            }
        }
    }
    if (failed) AlertDialog(onDismissRequest = { failed = false }, title = { Text(g("errorTitle")) }, text = { Text(g("manageError")) },
        confirmButton = { TextButton(onClick = { failed = false }) { Text(g("ok")) } })
}
