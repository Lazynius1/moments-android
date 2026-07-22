package com.moments.android.views.comments

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.moments.android.R
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.moderation.CommentsModerationService
import com.moments.android.models.AppUser
import com.moments.android.models.Comment
import com.moments.android.models.CommentMentionEntity
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.addComment
import com.moments.android.services.firestore.addCommentReaction
import com.moments.android.services.firestore.deleteComment
import com.moments.android.services.firestore.fetchComments
import com.moments.android.services.firestore.fetchUserByUsername
import com.moments.android.services.firestore.updateComment
import com.moments.android.services.social.AffinityInteractionType
import com.moments.android.services.social.AffinityTracker
import com.moments.android.utilities.MentionDraftToken
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

/**
 * Port de `ModernCommentsView.swift`.
 * Listener + mute filters + menciones + fila enriquecida + composer + moderación.
 */
enum class CommentSortOption { Newest, Oldest, MostLiked }

private enum class MentionInputTarget { NewComment, Editing }

@Composable
fun ModernCommentsView(
    moment: FeedMoment,
    onDismiss: () -> Unit = {},
    onOpenStory: (userId: String) -> Unit = {
        NavigationEventBus.emit(CoordinatorNavigationEvent.ShowStories)
    },
    onOpenProfile: (userId: String) -> Unit = { userId ->
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null && uid == userId) {
            NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToOwnProfileTab)
        } else {
            NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToUserProfileInFeed(userId))
        }
    },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val colors = rememberAdaptiveColors()
    val meLabel = stringResource(R.string.common_me)
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var mutedUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mutedWordsNormalized by remember { mutableStateOf<List<String>>(emptyList()) }
    var temporarilyRevealedCommentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var sortOption by remember { mutableStateOf(CommentSortOption.Newest) }
    var showSortMenu by remember { mutableStateOf(false) }

    var newComment by remember { mutableStateOf("") }
    var newCommentMentions by remember { mutableStateOf<List<CommentMentionEntity>>(emptyList()) }
    var activeNewCommentMention by remember { mutableStateOf<MentionDraftToken?>(null) }

    var replyToComment by remember { mutableStateOf<Comment?>(null) }
    var editingCommentId by remember { mutableStateOf<String?>(null) }
    var editingCommentContent by remember { mutableStateOf("") }
    var editingCommentMentions by remember { mutableStateOf<List<CommentMentionEntity>>(emptyList()) }
    var activeEditingCommentMention by remember { mutableStateOf<MentionDraftToken?>(null) }

    var showDeleteAlert by remember { mutableStateOf(false) }
    var commentToDelete by remember { mutableStateOf<Comment?>(null) }
    var expandedComments by remember { mutableStateOf<Set<String>>(emptySet()) }

    var commentsListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var muteSettingsListener by remember { mutableStateOf<ListenerRegistration?>(null) }

    val filterResult = remember(comments, mutedUserIds, mutedWordsNormalized, currentUid) {
        applyCommentMuteFilters(comments, currentUid, mutedUserIds, mutedWordsNormalized)
    }
    val filteredComments = filterResult.visible
    val mutedWordMaskedIds = filterResult.mutedWordMaskedIds

    val rootComments = remember(filteredComments, sortOption) {
        filteredComments.filter { it.parentCommentId == null }.sortedWith(
            when (sortOption) {
                CommentSortOption.Newest -> compareByDescending { it.timestamp }
                CommentSortOption.Oldest -> compareBy { it.timestamp }
                CommentSortOption.MostLiked -> compareByDescending { it.reactions["like"]?.size ?: 0 }
            },
        )
    }

    fun nestedFor(parentId: String): List<Comment> =
        filteredComments.filter { it.parentCommentId == parentId }.sortedBy { it.timestamp }

    fun setupMuteSettingsListener() {
        val uid = currentUid
        if (uid.isNullOrEmpty()) {
            mutedUserIds = emptySet()
            mutedWordsNormalized = emptyList()
            return
        }
        muteSettingsListener?.remove()
        muteSettingsListener = firestore.db.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                @Suppress("UNCHECKED_CAST")
                val muteSettings = snapshot?.data?.get("muteSettings") as? Map<String, Any?> ?: emptyMap()
                val mutedUsers = ((muteSettings["mutedUsers"] as? List<*>)?.filterIsInstance<String>() ?: emptyList())
                    .filter { it.isNotEmpty() }
                    .toSet()
                val mutedWords = ((muteSettings["mutedWords"] as? List<*>)?.filterIsInstance<String>() ?: emptyList())
                    .map { normalizeMutedText(it) }
                    .filter { it.isNotEmpty() }
                mutedUserIds = mutedUsers
                mutedWordsNormalized = mutedWords
            }
    }

    fun setupCommentsListener() {
        val momentId = moment.id
        if (momentId.isBlank()) {
            isLoading = false
            return
        }
        isLoading = true
        commentsListener?.remove()
        commentsListener = firestore.db
            .collection("users").document(moment.authorId)
            .collection("moments").document(momentId)
            .collection("comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    isLoading = false
                    return@addSnapshotListener
                }
                comments = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
                    runCatching { Comment.from(doc.id, data) }.getOrNull()
                }
                isLoading = false
            }
    }

    fun revealMutedCommentTemporarily(commentId: String) {
        if (commentId.isEmpty()) return
        temporarilyRevealedCommentIds = temporarilyRevealedCommentIds + commentId
        scope.launch {
            delay(8_000)
            temporarilyRevealedCommentIds = temporarilyRevealedCommentIds - commentId
        }
    }

    fun insertMention(user: AppUser, target: MentionInputTarget) {
        when (target) {
            MentionInputTarget.NewComment -> {
                val token = activeNewCommentMention ?: return
                val (text, entity) = CommentMentionDraft.insertMention(user, token, newComment)
                newComment = text
                newCommentMentions = CommentMentionDraft.replacingMention(entity, newCommentMentions)
                activeNewCommentMention = null
            }
            MentionInputTarget.Editing -> {
                val token = activeEditingCommentMention ?: return
                val (text, entity) = CommentMentionDraft.insertMention(user, token, editingCommentContent)
                editingCommentContent = text
                editingCommentMentions = CommentMentionDraft.replacingMention(entity, editingCommentMentions)
                activeEditingCommentMention = null
            }
        }
    }

    fun addComment(content: String, parentCommentId: String?, mentions: List<CommentMentionEntity>) {
        val uid = currentUid ?: return
        val momentId = moment.id.takeIf { it.isNotBlank() } ?: return
        val pendingId = UUID.randomUUID().toString()
        val pending = Comment(
            id = pendingId,
            authorId = uid,
            username = meLabel,
            content = content,
            timestamp = Date(),
            parentCommentId = parentCommentId,
            mentions = mentions,
        ).also { it.isPending = true }
        comments = comments + pending

        scope.launch {
            runCatching {
                firestore.addComment(
                    momentId = momentId,
                    userId = moment.authorId,
                    authorId = uid,
                    content = content,
                    parentCommentId = parentCommentId,
                    commentId = pendingId,
                    mentions = mentions,
                )
            }.onSuccess {
                AffinityTracker.trackInteraction(AffinityInteractionType.MOMENT_COMMENT, moment.authorId)
                moderateCommentInBackground(scope, firestore, content, momentId, uid, moment.authorId)
            }.onFailure {
                comments = comments.filterNot { it.id == pendingId }
            }
        }
    }

    fun updateComment(commentId: String, content: String, mentions: List<CommentMentionEntity>) {
        val momentId = moment.id.takeIf { it.isNotBlank() } ?: return
        scope.launch {
            runCatching {
                firestore.updateComment(
                    momentId = momentId,
                    userId = moment.authorId,
                    commentId = commentId,
                    content = content,
                    mentions = mentions,
                )
            }
        }
    }

    fun deleteComment(comment: Comment) {
        val commentId = comment.id ?: return
        val uid = currentUid ?: return
        val momentId = moment.id.takeIf { it.isNotBlank() } ?: return
        comments = comments.filterNot { it.id == commentId || it.parentCommentId == commentId }
        scope.launch {
            runCatching {
                firestore.deleteComment(
                    momentId = momentId,
                    commentId = commentId,
                    userId = moment.authorId,
                    authorId = uid,
                )
            }
        }
    }

    fun toggleLike(comment: Comment) {
        val commentId = comment.id ?: return
        val momentId = moment.id.takeIf { it.isNotBlank() } ?: return
        scope.launch {
            runCatching {
                firestore.addCommentReaction(
                    momentId = momentId,
                    commentId = commentId,
                    reaction = "like",
                    userId = moment.authorId,
                    authorId = comment.authorId,
                )
            }
        }
    }

    fun handleAvatarTap(userId: String, hasStory: Boolean) {
        val normalized = userId.trim()
        if (normalized.isEmpty()) return
        if (currentUid != null && normalized == currentUid) {
            NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToOwnProfileTab)
            return
        }
        if (hasStory) onOpenStory(normalized) else onOpenProfile(normalized)
    }

    fun handleMentionTap(identifier: String) {
        val trimmed = identifier.trim()
        if (trimmed.isEmpty()) return
        if (trimmed.startsWith("@")) {
            val username = trimmed.drop(1)
            scope.launch {
                runCatching { firestore.fetchUserByUsername(username) }
                    .onSuccess { onOpenProfile(it.id) }
            }
        } else {
            onOpenProfile(trimmed)
        }
    }

    LaunchedEffect(moment.id) {
        isLoading = true
        comments = emptyList()
        commentsListener?.remove()
        muteSettingsListener?.remove()
        delay(100)
        setupMuteSettingsListener()
        setupCommentsListener()
    }

    DisposableEffect(Unit) {
        onDispose {
            commentsListener?.remove()
            muteSettingsListener?.remove()
        }
    }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(5_000)
            if (isLoading) isLoading = false
        }
    }

    // El sheet aporta altura acotada (weight/height). fillMaxSize aquí es seguro.
    Box(modifier.fillMaxSize().background(colors.surfaceBackground)) {
        if (moment.disableComments) {
            Column(Modifier.fillMaxSize()) {
                CommentsHeader(
                    title = stringResource(R.string.modern_comments_title),
                    subtitle = stringResource(R.string.modern_comments_post_of, moment.username),
                    count = null,
                    isLoading = false,
                    showSortMenu = showSortMenu,
                    onShowSortMenuChange = { showSortMenu = it },
                    onSortChange = { sortOption = it },
                )
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(R.string.modern_comments_disabled_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = colors.primary,
                    )
                    Text(
                        stringResource(R.string.modern_comments_disabled_description),
                        fontSize = 14.sp,
                        color = Color.Gray,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        } else {
            // iOS: VStack { header; list (flex); input anclado abajo }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val headerApprox = 72.dp
                val composerApprox = 88.dp
                val listHeight = (maxHeight - headerApprox - composerApprox).coerceAtLeast(120.dp)

                Column(Modifier.fillMaxSize()) {
                    CommentsHeader(
                        title = stringResource(R.string.modern_comments_title),
                        subtitle = stringResource(R.string.modern_comments_post_of, moment.username),
                        count = if (!isLoading && filteredComments.isNotEmpty()) filteredComments.size else null,
                        isLoading = isLoading,
                        showSortMenu = showSortMenu,
                        onShowSortMenuChange = { showSortMenu = it },
                        onSortChange = { sortOption = it },
                    )

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(listHeight),
                    ) {
                        when {
                            isLoading && comments.isEmpty() -> CommentRowSkeletonList(rows = 4)
                            rootComments.isEmpty() -> {
                                Column(
                                    Modifier.fillMaxSize().padding(horizontal = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        stringResource(R.string.modern_comments_empty_title),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 18.sp,
                                        color = colors.primary,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.modern_comments_empty_description),
                                        fontSize = 14.sp,
                                        color = Color.Gray,
                                    )
                                }
                            }
                            else -> {
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp, start = 8.dp, end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(rootComments, key = { it.id ?: it.hashCode().toString() }) { comment ->
                                        val id = comment.id.orEmpty()
                                        EnhancedModernCommentRow(
                                            comment = comment,
                                            currentUid = currentUid,
                                            momentAuthorId = moment.authorId,
                                            nestedComments = nestedFor(id),
                                            isExpanded = expandedComments.contains(id),
                                            onToggleExpand = { commentId ->
                                                expandedComments = if (commentId in expandedComments) {
                                                    expandedComments - commentId
                                                } else {
                                                    expandedComments + commentId
                                                }
                                            },
                                            onLike = { toggleLike(it) },
                                            onReply = { replyToComment = it },
                                            onEdit = {
                                                editingCommentId = it.id
                                                editingCommentContent = it.content
                                                editingCommentMentions = it.mentions
                                                activeEditingCommentMention = CommentMentionDraft.detectToken(it.content)
                                                replyToComment = null
                                            },
                                            onDelete = {
                                                commentToDelete = it
                                                showDeleteAlert = true
                                            },
                                            onAvatarTap = { userId, hasStory -> handleAvatarTap(userId, hasStory) },
                                            onMentionTap = { handleMentionTap(it) },
                                            maskedCommentIds = mutedWordMaskedIds,
                                            temporarilyRevealedCommentIds = temporarilyRevealedCommentIds,
                                            onRevealTemporarily = { revealMutedCommentTemporarily(it) },
                                            nestingLevel = 0,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceBackground),
                    ) {
                        val activeMention = activeEditingCommentMention ?: activeNewCommentMention
                        if (activeMention != null) {
                            CommentMentionSearchOverlay(
                                query = activeMention.query,
                                showsSearchField = false,
                                onSelect = { user ->
                                    if (activeEditingCommentMention != null) {
                                        insertMention(user, MentionInputTarget.Editing)
                                    } else {
                                        insertMention(user, MentionInputTarget.NewComment)
                                    }
                                },
                                onCancel = {
                                    activeEditingCommentMention = null
                                    activeNewCommentMention = null
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }

                        replyToComment?.let { reply ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.modern_comments_replying_to, reply.username),
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { replyToComment = null }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, null, tint = Color.Gray)
                                }
                            }
                        }

                        CommentComposer(
                            text = if (editingCommentId != null) editingCommentContent else newComment,
                            onTextChange = { value ->
                                if (editingCommentId != null) {
                                    editingCommentContent = value
                                    activeEditingCommentMention = CommentMentionDraft.detectToken(value)
                                    editingCommentMentions =
                                        CommentMentionDraft.sanitizedMentions(editingCommentMentions, value)
                                } else {
                                    newComment = value
                                    activeNewCommentMention = CommentMentionDraft.detectToken(value)
                                    newCommentMentions =
                                        CommentMentionDraft.sanitizedMentions(newCommentMentions, value)
                                }
                            },
                            isEditing = editingCommentId != null,
                            enabled = true,
                            currentUid = currentUid,
                            onAvatarTap = { hasStory ->
                                currentUid?.let { handleAvatarTap(it, hasStory) }
                            },
                            onSend = {
                                if (editingCommentId != null) {
                                    val id = editingCommentId ?: return@CommentComposer
                                    if (editingCommentContent.isBlank()) return@CommentComposer
                                    val mentions = CommentMentionDraft.sanitizedMentions(
                                        editingCommentMentions,
                                        editingCommentContent,
                                    )
                                    updateComment(id, editingCommentContent.trim(), mentions)
                                    editingCommentId = null
                                    editingCommentContent = ""
                                    editingCommentMentions = emptyList()
                                    activeEditingCommentMention = null
                                } else {
                                    val text = newComment.trim()
                                    if (text.isEmpty()) return@CommentComposer
                                    val mentions = CommentMentionDraft.sanitizedMentions(newCommentMentions, newComment)
                                    addComment(text, replyToComment?.id, mentions)
                                    newComment = ""
                                    newCommentMentions = emptyList()
                                    activeNewCommentMention = null
                                    replyToComment = null
                                }
                            },
                            onCancelEdit = {
                                editingCommentId = null
                                editingCommentContent = ""
                                editingCommentMentions = emptyList()
                                activeEditingCommentMention = null
                            },
                        )
                    }
                }
            }
        }

        if (showDeleteAlert) {
            AlertDialog(
                onDismissRequest = { showDeleteAlert = false },
                title = { Text(stringResource(R.string.modern_comments_delete_title)) },
                text = { Text(stringResource(R.string.modern_comments_delete_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            commentToDelete?.let { deleteComment(it) }
                            showDeleteAlert = false
                            commentToDelete = null
                        },
                    ) {
                        Text(stringResource(R.string.modern_comments_delete_confirm), color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAlert = false }) {
                        Text(stringResource(R.string.modern_comments_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun CommentsHeader(
    title: String,
    subtitle: String,
    count: Int?,
    isLoading: Boolean,
    showSortMenu: Boolean,
    onShowSortMenuChange: (Boolean) -> Unit,
    onSortChange: (CommentSortOption) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.primary)
                when {
                    isLoading -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    count != null -> {
                        Text(
                            "$count",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .background(
                                    Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFFA855F7))),
                                    RoundedCornerShape(50),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(subtitle, fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(onClick = { onShowSortMenuChange(true) }) {
                    Icon(Icons.Filled.MoreVert, null, tint = colors.primary)
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { onShowSortMenuChange(false) }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.modern_comments_sort_newest)) },
                        onClick = { onSortChange(CommentSortOption.Newest); onShowSortMenuChange(false) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.modern_comments_sort_oldest)) },
                        onClick = { onSortChange(CommentSortOption.Oldest); onShowSortMenuChange(false) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.modern_comments_sort_most_liked)) },
                        onClick = { onSortChange(CommentSortOption.MostLiked); onShowSortMenuChange(false) },
                    )
                }
            }
            // iOS: dismiss vía sheet / drag indicator — sin botón Cerrar
        }
    }
}

