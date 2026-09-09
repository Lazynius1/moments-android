package com.moments.android.views.messaging.groups

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.alpha
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
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.views.feed.core.FeedProfileSheetRoute
import com.moments.android.views.profile.core.sections.UserProfileZoomNavigationHost
import com.moments.android.views.profile.core.sections.userProfileZoomSource
import com.moments.android.models.OnlineStatus
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.messaging.OnlineStatusService
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.utilities.MomentsFormat
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.extensions.MomentsGlassButtonTint
import com.moments.android.views.components.ModernFollowButton
import com.moments.android.views.components.ModernFollowButtonStyle
import com.moments.android.views.components.MomentsCircularProgressIndicator
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.core.PresenceDisplay
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.tabbar.MomentsTabBarHidden
import com.moments.android.views.profile.editor.sections.ProfileLibraryCropEntryView
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Date
import kotlinx.coroutines.tasks.await
import java.text.DateFormat

@Composable private fun g(key: String) = stringResource(groupStringId(key))

private const val GroupNameMaxLength = 60
private const val GroupDescriptionMaxLength = 280
private fun String.limitedGroupName(): String {
    val count = codePointCount(0, length)
    if (count <= GroupNameMaxLength) return this
    return substring(0, offsetByCodePoints(0, GroupNameMaxLength))
}
private fun String.limitedGroupDescription(): String {
    val count = codePointCount(0, length)
    if (count <= GroupDescriptionMaxLength) return this
    return substring(0, offsetByCodePoints(0, GroupDescriptionMaxLength))
}

