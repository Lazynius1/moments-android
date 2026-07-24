package com.moments.android.views.story.storyviewer

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** Port Compose de `StoryDeckInteractionLayout.swift`: publica la zona de un sticker interactivo. */
data class StoryInteractionExclusionZone(
    val id: String,
    val rect: Rect,
    val intents: Set<StoryGestureIntent>,
    val suppressionScope: StoryGestureSuppressionScope,
)

fun Modifier.storyDeckInteractionExclusion(
    id: String,
    intents: Set<StoryGestureIntent> = setOf(StoryGestureIntent.DECK_SWIPE),
    suppressionScope: StoryGestureSuppressionScope = StoryGestureSuppressionScope.SUPPRESS_DECK,
    horizontalInsetFraction: Float = 0f,
    verticalInsetPx: Float = 0f,
    onRegionChanged: (StoryInteractionExclusionZone) -> Unit,
): Modifier = onGloballyPositioned { coordinates ->
    val bounds = coordinates.boundsInRoot()
    val horizontalInset = bounds.width * horizontalInsetFraction.coerceIn(0f, 0.49f)
    onRegionChanged(
        StoryInteractionExclusionZone(
            id = id,
            rect = Rect(
                left = bounds.left + horizontalInset,
                top = bounds.top + verticalInsetPx,
                right = bounds.right - horizontalInset,
                bottom = bounds.bottom - verticalInsetPx,
            ),
            intents = intents,
            suppressionScope = suppressionScope,
        ),
    )
}
