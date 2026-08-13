package com.moments.android.views.creator.creatoruikit

import android.util.Rational
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture

/** Shared CameraX policy for Creator, Stories and attachment capture. */
object MomentsCameraController {
    fun createVideoCapture(
        qualities: List<Quality> = listOf(Quality.FHD, Quality.HD),
        stabilizationSupported: Boolean = false,
    ): VideoCapture<Recorder> {
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    qualities,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.HD),
                ),
            )
            .build()
        return VideoCapture.Builder(recorder)
            .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
            .setVideoStabilizationEnabled(stabilizationSupported)
            .build()
    }

    fun createPreview(targetRotation: Int, stabilizationSupported: Boolean): Preview =
        Preview.Builder()
            .setTargetRotation(targetRotation)
            .setPreviewStabilizationEnabled(stabilizationSupported)
            .build()

    fun createUseCaseGroup(
        preview: Preview,
        imageCapture: ImageCapture,
        videoCapture: VideoCapture<Recorder>,
        viewportWidth: Int,
        viewportHeight: Int,
        targetRotation: Int,
    ): UseCaseGroup {
        val viewPort = ViewPort.Builder(
            Rational(viewportWidth.coerceAtLeast(1), viewportHeight.coerceAtLeast(1)),
            targetRotation,
        ).setScaleType(ViewPort.FILL_CENTER).build()
        return UseCaseGroup.Builder()
            .setViewPort(viewPort)
            .addUseCase(preview)
            .addUseCase(imageCapture)
            .addUseCase(videoCapture)
            .build()
    }

    fun supportsVideoStabilization(cameraInfo: CameraInfo): Boolean =
        Recorder.getVideoCapabilities(cameraInfo).isStabilizationSupported

    fun enableLowLightBoostWhenAvailable(camera: Camera, enabled: Boolean = true) {
        if (!camera.cameraInfo.isLowLightBoostSupported) return
        runCatching { camera.cameraControl.enableLowLightBoostAsync(enabled) }
    }
}
