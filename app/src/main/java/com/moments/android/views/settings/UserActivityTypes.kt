package com.moments.android.views.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.feed.reactions.ReactionType
import com.moments.android.views.messaging.components.AttachmentIcon
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Port de `UserActivityTypes.swift`. */
enum class RecentlyDeletedContentKind { MOMENTS, STORIES }

enum class ArchivedContentKind { MOMENTS, STORIES }

sealed class RecentlyDeletedConfirmationAction(val id: String) {
    data object Restore : RecentlyDeletedConfirmationAction("restore")
    data object PermanentlyDelete : RecentlyDeletedConfirmationAction("permanentlyDelete")
}

sealed class ActivitySelectionConfirmationAction(val id: String) {
    data class ArchivedRestore(val ids: Set<String>) :
        ActivitySelectionConfirmationAction("archivedRestore-${ids.sorted().joinToString(",")}")

    data object ReactionsDelete : ActivitySelectionConfirmationAction("reactionsDelete")
    data object TagsRemove : ActivitySelectionConfirmationAction("tagsRemove")
    data object CommentsDelete : ActivitySelectionConfirmationAction("commentsDelete")
    data object StickerRepliesDelete : ActivitySelectionConfirmationAction("stickerRepliesDelete")
    data object RecentlyDeletedRestore : ActivitySelectionConfirmationAction("recentlyDeletedRestore")
    data object RecentlyDeletedDelete : ActivitySelectionConfirmationAction("recentlyDeletedDelete")
}

enum class RecentlyDeletedAutoScrollDirection { UP, DOWN }

enum class SelectionDragMode { SELECTING, DESELECTING }

/**
 * `icon` de iOS son SF Symbols; aquí se resuelven a [ImageVector] de Material salvo dos casos
 * que en iOS ya eran assets propios: `tags` (icono de etiquetado del chat) y `echoes`
 * (marca Echoes), expuestos por [drawableRes] para que la fila los pinte con su asset real.
 */
enum class ActivityInteractionCategory(
    val rawValue: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    /** `null` en las categorías que en iOS devuelven cadena vacía (timeSpent, accountHistory). */
    @StringRes val emptyRes: Int?,
    val icon: ImageVector,
    val accentColor: Color,
    @DrawableRes val drawableRes: Int? = null,
) {
    REACTIONS(
        "reactions",
        R.string.user_activity_cat_reactions_title,
        R.string.user_activity_cat_reactions_subtitle,
        R.string.user_activity_cat_reactions_empty,
        Icons.Filled.AutoAwesome,
        Color(0xFFF97316),
    ),
    COMMENTS(
        "comments",
        R.string.user_activity_cat_comments_title,
        R.string.user_activity_cat_comments_subtitle,
        R.string.user_activity_cat_comments_empty,
        Icons.AutoMirrored.Filled.Comment,
        Color(0xFF3B82F6),
    ),
    TAGS(
        "tags",
        R.string.user_activity_cat_tags_title,
        R.string.user_activity_cat_tags_subtitle,
        R.string.user_activity_cat_tags_empty,
        Icons.Filled.PersonAdd,
        Color(0xFFEC4899),
        AttachmentIcon.TAGGED.drawableRes,
    ),
    STICKER_REPLIES(
        "stickerReplies",
        R.string.user_activity_cat_sticker_replies_title,
        R.string.user_activity_cat_sticker_replies_subtitle,
        R.string.user_activity_cat_sticker_replies_empty,
        Icons.Filled.SentimentSatisfiedAlt,
        Color(0xFFEC4899),
    ),
    ARCHIVED(
        "archived",
        R.string.user_activity_cat_archived_title,
        R.string.user_activity_cat_archived_subtitle,
        R.string.user_activity_cat_archived_empty,
        Icons.Filled.Inventory2,
        Color(0xFF64748B),
    ),
    STORIES_ARCHIVE(
        "storiesArchive",
        R.string.user_activity_cat_stories_archive_title,
        R.string.user_activity_cat_stories_archive_subtitle,
        R.string.user_activity_cat_stories_archive_empty,
        Icons.Filled.History,
        Color(0xFF0EA5E9),
    ),
    RECENTLY_DELETED(
        "recentlyDeleted",
        R.string.user_activity_cat_recently_deleted_title,
        R.string.user_activity_cat_recently_deleted_subtitle,
        R.string.user_activity_cat_recently_deleted_empty,
        Icons.Filled.Delete,
        Color(0xFFEF4444),
    ),
    ECHOES(
        "echoes",
        R.string.user_activity_cat_echoes_title,
        R.string.user_activity_cat_echoes_subtitle,
        R.string.user_activity_cat_echoes_empty,
        Icons.Filled.AutoAwesome,
        Color(0xFF3B82F6),
        R.drawable.echoes_icon,
    ),
    FOLLOWERS(
        "followers",
        R.string.user_activity_cat_followers_title,
        R.string.user_activity_cat_followers_subtitle,
        R.string.user_activity_cat_followers_empty,
        Icons.Filled.PersonAdd,
        Color(0xFF10B981),
    ),
    VISITS(
        "visits",
        R.string.user_activity_cat_visits_title,
        R.string.user_activity_cat_visits_subtitle,
        R.string.user_activity_cat_visits_empty,
        Icons.Filled.Visibility,
        Color(0xFFF59E0B),
    ),
    MOMENTS(
        "moments",
        R.string.user_activity_cat_moments_title,
        R.string.user_activity_cat_moments_subtitle,
        R.string.user_activity_cat_moments_empty,
        Icons.Filled.GridView,
        Color(0xFF0EA5E9),
    ),
    REELS(
        "reels",
        R.string.user_activity_cat_reels_title,
        R.string.user_activity_cat_reels_subtitle,
        R.string.user_activity_cat_reels_empty,
        Icons.Filled.VideoLibrary,
        Color(0xFF0EA5E9),
    ),
    TIME_SPENT(
        "timeSpent",
        R.string.user_activity_cat_time_spent_title,
        R.string.user_activity_cat_time_spent_subtitle,
        null,
        Icons.Filled.Schedule,
        Color(0xFF64748B),
    ),
    SEARCHES(
        "searches",
        R.string.user_activity_cat_searches_title,
        R.string.user_activity_cat_searches_subtitle,
        R.string.user_activity_cat_searches_empty,
        Icons.Filled.Search,
        Color(0xFF3B82F6),
    ),
    ACCOUNT_HISTORY(
        "accountHistory",
        R.string.user_activity_cat_account_history_title,
        R.string.user_activity_cat_account_history_subtitle,
        null,
        Icons.Filled.CalendarMonth,
        Color(0xFF3B82F6),
    );

    val id: String get() = rawValue

    companion object {
        fun fromRaw(raw: String?): ActivityInteractionCategory? =
            entries.firstOrNull { it.rawValue == raw }
    }
}

