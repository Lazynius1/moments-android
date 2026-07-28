package com.moments.android.views.story.storyviewer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.GroupOff
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.models.Story
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchCustomListDetails
import com.moments.android.services.firestore.fetchMutuals
import com.moments.android.services.firestore.fetchUser
import com.moments.android.services.privacy.ContentVisibilityService
import com.moments.android.utilities.MomentsFormat
import com.moments.android.utilities.momentsEmptyStateAppear
import com.moments.android.views.components.AudienceIconMetrics
import com.moments.android.views.components.AudienceIconView
import com.moments.android.views.creator.audienceselector.ContentAudience
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.story.StoryReaction
import com.moments.android.views.story.StoryViewer
import com.moments.android.views.story.VerifiedBadgeView
import kotlinx.coroutines.tasks.await

/** Primer bloque de `StoryViewerOverlay.swift`: barra de progreso por audiencia. */
@Composable
fun GlassmorphicProgressBar(
    progress: Float,
    isActive: Boolean,
    audience: String?,
    modifier: Modifier = Modifier,
) {
    val normalizedAudience = audience?.trim()?.lowercase().orEmpty()
    val (colors, shadowColor) = when (normalizedAudience) {
        "bestfriends", "best_friends", "best-friends" ->
            listOf(Color.fromHex("24C26A"), Color.fromHex("5BE584")) to Color.fromHex("24C26A").copy(0.65f)
        "mutuals", "mutual" ->
            listOf(Color.fromHex("00B4D8"), Color.fromHex("4CC9F0")) to Color.fromHex("00B4D8").copy(0.55f)
        else ->
            listOf(Color.Blue, Color(0xFF9C27B0), Color(0xFFFF4081)) to Color(0xFF9C27B0).copy(0.6f)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (isActive) tween(100, easing = LinearEasing) else snap(),
        label = "glassProgress",
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(2.5.dp),
    ) {
        val barShape = RoundedCornerShape(1.25.dp)
        Box(
            Modifier
                .fillMaxSize()
                .clip(barShape)
                .background(Color.White.copy(0.15f)),
        )
        Box(
            Modifier
                .width(maxWidth * animatedProgress)
                .height(2.5.dp)
                .shadow(3.dp, barShape, ambientColor = shadowColor, spotColor = shadowColor, clip = false)
                .clip(barShape)
                .background(Brush.horizontalGradient(colors)),
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
    // ≡ iOS: blanco / rojo sobre storyGlassmorphic
    val titleColor = if (isDestructive) Color.Red else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .storyGlassmorphic(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon.isNotBlank()) {
            Text(icon, color = titleColor, fontSize = 18.sp, modifier = Modifier.width(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(it, color = Color.White.copy(0.7f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun GlassmorphicSuccessMessage(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .storyGlassmorphic(RoundedCornerShape(percent = 50))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Color.fromHex("007AFF"),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
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
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black.copy(0.88f)
    val secondary = if (isDark) Color.White.copy(0.82f) else Color.Black.copy(0.62f)
    val scrim = if (isDark) Color.Black.copy(0.45f) else Color.Black.copy(0.20f)
    val btnShape = RoundedCornerShape(16.dp)
    val panelShape = RoundedCornerShape(24.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(scrim)
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(panelShape)
                .background(Color.White.copy(0.08f))
                .momentsChromeGlass(panelShape, interactive = false)
                .clickable(enabled = false) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    title,
                    color = primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
                if (message.trim().isNotEmpty()) {
                    Text(
                        message,
                        color = secondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .momentsChromeGlass(btnShape, interactive = true)
                        .clip(btnShape)
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(cancelTitle, color = primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .momentsChromeGlass(btnShape, interactive = true)
                        .clip(btnShape)
                        .clickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        confirmTitle,
                        color = if (isDestructive) Color.Red else primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
fun GlassmorphicTabSelector(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black.copy(0.88f)
    val secondary = if (isDark) Color.White.copy(0.52f) else Color.Black.copy(0.46f)
    Row(modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, label ->
            val selected = selectedIndex == index
            Box(
                Modifier
                    .weight(1f)
                    .clickable { onSelected(index) }
                    .padding(vertical = 8.dp)
                    // ≡ opacity(isSelected ? 1 : 0.72)
                    .graphicsLayer { alpha = if (selected) 1f else 0.72f },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) primary else secondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(top = 22.dp)
                        .width(28.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) primary.copy(0.86f) else Color.Transparent),
                )
            }
        }
    }
}

@Composable
fun GlassmorphicViewerRow(viewer: StoryViewer, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black.copy(0.88f)
    val secondary = if (isDark) Color.White.copy(0.65f) else Color.Black.copy(0.52f)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StoryPersonAvatar(viewer.profileImagePath, 48.dp, stroked = true)
            Spacer(Modifier.width(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    viewer.username ?: stringResource(R.string.common_user_fallback),
                    color = primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                viewer.rewatchBadgeText?.let {
                    Text(it, color = primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.weight(1f))
        }
        HorizontalDivider(color = secondary.copy(if (isDark) 0.18f else 0.12f))
    }
}

@Composable
fun GlassmorphicReactionRow(
    reaction: StoryReaction,
    user: AppUser?,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black.copy(0.88f)
    val secondary = if (isDark) Color.White.copy(0.65f) else Color.Black.copy(0.52f)
    var resolvedUser by remember(reaction.userId) { mutableStateOf(user) }
    val timeAgo = remember(reaction.timestamp) { MomentsFormat.relativeTime(reaction.timestamp) }
    LaunchedEffect(reaction.userId, user) {
        if (user == null) {
            resolvedUser = runCatching { FirestoreService().fetchUser(reaction.userId) }.getOrNull()
        } else {
            resolvedUser = user
        }
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StoryPersonAvatar(resolvedUser?.profileImagePath, 48.dp, stroked = true)
            Spacer(Modifier.width(16.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    resolvedUser?.username ?: stringResource(R.string.common_user_fallback),
                    color = primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(timeAgo, color = secondary, fontSize = 13.sp)
            }
            Text(reaction.reaction, fontSize = 32.sp)
        }
        HorizontalDivider(color = secondary.copy(if (isDark) 0.18f else 0.12f))
    }
}

@Composable
fun GlassmorphicEmptyState(
    icon: ImageVector,
    message: String,
    showCloseButton: Boolean = false,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black.copy(0.84f)
    val secondary = if (isDark) Color.White.copy(0.62f) else Color.Black.copy(0.48f)
    Column(
        modifier
            .momentsEmptyStateAppear()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = secondary, modifier = Modifier.size(22.dp))
        Text(
            message,
            color = secondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        if (showCloseButton && onClose != null) {
            // ≡ momentsChromeGlass Capsule + stories.close
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.stories_close),
                    color = primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun GlassmorphicAudienceMembersSheet(
    title: String,
    users: List<AppUser>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black.copy(0.88f)
    Column(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onDismiss)
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
            }
            Text(
                title,
                color = primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
            )
        }
        if (users.isEmpty()) {
            GlassmorphicEmptyState(
                Icons.Outlined.GroupOff,
                stringResource(R.string.stories_activity_audience_no_members),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp)
                    .padding(top = 22.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(users, key = { it.id }) { user ->
                    GlassmorphicAudienceMemberRow(user)
                }
            }
        }
    }
}

@Composable
private fun GlassmorphicAudienceMemberRow(user: AppUser) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White.copy(0.92f) else Color.Black.copy(0.86f)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StoryPersonAvatar(user.profileImagePath, 42.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                user.username,
                color = primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (user.isVerified) {
                VerifiedBadgeView(userId = user.id, size = 12.dp)
            }
        }
    }
}

@Composable
private fun StoryPersonAvatar(
    path: String?,
    size: androidx.compose.ui.unit.Dp,
    stroked: Boolean = false,
) {
    val isDark = isSystemInDarkTheme()
    if (path.isNullOrBlank()) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = if (isDark) Color.White.copy(0.65f) else Color.Black.copy(0.45f),
                modifier = Modifier.size(size * 0.45f),
            )
        }
    } else {
        AsyncImage(
            model = path,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(
                    if (stroked) Modifier.border(1.dp, Color.White.copy(0.1f), CircleShape)
                    else Modifier,
                ),
        )
    }
}

@Composable
private fun ActivitySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black.copy(0.88f)
    val secondary = if (isDark) Color.White.copy(0.55f) else Color.Black.copy(0.45f)
    Row(
        modifier
            .fillMaxWidth()
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = secondary, modifier = Modifier.size(18.dp))
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(stringResource(R.string.user_list_view_search_placeholder), color = secondary, fontSize = 14.sp)
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = primary,
                unfocusedTextColor = primary,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = primary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = secondary.copy(0.8f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Port de `GlassmorphicViewersSheet` (StoryViewerOverlay.swift).
 * Presentar con [MomentsModalSheet] (`largeOnly = false` ≡ medium+large).
 */
@Composable
fun GlassmorphicViewersSheet(
    story: Story,
    viewers: List<StoryViewer>,
    reactions: List<StoryReaction>,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black.copy(0.88f)
    val secondary = if (isDark) Color.White.copy(0.62f) else Color.Black.copy(0.54f)
    val firestore = remember { FirestoreService() }

    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var viewerSearchText by remember { mutableStateOf("") }
    var reactionSearchText by remember { mutableStateOf("") }
    var audienceUsers by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var audienceListName by remember { mutableStateOf<String?>(null) }
    var isLoadingAudience by remember { mutableStateOf(false) }
    var didLoadAudience by remember { mutableStateOf(false) }
    var showAudienceList by remember { mutableStateOf(false) }
    var reactionUsersById by remember { mutableStateOf<Map<String, AppUser>>(emptyMap()) }

    val normalizedAudience = story.audience?.trim()?.lowercase().orEmpty().ifEmpty { "everyone" }
    val isEveryoneAudience = normalizedAudience == "everyone"
    val displayAudience = ContentAudience.fromAudienceValue(story.audience)

    val audienceTitle = when (normalizedAudience) {
        "mutuals", "mutual" -> stringResource(R.string.audience_type_mutuals)
        "bestfriends", "best_friends", "best-friends" -> stringResource(R.string.audience_type_best_friends)
        "customlist" -> audienceListName ?: stringResource(R.string.audience_type_custom_list)
        "custom" -> stringResource(R.string.audience_type_custom)
        "onlyme", "only_me", "only-me" -> stringResource(R.string.audience_type_only_me)
        else -> stringResource(R.string.audience_type_everyone)
    }

    val fallbackUser = stringResource(R.string.common_user_fallback)
    val filteredViewers = remember(viewers, viewerSearchText) {
        val q = viewerSearchText.trim()
        if (q.isEmpty()) viewers
        else viewers.filter { (it.username ?: fallbackUser).contains(q, ignoreCase = true) }
    }
    val filteredReactions = remember(reactions, reactionSearchText, reactionUsersById) {
        val q = reactionSearchText.trim()
        if (q.isEmpty()) reactions
        else reactions.filter { reaction ->
            (reactionUsersById[reaction.userId]?.username ?: fallbackUser).contains(q, ignoreCase = true)
        }
    }

    LaunchedEffect(story.id, story.audience, story.customListId, reactions.map { it.userId }) {
        if (!didLoadAudience) {
            didLoadAudience = true
            isLoadingAudience = true
            audienceUsers = emptyList()
            audienceListName = null
            audienceUsers = loadAudienceMembers(firestore, story, normalizedAudience) { name ->
                audienceListName = name
            }
            isLoadingAudience = false
        }
        val missing = reactions.map { it.userId }.distinct().filter { it !in reactionUsersById }
        if (missing.isNotEmpty()) {
            val loaded = runCatching { firestore.fetchUsers(missing) }.getOrDefault(emptyList())
            reactionUsersById = reactionUsersById + loaded.associateBy { it.id }
        }
    }

    if (showAudienceList) {
        MomentsModalSheet(
            onDismissRequest = { showAudienceList = false },
            largeOnly = false,
        ) {
            GlassmorphicAudienceMembersSheet(
                title = audienceTitle,
                users = audienceUsers,
                onDismiss = { showAudienceList = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    Column(modifier.fillMaxSize()) {
        // Header
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 12.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onDismiss)
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = primary)
            }
            Text(
                stringResource(R.string.stories_activity_title),
                color = primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Audience section
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                isLoadingAudience -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = secondary, strokeWidth = 2.dp)
                        Text(stringResource(R.string.stories_activity_audience_loading), color = secondary, fontSize = 13.sp)
                    }
                }
                isEveryoneAudience -> {
                    Text(stringResource(R.string.audience_description_everyone), color = secondary, fontSize = 13.sp)
                }
                else -> {
                    val canOpen = audienceUsers.isNotEmpty()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = canOpen) { showAudienceList = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(primary.copy(if (isDark) 0.08f else 0.06f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            AudienceIconView(
                                audience = displayAudience,
                                size = AudienceIconMetrics.storyActivity,
                                tintColor = primary.copy(0.82f),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(audienceTitle, color = primary, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1)
                            Text(
                                if (audienceUsers.isEmpty()) {
                                    stringResource(R.string.stories_activity_audience_no_members)
                                } else {
                                    stringResource(R.string.stories_activity_audience_members_count, audienceUsers.size)
                                },
                                color = secondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                        if (canOpen) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = secondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        GlassmorphicTabSelector(
            tabs = listOf(
                stringResource(R.string.stories_activity_viewers_tab, viewers.size),
                stringResource(R.string.stories_activity_reactions_tab, reactions.size),
            ),
            selectedIndex = selectedTab,
            onSelected = { selectedTab = it },
            modifier = Modifier
                .padding(horizontal = 22.dp)
                .padding(top = 14.dp),
        )

        when (selectedTab) {
            0 -> {
                if (viewers.isEmpty()) {
                    GlassmorphicEmptyState(
                        Icons.Outlined.VisibilityOff,
                        stringResource(R.string.stories_activity_no_viewers),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp)
                            .padding(top = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ActivitySearchBar(viewerSearchText, { viewerSearchText = it })
                        if (filteredViewers.isEmpty()) {
                            GlassmorphicEmptyState(
                                Icons.Filled.Search,
                                stringResource(R.string.stories_activity_search_empty),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(filteredViewers, key = { it.id }) { GlassmorphicViewerRow(it) }
                            }
                        }
                    }
                }
            }
            else -> {
                if (reactions.isEmpty()) {
                    GlassmorphicEmptyState(
                        Icons.Outlined.HeartBroken,
                        stringResource(R.string.stories_activity_no_reactions),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp)
                            .padding(top = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ActivitySearchBar(reactionSearchText, { reactionSearchText = it })
                        if (filteredReactions.isEmpty()) {
                            GlassmorphicEmptyState(
                                Icons.Filled.Search,
                                stringResource(R.string.stories_activity_search_empty),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        } else {
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(filteredReactions, key = { "${it.userId}_${it.reaction}_${it.timestamp.time}" }) {
                                    GlassmorphicReactionRow(it, reactionUsersById[it.userId])
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun loadAudienceMembers(
    firestore: FirestoreService,
    story: Story,
    normalizedAudience: String,
    onListName: (String) -> Unit,
): List<AppUser> = runCatching {
    when (normalizedAudience) {
        "mutuals", "mutual" ->
            firestore.fetchMutuals(story.authorId).sortedBy { it.username.lowercase() }
        "bestfriends", "best_friends", "best-friends" -> {
            val ids = firestore.fetchUser(story.authorId).bestFriends
            fetchAudienceUsersByIds(firestore, ids)
        }
        "customlist" -> {
            val listId = story.customListId.orEmpty()
            if (listId.isEmpty()) emptyList()
            else {
                val list = firestore.fetchCustomListDetails(listId, story.authorId)
                onListName(list.name)
                fetchAudienceUsersByIds(firestore, list.members)
            }
        }
        "custom" -> {
            val snap = FirebaseFirestore.getInstance()
                .collection("users").document(story.authorId).get().await()
            @Suppress("UNCHECKED_CAST")
            val visibility = snap.data?.get("contentVisibilitySettings") as? Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val customUsers = (visibility?.get("storyCustomUsers") as? List<*>)?.filterIsInstance<String>()
                ?: (visibility?.get("customStoryViewers") as? List<*>)?.filterIsInstance<String>()
                ?: ContentVisibilityService.getUserVisibilitySettings(story.authorId).customStoryViewers
            fetchAudienceUsersByIds(firestore, customUsers)
        }
        "onlyme", "only_me", "only-me" ->
            fetchAudienceUsersByIds(firestore, listOf(story.authorId))
        else -> emptyList()
    }
}.getOrDefault(emptyList())

private suspend fun fetchAudienceUsersByIds(
    firestore: FirestoreService,
    userIds: List<String>,
): List<AppUser> {
    val uniqueIds = userIds.filter { it.isNotEmpty() }.distinct()
    if (uniqueIds.isEmpty()) return emptyList()
    val merged = firestore.fetchUsers(uniqueIds)
    val order = uniqueIds.withIndex().associate { it.value to it.index }
    return merged.sortedBy { order[it.id] ?: Int.MAX_VALUE }
}