@Composable
internal fun GroupMentionCandidateList(
    query: String,
    members: List<GroupMember>,
    onSelect: (GroupMember) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val matches = members.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true)
    }.take(10)
    val panelHeight = (minOf(maxOf(matches.size, 1), 3) * 67).dp
    val panelShape = RoundedCornerShape(24.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 6.dp)
            .momentsChromeGlass(panelShape, interactive = false)
            .border(1.dp, colors.primary.copy(alpha = 0.08f), panelShape)
            .padding(vertical = 8.dp),
    ) {
        when {
            matches.isEmpty() -> {
                Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.common_no_results), color = Color.Gray, fontSize = 15.sp)
                }
            }
            else -> {
                LazyColumn(Modifier.height(panelHeight)) {
                    items(matches, key = { it.id }) { member ->
                        GroupMentionSearchRow(member = member, onClick = { onSelect(member) })
                        if (member.id != matches.last().id) {
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.25f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMentionSearchRow(member: GroupMember, onClick: () -> Unit) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncProfileImageView(member.id, Modifier.size(42.dp).clip(CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(
            member.name,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = colors.primary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier
                .size(28.dp)
                .momentsChromeGlass(CircleShape, interactive = true)
                .padding(4.dp),
        )
    }
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
    uploading: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val cameraTint = if (isSystemInDarkTheme()) Color.White else Color.Black
    Box(
        Modifier.size(size).then(if (onClick != null && !uploading) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                local != null -> Image(
                    bitmap = local.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = if (uploading) 0.42f else 1f,
                )
                image.isNotBlank() -> AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> Icon(Icons.Default.Groups, null, Modifier.size(size / 2))
            }
            if (uploading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.28f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MomentsCircularProgressIndicator(Modifier.size(size * 0.28f))
                    }
                }
            }
        }
        if (camera) {
            Icon(
                Icons.Default.PhotoCamera,
                g("photo"),
                Modifier
                    .size(size * 0.38f)
                    .clip(CircleShape)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .padding(size * 0.08f),
                tint = cameraTint,
            )
        }
    }
}
@Composable private fun GroupPersonRow(
    member: GroupMember,
    detail: String = "",
    joinedAt: Date? = null,
    showsPresence: Boolean = false,
    onProfileTap: (() -> Unit)? = null,
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
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncProfileImageView(
            userId = member.id,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .userProfileZoomSource(userId = member.id, cornerRadius = 24.dp)
                .then(if (onProfileTap != null) Modifier.clickable(onClick = onProfileTap) else Modifier),
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                member.name,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = if (onProfileTap != null) {
                    Modifier.fillMaxWidth().clickable(onClick = onProfileTap)
                } else {
                    Modifier
                },
            )
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

@Composable
private fun GroupMemberFollowButton(userId: String) {
    var followState by remember(userId) { mutableStateOf(FollowButtonState.CAN_FOLLOW) }
    var loading by remember(userId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }

    LaunchedEffect(userId) {
        FollowStateStore.state(userId)?.let { followState = it }
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        FollowStateStore.resolve(currentUserId, userId)?.let { followState = it }
    }
    DisposableEffect(userId) {
        val listener: (String, FollowButtonState) -> Unit = { id, state ->
            if (id == userId) followState = state
        }
        FollowStateStore.addListener(listener)
        onDispose { FollowStateStore.removeListener(listener) }
    }

    ModernFollowButton(
        state = followState,
        isLoading = loading,
        targetUserId = userId,
        style = ModernFollowButtonStyle.COMPACT,
        onClick = {
            scope.launch {
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                val previous = followState
                val optimistic = when (previous) {
                    FollowButtonState.FOLLOWING, FollowButtonState.MUTUALS -> FollowButtonState.CAN_FOLLOW
                    FollowButtonState.CAN_REQUEST_FOLLOW -> FollowButtonState.REQUEST_PENDING_CANCELLABLE
                    FollowButtonState.REQUEST_PENDING_CANCELLABLE -> FollowButtonState.CAN_REQUEST_FOLLOW
                    FollowButtonState.CAN_FOLLOW -> FollowButtonState.FOLLOWING
                    else -> previous
                }
                followState = optimistic
                FollowStateStore.setState(optimistic, userId)
                loading = true
                val result = runCatching {
                    when {
                        previous.isFollowingOrMutual -> firestore.unfollowUser(currentUserId, userId)
                        previous == FollowButtonState.REQUEST_PENDING_CANCELLABLE -> firestore.cancelFollowRequest(currentUserId, userId)
                        else -> firestore.followUser(currentUserId, userId)
                    }
                }
                loading = false
                if (result.isFailure) {
                    followState = previous
                    FollowStateStore.setState(previous, userId)
                }
            }
        },
    )
}

@Composable
private fun GroupMemberLine(
    member: GroupMember,
    group: GroupConversation,
    store: GroupChatStore,
    admin: Boolean,
    onRemove: (GroupMember) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        GroupPersonRow(
            member,
            if (member.id == group.owner) g("owner") else if (member.id in group.admins) g("admin") else "",
            joinedAt = group.joinedDate(member.id),
            showsPresence = true,
            onProfileTap = { onOpenProfile(member.id) },
            modifier = Modifier.weight(1f),
        )
        if (member.id != store.uid) GroupMemberFollowButton(member.id)
        if (admin && member.id != group.owner && member.id != store.uid) Box {
            IconButton(onClick = { menu = true }, enabled = !store.busy) { Icon(Icons.Default.MoreHoriz, g("manage")) }
            DropdownMenu(menu, { menu = false }) {
                if (member.id !in group.admins) DropdownMenuItem(text = { Text(g("promote")) }, onClick = { menu = false; scope.launch { store.command("promote", group, member.id) } })
                else if (store.uid == group.owner) DropdownMenuItem(text = { Text(g("demote")) }, onClick = { menu = false; scope.launch { store.command("demote", group, member.id) } })
                if (member.id !in group.admins || store.uid == group.owner) DropdownMenuItem(text = { Text(g("remove")) }, onClick = { menu = false; onRemove(member) })
            }
        }
    }
}

@Composable private fun GroupMemberPicker(store: GroupChatStore, group: GroupConversation?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(true) }
    var skipContinue by remember { mutableStateOf<GroupSaveResult?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var showPhotoCrop by remember { mutableStateOf(false) }
    val candidates = store.candidates.filter { person -> group?.members?.none { it.id == person.id } != false && group?.pendingNames?.containsKey(person.id) != true && (search.isBlank() || person.name.contains(search, true)) }
    val valid = (group != null || name.isNotBlank()) &&
        selected.size >= (if (group == null) 2 else 1) && selected.size + ((group?.members?.size ?: 1) + (group?.pendingNames?.size ?: 0)) <= 50
    val headerTitle = when {
        group != null -> g("add")
        name.isBlank() -> g("create")
        else -> name.trim()
    }
    MomentsTabBarHidden()
    LaunchedEffect(search) { loading = true; kotlinx.coroutines.delay(250); store.loadCandidates(search); loading = false }
    if (showPhotoCrop) {
        BackHandler { showPhotoCrop = false }
        ProfileLibraryCropEntryView(
            onImageCropped = { bitmap ->
                photo = bitmap
                showPhotoCrop = false
            },
            onDismiss = { showPhotoCrop = false },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
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
                GroupChatAvatar(local = photo, size = 64.dp, camera = true, onClick = { showPhotoCrop = true })
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

@Composable private fun GroupDetailsView(store: GroupChatStore, onBack: () -> Unit, onAdd: () -> Unit, onOpenProfile: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val group = store.active
    var removing by remember { mutableStateOf<GroupMember?>(null) }
    var followEpoch by remember { mutableIntStateOf(0) }
    val memberIds = group?.members?.map { it.id }.orEmpty()
    DisposableEffect(memberIds) {
        val listener: (String, FollowButtonState) -> Unit = { _, _ -> followEpoch++ }
        FollowStateStore.addListener(listener)
        onDispose { FollowStateStore.removeListener(listener) }
    }
    LaunchedEffect(memberIds) {
        val active = store.active ?: return@LaunchedEffect
        active.members.filter { it.id != store.uid }.forEach { FollowStateStore.resolve(store.uid, it.id) }
        followEpoch++
    }
    val members = group?.members.orEmpty()
    var memberSearch by remember { mutableStateOf("") }
    val query = memberSearch.trim()
    val visibleMembers = remember(members, query) {
        if (query.isEmpty()) members else members.filter { it.name.contains(query, ignoreCase = true) }
    }
    val you = remember(visibleMembers, followEpoch, store.uid) { visibleMembers.filter { it.id == store.uid } }
    val rest = remember(visibleMembers, followEpoch, store.uid) { visibleMembers.filter { it.id != store.uid } }
    val following = remember(rest, followEpoch) {
        rest.filter { FollowStateStore.state(it.id)?.isFollowingOrMutual == true }.sortedBy { it.name.lowercase() }
    }
    val others = remember(rest, followEpoch) {
        rest.filter { FollowStateStore.state(it.id)?.isFollowingOrMutual != true }.sortedBy { it.name.lowercase() }
    }
    GroupHeader(g("members"), onBack)
    if (group == null) { Text(g("unavailable"), Modifier.padding(20.dp)); return }
    val admin = store.uid in group.admins
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GroupChatAvatar(image = group.image, size = 72.dp)
            Text(
                group.name,
                Modifier.fillMaxWidth().padding(top = 12.dp),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 22.sp),
            )
            Text(stringResource(R.string.groups_member_count, group.members.size))
            if (group.groupDescription.isNotBlank()) {
                Text(
                    group.groupDescription,
                    color = rememberAdaptiveColors().secondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } }
        item {
            OutlinedTextField(
                memberSearch,
                { memberSearch = it },
                Modifier.fillMaxWidth(),
                label = { Text(g("searchMembers")) },
                singleLine = true,
            )
        }
        if (admin) {
            item { TextButton(onClick = onAdd, enabled = group.members.size < 50 && !store.busy) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(g("add")) } }
        }
        if (admin && group.pendingJoinNames.isNotEmpty()) {
            item { Text(g("joinRequests"), style = MaterialTheme.typography.titleMedium) }
            items(group.pendingJoinNames.keys.sorted(), key = { "join-$it" }) { id ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GroupPersonRow(
                        GroupMember(id, group.pendingJoinNames[id].orEmpty(), ""),
                        onProfileTap = { onOpenProfile(id) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = !store.busy, onClick = { scope.launch { store.command("declineJoin", group, memberId = id) } }) { Text(g("decline")) }
                    TextButton(enabled = !store.busy, onClick = { scope.launch { store.command("approveJoin", group, memberId = id) } }) { Text(g("approveJoin")) }
                }
            }
        }
        if (admin && group.pendingNames.isNotEmpty()) {
            item { Text(g("pendingInvitations"), style = MaterialTheme.typography.titleMedium) }
            items(group.pendingNames.keys.sorted(), key = { "invite-$it" }) { id ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GroupPersonRow(
                        GroupMember(id, group.pendingNames[id].orEmpty(), ""),
                        onProfileTap = { onOpenProfile(id) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = !store.busy, onClick = { scope.launch { store.command("cancelInvite", group, memberId = id) } }) { Text(g("cancel")) }
                }
            }
        }
        item { Text(g("members"), style = MaterialTheme.typography.titleMedium) }
        if (you.isNotEmpty()) {
            item { Text(g("you"), style = MaterialTheme.typography.titleSmall, color = rememberAdaptiveColors().secondary) }
            items(you, key = { it.id }) { member ->
                GroupMemberLine(member, group, store, admin, { removing = it }, onOpenProfile)
            }
        }
        if (following.isNotEmpty()) {
            item { Text(g("followingSection"), style = MaterialTheme.typography.titleSmall, color = rememberAdaptiveColors().secondary) }
            items(following, key = { it.id }) { member ->
                GroupMemberLine(member, group, store, admin, { removing = it }, onOpenProfile)
            }
        }
        if (others.isNotEmpty()) {
            item { Text(g("othersSection"), style = MaterialTheme.typography.titleSmall, color = rememberAdaptiveColors().secondary) }
            items(others, key = { it.id }) { member ->
                GroupMemberLine(member, group, store, admin, { removing = it }, onOpenProfile)
            }
        }
        if (you.isEmpty() && following.isEmpty() && others.isEmpty() && query.isNotEmpty()) {
            item { Text(g("noMembersFound"), color = rememberAdaptiveColors().secondary, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
    removing?.let { member ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(g("remove")) },
            text = { Text(g("removeBody")) },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(g("cancel")) } },
            confirmButton = { TextButton(onClick = {
                removing = null
                scope.launch { store.command("remove", group, memberId = member.id) }
            }) { Text(g("remove")) } },
        )
    }
}

@Composable
fun NewGroupView(onBack: () -> Unit, onCreated: (com.moments.android.views.messaging.core.Conversation) -> Unit) {
    val scope = rememberCoroutineScope()
    val store = remember { GroupChatStore(scope) }
    MomentsTabBarHidden()
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
fun GroupEditView(groupId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val store = remember { GroupChatStore(scope) }
    MomentsTabBarHidden()
    val group = store.active
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var showPhotoCrop by remember { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var isPhotoUploading by remember { mutableStateOf(false) }
    val canSave = name.isNotBlank() && !store.busy && !isPhotoUploading
    val saveTint = if (isSystemInDarkTheme()) MomentsGlassButtonTint.canvasLight else MomentsGlassButtonTint.canvasDark
    val saveLabel = if (isSystemInDarkTheme()) MomentsGlassButtonTint.canvasDark else MomentsGlassButtonTint.canvasLight
    DisposableEffect(groupId) { store.open(groupId); onDispose { store.stop() } }
    LaunchedEffect(group?.id) {
        if (name.isBlank() && group != null) {
            name = group.name
            description = group.groupDescription
        }
    }
    LaunchedEffect(group?.image) {
        pendingPhoto = null
    }
    if (showPhotoCrop) {
        BackHandler { showPhotoCrop = false }
        ProfileLibraryCropEntryView(
            onImageCropped = { bitmap ->
                showPhotoCrop = false
                pendingPhoto = bitmap
                isPhotoUploading = true
                scope.launch {
                    val current = store.active
                    if (current == null) {
                        isPhotoUploading = false
                        pendingPhoto = null
                        return@launch
                    }
                    store.setPhoto(bitmap, current)
                    isPhotoUploading = false
                    if (store.error != null) pendingPhoto = null
                }
            },
            onDismiss = { showPhotoCrop = false },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(rememberAdaptiveColors().surfaceBackground).statusBarsPadding().navigationBarsPadding().imePadding()) {
        GroupHeader(g("edit"), onBack)
        if (group == null) {
            Text(g("unavailable"), Modifier.padding(20.dp))
        } else {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                GroupChatAvatar(
                    image = group.image,
                    local = pendingPhoto,
                    size = 96.dp,
                    camera = true,
                    uploading = isPhotoUploading,
                    onClick = { showPhotoCrop = true },
                )
                OutlinedTextField(name, { name = it.limitedGroupName() }, Modifier.fillMaxWidth(), label = { Text(g("name")) }, enabled = !store.busy, singleLine = true)
                OutlinedTextField(
                    description,
                    { description = it.limitedGroupDescription() },
                    Modifier.fillMaxWidth(),
                    label = { Text(g("descriptionPlaceholder")) },
                    enabled = !store.busy,
                    minLines = 3,
                    maxLines = 6,
                )
                Text(
                    g("save"),
                    color = saveLabel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .alpha(if (canSave) 1f else 0.5f)
                        .clip(RoundedCornerShape(50))
                        .momentsChromeGlass(
                            RoundedCornerShape(50),
                            interactive = canSave,
                            tint = saveTint.copy(alpha = if (canSave) 0.92f else 0.35f),
                        )
                        .clickable(enabled = canSave) {
                            scope.launch {
                                val trimmed = name.trim()
                                val trimmedDescription = description.trim()
                                if (trimmed.isBlank()) return@launch
                                if (trimmed != group.name || trimmedDescription != group.groupDescription) {
                                    if (!store.command("rename", group, name = trimmed,
                                            extra = mapOf("description" to trimmedDescription))) return@launch
                                }
                                onBack()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
    GroupError(store)
}

@Composable
fun GroupInviteLinkManageView(groupId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val store = remember { GroupChatStore(scope) }
    MomentsTabBarHidden()
    val group = store.active
    var shareURL by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    var confirmRenew by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }
    val context = LocalContext.current
    DisposableEffect(groupId) { store.open(groupId); onDispose { store.stop() } }
    LaunchedEffect(group?.id) {
        if (group != null && shareURL == null) shareURL = store.invitationLink(group)
    }
    val colors = rememberAdaptiveColors()
    val inviteLinkLabel = g("inviteLink")
    val shareLinkLabel = g("shareLink")
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(colors.surfaceBackground).statusBarsPadding().navigationBarsPadding().imePadding()) {
        GroupHeader(inviteLinkLabel, onBack)
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(g("linkBody"), style = MaterialTheme.typography.bodySmall, color = colors.secondary)
            shareURL?.let { url ->
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(inviteLinkLabel, url))
                        copied = true
                    }) {
                        Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (copied) g("linkCopied") else g("copyLink"))
                    }
                    TextButton(onClick = {
                        context.startActivity(
                            android.content.Intent.createChooser(
                                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, url)
                                },
                                shareLinkLabel,
                            ),
                        )
                    }) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text(g("shareLink"))
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(g("link.approval"), modifier = Modifier.weight(1f))
                    Switch(
                        checked = group?.linkRequiresApproval == true,
                        onCheckedChange = { value ->
                            val current = store.active ?: return@Switch
                            scope.launch { store.command("setLinkApproval", current, extra = mapOf("requiresApproval" to value)) }
                        },
                        enabled = !store.busy && group != null,
                    )
                }
                TextButton(enabled = !store.busy, onClick = { confirmRenew = true }) { Text(g("renewLink")) }
                TextButton(enabled = !store.busy, onClick = { confirmDisable = true }) { Text(g("disableLink"), color = MaterialTheme.colorScheme.error) }
            } ?: run {
                if (store.busy || group != null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else TextButton(onClick = { scope.launch { shareURL = store.active?.let { store.invitationLink(it) } } }) { Text(g("getLink")) }
            }
        }
    }
    if (confirmRenew) AlertDialog(
        onDismissRequest = { confirmRenew = false },
        title = { Text(g("renewLink")) },
        text = { Text(g("renewBody")) },
        dismissButton = { TextButton(onClick = { confirmRenew = false }) { Text(g("cancel")) } },
        confirmButton = { TextButton(onClick = {
            confirmRenew = false
            scope.launch { shareURL = store.active?.let { store.invitationLink(it, renew = true) } }
        }) { Text(g("renewLink")) } },
    )
    if (confirmDisable) AlertDialog(
        onDismissRequest = { confirmDisable = false },
        title = { Text(g("disableLink")) },
        text = { Text(g("disableBody")) },
        dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text(g("cancel")) } },
        confirmButton = { TextButton(onClick = {
            confirmDisable = false
            scope.launch {
                val active = store.active ?: return@launch
                if (store.command("revokeLink", active)) shareURL = null
            }
        }) { Text(g("disableLink")) } },
    )
    GroupError(store)
}

