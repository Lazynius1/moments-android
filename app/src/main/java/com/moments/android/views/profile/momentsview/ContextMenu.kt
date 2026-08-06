package com.moments.android.views.profile.momentsview

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.HiddenLayerDiscovery
import com.moments.android.models.HiddenLayerMetricsSnapshot
import com.moments.android.models.Moment
import com.moments.android.models.MomentHiddenLayer
import com.moments.android.reportes.ModernReportContent
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchHiddenLayerDiscoveriesPage
import com.moments.android.services.firestore.fetchHiddenLayerMetrics
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.feed.core.StoryUserPresentationRoute
import com.moments.android.views.feed.sharing.AddToStoryView
import com.moments.android.views.feed.sharing.ModernShareSheet
import com.moments.android.views.feed.sharing.ShareMainActionsView
import com.moments.android.views.feed.sharing.buildMomentShareUrl
import com.moments.android.views.story.StoriesView
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.launch
import java.util.Date
/** Paridad iOS `ContextMenuViewState`. */
enum class ContextMenuViewState {
    Main,
    DeleteConfirm,
    HiddenLayerMetrics,
    HiddenLayerMetricDetail,
    Sharing,
    Messaging,
    PreparingStory,
    Reporting,
}

/**
 * Port de `ModernContextMenuOverlay` (`ContextMenu.swift`).
 * API del feed: onEdit / onDelete / onReport.
 */
