package com.moments.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.data.BackendFeedService
import com.moments.android.data.FeedMoment
import com.moments.android.data.StoryUser
import com.moments.android.ui.feed.controls.FloatingFeedToggle
import com.moments.android.ui.feed.sections.FeedHeaderBar
import com.moments.android.ui.feed.sections.PostCard

/** Pantalla principal del feed — equivalente a FeedView de iOS. */
@Composable
fun FeedScreen(padding: PaddingValues) {
    var following by remember { mutableStateOf(false) }
    var moments by remember { mutableStateOf<List<FeedMoment>>(emptyList()) }
    var stories by remember { mutableStateOf<List<StoryUser>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(following) {
        BackendFeedService.fetch(if (following) "following" else "forYou") { result ->
            result.onSuccess { moments = it; error = null }.onFailure { error = it.localizedMessage }
        }
    }
    LaunchedEffect(Unit) {
        BackendFeedService.fetchStoryUsers { it.onSuccess { users -> stories = users } }
    }

    Box(Modifier.fillMaxSize().background(FeedCanvas)) {
        Column(Modifier.fillMaxSize().padding(padding)) {
            FeedHeaderBar(
                stories = stories,
                onCreateStory = {},
                onOpenStory = {},
                onOpenActivity = {},
                onOpenMessages = {},
            )

            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 54.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (moments.isEmpty() && error == null) {
                        item { Text(stringResource(R.string.feed_loading), Modifier.padding(24.dp), color = FeedInk.copy(alpha = 0.58f)) }
                    }
                    error?.let {
                        item { Text(stringResource(R.string.feed_error), Modifier.padding(24.dp), color = FeedInk.copy(alpha = 0.58f)) }
                    }
                    items(moments, key = { it.id }) { moment -> PostCard(moment) }
                }

                // Toggle Para ti / Siguiendo flotando sobre la lista (como iOS).
                FloatingFeedToggle(
                    following = following,
                    onSelect = { following = it },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                )
            }
        }
    }
}
