package com.moments.android.views.story

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.moments.android.views.story.storyviewer.StoryGestureRegion
import com.moments.android.views.story.storyviewer.StoryGestureSuppressionScope

/**
 * Port de `Views/story/StoryDeckGestureGate.swift`.
 * Coordina cuándo el Deck Pass y la navegación por tap deben ceder a stickers interactivos.
 *
 * `LocalStoryDeckGestureGate` ≡ `EnvironmentValues.storyDeckGestureGate` (default nil).
 */
val LocalStoryDeckGestureGate = staticCompositionLocalOf<StoryDeckGestureGate?> { null }

class StoryDeckGestureGate {
    private val _activeSuppressionScopes = mutableStateMapOf<String, StoryGestureSuppressionScope>()
    /** ≡ `@Published private(set) var activeSuppressionScopes` */
    val activeSuppressionScopes: Map<String, StoryGestureSuppressionScope>
        get() = _activeSuppressionScopes.toMap()

    /** Fuentes por id (≡ PreferenceKey reduce + latestByID en iOS). */
    private val _interactionRegionsById = mutableStateMapOf<String, StoryGestureRegion>()
    private var storedInteractionRegions by mutableStateOf<List<StoryGestureRegion>>(emptyList())
    /** ≡ `@Published private(set) var interactionRegions` */
    val interactionRegions: List<StoryGestureRegion> get() = storedInteractionRegions

    val suppressionScope: StoryGestureSuppressionScope
        get() = _activeSuppressionScopes.values.maxByOrNull { it.level }
            ?: StoryGestureSuppressionScope.ALLOW_ALL

    val suppressDeckNavigation: Boolean
        get() = suppressionScope.level >= StoryGestureSuppressionScope.SUPPRESS_DECK.level
    val suppressStoryNavigationGestures: Boolean
        get() = suppressionScope.level >= StoryGestureSuppressionScope.SUPPRESS_STORY_NAVIGATION.level
    val suppressViewerGestures: Boolean
        get() = suppressionScope.level >= StoryGestureSuppressionScope.SUPPRESS_VIEWER_GESTURES.level

    fun setSuppressionScope(scope: StoryGestureSuppressionScope, sourceId: String) {
        if (scope == StoryGestureSuppressionScope.ALLOW_ALL) {
            _activeSuppressionScopes.remove(sourceId)
            return
        }
        if (_activeSuppressionScopes[sourceId] != scope) {
            _activeSuppressionScopes[sourceId] = scope
        }
    }

    fun clearSuppression(sourceId: String) {
        setSuppressionScope(StoryGestureSuppressionScope.ALLOW_ALL, sourceId)
    }

    fun setInteractionRegions(regions: List<StoryGestureRegion>) {
        _interactionRegionsById.clear()
        regions.forEach { _interactionRegionsById[it.id] = it }
        publishInteractionRegions()
    }

    /** Upsert de una zona (stickers reportan desde varias capas sin pisarse). */
    fun upsertInteractionRegion(region: StoryGestureRegion) {
        if (_interactionRegionsById[region.id] != region) {
            _interactionRegionsById[region.id] = region
            publishInteractionRegions()
        }
    }

    fun removeInteractionRegion(id: String) {
        if (_interactionRegionsById.remove(id) != null) {
            publishInteractionRegions()
        }
    }

    private fun publishInteractionRegions() {
        val next = _interactionRegionsById.values.toList()
        if (storedInteractionRegions != next) {
            storedInteractionRegions = next
        }
    }

    fun setStickerInteractionActive(active: Boolean) {
        setSuppressionScope(
            if (active) StoryGestureSuppressionScope.SUPPRESS_VIEWER_GESTURES
            else StoryGestureSuppressionScope.ALLOW_ALL,
            "legacy.sticker",
        )
    }
}
