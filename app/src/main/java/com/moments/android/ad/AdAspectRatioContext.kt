package com.moments.android.ad

import com.google.android.gms.ads.nativead.NativeAdOptions

/**
 * Port de `AdAspectRatioContext.swift` — aspect ratios for native ad media loading.
 *
 * Stories (9:16) and feed (4:5 vertical) both map to portrait on AdMob.
 * Banner uses landscape.
 */
enum class AdAspectRatioContext {
    Stories,
    Feed,
    Banner;

    val nativeAdOptions: NativeAdOptions
        get() {
            val builder = NativeAdOptions.Builder()
            val aspectRatio = when (this) {
                Stories, Feed -> NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_PORTRAIT
                Banner -> NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE
            }
            return builder.setMediaAspectRatio(aspectRatio).build()
        }

    companion object {
        /** Default feed options (iOS `createNativeAdOptions()`). */
        fun defaultFeedOptions(): NativeAdOptions = Feed.nativeAdOptions
    }
}