@Composable
fun ModernContextMenuOverlay(
    moment: FeedMoment,
    isPresented: Boolean,
    onPresentedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!isPresented) return

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val firestore = remember { FirestoreService() }
    val domainMoment = remember(moment) { moment.toDomainMoment() }
    val isMyMoment = moment.authorId == FirebaseAuth.getInstance().currentUser?.uid
    val canShare = PrivacyService.canShareMoment(domainMoment)

    var viewState by remember { mutableStateOf(ContextMenuViewState.Main) }
    var hiddenLayerMetrics by remember { mutableStateOf<HiddenLayerMetricsSnapshot?>(null) }
    var isLoadingHiddenLayerMetrics by remember { mutableStateOf(false) }
    var hiddenLayerMetricsError by remember { mutableStateOf<String?>(null) }
    var selectedMetricsLayer by remember { mutableStateOf<MomentHiddenLayer?>(null) }
    var selectedLayerDiscoveries by remember { mutableStateOf<List<HiddenLayerDiscovery>>(emptyList()) }
    var selectedLayerDiscoveriesCursor by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var isLoadingSelectedLayerDiscoveries by remember { mutableStateOf(false) }
    var canLoadMoreSelectedLayerDiscoveries by remember { mutableStateOf(false) }
    var showAddToStory by remember { mutableStateOf(false) }
    var storyRoute by remember { mutableStateOf<StoryUserPresentationRoute?>(null) }
    val metricsErrorDefault = stringResource(R.string.hidden_layers_metrics_error)

    fun dismiss() {
        viewState = ContextMenuViewState.Main
        onPresentedChange(false)
    }

    fun handleBack() {
        when (viewState) {
            ContextMenuViewState.Main -> dismiss()
            ContextMenuViewState.DeleteConfirm -> viewState = ContextMenuViewState.Main
            ContextMenuViewState.HiddenLayerMetrics -> viewState = ContextMenuViewState.Main
            ContextMenuViewState.HiddenLayerMetricDetail -> viewState = ContextMenuViewState.HiddenLayerMetrics
            ContextMenuViewState.Sharing -> viewState = ContextMenuViewState.Main
            ContextMenuViewState.Messaging -> viewState = ContextMenuViewState.Sharing
            ContextMenuViewState.PreparingStory -> viewState = ContextMenuViewState.Sharing
            ContextMenuViewState.Reporting -> viewState = ContextMenuViewState.Main
        }
    }

    LaunchedEffect(moment.id, isMyMoment) {
        if (!isMyMoment || !moment.hasHiddenLayers || moment.hiddenLayerCount <= 0) return@LaunchedEffect
        if (isLoadingHiddenLayerMetrics) return@LaunchedEffect
        isLoadingHiddenLayerMetrics = true
        hiddenLayerMetricsError = null
        runCatching { firestore.fetchHiddenLayerMetrics(moment.authorId, moment.id) }
            .onSuccess { hiddenLayerMetrics = it }
            .onFailure { hiddenLayerMetricsError = metricsErrorDefault }
        isLoadingHiddenLayerMetrics = false
    }

    fun loadSelectedLayerDiscoveries(reset: Boolean) {
        val layer = selectedMetricsLayer ?: return
        if (isLoadingSelectedLayerDiscoveries) return
        if (!reset && selectedLayerDiscoveriesCursor != null && !canLoadMoreSelectedLayerDiscoveries) return
        scope.launch {
            isLoadingSelectedLayerDiscoveries = true
            val page = runCatching {
                firestore.fetchHiddenLayerDiscoveriesPage(
                    userId = moment.authorId,
                    momentId = moment.id,
                    layerId = layer.id,
                    pageSize = 8,
                    startAfter = if (reset) null else selectedLayerDiscoveriesCursor,
                )
            }.getOrNull()
            isLoadingSelectedLayerDiscoveries = false
            if (page == null) {
                canLoadMoreSelectedLayerDiscoveries = false
                return@launch
            }
            selectedLayerDiscoveries = if (reset) page.discoveries else selectedLayerDiscoveries + page.discoveries
            selectedLayerDiscoveriesCursor = page.lastDocument
            canLoadMoreSelectedLayerDiscoveries = page.hasMore && page.lastDocument != null
        }
    }

    val scrimAlpha = when (viewState) {
        ContextMenuViewState.PreparingStory -> 0.4f
        ContextMenuViewState.Main,
        ContextMenuViewState.DeleteConfirm,
        ContextMenuViewState.Sharing,
        -> 0.3f
        else -> 0.01f
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { handleBack() },
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(20.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black.copy(0.3f))
                    .momentsChromeGlass(RoundedCornerShape(32.dp), interactive = false)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                AnimatedContent(
                    targetState = viewState,
                    transitionSpec = {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                    },
                    label = "contextMenuState",
                ) { state ->
                    when (state) {
                        ContextMenuViewState.Main -> ModernContextMenuContent(
                            moment = moment,
                            isMyMoment = isMyMoment,
                            canShare = canShare,
                            hiddenLayerMetrics = hiddenLayerMetrics,
                            isLoadingHiddenLayerMetrics = isLoadingHiddenLayerMetrics,
                            hiddenLayerMetricsError = hiddenLayerMetricsError,
                            onEdit = {
                                onEdit()
                                dismiss()
                            },
                            onOpenHiddenLayerMetrics = {
                                viewState = ContextMenuViewState.HiddenLayerMetrics
                            },
                            onDelete = {
                                // Confirmación in-tree (no AlertDialog anidado en Dialogs de detalle).
                                viewState = ContextMenuViewState.DeleteConfirm
                            },
                            onShare = { viewState = ContextMenuViewState.Sharing },
                            onReport = {
                                viewState = ContextMenuViewState.Reporting
                                onReport()
                            },
                            onCancel = { dismiss() },
                        )
                        ContextMenuViewState.DeleteConfirm -> ContextMenuDeleteConfirmPanel(
                            onConfirm = {
                                onDelete()
                                dismiss()
                            },
                            onCancel = { viewState = ContextMenuViewState.Main },
                        )
                        ContextMenuViewState.HiddenLayerMetrics -> HiddenLayerMetricsListPanel(
                            metrics = hiddenLayerMetrics,
                            isLoading = isLoadingHiddenLayerMetrics,
                            errorMessage = hiddenLayerMetricsError,
                            onBack = { viewState = ContextMenuViewState.Main },
                            onSelectLayer = { layer ->
                                selectedMetricsLayer = layer
                                selectedLayerDiscoveries = emptyList()
                                selectedLayerDiscoveriesCursor = null
                                canLoadMoreSelectedLayerDiscoveries = false
                                viewState = ContextMenuViewState.HiddenLayerMetricDetail
                                loadSelectedLayerDiscoveries(reset = true)
                            },
                        )
                        ContextMenuViewState.HiddenLayerMetricDetail -> HiddenLayerMetricDetailPanel(
                            layer = selectedMetricsLayer,
                            discoveries = selectedLayerDiscoveries,
                            isLoadingMore = isLoadingSelectedLayerDiscoveries,
                            canLoadMore = canLoadMoreSelectedLayerDiscoveries,
                            onLoadMore = { loadSelectedLayerDiscoveries(reset = false) },
                            totalLayers = hiddenLayerMetrics?.totalLayerCount ?: 0,
                            onBack = { viewState = ContextMenuViewState.HiddenLayerMetrics },
                            onAvatarTap = { viewerId, hasStory ->
                                if (viewerId.isEmpty()) return@HiddenLayerMetricDetailPanel
                                if (hasStory) {
                                    storyRoute = StoryUserPresentationRoute(viewerId)
                                } else {
                                    NavigationEventBus.emit(
                                        CoordinatorNavigationEvent.NavigateToUserProfileInFeed(viewerId),
                                    )
                                    dismiss()
                                }
                            },
                            onRowTap = { viewerId ->
                                if (viewerId.isEmpty()) return@HiddenLayerMetricDetailPanel
                                NavigationEventBus.emit(
                                    CoordinatorNavigationEvent.NavigateToUserProfileInFeed(viewerId),
                                )
                                dismiss()
                            },
                        )
                        ContextMenuViewState.Sharing -> ShareMainActionsView(
                            moment = moment,
                            onSendMessage = { viewState = ContextMenuViewState.Messaging },
                            onAddToStory = { showAddToStory = true },
                            onExternalShare = {
                                val freshUsername = UserCacheService.getCachedUser(moment.authorId)?.username
                                    ?: moment.username
                                val shareText = context.getString(R.string.share_moment_by, freshUsername)
                                val shareUrl = buildMomentShareUrl(moment)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "$shareText\n$shareUrl")
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                                dismiss()
                            },
                        )
                        ContextMenuViewState.Messaging -> ModernShareSheet(
                            moment = moment,
                            onBack = { viewState = ContextMenuViewState.Sharing },
                            onDismiss = { dismiss() },
                        )
                        ContextMenuViewState.PreparingStory -> PreparingStoryPanel(
                            onCancel = { viewState = ContextMenuViewState.Sharing },
                        )
                        ContextMenuViewState.Reporting -> ModernReportContent(
                            moment = domainMoment,
                            story = null,
                            reportedUserId = null,
                            reportedUsername = null,
                            onBack = { viewState = ContextMenuViewState.Main },
                            onDismiss = { dismiss() },
                        )
                    }
                }
            }
        }

        // ≡ iOS fullScreenCover encima del menú (mismo root Box → insets/canvas correctos)
        if (showAddToStory) {
            AddToStoryView(
                moment = moment,
                onDismiss = {
                    showAddToStory = false
                    dismiss()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f),
            )
        }

        storyRoute?.let { route ->
            Dialog(
                onDismissRequest = { storyRoute = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                StoriesView(
                    startWithUserId = route.userId,
                    onDismiss = { storyRoute = null },
                )
            }
        }
    }
}

