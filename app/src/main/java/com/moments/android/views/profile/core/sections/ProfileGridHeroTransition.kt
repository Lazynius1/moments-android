package com.moments.android.views.profile.core.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.moments.android.views.profile.core.canAdjustGridPreview
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.moments.android.models.Moment

/** Core port of `ProfileGridHeroTransition.swift`; Compose layers bind to this coordinator. */
enum class ProfileMomentDetailEntryKind { DIRECT, HERO }
data class ProfileMomentDetailRoute(val moments: List<Moment>, val initialIndex: Int, val initialMomentId: String?, val entryKind: ProfileMomentDetailEntryKind = ProfileMomentDetailEntryKind.DIRECT)
data class ProfileGridMomentMenuSelection(val moment: Moment, val index: Int)
enum class ProfileGridHeroMenuKind { OWNER, VISITOR }
sealed interface ProfileGridHeroPhase { data object Idle : ProfileGridHeroPhase; data class MenuPeek(val selection: ProfileGridMomentMenuSelection) : ProfileGridHeroPhase; data class Expanding(val route: ProfileMomentDetailRoute) : ProfileGridHeroPhase; data class Retracting(val route: ProfileMomentDetailRoute) : ProfileGridHeroPhase; data class Detail(val route: ProfileMomentDetailRoute) : ProfileGridHeroPhase }
object ProfileGridHeroMotion { fun smoothstep(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3 - 2 * x) }; fun easeOut(t: Float): Float = 1 - (1 - t.coerceIn(0f, 1f)) * (1 - t.coerceIn(0f, 1f)) * (1 - t.coerceIn(0f, 1f)); fun remap(value: Float, start: Float, end: Float) = if (end <= start) if (value >= end) 1f else 0f else ((value - start) / (end - start)).coerceIn(0f, 1f) }
object ProfileGridHeroLayout {
    const val maxCardWidth = 350f; const val horizontalPadding = 16f; const val peekFooterHeight = 56f; const val thumbnailCornerRadius = 12f
    fun sourceKey(moment: Moment, index: Int) = moment.id ?: "profile-grid-$index"
    fun aspect(value: String?): Float { val parts = value?.split(':', '/') ?: return 1f; return parts.getOrNull(0)?.toFloatOrNull()?.div(parts.getOrNull(1)?.toFloatOrNull()?.takeIf { it > 0f } ?: 1f) ?: 1f }
    fun cardWidth(width: Float) = (width - 32f).coerceAtMost(maxCardWidth)
    fun mediaHeight(width: Float, aspect: String?) = width / aspect(aspect).coerceIn(.75f, 16f / 9f)
    fun peekFrame(size: IntSize, moment: Moment, menuHeight: Float): Rect { val width = cardWidth(size.width.toFloat()); val height = mediaHeight(width, moment.aspectRatio) + peekFooterHeight; val total = height + 14f + menuHeight; return Rect((size.width - width) / 2f, (size.height - total) / 2f, (size.width + width) / 2f, (size.height - total) / 2f + height) }
    fun fallbackFrame(size: IntSize): Rect { val side = (size.width / 3f - 8f).coerceAtMost(140f); return Rect((size.width - side) / 2, size.height * .42f, (size.width + side) / 2, size.height * .42f + side) }
    fun lerp(a: Rect, b: Rect, t: Float): Rect { val x = smooth(t); return Rect(a.left + (b.left-a.left)*x, a.top+(b.top-a.top)*x, a.right+(b.right-a.right)*x, a.bottom+(b.bottom-a.bottom)*x) }; private fun smooth(t: Float) = ProfileGridHeroMotion.smoothstep(t)
}
class ProfileGridHeroTransitionCoordinator {
    var phase by mutableStateOf<ProfileGridHeroPhase>(ProfileGridHeroPhase.Idle); private set
    var sourceFrame by mutableStateOf(Rect.Zero); var peekProgress by mutableStateOf(0f); var menuOpacity by mutableStateOf(0f); var scrimOpacity by mutableStateOf(0f); var showPinConfirm by mutableStateOf(false); var toastMessage by mutableStateOf<String?>(null); var menuKind by mutableStateOf(ProfileGridHeroMenuKind.OWNER)
    private val thumbnailFrames = mutableMapOf<String, Rect>()
    var onEdit: ((Moment) -> Unit)? = null; var onDelete: ((Moment) -> Unit)? = null; var onArchive: ((Moment) -> Unit)? = null; var onAdjustPreview: ((Moment) -> Unit)? = null; var onPin: ((Moment, Boolean, Boolean) -> Unit)? = null; var onOpenDetail: ((ProfileMomentDetailRoute) -> Unit)? = null
    val menuSelection get() = (phase as? ProfileGridHeroPhase.MenuPeek)?.selection
    val activeMoment get() = menuSelection?.moment ?: when (val p = phase) { is ProfileGridHeroPhase.Expanding -> p.route.moments.getOrNull(p.route.initialIndex); is ProfileGridHeroPhase.Retracting -> p.route.moments.getOrNull(p.route.initialIndex); is ProfileGridHeroPhase.Detail -> p.route.moments.getOrNull(p.route.initialIndex); else -> null }
    fun ingestThumbnailFrames(frames: Map<String, Rect>) { thumbnailFrames.putAll(frames) }
    fun openMenu(moment: Moment, index: Int, kind: ProfileGridHeroMenuKind = ProfileGridHeroMenuKind.OWNER) { menuKind = kind; showPinConfirm = false; sourceFrame = thumbnailFrames[ProfileGridHeroLayout.sourceKey(moment, index)] ?: Rect.Zero; phase = ProfileGridHeroPhase.MenuPeek(ProfileGridMomentMenuSelection(moment, index)); peekProgress = 1f; scrimOpacity = 1f; menuOpacity = 1f }
    fun dismissMenu() { if (menuSelection == null) return; peekProgress = 0f; scrimOpacity = 0f; menuOpacity = 0f; showPinConfirm = false; phase = ProfileGridHeroPhase.Idle }
    fun openDirectDetail(moments: List<Moment>, index: Int) { val moment = moments.getOrNull(index) ?: return; onOpenDetail?.invoke(ProfileMomentDetailRoute(moments, index, moment.id)) }
    fun expandToDetail(moments: List<Moment>, index: Int) { val selection = menuSelection ?: return; val resolved = moments.indexOfFirst { it.id != null && it.id == selection.moment.id }.takeIf { it >= 0 } ?: index.coerceIn(0, (moments.size - 1).coerceAtLeast(0)); val route = ProfileMomentDetailRoute(moments, resolved, moments.getOrNull(resolved)?.id, ProfileMomentDetailEntryKind.HERO); phase = ProfileGridHeroPhase.Expanding(route); onOpenDetail?.invoke(route) }
    fun liftedSourceContentOpacity(moment: Moment, index: Int): Float = if (menuSelection?.let { ProfileGridHeroLayout.sourceKey(it.moment, it.index) == ProfileGridHeroLayout.sourceKey(moment, index) } == true) ProfileGridHeroMotion.smoothstep(1f - (peekProgress / .14f).coerceAtMost(1f)) else 1f
    fun resetToIdle() { phase = ProfileGridHeroPhase.Idle; peekProgress = 0f; menuOpacity = 0f; scrimOpacity = 0f; showPinConfirm = false; toastMessage = null }
}