@Composable
private fun CommentComposer(
    text: String,
    onTextChange: (String) -> Unit,
    isEditing: Boolean,
    enabled: Boolean,
    currentUid: String?,
    onAvatarTap: (hasStory: Boolean) -> Unit,
    onSend: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (isEditing) {
            Text(
                stringResource(R.string.modern_comments_editing),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.primary,
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (currentUid != null && !isEditing) {
                StoryRingAvatarView(
                    userId = currentUid,
                    size = 36.dp,
                    lineWidth = 2.2.dp,
                    showBaseStroke = true,
                    onTap = onAvatarTap,
                )
                Spacer(Modifier.width(8.dp))
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                enabled = enabled,
                cursorBrush = SolidColor(colors.primary),
                textStyle = TextStyle(color = colors.primary, fontSize = 15.sp),
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                        RoundedCornerShape(25.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            if (isEditing) {
                                stringResource(R.string.comments_edit_placeholder)
                            } else {
                                stringResource(R.string.modern_comments_placeholder)
                            },
                            color = Color.Gray,
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(8.dp))
            if (isEditing) {
                IconButton(onClick = onCancelEdit) {
                    Icon(Icons.Filled.Close, null, tint = Color.Gray)
                }
            }
            IconButton(onClick = onSend, enabled = enabled && text.isNotBlank()) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.modern_comments_send),
                    tint = if (text.isNotBlank()) Color(0xFF3B82F6) else Color.Gray,
                )
            }
        }
    }
}

