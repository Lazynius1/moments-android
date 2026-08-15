package com.moments.android.views.comments

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.Comment
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.story.StoryRingAvatarView

/**
 * Port de `EnhancedModernCommentRow` + contenido con menciones / mute mask
 * (`ModernCommentsView.swift`).
 */
@Composable
fun EnhancedModernCommentRow(
    comment: Comment,
    currentUid: String?,
    momentAuthorId: String,
    nestedCommentsProvider: (String) -> List<Comment>,
    isCommentExpanded: (String) -> Boolean,
    onToggleExpand: (String) -> Unit,
    onLike: (Comment) -> Unit,
    onReply: (Comment) -> Unit,
    onEdit: (Comment) -> Unit,
    onDelete: (Comment) -> Unit,
    onAvatarTap: (userId: String, hasStory: Boolean) -> Unit,
    onMentionTap: (identifier: String) -> Unit,
    maskedCommentIds: Set<String>,
    temporarilyRevealedCommentIds: Set<String>,
    onRevealTemporarily: (String) -> Unit,
    nestingLevel: Int,
    maxNestingLevel: Int = 4,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val commentId = comment.id.orEmpty()
    val nestedComments = nestedCommentsProvider(commentId)
    val isExpanded = commentId.isNotEmpty() && isCommentExpanded(commentId)
    val isMutedWordMasked = commentId.isNotEmpty() && commentId in maskedCommentIds
    val isTemporarilyRevealed = commentId.isNotEmpty() && commentId in temporarilyRevealedCommentIds
    val isMaskApplied = isMutedWordMasked && !isTemporarilyRevealed

    val likeCount = comment.reactions["like"]?.size ?: 0
    val likedByMe = currentUid != null && comment.reactions["like"]?.contains(currentUid) == true
    val canEdit = currentUid != null && currentUid == comment.authorId
    val canDelete = currentUid != null &&
        (currentUid == comment.authorId || currentUid == momentAuthorId)

    var showFullContent by remember(comment.id) { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val isLong = comment.content.length > 100
    val displayContent = if (isLong && !showFullContent && !isMaskApplied) {
        comment.content.take(100) + "..."
    } else {
        comment.content
    }

    val avatarSize = when (nestingLevel) {
        0 -> 42.dp
        1 -> 37.dp
        2 -> 32.dp
        else -> 28.dp
    }
    val indentationWidth = (nestingLevel.coerceAtMost(maxNestingLevel) * 16).dp
    val shouldShowConnectorLine = nestingLevel > 0
    val mentionColor = if (isDark) Color(0xFF85C7FF) else Color(0xFF0D6BF2)
    val shouldShowNested = nestedComments.isNotEmpty() && nestingLevel < maxNestingLevel && isExpanded

    Column(
        modifier
            .fillMaxWidth()
            .padding(end = 4.dp)
            .pointerInput(isMaskApplied, canEdit, canDelete) {
                detectTapGestures(
                    onLongPress = { offset ->
                        if (!isMaskApplied && (canEdit || canDelete)) {
                            menuOffset = offset
                            showContextMenu = true
                        }
                    },
                )
            },
    ) {
        Box {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                offset = with(density) {
                    DpOffset(menuOffset.x.toDp(), menuOffset.y.toDp())
                },
            ) {
                if (canEdit) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.comments_actions_edit)) },
                        onClick = {
                            showContextMenu = false
                            onEdit(comment)
                        },
                    )
                }
                if (canDelete) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.comments_actions_delete), color = Color.Red) },
                        onClick = {
                            showContextMenu = false
                            onDelete(comment)
                        },
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // ≡ iOS connector gradient + dot
            if (shouldShowConnectorLine) {
                Box(
                    Modifier
                        .width(indentationWidth)
                        .height(avatarSize + 24.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier
                            .padding(start = indentationWidth - 10.dp)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF007AFF).copy(0.3f), Color(0xFFAF52DE).copy(0.3f)),
                                ),
                            )
                            .align(Alignment.CenterStart),
                    )
                    Box(
                        Modifier
                            .padding(start = indentationWidth - 11.dp)
                            .offset(y = (-4).dp)
                            .size(4.dp)
                            .background(Color(0xFFAF52DE).copy(0.5f), CircleShape)
                            .align(Alignment.TopStart),
                    )
                }
            }

            Row(
                Modifier
                    .weight(1f)
                    .padding(
                        start = if (shouldShowConnectorLine) 0.dp else indentationWidth,
                        end = 0.dp,
                    )
                    .padding(horizontal = if (nestingLevel == 0) 10.dp else 6.dp, vertical = if (nestingLevel == 0) 12.dp else 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                StoryRingAvatarView(
                    userId = comment.authorId,
                    size = avatarSize,
                    lineWidth = if (nestingLevel == 0) 2.3.dp else 2.dp,
                    onTap = { hasStory -> onAvatarTap(comment.authorId, hasStory) },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (nestingLevel > 0) {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        comment.username,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (nestingLevel == 0) 14.sp else 13.sp,
                        color = colors.primary,
                        modifier = Modifier.clickable { onAvatarTap(comment.authorId, false) },
                    )
                    Spacer(Modifier.width(3.dp))
                    VerifiedBadgeView(
                        userId = comment.authorId,
                        size = if (nestingLevel == 0) 12.dp else 10.dp,
                    )
                    if (comment.isEditedFlag) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.modern_comments_edited),
                            fontSize = 10.sp,
                            color = Color.Gray.copy(0.6f),
                            fontStyle = FontStyle.Italic,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("•", color = Color.Gray.copy(0.6f), fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        MomentsFormat.relativeTime(comment.timestamp),
                        fontSize = if (nestingLevel == 0) 12.sp else 11.sp,
                        color = Color.Gray.copy(0.6f),
                    )
                    if (comment.isPending == true) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = Color.Gray.copy(0.6f),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Box {
                    CommentMentionText(
                        text = displayContent,
                        mentions = comment.mentions,
                        fontSize = if (nestingLevel == 0) 14 else 13,
                        baseColor = if (isDark) Color.White.copy(0.9f) else Color.Black.copy(0.9f),
                        mentionColor = mentionColor,
                        isBlurred = isMaskApplied,
                        onMentionTap = onMentionTap,
                    )
                    if (isMaskApplied) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f),
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, null, Modifier.size(12.dp), tint = colors.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.modern_comments_muted_word_placeholder),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.primary,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.modern_comments_muted_word_reveal),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.Black else Color.White,
                                modifier = Modifier
                                    .background(if (isDark) Color.White else Color.Black, RoundedCornerShape(50))
                                    .clickable { onRevealTemporarily(commentId) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                if (isLong && !isMaskApplied) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (showFullContent) {
                            stringResource(R.string.modern_comments_see_less)
                        } else {
                            stringResource(R.string.modern_comments_see_more)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary,
                        modifier = Modifier.clickable { showFullContent = !showFullContent },
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!isMaskApplied) {
                        CommentActionChip(
                            active = likedByMe,
                            activeColor = Color.Red,
                            onClick = { onLike(comment) },
                        ) {
                            Icon(
                                if (likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                null,
                                Modifier.size(14.dp),
                                tint = if (likedByMe) Color.Red else colors.primary,
                            )
                            if (likeCount > 0) {
                                Spacer(Modifier.width(4.dp))
                                Text("$likeCount", fontSize = 12.sp, color = colors.primary)
                            }
                        }
                        if (nestingLevel < maxNestingLevel) {
                            CommentActionChip(active = false, activeColor = colors.primary, onClick = { onReply(comment) }) {
                                Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(14.dp), tint = colors.primary)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.modern_comments_reply), fontSize = 12.sp, color = colors.primary)
                            }
                        }
                    }
                    if (nestedComments.isNotEmpty() && nestingLevel < maxNestingLevel) {
                        CommentActionChip(
                            active = isExpanded,
                            activeColor = colors.primary,
                            onClick = { if (commentId.isNotEmpty()) onToggleExpand(commentId) },
                        ) {
                            Icon(
                                if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                null,
                                Modifier.size(14.dp),
                                tint = colors.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (nestedComments.size == 1) {
                                    stringResource(R.string.modern_comments_replies_one, 1)
                                } else {
                                    stringResource(R.string.modern_comments_replies_other, nestedComments.size)
                                },
                                fontSize = 12.sp,
                                color = colors.primary,
                            )
                        }
                    }
                }
            }
        }
        }

        if (shouldShowNested) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                nestedComments.forEach { nested ->
                    EnhancedModernCommentRow(
                        comment = nested,
                        currentUid = currentUid,
                        momentAuthorId = momentAuthorId,
                        nestedCommentsProvider = nestedCommentsProvider,
                        isCommentExpanded = isCommentExpanded,
                        onToggleExpand = onToggleExpand,
                        onLike = onLike,
                        onReply = onReply,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onAvatarTap = onAvatarTap,
                        onMentionTap = onMentionTap,
                        maskedCommentIds = maskedCommentIds,
                        temporarilyRevealedCommentIds = temporarilyRevealedCommentIds,
                        onRevealTemporarily = onRevealTemporarily,
                        nestingLevel = nestingLevel + 1,
                        maxNestingLevel = maxNestingLevel,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentActionChip(
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(20.dp)
    // ≡ iOS CommentActionButton `.scaleEffect(isActive ? 1.05 : 1.0)`
    val scale by animateFloatAsState(
        targetValue = if (active) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "commentActionScale",
    )
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                if (active) activeColor.copy(0.1f)
                else if (isDark) Color.Black.copy(0.3f) else Color.Gray.copy(0.1f),
                shape,
            )
            .then(
                if (!active) {
                    Modifier.border(
                        0.5.dp,
                        if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.1f),
                        shape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun CommentMentionText(
    text: String,
    mentions: List<com.moments.android.models.CommentMentionEntity>,
    fontSize: Int,
    baseColor: Color,
    mentionColor: Color,
    isBlurred: Boolean,
    onMentionTap: (String) -> Unit,
) {
    val annotated = remember(text, mentions) {
        buildAnnotatedString {
            val ranges = CommentMentionDraft.mentionRanges(text, mentions)
            if (ranges.isNotEmpty()) {
                var cursor = 0
                for ((start, end, userId) in ranges) {
                    if (cursor < start) {
                        withStyle(SpanStyle(color = baseColor)) { append(text.substring(cursor, start)) }
                    }
                    if (start in text.indices && end <= text.length) {
                        val annotation = userId ?: text.substring(start, end)
                        pushStringAnnotation("mention", annotation)
                        withStyle(
                            SpanStyle(
                                color = mentionColor,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.None,
                            ),
                        ) { append(text.substring(start, end)) }
                        pop()
                    }
                    cursor = end
                }
                if (cursor < text.length) {
                    withStyle(SpanStyle(color = baseColor)) { append(text.substring(cursor)) }
                }
            } else {
                withStyle(SpanStyle(color = baseColor)) { append(text) }
            }
        }
    }

    androidx.compose.foundation.text.ClickableText(
        text = annotated,
        style = androidx.compose.ui.text.TextStyle(
            fontSize = fontSize.sp,
            color = baseColor,
        ),
        modifier = if (isBlurred) Modifier.blur(8.dp) else Modifier,
        onClick = { offset ->
            if (isBlurred) return@ClickableText
            annotated.getStringAnnotations("mention", offset, offset)
                .firstOrNull()
                ?.let { onMentionTap(it.item) }
        },
    )
}