/** Compose overlay equivalent of Swift `ProfileGridHeroDetailLayer` menu stack. */
@Composable fun ProfileGridHeroDetailLayer(coordinator: ProfileGridHeroTransitionCoordinator, moments: List<Moment>, modifier: Modifier = Modifier) {
    val selection = coordinator.menuSelection ?: return
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = .30f)).clickable { coordinator.dismissMenu() }, contentAlignment = Alignment.Center) {
        Column(Modifier.width(240.dp).background(Color(0xE61B2025), RoundedCornerShape(16.dp)).clickable { }, verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (coordinator.showPinConfirm) {
                Text(stringResource(R.string.context_menu_pin_limit_confirm_title), Modifier.padding(18.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.context_menu_pin_limit_confirm_message), Modifier.padding(horizontal = 18.dp), color = Color.White.copy(alpha = .78f), fontSize = 14.sp)
                HeroMenuRow(null, stringResource(R.string.context_menu_pin_limit_confirm), false) { coordinator.onPin?.invoke(selection.moment, true, true); coordinator.dismissMenu() }
                HeroMenuRow(null, stringResource(R.string.context_menu_pin_limit_cancel), false) { coordinator.showPinConfirm = false }
            } else if (coordinator.menuKind == ProfileGridHeroMenuKind.OWNER) {
                HeroMenuRow(Icons.Default.PushPin, stringResource(if (selection.moment.isPinned == true) R.string.context_menu_unpin_moment else R.string.context_menu_pin_moment), false) { if (selection.moment.isPinned != true && moments.count { it.isPinned == true } >= 3) coordinator.showPinConfirm = true else { coordinator.onPin?.invoke(selection.moment, selection.moment.isPinned != true, false); coordinator.dismissMenu() } }
                if (selection.moment.canAdjustGridPreview) HeroMenuRow(null, stringResource(R.string.context_menu_adjust_preview), false) { coordinator.onAdjustPreview?.invoke(selection.moment); coordinator.dismissMenu() }
                HeroMenuRow(Icons.Default.Archive, stringResource(R.string.context_menu_archive_moment), false) { coordinator.onArchive?.invoke(selection.moment); coordinator.dismissMenu() }
                HeroMenuRow(Icons.Default.Edit, stringResource(R.string.context_menu_edit_moment), false) { coordinator.onEdit?.invoke(selection.moment); coordinator.dismissMenu() }
                HeroMenuRow(Icons.Default.Delete, stringResource(R.string.context_menu_delete_moment), true) { coordinator.onDelete?.invoke(selection.moment); coordinator.dismissMenu() }
            }
        }
    }
}
@Composable private fun HeroMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector?, title: String, destructive: Boolean, action: () -> Unit) = androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { icon?.let { Icon(it, null, tint = if (destructive) Color.Red else Color.White) }; if (icon != null) Spacer(Modifier.width(12.dp)); Text(title, color = if (destructive) Color.Red else Color.White, fontWeight = FontWeight.SemiBold) }
