package com.moments.android.views.story

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moments.android.views.story.storyviewer.StoryGestureRegion
import com.moments.android.views.story.storyviewer.StoryGestureSuppressionScope

/** Port de `Views/story/StoryDeckGestureGate.swift`. */
class StoryDeckGestureGate {
    private val activeSuppressionScopes = mutableStateMapOf<String, StoryGestureSuppressionScope>()
    private var storedInteractionRegions by mutableStateOf<List<StoryGestureRegion>>(emptyList())
    val interactionRegions: List<StoryGestureRegion> get() = storedInteractionRegions

    val suppressionScope: StoryGestureSuppressionScope
        get() = activeSuppressionScopes.values.maxByOrNull { it.level } ?: StoryGestureSuppressionScope.ALLOW_ALL
    val suppressDeckNavigation: Boolean get() = suppressionScope.level >= StoryGestureSuppressionScope.SUPPRESS_DECK.level
    val suppressStoryNavigationGestures: Boolean get() = suppressionScope.level >= StoryGestureSuppressionScope.SUPPRESS_STORY_NAVIGATION.level
    val suppressViewerGestures: Boolean get() = suppressionScope.level >= StoryGestureSuppressionScope.SUPPRESS_VIEWER_GESTURES.level

    fun setSuppressionScope(scope: StoryGestureSuppressionScope, sourceId: String) {
        if (scope == StoryGestureSuppressionScope.ALLOW_ALL) activeSuppressionScopes.remove(sourceId)
        else activeSuppressionScopes[sourceId] = scope
    }

    fun clearSuppression(sourceId: String) = setSuppressionScope(StoryGestureSuppressionScope.ALLOW_ALL, sourceId)

    fun setInteractionRegions(regions: List<StoryGestureRegion>) {
        if (storedInteractionRegions != regions) storedInteractionRegions = regions
    }

    fun setStickerInteractionActive(active: Boolean) {
        setSuppressionScope(
            if (active) StoryGestureSuppressionScope.SUPPRESS_VIEWER_GESTURES else StoryGestureSuppressionScope.ALLOW_ALL,
            "legacy.sticker",
        )
    }
}
