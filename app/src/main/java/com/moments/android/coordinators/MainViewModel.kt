package com.moments.android.coordinators

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de badges de la barra de pestañas (feed nuevo / notificaciones sin leer).
 */
class MainViewModel private constructor() : DefaultLifecycleObserver {

    private val _hasNewFeedContent = MutableStateFlow(false)
    val hasNewFeedContent: StateFlow<Boolean> = _hasNewFeedContent.asStateFlow()

    private val _hasUnreadNotifications = MutableStateFlow(false)
    val hasUnreadNotifications: StateFlow<Boolean> = _hasUnreadNotifications.asStateFlow()

    private val _isAppActive = MutableStateFlow(true)
    val isAppActive: StateFlow<Boolean> = _isAppActive.asStateFlow()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        _isAppActive.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        _isAppActive.value = false
    }

    /** Llamar cuando lleguen nuevos momentos en el feed. */
    fun newFeedContentArrived() {
        if (!_isAppActive.value) {
            _hasNewFeedContent.value = true
        }
    }

    /** Llamar cuando el usuario visite el feed. */
    fun markFeedAsSeen() {
        _hasNewFeedContent.value = false
    }

    /** Llamar cuando lleguen nuevas notificaciones. */
    fun newNotificationsArrived() {
        if (!_isAppActive.value) {
            _hasUnreadNotifications.value = true
        }
    }

    /** Llamar cuando el usuario visite las notificaciones. */
    fun markNotificationsAsSeen() {
        _hasUnreadNotifications.value = false
    }

    companion object {
        val shared: MainViewModel by lazy { MainViewModel() }
    }
}
