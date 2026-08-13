package com.moments.android.coordinators.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * Estado multi-backstack Nav3 para el dock (skill `navigation-3`, recipe *multiple-backstacks*).
 *
 * Patrón “exit through home”: al estar en otro tab en su root, Back vuelve a Feed;
 * desde Feed root el sistema puede cerrar la Activity.
 */
class MomentsTabNavigationState(
    val startRoute: MomentsTabNavKey,
    topLevelRoute: MutableState<MomentsTabNavKey>,
    val backStacks: Map<MomentsTabNavKey, NavBackStack<NavKey>>,
) {
    var topLevelRoute: MomentsTabNavKey by topLevelRoute

    val selectedTabIndex: Int get() = topLevelRoute.tabIndex

    /**
     * true si el deep link actual llegó con FLAG_ACTIVITY_NEW_TASK
     * (Up/Back recorren stack sintético; ver deeplink-guide).
     */
    var deepLinkFromNewTask: Boolean = false

    @Composable
    fun toDecoratedEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>,
    ): List<NavEntry<NavKey>> {
        val decoratedEntries = backStacks.mapValues { (_, stack) ->
            val decorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            )
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = decorators,
                entryProvider = entryProvider,
            )
        }
        return topLevelRoutesInUse().flatMap { decoratedEntries[it].orEmpty() }
    }

    /** Feed siempre primero; como máximo un segundo tab activo. */
    private fun topLevelRoutesInUse(): List<MomentsTabNavKey> =
        if (topLevelRoute == startRoute) {
            listOf(startRoute)
        } else {
            listOf(startRoute, topLevelRoute)
        }
}

class MomentsTabNavigator(val state: MomentsTabNavigationState) {
    fun selectTab(route: MomentsTabNavKey) {
        if (route !in state.backStacks) return
        state.topLevelRoute = route
    }

    fun selectTabIndex(index: Int) {
        val route = MomentsTabNavKey.fromTabIndex(index)
        if (route == MomentsTabNavKey.Create) return
        selectTab(route)
    }

    /**
     * Empuja un overlay (fase 2b — DialogSceneStrategy) sobre el tab activo.
     * Si ya es el tope, no duplica.
     */
    fun push(key: NavKey) {
        val stack = state.backStacks[state.topLevelRoute] ?: return
        if (stack.lastOrNull() == key) return
        stack.add(key)
    }

    /**
     * Push sobre el stack Feed (root Feed debajo → Back sintético).
     * Los sheets/zooms locales del feed (anillo, `FeedProfileSheetRoute`) no usan esto.
     */
    fun openProfile(userId: String) {
        pushOnFeed(MomentsNavKey.Profile(userId.trim()))
    }

    fun openMoment(momentId: String, authorId: String) {
        val id = momentId.trim()
        val author = authorId.trim()
        if (id.isEmpty() || author.isEmpty()) return
        pushOnFeed(MomentsNavKey.Moment(id = id, authorId = author))
    }

    fun openConversation(conversationId: String) {
        val id = conversationId.trim()
        if (id.isEmpty()) return
        // ≡ iOS: conversación sobre el tab Mensajes (no overlay del Feed).
        selectTab(MomentsTabNavKey.Messages)
        val stack = state.backStacks[MomentsTabNavKey.Messages] ?: return
        val key = MomentsNavKey.Conversation(id)
        if (stack.lastOrNull() == key) return
        stack.add(key)
    }

    fun openStories(startAtUserId: String? = null) {
        val uid = startAtUserId?.trim().orEmpty()
        if (uid.isNotEmpty()) {
            pushOnFeed(MomentsNavKey.Story(storyId = "", authorId = uid))
        } else {
            pushOnFeed(MomentsNavKey.ShowStories)
        }
    }

    fun openStory(storyId: String, authorId: String?) {
        val sid = storyId.trim()
        val uid = authorId?.trim().orEmpty()
        if (uid.isNotEmpty()) {
            pushOnFeed(MomentsNavKey.Story(storyId = sid, authorId = uid))
        } else if (sid.isNotEmpty()) {
            // Sin autor: abre el deck genérico (StoriesView resuelve).
            pushOnFeed(MomentsNavKey.ShowStories)
        }
    }

    fun openStoryChain(chainId: String, title: String = "") {
        val id = chainId.trim()
        if (id.isEmpty()) return
        pushOnFeed(MomentsNavKey.StoryChain(chainId = id, title = title))
    }

    private fun pushOnFeed(key: NavKey) {
        if (key is MomentsNavKey.Profile && key.userId.isEmpty()) return
        selectTab(MomentsTabNavKey.Feed)
        val stack = state.backStacks[MomentsTabNavKey.Feed] ?: return
        if (stack.lastOrNull() == key) return
        stack.add(key)
    }

