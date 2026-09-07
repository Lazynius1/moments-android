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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.OnlineStatus
import com.moments.android.services.messaging.OnlineStatusService
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.core.PresenceDisplay
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormat

@Composable private fun g(key: String) = stringResource(groupStringId(key))

private const val GroupNameMaxLength = 60
private fun String.limitedGroupName(): String {
    val count = codePointCount(0, length)
    if (count <= GroupNameMaxLength) return this
    return substring(0, offsetByCodePoints(0, GroupNameMaxLength))
}

@Composable private fun GroupHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, g("back")) }
        Text(
            title,
            Modifier.weight(1f).padding(horizontal = 8.dp),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 22.sp),
        )
        Row { actions() }
    }
}
@Composable internal fun GroupChatAvatar(
    image: String = "",
    local: Bitmap? = null,
    size: androidx.compose.ui.unit.Dp = 52.dp,
    camera: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        Modifier.size(size).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                local != null -> Image(bitmap = local.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                image.isNotBlank() -> AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> Icon(Icons.Default.Groups, null, Modifier.size(size / 2))
            }
        }
        if (camera) {
            Icon(
                Icons.Default.PhotoCamera,
                g("photo"),
                Modifier.size(size * 0.34f).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(5.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
@Composable private fun GroupPersonRow(
    member: GroupMember,
    detail: String = "",
    joinedAt: Date? = null,
    showsPresence: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    var presence by remember(member.id) { mutableStateOf<PresenceDisplay?>(null) }
    DisposableEffect(member.id, showsPresence) {
        if (!showsPresence) {
            onDispose { }
        } else {
            val stop = OnlineStatusService.shared.observeUserStatus(member.id) { status, lastSeen ->
                presence = OnlineStatusService.shared.presenceDisplay(status, lastSeen)
            }
            onDispose { stop() }
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null)
            if (member.image.isNotBlank()) AsyncImage(member.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(member.name, fontWeight = FontWeight.Medium, maxLines = 1)
            if (showsPresence) {
                presence?.let { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(groupPresenceColor(p.status)))
                        Text(p.statusText, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
                        p.supplementalText?.let { Text("• $it", style = MaterialTheme.typography.bodySmall, color = colors.tertiary) }
                    }
                }
            }
            if (joinedAt != null) {
                Text(
                    stringResource(R.string.groups_joined_on, MomentsFormat.smartDate(joinedAt, MomentsFormat.DateContext.MEDIUM_DATE)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondary,
                )
            }
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
        }
    }
}

private fun groupPresenceColor(status: OnlineStatus): Color = when (status) {
    OnlineStatus.ONLINE -> Color(0xFF34C759)
    OnlineStatus.AWAY -> Color(0xFFFFCC00)
    OnlineStatus.BUSY -> Color(0xFFFF3B30)
    OnlineStatus.OFFLINE, OnlineStatus.INVISIBLE -> Color(0xFF8E8E93)
}
@Composable private fun GroupMemberPicker(store: GroupChatStore, group: GroupConversation?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(true) }
    var skipContinue by remember { mutableStateOf<GroupSaveResult?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        photo = uri?.let { context.contentResolver.openInputStream(it)?.use(BitmapFactory::decodeStream) }
    }
    val candidates = store.candidates.filter { person -> group?.members?.none { it.id == person.id } != false && group?.pendingNames?.containsKey(person.id) != true && (search.isBlank() || person.name.contains(search, true)) }
    val valid = (group != null || name.isNotBlank()) &&
        selected.size >= (if (group == null) 2 else 1) && selected.size + ((group?.members?.size ?: 1) + (group?.pendingNames?.size ?: 0)) <= 50
    val headerTitle = when {
        group != null -> g("add")
        name.isBlank() -> g("create")
        else -> name.trim()
    }
    LaunchedEffect(search) { loading = true; kotlinx.coroutines.delay(250); store.loadCandidates(search); loading = false }
    GroupHeader(headerTitle, onBack) {
        TextButton(enabled = valid && !store.busy, onClick = { scope.launch {
            val result = store.saveMembers(name, selected.sorted(), group) ?: return@launch
            if (photo != null && group == null) {
                val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("groupConversations").document(result.id).get().await()
                store.setPhoto(photo!!, GroupConversation.from(doc))
            }
            if (result.skipped.isEmpty()) onSaved(result.id) else skipContinue = result
        } }) { Text(g(if (group == null) "create" else "add")) }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text(g("search")) }, singleLine = true) }
        if (group == null) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GroupChatAvatar(local = photo, size = 64.dp, camera = true, onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                OutlinedTextField(name, { name = it.limitedGroupName() }, Modifier.weight(1f).padding(start = 14.dp), label = { Text(g("name")) }, singleLine = true)
            }
        }
        item { Text(g("pickerHint"), Modifier.padding(20.dp), color = rememberAdaptiveColors().secondary) }
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
    skipContinue?.let { pending ->
        AlertDialog(
            onDismissRequest = { onSaved(pending.id); skipContinue = null },
            title = { Text(g("errorTitle")) },
            text = { Text(inviteForbiddenText(skippedNames(pending.skipped, store.candidates))) },
            confirmButton = { TextButton(onClick = { onSaved(pending.id); skipContinue = null }) { Text(g("ok")) } },
        )
    }
}