@Composable
fun ModernContextMenuContent(
    moment: FeedMoment,
    isMyMoment: Boolean,
    canShare: Boolean,
    hiddenLayerMetrics: HiddenLayerMetricsSnapshot?,
    isLoadingHiddenLayerMetrics: Boolean,
    hiddenLayerMetricsError: String?,
    onEdit: () -> Unit,
    onOpenHiddenLayerMetrics: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onCancel: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val secondary = if (isDark) Color.White.copy(0.7f) else Color.Black.copy(0.6f)
    val divider = if (isDark) Color.White.copy(0.2f) else Color.Black.copy(0.1f)
    val relative = MomentsFormat.relativeTime(Date(moment.timestamp))

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncProfileImageView(userId = moment.authorId, modifier = Modifier.size(44.dp).clip(CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(moment.username, color = primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.context_menu_moment_meta, relative),
                    color = secondary,
                    fontSize = 13.sp,
                )
            }
        }

        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isMyMoment) {
                if (moment.hasHiddenLayers && moment.hiddenLayerCount > 0) {
                    HiddenLayerMetricsSummaryCard(
                        metrics = hiddenLayerMetrics,
                        isLoading = isLoadingHiddenLayerMetrics,
                        errorMessage = hiddenLayerMetricsError,
                        onClick = onOpenHiddenLayerMetrics,
                    )
                }
                ContextMenuButton(
                    icon = Icons.Filled.Edit,
                    title = stringResource(R.string.context_menu_edit_moment),
                    subtitle = stringResource(R.string.context_menu_edit_moment_subtitle),
                    forceRedIcon = false,
                    onClick = onEdit,
                )
                ContextMenuButton(
                    icon = Icons.Filled.Delete,
                    title = stringResource(R.string.context_menu_delete_moment),
                    subtitle = stringResource(R.string.context_menu_delete_moment_subtitle),
                    forceRedIcon = true,
                    onClick = onDelete,
                )
                HorizontalDivider(color = divider, modifier = Modifier.padding(vertical = 8.dp))
            }

            if (canShare) {
                ContextMenuButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = stringResource(R.string.context_menu_share_moment),
                    subtitle = stringResource(R.string.context_menu_share_moment_subtitle),
                    forceRedIcon = false,
                    onClick = onShare,
                )
            } else {
                ContextMenuButtonDisabled(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = stringResource(R.string.context_menu_share_moment),
                    subtitle = stringResource(R.string.context_menu_share_moment_disabled),
                )
            }

            if (!isMyMoment) {
                HorizontalDivider(color = divider, modifier = Modifier.padding(vertical = 8.dp))
                ContextMenuButton(
                    icon = Icons.Filled.Flag,
                    title = stringResource(R.string.context_menu_report_moment),
                    subtitle = stringResource(R.string.context_menu_report_moment_subtitle),
                    forceRedIcon = true,
                    onClick = onReport,
                )
            }
            // iOS: dismiss vía scrim (onCancel); no hay fila Cancel en el menú principal.
            @Suppress("UNUSED_PARAMETER")
            val unusedCancel = onCancel
        }
    }
}

