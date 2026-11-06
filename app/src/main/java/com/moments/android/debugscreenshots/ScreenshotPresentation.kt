package com.moments.android.debugscreenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.coordinators.TabBarScreen
import com.moments.android.models.Story
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.views.creator.audienceselector.AudienceSelectionView
import com.moments.android.views.creator.audienceselector.ContentAudience
import com.moments.android.views.echoes.EchoViewerUI
import com.moments.android.views.feed.maps.DiscoverMapView
import com.moments.android.views.feed.maps.toFeedMomentForMap
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.nova.NovaView
import com.moments.android.views.settings.sections.ScreenshotAppIconPickerHost
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.momentdetail.MomentDetailContainerView
import com.moments.android.views.shared.momentdetail.MomentDetailContext
import com.moments.android.views.story.StoryViewModel
import com.moments.android.views.story.storyviewer.StoryViewerScreen
import kotlinx.coroutines.delay

/** Margen para que la app termine su primer layout antes de navegar o abrir hojas. */
private const val PRESENT_DELAY_MS = 600L

/**
 * Raíz del modo capturas (sustituye a `MomentsApp`): sin login ni onboarding, abre
 * directamente la pantalla pedida. Pestañas y hojas van sobre el `TabBarScreen` real;
 * las pantallas completas se montan solas.
 */
@Composable
fun ScreenshotRoot() {
    when (val screen = ScreenshotMode.screen ?: return) {
        ScreenshotMode.Screen.FEED,
        ScreenshotMode.Screen.AI_LABEL,
        ScreenshotMode.Screen.CHAT,
        ScreenshotMode.Screen.CHAT_INBOX,
        -> ScreenshotTabHost(screen)
        ScreenshotMode.Screen.PRIVACY,
        ScreenshotMode.Screen.APP_ICONS,
        -> ScreenshotSheetHost(screen)
        ScreenshotMode.Screen.STORY_VIEWER -> ScreenshotStoryHost(
            stories = ScreenshotFixtures.stories,
            index = ScreenshotMode.storyIndex,
        )
        ScreenshotMode.Screen.HIDDEN_LAYERS -> ScreenshotCover {
            MomentDetailContainerView(
                context = MomentDetailContext.Single(ScreenshotFixtures.hiddenLayersMoment.toFeedMomentForMap()),
                onDismiss = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
        ScreenshotMode.Screen.ECHO -> ScreenshotCover {
            val echo = ScreenshotFixtures.echo
            EchoViewerUI(
                echoId = echo.id ?: "ss_echo",
                onDismiss = {},
                modifier = Modifier.fillMaxSize(),
                initialEcho = echo,
            )
        }
        ScreenshotMode.Screen.MAP -> ScreenshotCover {
            DiscoverMapView(
                onDismiss = {},
                modifier = Modifier.fillMaxSize(),
                initialLatitude = ScreenshotFixtures.BARCELONA_LAT,
                initialLongitude = ScreenshotFixtures.BARCELONA_LON,
            )
        }
        ScreenshotMode.Screen.NOVA -> ScreenshotCover {
            NovaView(onDismiss = {})
        }
    }
}

/** Pestañas reales; chat y bandeja se abren con los mismos eventos que usa la app. */
@Composable
private fun ScreenshotTabHost(screen: ScreenshotMode.Screen) {
    LaunchedEffect(screen) {
        delay(PRESENT_DELAY_MS)
        when (screen) {
            ScreenshotMode.Screen.CHAT -> NavigationEventBus.emit(
                CoordinatorNavigationEvent.NavigateToConversation(ScreenshotFixtures.CHAT_CONVERSATION_ID),
            )
            ScreenshotMode.Screen.CHAT_INBOX -> NavigationEventBus.emit(CoordinatorNavigationEvent.ShowMessages)
            else -> Unit
        }
    }
    TabBarScreen()
}

/** Hojas sobre el feed de demo, como las presenta la app. */
@Composable
private fun ScreenshotSheetHost(screen: ScreenshotMode.Screen) {
    var showSheet by remember { mutableStateOf(false) }
    LaunchedEffect(screen) {
        delay(PRESENT_DELAY_MS)
        showSheet = true
    }
    Box(Modifier.fillMaxSize()) {
        TabBarScreen()
        if (showSheet) {
            when (screen) {
                ScreenshotMode.Screen.PRIVACY -> ScreenshotAudienceSheet()
                ScreenshotMode.Screen.APP_ICONS -> ScreenshotAppIconPickerHost()
                else -> Unit
            }
        }
    }
}

/** Misma hoja que en CaptionAndDetailsView, a altura completa y con mejores amigos elegidos. */
@Composable
private fun ScreenshotAudienceSheet() {
    var audience by remember { mutableStateOf(ContentAudience.BEST_FRIENDS) }
    var listId by remember { mutableStateOf<String?>(null) }
    var listName by remember { mutableStateOf<String?>(null) }
    var customUsers by remember { mutableStateOf<List<String>>(emptyList()) }
    MomentsModalSheet(onDismissRequest = {}, largeOnly = true) { _ ->
        AudienceSelectionView(
            selectedAudience = audience,
            selectedListId = listId,
            selectedListName = listName,
            customSelectedUsers = customUsers,
            onSelectedAudienceChange = { audience = it },
            onSelectedListIdChange = { listId = it },
            onSelectedListNameChange = { listName = it },
            onCustomSelectedUsersChange = { customUsers = it },
            onDismiss = {},
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

/** Pantalla completa sobre el fondo de la app. */
@Composable
private fun ScreenshotCover(content: @Composable () -> Unit) {
    val surface = rememberAdaptiveColors().surfaceBackground
    Box(Modifier.fillMaxSize().background(surface)) {
        content()
    }
}

/**
 * Visor de historias fijo (como ArchiveDayStoriesViewer): abre una historia concreta
 * con el progreso a mitad y nunca se cierra. Espera a que la ventana tenga sus
 * márgenes antes de montar el visor (mismo arreglo que en iOS).
 */
@Composable
private fun ScreenshotStoryHost(stories: List<Story>, index: Int) {
    val storyViewModel: StoryViewModel = viewModel()
    var isSceneReady by remember { mutableStateOf(false) }
    val clampedIndex = index.coerceIn(0, (stories.size - 1).coerceAtLeast(0))
    val story = stories.getOrNull(clampedIndex)

    LaunchedEffect(Unit) {
        GlobalVideoManager.pauseAllVideos()
        stories.firstOrNull()?.authorId?.let { storyViewModel.hydrateStoriesForAuthor(it, stories) }
        delay(800)
        isSceneReady = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isSceneReady && story != null) {
            StoryViewerScreen(
                story = story,
                segmentCount = stories.size,
                segmentIndex = clampedIndex,
                onNext = {},
                onPrevious = {},
                onDismiss = {},
                storyViewModel = storyViewModel,
                initialElapsed = story.duration * 0.4,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
