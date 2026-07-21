package com.moments.android.notifications

import com.moments.android.models.MomentsNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Stub mínimo de [NotificationPresentationCoordinator] (iOS) para que Services/Activity
 * pueda emitir banners in-app. La carpeta Notifications completa se portará después;
 * hasta entonces la UI puede colectar [presentations].
 */
enum class NotificationPresentationSource {
    PUSH,
    FIRESTORE,
    LOCAL,
}

object NotificationPresentationCoordinator {
    private val _presentations = MutableSharedFlow<Pair<MomentsNotification, NotificationPresentationSource>>(
        extraBufferCapacity = 16,
    )
    val presentations: SharedFlow<Pair<MomentsNotification, NotificationPresentationSource>> =
        _presentations.asSharedFlow()

    fun present(
        notification: MomentsNotification,
        source: NotificationPresentationSource,
    ) {
        _presentations.tryEmit(notification to source)
    }
}