/** Confirmación in-tree — evita AlertDialog anidado en Dialogs de detalle (no se muestra / no confirma). */
@Composable
private fun ContextMenuDeleteConfirmPanel(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val secondary = if (isDark) Color.White.copy(0.7f) else Color.Black.copy(0.6f)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.context_menu_delete_title),
            color = primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.context_menu_delete_message),
            color = secondary,
            fontSize = 14.sp,
        )
        ContextMenuButton(
            icon = Icons.Filled.Delete,
            title = stringResource(R.string.context_menu_delete_confirm),
            subtitle = stringResource(R.string.context_menu_delete_moment_subtitle),
            forceRedIcon = true,
            onClick = onConfirm,
        )
        ContextMenuButton(
            icon = Icons.Filled.Close,
            title = stringResource(R.string.context_menu_delete_cancel),
            subtitle = "",
            forceRedIcon = false,
            onClick = onCancel,
        )
    }
}

@Composable
private fun HiddenLayerMetricsSummaryCard(
    metrics: HiddenLayerMetricsSnapshot?,
    isLoading: Boolean,
    errorMessage: String?,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val secondary = if (isDark) Color.White.copy(0.7f) else Color.Black.copy(0.6f)
    val summary = when {
        isLoading -> stringResource(R.string.hidden_layers_metrics_loading)
        errorMessage != null -> stringResource(R.string.hidden_layers_metrics_error)
        metrics == null -> stringResource(R.string.hidden_layers_metrics_empty_subtitle)
        metrics.totalDiscoveries == 0 -> stringResource(R.string.hidden_layers_metrics_empty_title)
        else -> stringResource(
            R.string.hidden_layers_metrics_summary,
            metrics.totalDiscoveries,
            metrics.discoveredLayerCount,
        )
    }
    val leadingChip = metrics?.takeIf { it.totalDiscoveries > 0 }?.let { snap ->
        snap.topLayer?.let {
            stringResource(R.string.hidden_layers_metrics_chip_top, it.metricsDisplayName())
        } ?: if (snap.uniquePeopleCount > 0) {
            stringResource(R.string.hidden_layers_metrics_chip_people, snap.uniquePeopleCount)
        } else {
            stringResource(R.string.hidden_layers_metrics_chip_coverage, snap.coverageRatio * 100.0)
        }
    }
    Column(Modifier.clickable(onClick = onClick).fillMaxWidth()) {
        HorizontalDivider(
            color = if (isDark) Color.White.copy(0.16f) else Color.Black.copy(0.08f),
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFFFD54F).copy(alpha = if (isDark) 0.82f else 0.72f),
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.hidden_layers_metrics_title),
                    color = primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(summary, color = secondary, fontSize = 12.sp, maxLines = 2)
            }
            leadingChip?.let { chip ->
                Text(
                    chip,
                    color = if (isDark) Color.White.copy(0.82f) else Color.Black.copy(0.72f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                null,
                tint = if (isDark) Color.White.copy(0.5f) else Color.Black.copy(0.35f),
            )
        }
    }
}

