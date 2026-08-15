package com.moments.android.views.comments

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.moments.android.R
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.moderation.CommentsModerationService
import com.moments.android.models.AppUser
import com.moments.android.models.Comment
import com.moments.android.models.CommentMentionEntity
import com.moments.android.services.auth.AuthService
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
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MentionDraftToken
import com.moments.android.views.components.CommentRowSkeletonList
import com.moments.android.views.components.LiveUsernameContent
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
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
    onOpenStory: (userId: String) -> Unit = { userId ->
        NavigationEventBus.emit(CoordinatorNavigationEvent.ShowStoriesStartingAt(userId))
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
    val authUser by AuthService.currentUser.collectAsState()

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
        HapticManager.shared.selection()
    }

    fun initializeCommentsView() {
        scope.launch {
            isLoading = true
            comments = emptyList()
            commentsListener?.remove()
            delay(100)
            setupMuteSettingsListener()
            setupCommentsListener()
        }
    }

    fun addComment(content: String, parentCommentId: String?, mentions: List<CommentMentionEntity>) {
        val uid = currentUid ?: return
        val momentId = moment.id.takeIf { it.isNotBlank() } ?: return
        val pendingId = UUID.randomUUID().toString()
        val pending = Comment(
            id = pendingId,
            authorId = uid,
            username = authUser?.username?.takeIf { it.isNotBlank() } ?: meLabel,
            content = content,
            timestamp = Date(),
            parentCommentId = parentCommentId,
            mentions = mentions,
            profileImagePath = authUser?.profileImagePath,
        ).also { it.isPending = true }
        comments = comments + pending
        HapticManager.shared.mediumImpact()

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
        scope.launch {
            runCatching {
                firestore.deleteComment(
                    momentId = momentId,
                    commentId = commentId,
                    userId = moment.authorId,
                    authorId = uid,
                )
            }.onSuccess {
                HapticManager.shared.warning()
                comments = comments.filterNot { it.id == commentId || it.parentCommentId == commentId }
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

    var skipNextResumeInit by remember(moment.id) { mutableStateOf(true) }

    LaunchedEffect(moment.id) {
        initializeCommentsView()
        skipNextResumeInit = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, moment.id) {
        val observer = LifecycleEventObserver { _, event ->
            // ≡ iOS didBecomeActive → re-init (no el primer resume tras open)
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipNextResumeInit) {
                    skipNextResumeInit = false
                } else {
                    initializeCommentsView()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            commentsListener?.remove()
            muteSettingsListener?.remove()
            isLoading = false
            comments = emptyList()
            commentsListener = null
            muteSettingsListener = null
        }
    }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(5_000)
            if (isLoading) isLoading = false
        }
    }

    // Sheet large: header + lista (weight) + composer al fondo.
    Column(modifier.fillMaxWidth().fillMaxSize().background(colors.surfaceBackground)) {
        if (moment.disableComments) {
            CommentsHeader(
                authorId = moment.authorId,
                fallbackUsername = moment.username,
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
                AttachmentIconView(
                    icon = AttachmentIcon.COMMENTS,
                    preset = AttachmentIconPreset.EMPTY_STATE_HERO,
                        tintColor = colors.tertiary,
                )
                Text(
                    stringResource(R.string.modern_comments_disabled_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = colors.primary,
                )
                Text(
                    stringResource(R.string.modern_comments_disabled_description),
                    fontSize = 14.sp,
                    color = colors.secondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
        } else {
            CommentsHeader(
                authorId = moment.authorId,
                fallbackUsername = moment.username,
                count = if (!isLoading && filteredComments.isNotEmpty()) filteredComments.size else null,
                isLoading = isLoading,
                showSortMenu = showSortMenu,
                onShowSortMenuChange = { showSortMenu = it },
                onSortChange = { sortOption = it },
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    isLoading -> CommentRowSkeletonList(
                        rows = 4,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = 8.dp),
                    )
                    rootComments.isEmpty() -> {
                        ModernEmptyCommentsView(modifier = Modifier.fillMaxSize())
                    }
                    else -> {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 12.dp, top = 8.dp, start = 16.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                                        activeEditingCommentMention =
                                            CommentMentionDraft.detectToken(it.content)
                                        replyToComment = null
                                    },
                                    onDelete = {
                                        commentToDelete = it
                                        showDeleteAlert = true
                                    },
                                    onAvatarTap = { userId, hasStory ->
                                        handleAvatarTap(userId, hasStory)
                                    },
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

            Column(Modifier.fillMaxWidth()) {
                replyToComment?.let { reply ->
                    val preview = reply.content.take(50) + if (reply.content.length > 50) "..." else ""
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.controlSurface)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.modern_comments_replying_to, reply.username),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.secondary,
                            )
                            Text(
                                preview,
                                fontSize = 11.sp,
                                color = colors.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { replyToComment = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, null, tint = colors.tertiary)
                        }
                    }
                }

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
                    enabled = !isLoading,
                    replyUsername = replyToComment?.username,
                    currentUid = currentUid,
                    onAvatarTap = { hasStory ->
                        val uid = currentUid ?: return@CommentComposer
                        if (hasStory) onOpenStory(uid) else onOpenProfile(uid)
                    },
                    onSend = {
                        if (editingCommentId != null) {
                            val id = editingCommentId ?: return@CommentComposer
                            if (editingCommentContent.isEmpty()) return@CommentComposer
                            HapticManager.shared.mediumImpact()
                            val mentions = CommentMentionDraft.sanitizedMentions(
                                editingCommentMentions,
                                editingCommentContent,
                            )
                            updateComment(id, editingCommentContent, mentions)
                            editingCommentId = null
                            editingCommentContent = ""
                            editingCommentMentions = emptyList()
                            activeEditingCommentMention = null
                        } else {
                            if (newComment.isEmpty()) return@CommentComposer
                            val mentions =
                                CommentMentionDraft.sanitizedMentions(newCommentMentions, newComment)
                            addComment(newComment, replyToComment?.id, mentions)
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
    authorId: String,
    fallbackUsername: String,
    count: Int?,
    isLoading: Boolean,
    showSortMenu: Boolean,
    onShowSortMenuChange: (Boolean) -> Unit,
    onSortChange: (CommentSortOption) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    // Pegado al drag handle del ModalBottomSheet (sin gap grande encima del título).
    Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 8.dp)) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.modern_comments_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colors.primary,
                )
                when {
                    isLoading -> MomentsCircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    count != null -> {
                        Text(
                            "$count",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .background(
                                    Brush.horizontalGradient(listOf(Color(0xFF007AFF), Color(0xFFAF52DE))),
                                    RoundedCornerShape(50),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LiveUsernameContent(userId = authorId, fallbackUsername = fallbackUsername) { username ->
                        Text(
                            stringResource(R.string.modern_comments_post_of, username),
                        fontSize = 12.sp,
                        color = colors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                VerifiedBadgeView(userId = authorId, size = 10.dp)
            }
        }
        Box(Modifier.align(Alignment.CenterEnd)) {
            Box(
                Modifier
                    .size(32.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .border(0.8.dp, colors.controlStroke, CircleShape)
                    .clickable { onShowSortMenuChange(true) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { onShowSortMenuChange(false) }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.modern_comments_sort_newest)) },
                    leadingIcon = { Icon(Icons.Filled.ArrowCircleUp, contentDescription = null) },
                    onClick = { onSortChange(CommentSortOption.Newest); onShowSortMenuChange(false) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.modern_comments_sort_oldest)) },
                    leadingIcon = { Icon(Icons.Filled.ArrowCircleDown, contentDescription = null) },
                    onClick = { onSortChange(CommentSortOption.Oldest); onShowSortMenuChange(false) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.modern_comments_sort_most_liked)) },
                    leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    onClick = { onSortChange(CommentSortOption.MostLiked); onShowSortMenuChange(false) },
                )
            }
        }
    }
}

