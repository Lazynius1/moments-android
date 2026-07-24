package com.moments.android.views.profile.core.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moments.android.models.Moment

/** Port de `ProfileBentoLayout.swift`: asignación determinista de mosaico. */
enum class BentoTileKind(val colSpan: Int, val rowSpan: Int) { UNIT(1, 1), TALL(1, 2), HERO(2, 2) }
enum class ProfileGridVisualRole { PHOTO, VIDEO, REEL_HERO, REEL_TALL, FEATURED_PINNED }
data class ProfileGridTileDescriptor(val layoutKind: BentoTileKind, val visualRole: ProfileGridVisualRole, val showsPlayCue: Boolean, val showsDuration: Boolean, val showsPin: Boolean, val showsScheduledCue: Boolean) { val usesPortraitCrop get() = visualRole == ProfileGridVisualRole.REEL_HERO || visualRole == ProfileGridVisualRole.REEL_TALL
    companion object { fun standard(moment: Moment, kind: BentoTileKind = BentoTileKind.UNIT): ProfileGridTileDescriptor { val video = moment.videoUrl != null || moment.visibleMediaItems.any { it.type.raw == "video" }; val reel = video && (moment.aspectRatio == "9:16" || moment.videoDuration != null); val role = when { moment.isPinned == true && kind == BentoTileKind.HERO && reel -> ProfileGridVisualRole.REEL_HERO; moment.isPinned == true && kind == BentoTileKind.HERO -> ProfileGridVisualRole.FEATURED_PINNED; reel && kind == BentoTileKind.HERO -> ProfileGridVisualRole.REEL_HERO; reel && kind == BentoTileKind.TALL -> ProfileGridVisualRole.REEL_TALL; video -> ProfileGridVisualRole.VIDEO; else -> ProfileGridVisualRole.PHOTO }; return ProfileGridTileDescriptor(kind, role, video, video && kind != BentoTileKind.UNIT, moment.isPinned == true, moment.isScheduled) } }
}
object ProfileBentoTileAssigner {
    fun assign(moments: List<Moment>): List<ProfileGridTileDescriptor> { if (moments.isEmpty()) return emptyList(); val kinds = MutableList(moments.size) { BentoTileKind.UNIT }; heroIndex(moments)?.let { kinds[it] = BentoTileKind.HERO }; var tall = 0; moments.indices.take(12).forEach { i -> if (kinds[i] == BentoTileKind.UNIT && tall < 2 && isReel(moments[i])) { kinds[i] = BentoTileKind.TALL; tall++ } }; return moments.indices.map { ProfileGridTileDescriptor.standard(moments[it], kinds[it]) } }
    fun simple(moments: List<Moment>) = moments.map(ProfileGridTileDescriptor::standard)
    private fun heroIndex(moments: List<Moment>): Int? { val candidates = moments.indices.take(9); return candidates.firstOrNull { moments[it].isPinned == true && isReel(moments[it]) } ?: candidates.firstOrNull { isReel(moments[it]) } ?: candidates.firstOrNull { moments[it].isPinned == true && moments[it].previewImageURLString != null } }
    private fun isReel(moment: Moment) = (moment.videoUrl != null || moment.visibleMediaItems.any { it.type.raw == "video" }) && (moment.aspectRatio == "9:16" || moment.videoDuration != null)
}

data class BentoPlacement(val index: Int, val kind: BentoTileKind, val column: Int, val yUnits: Int)
/** Same shortest-column placement used by SwiftUI `Layout`; UI cells consume these frames. */
object ProfileBentoLayoutPlanner {
    fun plan(kinds: List<BentoTileKind>, columns: Int = 2): List<BentoPlacement> { val heights = IntArray(columns); return kinds.mapIndexed { index, kind -> val start = (0..(columns - kind.colSpan)).minBy { candidate -> (candidate until candidate + kind.colSpan).maxOf { heights[it] } }; val y = (start until start + kind.colSpan).maxOf { heights[it] }; repeat(kind.colSpan) { heights[start + it] = y + kind.rowSpan }; BentoPlacement(index, kind, start, y) } }
    fun height(kinds: List<BentoTileKind>, columns: Int = 2): Int = plan(kinds, columns).maxOfOrNull { it.yUnits + it.kind.rowSpan } ?: 0
}

/** Grid bento renderizado, equivalente al `Layout` SwiftUI del source. */
@Composable
fun ProfileMomentsBentoGrid(
    moments: List<Moment>,
    descriptors: List<ProfileGridTileDescriptor> = ProfileBentoTileAssigner.assign(moments),
    modifier: Modifier = Modifier,
    content: @Composable (moment: Moment, unitWidth: androidx.compose.ui.unit.Dp, index: Int, descriptor: ProfileGridTileDescriptor) -> Unit,
) {
    val kinds = descriptors.map(ProfileGridTileDescriptor::layoutKind)
    val placements = ProfileBentoLayoutPlanner.plan(kinds, columns = 3)
    val rows = ProfileBentoLayoutPlanner.height(kinds, columns = 3)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 1.dp
        val unit = (maxWidth - gap * 2) / 3
        val height = if (rows == 0) 0.dp else unit * rows + gap * (rows - 1)
        Box(Modifier.fillMaxWidth().height(height)) {
            placements.forEach { placement ->
                val moment = moments.getOrNull(placement.index) ?: return@forEach
                val descriptor = descriptors.getOrNull(placement.index) ?: return@forEach
                val tileWidth = unit * placement.kind.colSpan + gap * (placement.kind.colSpan - 1)
                val tileHeight = unit * placement.kind.rowSpan + gap * (placement.kind.rowSpan - 1)
                Box(
                    Modifier
                        .offset(x = (unit + gap) * placement.column, y = (unit + gap) * placement.yUnits)
                        .width(tileWidth)
                        .height(tileHeight),
                ) { content(moment, unit, placement.index, descriptor) }
            }
        }
    }
}
