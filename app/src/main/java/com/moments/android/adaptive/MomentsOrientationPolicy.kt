package com.moments.android.adaptive

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

enum class MomentsOrientation { Portrait, User }

object MomentsOrientationPolicy {
    fun orientationFor(window: AdaptiveWindowState): MomentsOrientation =
        if (window.isCompactHandset) MomentsOrientation.Portrait else MomentsOrientation.User

    fun activityOrientationFor(window: AdaptiveWindowState): Int = when (orientationFor(window)) {
        MomentsOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        MomentsOrientation.User -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

@Composable
fun ApplyMomentsOrientationPolicy(activity: Activity, window: AdaptiveWindowState) {
    val desired = MomentsOrientationPolicy.activityOrientationFor(window)
    LaunchedEffect(activity, desired) {
        if (activity.requestedOrientation != desired) {
            activity.requestedOrientation = desired
        }
    }
}
