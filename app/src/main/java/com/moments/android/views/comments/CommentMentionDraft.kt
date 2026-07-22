package com.moments.android.views.comments

import com.moments.android.models.AppUser
import com.moments.android.models.CommentMentionEntity
import com.moments.android.utilities.MentionDraftToken
import com.moments.android.utilities.MentionParsing

/** Helpers de mención — port de funciones privadas en `ModernCommentsView.swift`. */
internal object CommentMentionDraft {
    fun detectToken(text: String): MentionDraftToken? = MentionParsing.detectActiveToken(text)

    fun insertMention(
        user: AppUser,
        token: MentionDraftToken,
        text: String,
    ): Pair<String, CommentMentionEntity> {
        val start = token.fullRange.first
        val endExclusive = token.fullRange.last + 1
        val replacement = "@${user.username} "
        val newText = text.replaceRange(start, endExclusive, replacement)
        val entity = CommentMentionEntity(
            userId = user.id,
            username = user.username,
            rangeStart = start,
            rangeLength = replacement.trimEnd().length,
        )
        return newText to entity
    }

    fun replacingMention(
        entity: CommentMentionEntity,
        inMentions: List<CommentMentionEntity>,
    ): List<CommentMentionEntity> =
        inMentions.filter { it.userId != entity.userId } + entity

    fun sanitizedMentions(
        mentions: List<CommentMentionEntity>,
        text: String,
    ): List<CommentMentionEntity> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<CommentMentionEntity>()
        for (mention in mentions) {
            if (mention.userId in seen) continue
            val needle = "@${mention.username}"
            val idx = text.indexOf(needle, ignoreCase = true)
            if (idx < 0) continue
            seen.add(mention.userId)
            out += CommentMentionEntity(
                userId = mention.userId,
                username = mention.username,
                rangeStart = idx,
                rangeLength = needle.length,
            )
        }
        return out
    }
}