@Composable private fun GroupDetailsView(store: GroupChatStore, onBack: () -> Unit, onAdd: () -> Unit, onLeft: () -> Unit) {
    val scope = rememberCoroutineScope()
    val group = store.active
    var name by remember { mutableStateOf(group?.name.orEmpty()) }
    var leaving by remember { mutableStateOf(false) }
    var shareURL by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var removing by remember { mutableStateOf<GroupMember?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val bitmap = uri?.let { context.contentResolver.openInputStream(it)?.use(BitmapFactory::decodeStream) } ?: return@rememberLauncherForActivityResult
        scope.launch { store.active?.let { store.setPhoto(bitmap, it) } }
    }
    GroupHeader(g("details"), onBack)
    if (group == null) { Text(g("unavailable"), Modifier.padding(20.dp)); return }
    val admin = store.uid in group.admins
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GroupChatAvatar(
                image = group.image,
                size = 96.dp,
                camera = admin,
                onClick = if (admin) {
                    { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                } else null,
            )
            Text(
                group.name,
                Modifier.fillMaxWidth().padding(top = 16.dp),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 24.sp),
            )
            Text(stringResource(R.string.groups_member_count, group.members.size))
        } }
        if (admin) {
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it.limitedGroupName() }, Modifier.weight(1f), label = { Text(g("name")) }, enabled = !store.busy, singleLine = true)
                TextButton(onClick = { scope.launch { store.command("rename", group, name = name) } }, enabled = !store.busy && name.isNotBlank() && name != group.name) { Text(g("save")) }
            } }
            item { TextButton(onClick = onAdd, enabled = group.members.size < 50 && !store.busy) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(g("add")) } }
        }
        if (admin) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(g("inviteLink"), style = MaterialTheme.typography.titleMedium)
                Text(g("linkBody"), style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !store.busy, onClick = { scope.launch { shareURL = store.invitationLink(group) } }) { Text(g("getLink")) }
                shareURL?.let { url ->
                    val title = g("shareLink")
                    TextButton(enabled = !store.busy, onClick = {
                        context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, url)
                        }, title))
                    }) { Text(title) }
                }
                TextButton(enabled = !store.busy, onClick = { scope.launch { shareURL = store.invitationLink(group, renew = true) } }) { Text(g("renewLink")) }
                TextButton(enabled = !store.busy, onClick = { scope.launch {
                    if (store.command("revokeLink", group)) shareURL = null
                } }) { Text(g("disableLink")) }
            }
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
                GroupPersonRow(
                    member,
                    if (member.id == group.owner) g("owner") else if (member.id in group.admins) g("admin") else "",
                    joinedAt = group.joinedDate(member.id),
                    showsPresence = true,
                    modifier = Modifier.weight(1f),
                )
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
    Column(Modifier.fillMaxSize().background(rememberAdaptiveColors().surfaceBackground).statusBarsPadding().navigationBarsPadding().imePadding()) {
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
    Column(Modifier.fillMaxSize().background(rememberAdaptiveColors().surfaceBackground).statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (adding) GroupMemberPicker(store, store.active, { adding = false }) { adding = false }
        else GroupDetailsView(store, onBack, { adding = true }, onBack)
    }
    GroupError(store)
}
@Composable private fun GroupError(store: GroupChatStore) {
    val skipped = skippedNames(store.skippedInvites, store.candidates)
    store.error?.let { error ->
        AlertDialog(
            onDismissRequest = { store.error = null; store.skippedInvites = emptyList() },
            title = { Text(g("errorTitle")) },
            text = { Text(if (skipped.isNotEmpty()) inviteForbiddenText(skipped) else g(error)) },
            confirmButton = { TextButton(onClick = { store.error = null; store.skippedInvites = emptyList() }) { Text(g("ok")) } },
        )
    }
}