private fun moderateCommentInBackground(
    scope: kotlinx.coroutines.CoroutineScope,
    firestore: FirestoreService,
    content: String,
    momentId: String,
    authorUid: String,
    momentAuthorId: String,
) {
    scope.launch {
        delay(2_000)
        CommentsModerationService.shared.moderateAndHandle(
            content = content,
            onApproved = {
                CommentsModerationService.shared.logModerationEvent(
                    userId = authorUid, content = content, action = "approved",
                    reason = "Contenido apropiado", category = "clean", momentId = momentId,
                )
            },
            onWarning = { reason, category ->
                CommentsModerationService.shared.logModerationEvent(
                    userId = authorUid, content = content, action = "flagged_for_review",
                    reason = reason, category = category, momentId = momentId,
                )
            },
            onRejected = { reason, category ->
                CommentsModerationService.shared.logModerationEvent(
                    userId = authorUid, content = content, action = "auto_deleted_silent",
                    reason = reason, category = category, momentId = momentId,
                )
                runCatching {
                    val page = firestore.fetchComments(momentId, momentAuthorId, limit = 50)
                    val target = page.comments
                        .filter { it.content == content && it.authorId == authorUid }
                        .maxByOrNull { it.timestamp.time }
                    val commentId = target?.id ?: return@runCatching
                    firestore.deleteComment(momentId, commentId, momentAuthorId, authorUid)
                }
            },
            onError = { error ->
                CommentsModerationService.shared.logModerationEvent(
                    userId = authorUid, content = content, action = "moderation_error",
                    reason = "API error: ${error.message}", category = "system_error", momentId = momentId,
                )
            },
        )
    }
}