/** Port de `ModernEmptyCommentsView`. */
@Composable
private fun ModernEmptyCommentsView(modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier
                .size(80.dp)
                .background(colors.controlSurface, CircleShape)
                .border(2.dp, colors.controlStroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(40.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.modern_comments_empty_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = colors.primary,
            )
            Text(
                stringResource(R.string.modern_comments_empty_description),
                fontSize = 14.sp,
                color = colors.secondary,
                textAlign = TextAlign.Center,
            )
            Text(
                "💭",
                fontSize = 24.sp,
                modifier = Modifier.padding(top = 0.dp),
            )
        }
    }
}

@Composable
private fun CommentComposer(
    text: String,
    onTextChange: (String) -> Unit,
    isEditing: Boolean,
    enabled: Boolean,
    replyUsername: String?,
    currentUid: String?,
    onAvatarTap: (hasStory: Boolean) -> Unit,
    onSend: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val canSend = enabled && text.isNotEmpty()
    // ≡ iOS `.scaleEffect(newComment.isEmpty || isLoading ? 0.95 : 1.0)` + spring
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.95f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "commentSendScale",
    )
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)) {
        if (isEditing) {
            // ≡ iOS modo edición: pencil + campo + check azul→púrpura + xmark circular
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .background(colors.controlSurface, RoundedCornerShape(20.dp))
                        .border(1.dp, colors.controlStroke, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.modern_comments_editing),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.primary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    // ≡ iOS TextField axis:.vertical + lineLimit(1...4)
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        enabled = enabled,
                        cursorBrush = SolidColor(colors.primary),
                        textStyle = TextStyle(color = colors.primary, fontSize = 15.sp),
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 20.dp),
                        decorationBox = { inner ->
                            if (text.isEmpty()) {
                                Text(
                                    stringResource(R.string.comments_edit_placeholder),
                                    color = colors.placeholder,
                                    fontSize = 15.sp,
                                )
                            }
                            inner()
                        },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .graphicsLayer {
                                scaleX = sendScale
                                scaleY = sendScale
                            }
                            .background(
                                if (canSend) {
                                    Brush.linearGradient(
                                        listOf(Color(0xFF007AFF), Color(0xFFAF52DE)),
                                    )
                                } else {
                                    Brush.linearGradient(
                                        listOf(colors.controlSurface, colors.controlSurface),
                                    )
                                },
                                CircleShape,
                            )
                            .clickable(enabled = canSend, onClick = onSend),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!enabled) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.modern_comments_send),
                                tint = if (canSend) Color.White else colors.tertiary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(colors.controlSurface, CircleShape)
                            .border(0.5.dp, colors.controlStroke, CircleShape)
                            .clickable(enabled = enabled, onClick = onCancelEdit),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = colors.tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentUid != null) {
                    StoryRingAvatarView(
                        userId = currentUid,
                        size = 36.dp,
                        lineWidth = 2.2.dp,
                        showBaseStroke = true,
                        onTap = onAvatarTap,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                // ≡ iOS TextField axis:.vertical + lineLimit(1...4)
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = enabled,
                    cursorBrush = SolidColor(colors.primary),
                    textStyle = TextStyle(color = colors.primary, fontSize = 15.sp),
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .background(colors.controlSurface, RoundedCornerShape(25.dp))
                        .border(1.dp, colors.controlStroke, RoundedCornerShape(25.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text(
                                if (!replyUsername.isNullOrBlank()) {
                                    stringResource(R.string.modern_comments_reply_placeholder, replyUsername)
                                } else {
                                    stringResource(R.string.modern_comments_placeholder)
                                },
                                color = colors.placeholder,
                                fontSize = 15.sp,
                            )
                        }
                        inner()
                    },
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(44.dp)
                        .graphicsLayer {
                            scaleX = sendScale
                            scaleY = sendScale
                        }
                        .then(
                            if (canSend) {
                                // ≡ iOS purple shadow on send
                                Modifier.shadow(8.dp, CircleShape, ambientColor = Color(0xFFAF52DE).copy(0.45f), spotColor = Color(0xFFAF52DE).copy(0.55f))
                            } else {
                                Modifier
                            },
                        )
                        .background(
                            if (canSend) {
                                Brush.linearGradient(
                                    listOf(Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55)),
                                )
                            } else {
                                Brush.linearGradient(listOf(colors.controlSurface, colors.controlSurface))
                            },
                            shape = CircleShape,
                        )
                        .clickable(enabled = canSend, onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!enabled) {
                        MomentsCircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.modern_comments_send),
                            tint = if (canSend) Color.White else colors.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
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