enum class ReactionsSortOption(val rawValue: String, @StringRes val titleRes: Int) {
    NEWEST("newest", R.string.user_activity_sort_newest),
    OLDEST("oldest", R.string.user_activity_sort_oldest);

    val id: String get() = rawValue
}

enum class ReactionsDateFilter(val rawValue: String, @StringRes val titleRes: Int) {
    ALL("all", R.string.user_activity_date_all),
    WEEK("week", R.string.user_activity_date_week),
    MONTH("month", R.string.user_activity_date_month),
    YEAR("year", R.string.user_activity_date_year),
    CUSTOM("custom", R.string.user_activity_date_custom);

    val id: String get() = rawValue
}

val Moment.hasVideoMedia: Boolean
    get() = videoUrl != null || mediaItems?.firstOrNull()?.type?.raw == "video"

val Moment.parsedAspectRatioValue: Double?
    get() {
        val raw = aspectRatio?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val parts = raw.split(Regex("[:/xX]")).filter(String::isNotBlank)
        if (parts.size == 2) {
            val w = parts[0].toDoubleOrNull()
            val h = parts[1].toDoubleOrNull()
            if (w != null && h != null && h > 0) return w / h
        }
        return raw.toDoubleOrNull()?.takeIf { it > 0 }
    }

val Moment.isVerticalReelAspect: Boolean
    get() = parsedAspectRatioValue?.let { abs(it - 9.0 / 16.0) <= 0.05 } == true

val Moment.isReelCandidate: Boolean
    get() = hasVideoMedia && isVerticalReelAspect

/**
 * Emoji de reacción que rota cada 1,2 s con salida rápida (escala 0,6 + fade) y entrada
 * elástica, igual que el `AnimatedReactionIcon` de iOS. Los emoji salen del modelo
 * [ReactionType], no de copy visible.
 */
@Composable
fun AnimatedReactionIcon(modifier: Modifier = Modifier) {
    val reactions = remember { ReactionType.entries.map { it.icon } }
    var currentIndex by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(true) }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = if (visible) spring(dampingRatio = 0.55f, stiffness = 300f) else tween(200),
        label = "reactionScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) spring(dampingRatio = 0.55f, stiffness = 300f) else tween(200),
        label = "reactionAlpha",
    )

    LaunchedEffect(reactions) {
        if (reactions.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(1_200)
            if (MotionPolicy.reduceMotion) {
                currentIndex = (currentIndex + 1) % reactions.size
                continue
            }
            visible = false
            delay(200)
            currentIndex = (currentIndex + 1) % reactions.size
            visible = true
        }
    }

    Box(modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Text(
            text = reactions.getOrElse(currentIndex) { "" },
            fontSize = 22.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        )
    }
}

/**
 * Ciclo de burbujas de comentario cada 1 s. iOS alterna 6 SF Symbols de burbuja; Material no
 * tiene las variantes izquierda/derecha/relleno, así que se rota entre los tres equivalentes
 * disponibles manteniendo el mismo ritmo y la misma animación.
 */
@Composable
fun AnimatedCommentIcon(modifier: Modifier = Modifier) {
    val bubbles = remember {
        listOf(
            Icons.AutoMirrored.Filled.Comment,
            Icons.AutoMirrored.Filled.Chat,
            Icons.Filled.MoreHoriz,
        )
    }
    var currentIndex by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(true) }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.7f,
        animationSpec = if (visible) spring(dampingRatio = 0.55f, stiffness = 400f) else tween(150),
        label = "commentScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) spring(dampingRatio = 0.55f, stiffness = 400f) else tween(150),
        label = "commentAlpha",
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            if (MotionPolicy.reduceMotion) {
                currentIndex = (currentIndex + 1) % bubbles.size
                continue
            }
            visible = false
            delay(150)
            currentIndex = (currentIndex + 1) % bubbles.size
            visible = true
        }
    }

    Box(modifier.size(36.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(
            imageVector = bubbles[currentIndex],
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
        )
    }
}
