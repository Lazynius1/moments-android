package com.moments.android.views.story.storyviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUserProfile
import com.moments.android.services.firestore.fetchCustomListDetails
import com.moments.android.services.firestore.fetchMutuals
import com.moments.android.services.firestore.fetchUser
import com.moments.android.services.privacy.ContentVisibilityService
import com.moments.android.models.Story
import com.moments.android.views.story.StoryReaction
import com.moments.android.views.story.StoryViewer
import coil.compose.AsyncImage

/** Primer bloque de `StoryViewerOverlay.swift`: barra de progreso por audiencia. */
@Composable
fun GlassmorphicProgressBar(
    progress: Float,
    isActive: Boolean,
    audience: String?,
    modifier: Modifier = Modifier,
) {
    val normalizedAudience = audience?.trim()?.lowercase().orEmpty()
    val (colors, shadow) = when (normalizedAudience) {
        "bestfriends", "best_friends", "best-friends" -> listOf(Color.fromHex("24C26A"), Color.fromHex("5BE584")) to Color.fromHex("24C26A").copy(.65f)
        "mutuals", "mutual" -> listOf(Color.fromHex("00B4D8"), Color.fromHex("4CC9F0")) to Color.fromHex("00B4D8").copy(.55f)
        else -> listOf(Color.Blue, Color(0xFF9C27B0), Color(0xFFFF4081)) to Color(0xFF9C27B0).copy(.6f)
    }
    Box(modifier.height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(.15f))) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.5.dp)
                .background(Brush.horizontalGradient(colors))
                .momentsChromeGlass(RoundedCornerShape(2.dp), interactive = isActive),
        )
    }
}

@Composable
fun GlassmorphicActionButton(
    icon: String,
    title: String,
    subtitle: String?,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleColor = if (isDestructive) Color.Red else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = titleColor, fontSize = 18.sp, modifier = Modifier.width(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, color = Color.White.copy(.7f), fontSize = 11.sp) }
        }
    }
}