@Composable
fun GroupManagementView(groupId: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val store = remember { GroupChatStore(scope) }
    MomentsTabBarHidden()
    var adding by remember { mutableStateOf(false) }
    var profileRoute by remember { mutableStateOf<FeedProfileSheetRoute?>(null) }
    DisposableEffect(groupId) { store.open(groupId); onDispose { store.stop() } }
    UserProfileZoomNavigationHost(
        profileRoute = profileRoute,
        onProfileRouteChange = { profileRoute = it },
        modifier = Modifier.fillMaxSize(),
    ) { profileOpen ->
        BackHandler {
            when {
                profileOpen -> profileRoute = null
                adding -> adding = false
                else -> onBack()
            }
        }
        Column(Modifier.fillMaxSize().background(rememberAdaptiveColors().surfaceBackground).statusBarsPadding().navigationBarsPadding().imePadding()) {
            if (adding) GroupMemberPicker(store, store.active, { adding = false }) { adding = false }
            else GroupDetailsView(store, onBack, { adding = true }) { profileRoute = FeedProfileSheetRoute(it) }
        }
        GroupError(store)
    }
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
    var requested by remember(link.groupId, link.token) { mutableStateOf(false) }
    val colors = rememberAdaptiveColors()
    MomentsModalSheet(onDismissRequest = onDismiss, largeOnly = false) { _ ->
        com.moments.android.views.messaging.components.ChatRecoveryGateView(onCancel = onDismiss) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(20.dp))
                GroupChatAvatar(image = image, size = 104.dp)
                Spacer(Modifier.height(14.dp))
                val groupName = name
                if (groupName != null) {
                    Text(
                        groupName,
                        color = colors.primary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (requested) g("join.requested") else g("joinBody"),
                        color = colors.secondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                } else if (store.error == null) {
                    CircularProgressIndicator(Modifier.padding(top = 12.dp), color = colors.primary)
                } else {
                    Text(g("linkError"), color = colors.secondary, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = {
                        scope.launch {
                            when (store.joinLink(link)) {
                                true -> onJoined()
                                false -> requested = true
                                null -> Unit
                            }
                        }
                    },
                    enabled = !store.busy && name != null && !requested,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.surfaceBackground,
                        disabledContainerColor = colors.primary.copy(alpha = 0.35f),
                        disabledContentColor = colors.surfaceBackground,
                    ),
                ) {
                    Text(if (requested) g("join.requested") else g("joinLink"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            LaunchedEffect(link.groupId, link.token) {
                store.previewLink(link)?.let { name = it.first; image = it.second }
            }
        }
    }
    GroupError(store)
}