@Composable
private fun ContextSubheaderView(title: String, onBack: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = primary,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(6.dp),
        )
        Text(title, color = primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun HiddenLayerMetricsListPanel(
    metrics: HiddenLayerMetricsSnapshot?,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSelectLayer: (MomentHiddenLayer) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val secondary = if (isDark) Color.White.copy(0.72f) else Color.Black.copy(0.56f)
    Column(Modifier.fillMaxWidth()) {
        ContextSubheaderView(
            title = stringResource(R.string.hidden_layers_metrics_title),
            onBack = onBack,
        )
        when {
            isLoading -> Column(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MomentsCircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.hidden_layers_metrics_loading), color = secondary, fontSize = 13.sp)
            }
            errorMessage != null -> Text(
                errorMessage,
                color = secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
            )
            metrics != null -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                InlineMetricsStrip(metrics)
                Spacer(Modifier.height(14.dp))
                metrics.layers.forEachIndexed { index, layer ->
                    HiddenLayerMetricsRow(
                        layer = layer,
                        discoveries = metrics.recentDiscoveriesByLayer[layer.id].orEmpty(),
                        onClick = { onSelectLayer(layer) },
                    )
                    if (index < metrics.layers.lastIndex) {
                        HorizontalDivider(
                            color = if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.08f),
                            modifier = Modifier.padding(start = 68.dp),
                        )
                    }
                }
            }
            else -> Text(
                stringResource(R.string.hidden_layers_metrics_empty_subtitle),
                color = secondary,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
private fun InlineMetricsStrip(metrics: HiddenLayerMetricsSnapshot) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val secondary = if (isDark) Color.White.copy(0.72f) else Color.Black.copy(0.56f)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InlineMetricText("${metrics.totalDiscoveries}", stringResource(R.string.hidden_layers_metrics_card_discoveries), primary, secondary)
        BulletDot(isDark)
        InlineMetricText("${metrics.uniquePeopleCount}", stringResource(R.string.hidden_layers_metrics_card_people), primary, secondary)
        BulletDot(isDark)
        InlineMetricText(
            "${(metrics.coverageRatio * 100).toInt()}%",
            stringResource(R.string.hidden_layers_metrics_card_coverage),
            primary,
            secondary,
        )
    }
}

@Composable
private fun InlineMetricText(value: String, label: String, primary: Color, secondary: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(value, color = primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = secondary, fontSize = 12.sp)
    }
}

@Composable
private fun BulletDot(isDark: Boolean) {
    Box(
        Modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(if (isDark) Color.White.copy(0.25f) else Color.Black.copy(0.16f)),
    )
}

