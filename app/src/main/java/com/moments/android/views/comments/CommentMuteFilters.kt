package com.moments.android.views.comments

import com.moments.android.models.Comment
import java.text.Normalizer

/** Port de la lógica de mute en `ModernCommentsView.swift`. */
internal enum class CommentMuteFilterReason { MutedAccount, MutedWord }

internal data class CommentFilterResult(
    val visible: List<Comment>,
    val mutedWordMaskedIds: Set<String>,
)

internal fun normalizeMutedText(text: String): String {
    val trimmed = text.trim()
    val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
    return decomposed.replace("\\p{Mn}+".toRegex(), "").lowercase()
}

internal fun applyCommentMuteFilters(
    source: List<Comment>,
    currentUserId: String?,
    mutedUserIds: Set<String>,
    mutedWordsNormalized: List<String>,
): CommentFilterResult {
    if (source.isEmpty()) return CommentFilterResult(emptyList(), emptySet())
    if (currentUserId.isNullOrEmpty()) return CommentFilterResult(source, emptySet())
    if (mutedUserIds.isEmpty() && mutedWordsNormalized.isEmpty()) {
        return CommentFilterResult(source, emptySet())
    }

    val childrenByParent = mutableMapOf<String, MutableList<String>>()
    for (comment in source) {
        val id = comment.id ?: continue
        val parentId = comment.parentCommentId
        if (parentId != null) {
            childrenByParent.getOrPut(parentId) { mutableListOf() }.add(id)
        }
    }

    val flaggedByReason = mutableMapOf<String, CommentMuteFilterReason>()
    for (comment in source) {
        val id = comment.id ?: continue
        muteReason(comment, currentUserId, mutedUserIds, mutedWordsNormalized)?.let {
            flaggedByReason[id] = it
        }
    }
    if (flaggedByReason.isEmpty()) return CommentFilterResult(source, emptySet())

    val mutedWordMaskedIds = mutableSetOf<String>()
    val hiddenIds = mutableSetOf<String>()
    val branchHiddenIds = mutableSetOf<String>()

    for ((id, reason) in flaggedByReason) {
        when (reason) {
            CommentMuteFilterReason.MutedAccount -> {
                hiddenIds.add(id)
                branchHiddenIds.add(id)
            }
            CommentMuteFilterReason.MutedWord -> {
                val hasChildren = childrenByParent[id].orEmpty().isNotEmpty()
                if (hasChildren) {
                    mutedWordMaskedIds.add(id)
                } else {
                    hiddenIds.add(id)
                    branchHiddenIds.add(id)
                }
            }
        }
    }

    if (branchHiddenIds.isNotEmpty()) {
        var changed = true
        while (changed) {
            changed = false
            for (comment in source) {
                val id = comment.id ?: continue
                if (id in hiddenIds) continue
                val parentId = comment.parentCommentId ?: continue
                if (parentId in branchHiddenIds) {
                    hiddenIds.add(id)
                    branchHiddenIds.add(id)
                    changed = true
                }
            }
        }
    }

    val masked = mutedWordMaskedIds.filter { it !in hiddenIds }.toSet()
    val visible = source.filter { comment ->
        val id = comment.id ?: return@filter true
        id !in hiddenIds
    }
    return CommentFilterResult(visible, masked)
}

private fun muteReason(
    comment: Comment,
    currentUserId: String,
    mutedUserIds: Set<String>,
    mutedWordsNormalized: List<String>,
): CommentMuteFilterReason? {
    if (comment.authorId == currentUserId) return null
    if (comment.authorId in mutedUserIds) return CommentMuteFilterReason.MutedAccount
    if (mutedWordsNormalized.isEmpty()) return null
    val normalizedContent = normalizeMutedText(comment.content)
    if (normalizedContent.isEmpty()) return null
    return if (mutedWordsNormalized.any { normalizedContent.contains(it) }) {
        CommentMuteFilterReason.MutedWord
    } else {
        null
    }
}
