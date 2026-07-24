package com.moments.android.views.messaging.media

import androidx.annotation.StringRes
import com.moments.android.R

/** Modo cíclico de envío de la cámara del chat. */
enum class ChatMediaSendMode(@StringRes val labelRes: Int, val innerIcon: ChatMediaSendModeIcon?) {
    VIEW_ONCE(R.string.chat_camera_mode_view_once, null),
    ALLOW_REPLAY(R.string.chat_camera_mode_allow_replay, ChatMediaSendModeIcon.PLAY),
    KEEP_IN_CHAT(R.string.chat_camera_mode_keep_in_chat, ChatMediaSendModeIcon.SAVE);

    fun next(): ChatMediaSendMode = when (this) {
        VIEW_ONCE -> ALLOW_REPLAY
        ALLOW_REPLAY -> KEEP_IN_CHAT
        KEEP_IN_CHAT -> VIEW_ONCE
    }
}

enum class ChatMediaSendModeIcon { PLAY, SAVE }
