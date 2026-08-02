package com.moments.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.moments.android.MomentsApplication
import com.moments.android.models.AppUser

/**
 * SharedPreferences del home widget Android (`com.moments.android`).
 * iOS usa App Group `group.com.glowsyapp` + WidgetKit kind `GlowsyWidgetExtension`;
 * aquí las mismas *keys* de datos, pero prefs/provider propios de Moments.
 */
object MomentsWidgetStore {

    const val PREFS = "com.moments.android.widget"

    const val KEY_PROFILE_VISITS_TODAY = "widget_profile_visits_today"
    const val KEY_UNREAD_MESSAGES = "widget_unread_messages"
    const val KEY_UNREAD_NOTIFICATIONS = "widget_unread_notifications"
    const val KEY_NEW_STORIES_COUNT = "widget_new_stories_count"
    const val KEY_PENDING_MESSAGE_REQUESTS = "widget_pending_message_requests"
    const val KEY_UNREAD_ECHOES = "widget_unread_echoes"
    const val KEY_UNREAD_TAGS = "widget_unread_tags"
    const val KEY_USER_NAME = "widget_user_name"
    const val KEY_USER_PROFILE_IMAGE = "widget_user_profile_image"

    data class Snapshot(
        val profileVisitsToday: Int,
        val unreadMessages: Int,
        val unreadNotifications: Int,
        val newStoriesCount: Int,
        val pendingMessageRequests: Int,
        val unreadEchoes: Int,
        val unreadTags: Int,
        val profileImageUrl: String?,
    ) {
        val shouldPulse: Boolean get() = unreadEchoes > 0
    }

    fun prefs(context: Context): android.content.SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Snapshot {
        val p = prefs(context)
        return Snapshot(
            profileVisitsToday = p.getInt(KEY_PROFILE_VISITS_TODAY, 0),
            unreadMessages = p.getInt(KEY_UNREAD_MESSAGES, 0),
            unreadNotifications = p.getInt(KEY_UNREAD_NOTIFICATIONS, 0),
            newStoriesCount = p.getInt(KEY_NEW_STORIES_COUNT, 0),
            pendingMessageRequests = p.getInt(KEY_PENDING_MESSAGE_REQUESTS, 0),
            unreadEchoes = p.getInt(KEY_UNREAD_ECHOES, 0),
            unreadTags = p.getInt(KEY_UNREAD_TAGS, 0),
            profileImageUrl = p.getString(KEY_USER_PROFILE_IMAGE, null),
        )
    }

    fun putInt(key: String, value: Int, context: Context? = appContextOrNull(), reload: Boolean = true) {
        val ctx = context ?: return
        prefs(ctx).edit().putInt(key, value).apply()
        if (reload) reloadWidgets(ctx)
    }

    fun putBadgeCounts(
        unreadNotifications: Int? = null,
        unreadMessages: Int? = null,
        unreadEchoes: Int? = null,
        unreadTags: Int? = null,
        context: Context? = appContextOrNull(),
        reload: Boolean = true,
    ) {
        val ctx = context ?: return
        prefs(ctx).edit().apply {
            unreadNotifications?.let { putInt(KEY_UNREAD_NOTIFICATIONS, it) }
            unreadMessages?.let { putInt(KEY_UNREAD_MESSAGES, it) }
            unreadEchoes?.let { putInt(KEY_UNREAD_ECHOES, it) }
            unreadTags?.let { putInt(KEY_UNREAD_TAGS, it) }
        }.apply()
        if (reload) reloadWidgets(ctx)
    }

    fun syncProfileData(user: AppUser?, context: Context? = appContextOrNull()) {
        if (user == null) return
        val ctx = context ?: return
        prefs(ctx).edit()
            .putString(KEY_USER_NAME, user.username)
            .putString(KEY_USER_PROFILE_IMAGE, user.profileImagePath)
            .apply()
        reloadWidgets(ctx)
    }

    fun clearBadgeCounts(context: Context? = appContextOrNull()) {
        val ctx = context ?: return
        prefs(ctx).edit()
            .putInt(KEY_UNREAD_NOTIFICATIONS, 0)
            .putInt(KEY_UNREAD_MESSAGES, 0)
            .putInt(KEY_UNREAD_ECHOES, 0)
            .putInt(KEY_UNREAD_TAGS, 0)
            .apply()
        reloadWidgets(ctx)
    }

    fun reloadWidgets(context: Context? = appContextOrNull()) {
        val appContext = context?.applicationContext ?: return
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, MomentsWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val intent = Intent(appContext, MomentsWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        appContext.sendBroadcast(intent)
    }

    private fun appContextOrNull(): Context? = MomentsApplication.instance
}
