package com.moments.android.views.profile.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.HighlightedStory
import com.moments.android.models.Story
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchStoriesByIds
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.privacy.canUserViewStoryEnhanced
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.profile.core.ProfileColors
import com.moments.android.views.story.StoriesView
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Port de `HighlightViewer`: resuelve las historias del destacado y las reproduce. */
@Composable
fun HighlightViewer(
    highlight: HighlightedStory,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var stories by remember(highlight.id) { mutableStateOf<List<Story>>(emptyList()) }
    var isLoading by remember(highlight.id) { mutableStateOf(true) }

    LaunchedEffect(highlight.id) {
        isLoading = true
        stories = loadHighlightStories(highlight)
        isLoading = false
    }

    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        when {
            isLoading -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                CircularProgressIndicator(color = Color.White)
                Text(
                    stringResource(R.string.highlighted_stories_loading),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                )
            }

            stories.isEmpty() -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Icon(
                    Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    stringResource(R.string.stories_no_stories_available),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    stringResource(R.string.common_close),
                    color = ProfileColors.accent,
                    fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }

            else -> StoriesView(
                onDismiss = onDismiss,
                explicitStories = stories,
                startAtIndex = 0,
                highlightTitle = highlight.title,
            )
        }
    }
}

/** Equivalente a `HighlightViewerViewModel.loadStories` + filtro de privacidad. */
private suspend fun loadHighlightStories(highlight: HighlightedStory): List<Story> {
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()
    val all = runCatching {
        FirestoreService().fetchStoriesByIds(highlight.authorId, highlight.storyIds)
    }.getOrDefault(emptyList())
    if (all.isEmpty()) return emptyList()

    val visibleIds = coroutineScope {
        all.map { story ->
            async { story.id.takeIf { PrivacyService.canUserViewStoryEnhanced(story, viewerId) } }
        }.awaitAll().filterNotNull().toSet()
    }
    // Se conserva el orden original, como el `stories.filter` de iOS.
    return all.filter { it.id in visibleIds }
}