private fun skippedNames(skipped: List<GroupSkippedInvite>, candidates: List<GroupMember>): List<String> =
    skipped.map { item -> candidates.firstOrNull { it.id == item.id }?.name?.ifBlank { null } ?: item.username }
        .map { it.trim() }.filter { it.isNotEmpty() }

@Composable private fun inviteForbiddenText(names: List<String>): String {
    if (names.isEmpty()) return g("inviteForbidden")
    val joined = android.icu.text.ListFormatter.getInstance().format(names)
    return if (names.size == 1) stringResource(R.string.groups_invite_forbidden_one, joined)
    else stringResource(R.string.groups_invite_forbidden_many, joined)
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
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GroupChatAvatar(image = invitation.getString("groupImagePath").orEmpty(), size = 56.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(invitation.getString("groupName").orEmpty(), fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            stringResource(R.string.groups_invited_you, invitation.getString("inviterName").orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = rememberAdaptiveColors().secondary,
                            maxLines = 2,
                        )
                    }
                }
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
    }
    if (failed) AlertDialog(onDismissRequest = { failed = false }, title = { Text(g("errorTitle")) }, text = { Text(g("manageError")) },
        confirmButton = { TextButton(onClick = { failed = false }) { Text(g("ok")) } })
}

@Composable
internal fun GroupJoinLinkDialog(link: GroupInviteLink, onDismiss: () -> Unit, onJoined: () -> Unit) {
    val scope = rememberCoroutineScope()
    val store = remember(link.groupId, link.token) { GroupChatStore(scope) }
    var name by remember(link.groupId, link.token) { mutableStateOf<String?>(null) }
    var image by remember(link.groupId, link.token) { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            com.moments.android.views.messaging.components.ChatRecoveryGateView(onCancel = onDismiss) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
                    GroupChatAvatar(image = image, size = 88.dp)
                    Text(name ?: g("inviteLink"), style = MaterialTheme.typography.headlineSmall)
                    if (name != null) {
                        Text(g("linkBody"))
                        Button(enabled = !store.busy, onClick = { scope.launch { if (store.joinLink(link)) onJoined() } }) { Text(g("joinLink")) }
                    } else if (store.error == null) CircularProgressIndicator()
                    else Text(g("linkError"))
                    TextButton(onClick = onDismiss) { Text(g("cancel")) }
                }
                LaunchedEffect(link.groupId, link.token) {
                    store.previewLink(link)?.let { name = it.first; image = it.second }
                }
            }
        }
    }
    GroupError(store)
}