@Composable
fun GlassmorphicSuccessMessage(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✓", color = Color.fromHex("007AFF"), fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GlassmorphicStoryConfirmationDialog(
    title: String,
    message: String,
    confirmTitle: String,
    cancelTitle: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(.45f)).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .momentsChromeGlass(RoundedCornerShape(24.dp), interactive = false)
                .clickable(enabled = false) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, textAlign = TextAlign.Center)
            if (message.trim().isNotEmpty()) Text(message, color = Color.White.copy(.82f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassmorphicActionButton("", cancelTitle, null, onClick = onCancel, modifier = Modifier.weight(1f))
                GlassmorphicActionButton("", confirmTitle, null, isDestructive, onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun GlassmorphicTabSelector(tabs: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, label ->
            val selected = selectedIndex == index
            Text(
                label,
                color = if (selected) Color.White else Color.White.copy(.52f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clickable { onSelected(index) }.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
fun GlassmorphicViewerRow(viewer: StoryViewer, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        StoryPersonAvatar(viewer.profileImagePath, 48.dp)
        Spacer(Modifier.width(16.dp))
        Text(viewer.username ?: "Usuario", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        viewer.rewatchBadgeText?.let { Text("  $it", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun GlassmorphicReactionRow(reaction: StoryReaction, user: AppUser?, modifier: Modifier = Modifier) {
    var resolvedUser by remember(reaction.userId) { mutableStateOf(user) }
    LaunchedEffect(reaction.userId, user) {
        if (user == null) resolvedUser = runCatching { FirestoreService().fetchUserProfile(reaction.userId) }.getOrNull()
    }
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        StoryPersonAvatar(resolvedUser?.profileImagePath, 48.dp)
        Spacer(Modifier.width(16.dp))
        Text(resolvedUser?.username ?: "Usuario", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(reaction.reaction, fontSize = 32.sp)
    }
}

@Composable
fun GlassmorphicEmptyState(icon: String, message: String, showCloseButton: Boolean = false, onClose: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 40.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, color = Color.White.copy(.62f), fontSize = 22.sp)
        Text(message, color = Color.White.copy(.62f), fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        if (showCloseButton && onClose != null) GlassmorphicActionButton("", "Close", null, onClick = onClose, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun GlassmorphicAudienceMembersSheet(title: String, users: List<AppUser>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Color.White, fontSize = 28.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(end = 16.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (users.isEmpty()) GlassmorphicEmptyState("person.2.slash", "No users in this audience", modifier = Modifier.weight(1f))
        else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp)) { items(users, key = { it.id }) { user ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StoryPersonAvatar(user.profileImagePath, 42.dp)
                Spacer(Modifier.width(12.dp))
                Text(user.username, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (user.isVerified) Text(" ✓", color = Color.fromHex("007AFF"), fontSize = 12.sp)
            }
        } }
    }
}

@Composable
private fun StoryPersonAvatar(path: String?, size: androidx.compose.ui.unit.Dp) {
    if (path.isNullOrBlank()) Box(Modifier.width(size).height(size).clip(CircleShape).background(Color.White.copy(.10f)), contentAlignment = Alignment.Center) { Text("●", color = Color.White.copy(.65f)) }
    else AsyncImage(path, null, Modifier.width(size).height(size).clip(CircleShape))
}

/** Hoja del propietario: port de `GlassmorphicViewersSheet`. */
@Composable
fun GlassmorphicViewersSheet(
    story: Story,
    viewers: List<StoryViewer>,
    reactions: List<StoryReaction>,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(initialTab.coerceIn(0, 1)) }
    var viewerQuery by remember { mutableStateOf("") }
    var reactionQuery by remember { mutableStateOf("") }
    var audienceUsers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var audienceTitle by remember { mutableStateOf(audienceLabel(story.audience)) }
    var loadingAudience by remember { mutableStateOf(true) }
    var showAudienceMembers by remember { mutableStateOf(false) }
    var reactionUsers by remember { mutableStateOf<Map<String, AppUser>>(emptyMap()) }
    val firestore = remember { FirestoreService() }
    val audience = story.audience?.trim()?.lowercase().orEmpty()

    LaunchedEffect(story.id, story.audience, story.customListId) {
        loadingAudience = true
        val result = runCatching {
            when (audience) {
                "mutuals", "mutual" -> firestore.fetchMutuals(story.authorId)
                "bestfriends", "best_friends", "best-friends" -> firestore.fetchUsers(firestore.fetchUser(story.authorId).bestFriends)
                "customlist" -> firestore.fetchCustomListDetails(story.customListId.orEmpty(), story.authorId).also { audienceTitle = it.name }.let { firestore.fetchUsers(it.members) }
                "custom" -> firestore.fetchUsers(ContentVisibilityService.getUserVisibilitySettings(story.authorId).customStoryViewers)
                "onlyme", "only_me", "only-me" -> listOf(firestore.fetchUser(story.authorId))
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
        audienceUsers = result
        loadingAudience = false
        reactionUsers = runCatching { firestore.fetchUsers(reactions.map { it.userId }.distinct()).associateBy { it.id } }.getOrDefault(emptyMap())
    }

    if (showAudienceMembers) {
        GlassmorphicAudienceMembersSheet(audienceTitle, audienceUsers, onDismiss = { showAudienceMembers = false }, modifier = modifier)
        return
    }
    val filteredViewers = viewers.filter { viewerQuery.isBlank() || (it.username ?: "Usuario").contains(viewerQuery, true) }
    val filteredReactions = reactions.filter { reactionQuery.isBlank() || (reactionUsers[it.userId]?.username ?: "Usuario").contains(reactionQuery, true) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⌄", color = Color.White, fontSize = 25.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(end = 16.dp))
            Text("Activity", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (audience != "everyone" && audience.isNotBlank()) {
            val label = if (loadingAudience) "Loading audience…" else "$audienceTitle · ${audienceUsers.size}"
            Text(label, color = Color.White.copy(.65f), fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clickable(enabled = audienceUsers.isNotEmpty()) { showAudienceMembers = true }.padding(horizontal = 22.dp, vertical = 8.dp))
        } else Text("Everyone", color = Color.White.copy(.65f), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        GlassmorphicTabSelector(listOf("Viewers ${viewers.size}", "Reactions ${reactions.size}"), tab, { tab = it }, Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        if (tab == 0) {
            if (viewers.isEmpty()) GlassmorphicEmptyState("eye.slash", "No viewers yet", modifier = Modifier.weight(1f))
            else ActivityList(viewerQuery, { viewerQuery = it }, filteredViewers) { GlassmorphicViewerRow(it) }
        } else {
            if (reactions.isEmpty()) GlassmorphicEmptyState("heart.slash", "No reactions yet", modifier = Modifier.weight(1f))
            else ActivityList(reactionQuery, { reactionQuery = it }, filteredReactions) { GlassmorphicReactionRow(it, reactionUsers[it.userId]) }
        }
    }
}

@Composable
private fun <T> ActivityList(query: String, onQueryChange: (String) -> Unit, values: List<T>, row: @Composable (T) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        TextField(query, onQueryChange, placeholder = { Text("Search") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        if (values.isEmpty()) GlassmorphicEmptyState("magnifyingglass", "No users match your search.", modifier = Modifier.weight(1f))
        else LazyColumn(Modifier.fillMaxSize()) { items(values) { row(it) } }
    }
}

private fun audienceLabel(raw: String?): String = when (raw?.trim()?.lowercase()) {
    "mutuals", "mutual" -> "Mutuals"
    "bestfriends", "best_friends", "best-friends" -> "Best friends"
    "customlist" -> "Custom list"
    "custom" -> "Custom"
    "onlyme", "only_me", "only-me" -> "Only me"
    else -> "Everyone"
}
