package com.moments.android.views.profile.highlights

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.HighlightedStory
import com.moments.android.models.Story
import com.moments.android.services.content.BackendFeedService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.deleteHighlight
import com.moments.android.services.firestore.fetchHighlights
import com.moments.android.services.firestore.fetchStoriesByIds
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.privacy.canUserViewStoryEnhanced
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.profile.core.ProfileColors
import com.moments.android.views.shared.AppErrorBanner
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Port de `ProfileHighlightsView`: carril de destacados con crear/editar/borrar y visor. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileHighlightsView(
    userId: String,
    isOwnProfile: Boolean,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    refreshTrigger: Int = 0,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dark = isSystemInDarkTheme()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val circleSize: Dp = if (isCompact) 56.dp else 74.dp
    val horizontalSpacing: Dp = if (isCompact) 10.dp else 16.dp

    var highlights by remember(userId) { mutableStateOf<List<HighlightedStory>>(emptyList()) }
    var isLoading by remember(userId) { mutableStateOf(true) }
    var errorMessage by remember(userId) { mutableStateOf<String?>(null) }
    val presentation = remember { HighlightPresentationCoordinator() }

    suspend fun reload() {
        if (userId.isBlank()) return
        isLoading = true
        val result = runCatching { loadVisibleHighlights(userId) }
        result.onSuccess { highlights = it; errorMessage = null }
            .onFailure {
                highlights = emptyList()
                // Un PERMISSION_DENIED es "no visible", no un error que enseñar (igual que iOS).
                errorMessage = if (isPermissionDenied(it)) null else it.message
            }
        isLoading = false
    }

    LaunchedEffect(userId, refreshTrigger) { reload() }

    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp),
    ) {
        errorMessage?.let { message ->
            AppErrorBanner(
                message = message,
                onRetry = { scope.launch { reload() } },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        if (highlights.isEmpty() && !isOwnProfile && !isLoading) return@Column

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        ) {
            if (isOwnProfile) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp),
                    modifier = Modifier.combinedClickable(onClick = { presentation.presentCreate() }),
                ) {
                    Box(
                        Modifier
                            .size(circleSize)
                            .clip(CircleShape)
                            .background(if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6))
                            .border(1.dp, ProfileColors.textSecondary().copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.highlighted_stories_new),
                            tint = ProfileColors.accent,
                            modifier = Modifier.size(if (isCompact) 16.dp else 20.dp),
                        )
                    }
                    if (!isCompact) {
                        Text(
                            stringResource(R.string.highlighted_stories_new),
                            color = ProfileColors.textSecondary(),
                            fontSize = with(density) { legacyPoppinsSize(context, 10).toSp() },
                        )
                    }
                }
            }

            if (isLoading && highlights.isEmpty()) {
                repeat(3) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(circleSize)
                                .clip(CircleShape)
                                .background(ProfileColors.textSecondary().copy(alpha = 0.15f)),
                        )
                        Box(
                            Modifier
                                .width(40.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(ProfileColors.textSecondary().copy(alpha = 0.15f)),
                        )
                    }
                }
            }

            highlights.forEach { highlight ->
                var menuExpanded by remember(highlight.id) { mutableStateOf(false) }
                Box {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp),
                        modifier = Modifier.combinedClickable(
                            onClick = { presentation.presentViewer(highlight) },
                            onLongClick = { if (isOwnProfile) menuExpanded = true },
                        ),
                    ) {
                        HighlightIconView(highlight = highlight, size = circleSize)
                        Text(
                            highlight.title,
                            color = ProfileColors.textPrimary(),
                            fontSize = with(density) { legacyPoppinsSize(context, if (isCompact) 9 else 11).toSp() },
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(if (isCompact) 60.dp else 80.dp),
                        )
                    }

                    if (isOwnProfile) {
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_edit)) },
                                onClick = { menuExpanded = false; presentation.presentEdit(highlight) },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_delete), color = Color.Red) },
                                onClick = {
                                    menuExpanded = false
                                    val id = highlight.id ?: return@DropdownMenuItem
                                    scope.launch {
                                        runCatching { FirestoreService().deleteHighlight(userId, id) }
                                            .onFailure { errorMessage = it.message }
                                        reload()
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color.Red) },
                            )
                        }
                    }
                }
            }
        }
    }

    presentation.sheet?.let { sheet ->
        Dialog(
            onDismissRequest = { presentation.dismissSheet(); scope.launch { reload() } },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            HighlightCreateFlowView(
                mode = when (sheet) {
                    is HighlightSheet.Create -> HighlightFlowMode.Create
                    is HighlightSheet.Edit -> HighlightFlowMode.Edit(sheet.highlight)
                },
                onDismiss = { presentation.dismissSheet(); scope.launch { reload() } },
            )
        }
    }

    presentation.viewerHighlight?.let { highlight ->
        Dialog(
            onDismissRequest = { presentation.dismissViewer() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            HighlightViewer(highlight = highlight, onDismiss = { presentation.dismissViewer() })
        }
    }
}

/** Port de `HighlightIconView`. */
@Composable
fun HighlightIconView(
    highlight: HighlightedStory,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(ProfileColors.textSecondary().copy(alpha = 0.12f))
            .border(1.dp, ProfileColors.textSecondary().copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!highlight.coverImageUrl.isNullOrBlank()) {
            AsyncImage(
                model = highlight.coverImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = ProfileColors.accent.copy(alpha = 0.5f),
                modifier = Modifier.size(size * 0.3f),
            )
        }
    }
}

private fun isPermissionDenied(error: Throwable): Boolean {
    val message = error.message.orEmpty().lowercase()
    return "missing or insufficient permissions" in message ||
        "permission denied" in message ||
        "insufficient permissions" in message
}

/**
 * Equivalente a `ProfileHighlightsViewModel.loadHighlights`: primero el backend,
 * y si no responde, Firestore + filtrado por privacidad para perfiles ajenos.
 */
private suspend fun loadVisibleHighlights(userId: String): List<HighlightedStory> {
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()

    BackendFeedService.fetchVisibleHighlights(targetUserId = userId, limit = 30)?.let { return it.highlights }

    val firestore = FirestoreService()
    val all = firestore.fetchHighlights(userId)
    if (userId == viewerId) return all

    return coroutineScope {
        all.map { highlight ->
            async { resolveHighlightForViewer(firestore, highlight, userId, viewerId) }
        }.awaitAll().filterNotNull()
    }
}

/** Recorta el destacado a las historias que el visitante puede ver y recalcula portada y conteo. */
private suspend fun resolveHighlightForViewer(
    firestore: FirestoreService,
    highlight: HighlightedStory,
    authorId: String,
    viewerId: String,
): HighlightedStory? {
    val stories = runCatching { firestore.fetchStoriesByIds(authorId, highlight.storyIds) }
        .getOrDefault(emptyList())
    if (stories.isEmpty()) return null

    val viewable: List<Story> = coroutineScope {
        stories.map { story ->
            async { story.takeIf { PrivacyService.canUserViewStoryEnhanced(story, viewerId) } }
        }.awaitAll().filterNotNull()
    }
    if (viewable.isEmpty()) return null

    val cover = highlight.coverImageUrl?.takeIf { original ->
        viewable.any { it.mediaItem.url == original }
    } ?: viewable.firstOrNull()?.mediaItem?.url

    return highlight.copy(
        coverImageUrl = cover,
        storiesCount = viewable.size,
        storyIds = viewable.mapNotNull { it.id },
    )
}
