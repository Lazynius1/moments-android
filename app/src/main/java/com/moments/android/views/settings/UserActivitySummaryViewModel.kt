package com.moments.android.views.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchArchivedMoments
import com.moments.android.services.firestore.fetchMoments
import com.moments.android.services.social.EchoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Port de `ActivitySummaryViewModel` (`UserActivitySummaryViewModel.swift`).
 *
 * Contadores de la pantalla principal de Actividad. Como en iOS, los de reacciones/comentarios/
 * etiquetas/stickers salen de [ActivityCache] (los rellena [ActivityInteractionDetailViewModel]
 * al cargar cada categoría) y el resto se cuentan contra Firestore.
 */
class ActivitySummaryViewModel(
    private val firestoreService: FirestoreService = FirestoreService(),
) : ViewModel() {

    var summaries by mutableStateOf<Map<ActivityInteractionCategory, ActivityCategorySummary>>(emptyMap())
        private set

    /** Se mantienen vivos mientras refrescan sus cachés en segundo plano. */
    private var warmUpViewModels: List<ActivityInteractionDetailViewModel> = emptyList()
    private var isRefreshing = false

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun autoRefresh() {
        if (isRefreshing) return
        isRefreshing = true

        warmUpViewModels = listOf(
            ActivityInteractionCategory.REACTIONS,
            ActivityInteractionCategory.COMMENTS,
            ActivityInteractionCategory.TAGS,
            ActivityInteractionCategory.STICKER_REPLIES,
            ActivityInteractionCategory.ARCHIVED,
            ActivityInteractionCategory.RECENTLY_DELETED,
            ActivityInteractionCategory.ECHOES,
            ActivityInteractionCategory.FOLLOWERS,
            ActivityInteractionCategory.VISITS,
            ActivityInteractionCategory.MOMENTS,
            ActivityInteractionCategory.REELS,
        ).map { ActivityInteractionDetailViewModel(it, firestoreService = firestoreService) }

        warmUpViewModels.forEach { it.reload() }

        viewModelScope.launch {
            delay(1_200)
            load()
            load()
            delay(2_000)
            load()
            warmUpViewModels = emptyList()
            isRefreshing = false
        }
    }

    fun load() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotEmpty() } ?: return

        viewModelScope.launch {
            val reactions = ActivityCache.loadReactions(userId)
            val comments = ActivityCache.loadComments(userId)
            val tagged = ActivityCache.loadTagged(userId)
            val stickerRepliesCount = ActivityCache.loadStickerReplyCount(userId)

            val result = withContext(Dispatchers.IO) {
                val echoes = async { runCatching { EchoService.fetchEchoHistoryOnce(userId) }.getOrDefault(emptyList()) }
                val archivedCount = async { runCatching { firestoreService.fetchArchivedMoments(userId).size }.getOrDefault(0) }
                val followersCount = async {
                    runCatching {
                        db.collection("users").document(userId).collection("followers").get().await().size()
                    }.getOrDefault(0)
                }
                val visitsCount = async {
                    runCatching {
                        db.collection("users").document(userId).collection("visits").get().await().size()
                    }.getOrDefault(0)
                }
                val storiesArchiveCount = async {
                    runCatching {
                        db.collection("users").document(userId).collection("stories")
                            .whereLessThan("expirationDate", Date())
                            .get().await().size()
                    }.getOrDefault(0)
                }
                val allMoments = async { runCatching { firestoreService.fetchMoments(userId) }.getOrDefault(emptyList()) }

                val moments = allMoments.await()
                val momentsCount = moments.count { moment ->
                    val isArchived = moment.isArchived ?: false
                    !isArchived && !moment.isReelCandidate
                }
                val reelsCount = moments.count { moment ->
                    val isArchived = moment.isArchived ?: false
                    !isArchived && moment.isReelCandidate
                }

                mapOf(
                    ActivityInteractionCategory.REACTIONS to ActivityCategorySummary(reactions.size, emptyList()),
                    ActivityInteractionCategory.COMMENTS to ActivityCategorySummary(comments.size, emptyList()),
                    ActivityInteractionCategory.TAGS to ActivityCategorySummary(tagged.size, emptyList()),
                    ActivityInteractionCategory.STICKER_REPLIES to ActivityCategorySummary(stickerRepliesCount, emptyList()),
                    ActivityInteractionCategory.RECENTLY_DELETED to ActivityCategorySummary(
                        ActivityCache.loadRecentlyDeletedCount(userId),
                        emptyList(),
                    ),
                    ActivityInteractionCategory.ARCHIVED to ActivityCategorySummary(archivedCount.await(), emptyList()),
                    ActivityInteractionCategory.STORIES_ARCHIVE to ActivityCategorySummary(storiesArchiveCount.await(), emptyList()),
                    ActivityInteractionCategory.ECHOES to ActivityCategorySummary(echoes.await().size, emptyList()),
                    ActivityInteractionCategory.FOLLOWERS to ActivityCategorySummary(followersCount.await(), emptyList()),
                    ActivityInteractionCategory.VISITS to ActivityCategorySummary(visitsCount.await(), emptyList()),
                    ActivityInteractionCategory.MOMENTS to ActivityCategorySummary(momentsCount, emptyList()),
                    ActivityInteractionCategory.REELS to ActivityCategorySummary(reelsCount, emptyList()),
                )
            }

            summaries = result
        }
    }
}