    /**
     * Deep link con back stack sintético (recipe deeplinks-advanced).
     * Resetea el stack del tab root y empuja el camino root→destino.
     */
    fun openDeepLink(target: MomentsNavKey, fromNewTask: Boolean = true) {
        state.deepLinkFromNewTask = fromNewTask
        when (target) {
            MomentsNavKey.ScrollFeedToTop -> {
                selectTab(MomentsTabNavKey.Feed)
                return
            }
            MomentsNavKey.ShowMessages -> {
                selectTab(MomentsTabNavKey.Messages)
                return
            }
            MomentsNavKey.ShowNova -> {
                selectTab(MomentsTabNavKey.Feed)
                pushOnFeed(MomentsNavKey.ShowNova)
                return
            }
            MomentsNavKey.OwnProfileTab -> {
                selectTab(MomentsTabNavKey.Profile)
                return
            }
            MomentsNavKey.ShowProfileVisits -> {
                // Visits vive en ProfileView (EventBus); tab Profile es el padre sintético.
                selectTab(MomentsTabNavKey.Profile)
                com.moments.android.coordinators.NavigationEventBus.emit(
                    com.moments.android.coordinators.CoordinatorNavigationEvent.ShowProfileVisits,
                )
                return
            }
            is MomentsNavKey.Echo,
            is MomentsNavKey.EchoSuggestion,
            -> {
                target.navigateViaAppRouter()
                return
            }
            else -> Unit
        }

        val path = buildSyntheticBackStack(target)
        val tabRoot = (path.firstOrNull() as? MomentsTabNavKey)
            ?.takeUnless { it == MomentsTabNavKey.Create }
            ?: MomentsTabNavKey.Feed
        selectTab(tabRoot)
        val stack = state.backStacks[tabRoot] ?: return

        // Dejar solo el root del tab.
        while (stack.size > 1) {
            stack.removeLastOrNull()
        }
        if (stack.isEmpty()) {
            stack.add(tabRoot)
        }

        val toPush = path.dropWhile { it != tabRoot }.drop(1)
        for (key in toPush) {
            if (stack.lastOrNull() != key) {
                stack.add(key)
            }
        }
    }

    /**
     * Up dentro de la app: pop si hay destino sobre el root; si no, false
     * (el caller puede reiniciar Task con [createDeepLinkUpTaskStack]).
     */
    fun navigateUp(): Boolean {
        val stack = state.backStacks[state.topLevelRoute] ?: return false
        if (stack.size <= 1) {
            if (state.topLevelRoute != state.startRoute) {
                state.topLevelRoute = state.startRoute
                return true
            }
            return false
        }
        stack.removeLastOrNull()
        return true
    }

    /** Padre del tope actual (para Up → TaskStackBuilder en task existente). */
    fun currentDeepLinkParent(): NavKey? {
        val stack = state.backStacks[state.topLevelRoute] ?: return null
        val top = stack.lastOrNull() as? MomentsNavKey ?: return null
        return top.deepLinkParent()
    }

    /** Quita [key] del stack activo si está en el tope (dismiss de overlay). */
    fun popIfTop(key: NavKey): Boolean {
        val stack = state.backStacks[state.topLevelRoute] ?: return false
        if (stack.lastOrNull() != key) return false
        stack.removeLastOrNull()
        return true
    }

    /** ¿El stack del tab activo contiene [key]? (lectura snapshot → recomposición). */
    fun contains(key: NavKey): Boolean {
        val stack = state.backStacks[state.topLevelRoute] ?: return false
        return stack.any { it == key }
    }

    /** El dock solo pertenece a las raíces de las pestañas, nunca a un destino abierto encima. */
    fun shouldHideTabBarForPush(): Boolean {
        val stack = state.backStacks[state.topLevelRoute] ?: return false
        return stack.lastOrNull() != state.topLevelRoute
    }

    /**
     * @return true si consumió el back; false si estamos en Feed root (dejar salir).
     */
    fun goBack(): Boolean {
        val stack = state.backStacks[state.topLevelRoute] ?: return false
        val current = stack.lastOrNull() ?: return false
        return if (current == state.topLevelRoute) {
            if (state.topLevelRoute == state.startRoute) {
                false
            } else {
                state.topLevelRoute = state.startRoute
                true
            }
        } else {
            stack.removeLastOrNull()
            true
        }
    }
}

@Composable
fun rememberMomentsTabNavigationState(
    startRoute: MomentsTabNavKey = MomentsTabNavKey.Feed,
): MomentsTabNavigationState {
    val topLevelRoute = rememberSaveable(
        saver = Saver<MutableState<MomentsTabNavKey>, Int>(
            save = { it.value.tabIndex },
            restore = { mutableStateOf(MomentsTabNavKey.fromTabIndex(it)) },
        ),
    ) { mutableStateOf(startRoute) }
    val feedStack = rememberNavBackStack(MomentsTabNavKey.Feed)
    val messagesStack = rememberNavBackStack(MomentsTabNavKey.Messages)
    val exploreStack = rememberNavBackStack(MomentsTabNavKey.Explore)
    val profileStack = rememberNavBackStack(MomentsTabNavKey.Profile)
    return remember(startRoute) {
        MomentsTabNavigationState(
            startRoute = startRoute,
            topLevelRoute = topLevelRoute,
            backStacks = mapOf(
                MomentsTabNavKey.Feed to feedStack,
                MomentsTabNavKey.Messages to messagesStack,
                MomentsTabNavKey.Explore to exploreStack,
                MomentsTabNavKey.Profile to profileStack,
            ),
        )
    }
}

@Composable
fun rememberMomentsTabNavigator(
    state: MomentsTabNavigationState,
): MomentsTabNavigator = remember(state) { MomentsTabNavigator(state) }