@Composable
private fun HiddenLayerMetricsRow(
    layer: MomentHiddenLayer,
    discoveries: List<HiddenLayerDiscovery>,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val secondary = if (isDark) Color.White.copy(0.72f) else Color.Black.copy(0.56f)
    val muted = if (isDark) Color.White.copy(0.55f) else Color.Black.copy(0.45f)
    val status = layer.metricsStatusText()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HiddenLayerMetricLayerPreview(layer = layer, compact = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(layer.metricsDisplayName(), color = primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (status != null) {
                    Text(
                        status,
                        color = if (isDark) Color.White.copy(0.78f) else Color.Black.copy(0.65f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.hidden_layers_metrics_row_discoveries, layer.discoverCount ?: 0),
                    color = secondary,
                    fontSize = 11.sp,
                )
                Text(
                    stringResource(R.string.hidden_layers_metrics_row_people, layer.uniqueDiscovererCount ?: 0),
                    color = secondary,
                    fontSize = 11.sp,
                )
            }
            discoveries.firstOrNull()?.let { latest ->
                Text(
                    stringResource(
                        R.string.hidden_layers_metrics_row_latest,
                        MomentsFormat.smartDate(latest.discoveredAt, MomentsFormat.DateContext.MEDIUM_DATE_TIME),
                    ),
                    color = muted,
                    fontSize = 11.sp,
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            null,
            tint = if (isDark) Color.White.copy(0.45f) else Color.Black.copy(0.3f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun HiddenLayerMetricLayerPreview(layer: MomentHiddenLayer, compact: Boolean) {
    val size = if (compact) 34.dp else 42.dp
    val isDark = isSystemInDarkTheme()
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(if (compact) 8.dp else 10.dp))
            .background(if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        when (layer.type) {
            MomentHiddenLayer.LayerType.IMAGE -> {
                val url = layer.thumbnailURL ?: layer.mediaURL
                if (!url.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Filled.Image, null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(18.dp))
                }
            }
            MomentHiddenLayer.LayerType.AUDIO ->
                Icon(Icons.Filled.GraphicEq, null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(18.dp))
            MomentHiddenLayer.LayerType.TEXT ->
                Icon(Icons.Filled.Notes, null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HiddenLayerMetricDetailPanel(
    layer: MomentHiddenLayer?,
    discoveries: List<HiddenLayerDiscovery>,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    totalLayers: Int,
    onBack: () -> Unit,
    onAvatarTap: (String, Boolean) -> Unit,
    onRowTap: (String) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val secondary = if (isDark) Color.White.copy(0.72f) else Color.Black.copy(0.56f)
    val title = layer?.metricsDisplayName() ?: stringResource(R.string.hidden_layers_metrics_detail)
    @Suppress("UNUSED_PARAMETER")
    val unusedTotal = totalLayers
    Column(Modifier.fillMaxWidth()) {
        ContextSubheaderView(title = title, onBack = onBack)
        if (layer == null) return
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HiddenLayerMetricLayerPreview(layer = layer, compact = false)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(layer.metricsDisplayName(), color = primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        layer.metricsStatusText()?.let { status ->
                            Text(
                                status,
                                color = if (isDark) Color.White.copy(0.78f) else Color.Black.copy(0.65f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        InlineMetricText("${layer.discoverCount ?: 0}", stringResource(R.string.hidden_layers_metrics_card_discoveries), primary, secondary)
                        BulletDot(isDark)
                        InlineMetricText("${layer.uniqueDiscovererCount ?: 0}", stringResource(R.string.hidden_layers_metrics_card_people), primary, secondary)
                        BulletDot(isDark)
                        val coverage = if ((layer.discoverCount ?: 0) > 0) 100 else 0
                        InlineMetricText("$coverage%", stringResource(R.string.hidden_layers_metrics_card_coverage), primary, secondary)
                    }
                }
            }
            HorizontalDivider(color = if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.08f))
            Text(stringResource(R.string.hidden_layers_metrics_latest_people), color = primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (discoveries.isEmpty()) {
                Text(stringResource(R.string.hidden_layers_metrics_latest_people_empty), color = secondary, fontSize = 12.sp)
            } else {
                discoveries.forEachIndexed { index, discovery ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onRowTap(discovery.viewerId) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StoryRingAvatarView(
                            userId = discovery.viewerId,
                            size = 32.dp,
                            lineWidth = 2.2.dp,
                            onTap = { hasStory -> onAvatarTap(discovery.viewerId, hasStory) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                discovery.username?.takeIf { it.isNotBlank() } ?: discovery.viewerId.take(10),
                                color = primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(MomentsFormat.smartDate(discovery.discoveredAt, MomentsFormat.DateContext.MEDIUM_DATE_TIME), color = secondary, fontSize = 11.sp)
                        }
                    }
                    if (index < discoveries.lastIndex) {
                        HorizontalDivider(
                            color = if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.08f),
                            modifier = Modifier.padding(start = 44.dp),
                        )
                    }
                }
                if (isLoadingMore) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MomentsCircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                } else if (canLoadMore) {
                    Text(
                        stringResource(R.string.feed_see_more),
                        color = primary,
                        modifier = Modifier.clickable(onClick = onLoadMore).padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MomentHiddenLayer.metricsDisplayName(): String = when (type) {
    MomentHiddenLayer.LayerType.TEXT -> {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) stringResource(R.string.hidden_layers_metrics_layer_text) else trimmed.take(24)
    }
    MomentHiddenLayer.LayerType.AUDIO -> stringResource(R.string.hidden_layers_metrics_layer_audio)
    MomentHiddenLayer.LayerType.IMAGE -> {
        val trimmed = caption?.trim().orEmpty()
        if (trimmed.isEmpty()) stringResource(R.string.hidden_layers_metrics_layer_image) else trimmed.take(24)
    }
}

@Composable
private fun MomentHiddenLayer.metricsStatusText(): String? = when {
    moderationState == MomentHiddenLayer.ModerationState.HIDDEN ->
        stringResource(R.string.hidden_layers_metrics_status_moderated)
    moderationState == MomentHiddenLayer.ModerationState.PENDING ->
        stringResource(R.string.hidden_layers_metrics_status_pending)
    unlockMode == MomentHiddenLayer.UnlockMode.SCHEDULED && unlockAt != null && unlockAt.after(Date()) ->
        stringResource(R.string.hidden_layers_metrics_status_scheduled, MomentsFormat.smartDate(unlockAt, MomentsFormat.DateContext.MEDIUM_DATE_TIME))
    else -> null
}

@Composable
private fun PreparingStoryPanel(onCancel: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(color = if (isDark) Color.White else Color.Black, strokeWidth = 2.dp)
        Text(
            stringResource(R.string.context_menu_preparing_story),
            color = if (isDark) Color.White else Color.Black,
        )
        Text(
            stringResource(R.string.context_menu_cancel),
            color = if (isDark) Color.White.copy(0.7f) else Color.Black.copy(0.6f),
            modifier = Modifier.clickable(onClick = onCancel),
        )
    }
}

@Composable
private fun ContextMenuButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    forceRedIcon: Boolean,
    onClick: () -> Unit,
    showChevron: Boolean = true,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val secondary = if (isDark) Color.White.copy(0.7f) else Color.Black.copy(0.6f)
    val iconTint = if (forceRedIcon) Color.Red else primary
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = secondary, fontSize = 13.sp)
            }
        }
        if (showChevron) {
            Icon(
                Icons.Filled.ChevronRight,
                null,
                tint = if (isDark) Color.White.copy(0.4f) else Color.Black.copy(0.3f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ContextMenuButtonDisabled(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    val isDark = isSystemInDarkTheme()
    val muted = if (isDark) Color.White.copy(0.5f) else Color.Black.copy(0.4f)
    val mutedSub = if (isDark) Color.White.copy(0.4f) else Color.Black.copy(0.3f)
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = muted, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = muted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = mutedSub, fontSize = 13.sp)
        }
        Icon(Icons.Filled.Lock, null, tint = if (isDark) Color.White.copy(0.3f) else Color.Black.copy(0.2f), modifier = Modifier.size(12.dp))
    }
}

private fun FeedMoment.toDomainMoment(): Moment = Moment(
    id = id,
    authorId = authorId,
    username = username,
    content = content,
    audience = audience,
    customListId = customListId,
    hasHiddenLayers = hasHiddenLayers,
    hiddenLayerCount = hiddenLayerCount,
    commentCount = commentCount,
    hideLikeCounts = hideLikeCounts,
    disableComments = disableComments,
    profileImagePath = profileImagePath,
    location = location,
    locationCoordinate = locationCoordinate,
    aspectRatio = aspectRatio,
    timestamp = Date(timestamp),
    isArchived = isArchived,
)
