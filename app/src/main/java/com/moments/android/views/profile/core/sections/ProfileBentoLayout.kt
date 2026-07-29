package com.moments.android.views.profile.core.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.moments.android.models.Moment
import com.moments.android.views.settings.hasVideoMedia
import com.moments.android.views.settings.isReelCandidate

/** Port de `BentoTileKind` (ProfileBentoLayout.swift). */
enum class BentoTileKind(val colSpan: Int, val rowSpan: Int) {
    UNIT(1, 1),
    TALL(1, 2),
    HERO(2, 2),
}

/** Port de `ProfileGridVisualRole`. */
enum class ProfileGridVisualRole {
    PHOTO,
    VIDEO,
    REEL_HERO,
    REEL_TALL,
    FEATURED_PINNED,
}

/** Port de `ProfileGridTileDescriptor`. */
data class ProfileGridTileDescriptor(
    val layoutKind: BentoTileKind,
    val visualRole: ProfileGridVisualRole,
    val showsPlayCue: Boolean,
    val showsDuration: Boolean,
    val showsPin: Boolean,
    val showsScheduledCue: Boolean,
) {
    val usesPortraitCrop: Boolean
        get() = visualRole == ProfileGridVisualRole.REEL_HERO || visualRole == ProfileGridVisualRole.REEL_TALL

    companion object {
        fun standard(moment: Moment, kind: BentoTileKind = BentoTileKind.UNIT): ProfileGridTileDescriptor {
            val isVideo = moment.hasVideoMedia
            val visualRole = when {
                moment.isPinned == true && kind == BentoTileKind.HERO ->
                    if (moment.isReelCandidate) ProfileGridVisualRole.REEL_HERO else ProfileGridVisualRole.FEATURED_PINNED
                moment.isReelCandidate && kind == BentoTileKind.HERO -> ProfileGridVisualRole.REEL_HERO
                moment.isReelCandidate && kind == BentoTileKind.TALL -> ProfileGridVisualRole.REEL_TALL
                isVideo -> ProfileGridVisualRole.VIDEO
                else -> ProfileGridVisualRole.PHOTO
            }
            return ProfileGridTileDescriptor(
                layoutKind = kind,
                visualRole = visualRole,
                showsPlayCue = isVideo,
                showsDuration = isVideo && (kind == BentoTileKind.HERO || kind == BentoTileKind.TALL),
                showsPin = moment.isPinned == true,
                showsScheduledCue = moment.isScheduled,
            )
        }
    }
}

/** Port de `ProfileBentoTileAssigner`. */
object ProfileBentoTileAssigner {
    fun assign(moments: List<Moment>): List<ProfileGridTileDescriptor> {
        if (moments.isEmpty()) return emptyList()
        val kinds = MutableList(moments.size) { BentoTileKind.UNIT }
        heroCandidateIndex(moments)?.let { kinds[it] = BentoTileKind.HERO }
        var tallCount = 0
        for (index in moments.indices) {
            if (index >= 12) break
            if (kinds[index] != BentoTileKind.UNIT) continue
            if (tallCount >= 2) break
            if (!moments[index].isReelCandidate) continue
            kinds[index] = BentoTileKind.TALL
            tallCount++
        }
        return moments.indices.map { ProfileGridTileDescriptor.standard(moments[it], kinds[it]) }
    }

    fun simple(moments: List<Moment>): List<ProfileGridTileDescriptor> =
        moments.map { ProfileGridTileDescriptor.standard(it) }

    private fun heroCandidateIndex(moments: List<Moment>): Int? {
        val candidates = moments.indices.take(9)
        candidates.firstOrNull { moments[it].isPinned == true && moments[it].isReelCandidate }?.let { return it }
        candidates.firstOrNull { moments[it].isReelCandidate }?.let { return it }
        return candidates.firstOrNull {
            moments[it].isPinned == true && moments[it].previewImageURLString != null
        }
    }
}

data class BentoPlacement(
    val index: Int,
    val kind: BentoTileKind,
    val column: Int,
    val yUnits: Int,
)

/**
 * Placement en unidades de fila (mismo algoritmo shortest-column que el `Layout` SwiftUI).
 * Por defecto [ProfileMomentsGridMetrics.columns] (= 3).
 */
object ProfileBentoLayoutPlanner {
    fun plan(
        kinds: List<BentoTileKind>,
        columns: Int = ProfileMomentsGridMetrics.columns,
    ): List<BentoPlacement> {
        val heights = IntArray(columns)
        return kinds.mapIndexed { index, kind ->
            val start = (0..(columns - kind.colSpan)).minBy { candidate ->
                (candidate until candidate + kind.colSpan).maxOf { heights[it] }
            }
            val y = (start until start + kind.colSpan).maxOf { heights[it] }
            repeat(kind.colSpan) { heights[start + it] = y + kind.rowSpan }
            BentoPlacement(index, kind, start, y)
        }
    }

    fun height(kinds: List<BentoTileKind>, columns: Int = ProfileMomentsGridMetrics.columns): Int =
        plan(kinds, columns).maxOfOrNull { it.yUnits + it.kind.rowSpan } ?: 0
}

/** Port de `ProfileMomentsBentoGrid`. */
@Composable
fun ProfileMomentsBentoGrid(
    moments: List<Moment>,
    availableWidth: Dp? = null,
    descriptors: List<ProfileGridTileDescriptor> = ProfileBentoTileAssigner.assign(moments),
    modifier: Modifier = Modifier,
    content: @Composable (moment: Moment, unitWidth: Dp, index: Int, descriptor: ProfileGridTileDescriptor) -> Unit,
) {
    val kinds = descriptors.map(ProfileGridTileDescriptor::layoutKind)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = availableWidth ?: maxWidth
        val unit = ProfileMomentsGridMetrics.columnWidth(width)
        val frames = ProfileMomentsGridMetrics.planFrames(kinds, width)
        val height = ProfileMomentsGridMetrics.bentoHeight(kinds, width)
        Box(Modifier.fillMaxWidth().height(height)) {
            frames.forEach { frame ->
                val moment = moments.getOrNull(frame.index) ?: return@forEach
                val descriptor = descriptors.getOrNull(frame.index)
                    ?: ProfileGridTileDescriptor.standard(moment)
                Box(
                    Modifier
                        .offset(x = frame.x, y = frame.y)
                        .width(frame.width)
                        .height(frame.height),
                ) {
                    content(moment, unit, frame.index, descriptor)
                }
            }
        }
    }
}
