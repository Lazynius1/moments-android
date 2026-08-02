package com.moments.android.coordinators.nav3

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Rutas top-level del dock ≡ [com.moments.android.coordinators.AppTab]
 * (home=0, nova=1, create=2, explore=3, profile=4).
 *
 * Skill navigation-3 / multiple backstacks: cada tab navegable tiene su
 * [androidx.navigation3.runtime.NavBackStack] bajo NavDisplay.
 * Create no mantiene stack — abre el creator overlay (paridad iOS).
 */
@Serializable
sealed interface MomentsTabNavKey : NavKey {
    val tabIndex: Int

    @Serializable
    data object Feed : MomentsTabNavKey {
        override val tabIndex: Int = 0
    }

    @Serializable
    data object Nova : MomentsTabNavKey {
        override val tabIndex: Int = 1
    }

    /** Centro del dock — no entra en [navigableTabs]. */
    @Serializable
    data object Create : MomentsTabNavKey {
        override val tabIndex: Int = 2
    }

    @Serializable
    data object Explore : MomentsTabNavKey {
        override val tabIndex: Int = 3
    }

    @Serializable
    data object Profile : MomentsTabNavKey {
        override val tabIndex: Int = 4
    }

    companion object {
        /** Tabs con back stack Nav3 (Create = overlay). */
        val navigableTabs: Set<MomentsTabNavKey> = setOf(Feed, Nova, Explore, Profile)

        fun fromTabIndex(index: Int): MomentsTabNavKey = when (index) {
            0 -> Feed
            1 -> Nova
            2 -> Create
            3 -> Explore
            4 -> Profile
            else -> Feed
        }
    }
}
