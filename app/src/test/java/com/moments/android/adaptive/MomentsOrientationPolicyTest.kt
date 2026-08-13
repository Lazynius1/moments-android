package com.moments.android.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentsOrientationPolicyTest {
    @Test
    fun compactPhone_staysPortrait() {
        assertEquals(
            MomentsOrientation.Portrait,
            MomentsOrientationPolicy.orientationFor(window(width = 390, posture = MomentsFoldPosture.Flat)),
        )
    }

    @Test
    fun phoneInLandscape_staysPortraitEvenWhenWindowIsWide() {
        assertEquals(
            MomentsOrientation.Portrait,
            MomentsOrientationPolicy.orientationFor(window(width = 800, posture = MomentsFoldPosture.Flat)),
        )
    }

    @Test
    fun tablet_allowsUserOrientation() {
        assertEquals(
            MomentsOrientation.User,
            MomentsOrientationPolicy.orientationFor(
                window(width = 800, posture = MomentsFoldPosture.Flat, isLargeDevice = true),
            ),
        )
    }

    @Test
    fun halfOpenedFoldable_allowsUserOrientationEvenWhenNarrow() {
        assertEquals(
            MomentsOrientation.User,
            MomentsOrientationPolicy.orientationFor(window(width = 540, posture = MomentsFoldPosture.Book)),
        )
    }

    private fun window(
        width: Int,
        posture: MomentsFoldPosture,
        isLargeDevice: Boolean = false,
    ) = AdaptiveWindowState(
        width = width.dp,
        height = 900.dp,
        widthClass = adaptiveWidthClass(width.dp),
        isLargeDevice = isLargeDevice,
        foldPosture = posture,
        hingeBounds = null,
        isSeparating = posture != MomentsFoldPosture.Flat,
    )
}
