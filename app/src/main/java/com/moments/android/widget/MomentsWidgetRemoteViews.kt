package com.moments.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.moments.android.MainActivity
import com.moments.android.R
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

// RemoteViews home widget — no Compose/Glance.

/**
 * ≡ MomentsWidgetEntryView — layouts small (creator) + medium (panel métricas).
 */
object MomentsWidgetRemoteViews {

    private data class MetricInfo(
        val id: String,
        val label: String,
        val value: Int,
        val deepLink: String,
    )

    private data class WidgetEvent(
        val text: String,
        val deepLink: String,
    )

    fun build(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ): RemoteViews {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val useMedium = isMedium(context, options)
        val snapshot = MomentsWidgetStore.load(context)
        val events = buildEvents(context, snapshot)
        val profileBitmap = if (!useMedium || heroMetric(snapshot) != null) {
            null
        } else {
            fetchProfileBitmap(snapshot.profileImageUrl)
        }

        return if (useMedium) {
            buildMedium(context, snapshot, events, profileBitmap)
        } else {
            buildSmall(context, snapshot)
        }
    }

    private fun isMedium(context: Context, options: Bundle): Boolean {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        // ~2 cells ≈ small; ≥ ~180dp → medium (paridad systemMedium).
        return minWidth >= 180
    }

    private fun buildSmall(context: Context, snapshot: MomentsWidgetStore.Snapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_moments_small)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_create_story_title))
        if (snapshot.unreadEchoes > 0) {
            views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_echoes_singular))
            views.setTextColor(
                R.id.widget_subtitle,
                ContextCompat.getColor(context, R.color.widget_brand_blue),
            )
        } else {
            views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_create_story_subtitle))
            views.setTextColor(
                R.id.widget_subtitle,
                ContextCompat.getColor(context, R.color.widget_secondary_text),
            )
        }
        views.setViewVisibility(
            R.id.widget_pulse_aura,
            if (snapshot.shouldPulse) View.VISIBLE else View.GONE,
        )
        val showBadge = snapshot.unreadEchoes > 0 || snapshot.unreadTags > 0
        views.setViewVisibility(R.id.widget_badge_dot, if (showBadge) View.VISIBLE else View.GONE)
        if (showBadge) {
            views.setImageViewResource(
                R.id.widget_badge_dot,
                if (snapshot.unreadEchoes > 0) {
                    R.drawable.widget_badge_dot
                } else {
                    R.drawable.widget_badge_dot_teal
                },
            )
        }
        views.setOnClickPendingIntent(
            R.id.widget_root,
            deepLinkPendingIntent(context, "moments://story/create", requestCode = 100),
        )
        return views
    }

    private fun buildMedium(
        context: Context,
        snapshot: MomentsWidgetStore.Snapshot,
        events: List<WidgetEvent>,
        profileBitmap: Bitmap?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_moments_medium)
        val allMetrics = listOf(
            MetricInfo("messages", "Chats", snapshot.unreadMessages, "moments://messages"),
            MetricInfo("echoes", "Echoes", snapshot.unreadEchoes, "moments://echoes"),
            MetricInfo("tags", "Tags", snapshot.unreadTags, "moments://notifications"),
            MetricInfo("notifications", "Notifs", snapshot.unreadNotifications, "moments://notifications"),
        ).sortedByDescending { it.value }

        val hero = allMetrics.firstOrNull { it.value > 0 }
        val secondary = allMetrics.filter { it.id != (hero?.id ?: "") }

        if (hero != null) {
            views.setViewVisibility(R.id.widget_hero_metric, View.VISIBLE)
            views.setViewVisibility(R.id.widget_profile_card, View.GONE)
            views.setTextViewText(R.id.widget_hero_value, hero.value.toString())
            views.setTextViewText(R.id.widget_hero_label, hero.label.uppercase())
            views.setOnClickPendingIntent(
                R.id.widget_hero_metric,
                deepLinkPendingIntent(context, hero.deepLink, 201),
            )
        } else {
            views.setViewVisibility(R.id.widget_hero_metric, View.GONE)
            views.setViewVisibility(R.id.widget_profile_card, View.VISIBLE)
            if (profileBitmap != null) {
                views.setImageViewBitmap(R.id.widget_profile_image, roundRectBitmap(profileBitmap, 16f, context))
                views.setViewVisibility(R.id.widget_profile_image, View.VISIBLE)
                views.setViewVisibility(R.id.widget_profile_placeholder, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_profile_image, View.GONE)
                views.setViewVisibility(R.id.widget_profile_placeholder, View.VISIBLE)
            }
        }

        bindSecondaryChip(views, R.id.widget_chip1, R.id.widget_chip1_value, R.id.widget_chip1_label, secondary.getOrNull(0), context, 210)
        bindSecondaryChip(views, R.id.widget_chip2, R.id.widget_chip2_value, R.id.widget_chip2_label, secondary.getOrNull(1), context, 211)
        bindSecondaryChip(views, R.id.widget_chip3, R.id.widget_chip3_value, R.id.widget_chip3_label, secondary.getOrNull(2), context, 212)
        bindSecondaryChip(views, R.id.widget_chip4, R.id.widget_chip4_value, R.id.widget_chip4_label, secondary.getOrNull(3), context, 213)

        if (events.isEmpty()) {
            views.setViewVisibility(R.id.widget_events_empty, View.VISIBLE)
            views.setViewVisibility(R.id.widget_event1, View.GONE)
            views.setViewVisibility(R.id.widget_event2, View.GONE)
            views.setTextViewText(R.id.widget_events_empty_text, context.getString(R.string.widget_all_caught_up))
        } else {
            views.setViewVisibility(R.id.widget_events_empty, View.GONE)
            bindEvent(views, R.id.widget_event1, R.id.widget_event1_text, events.getOrNull(0), context, 220)
            bindEvent(views, R.id.widget_event2, R.id.widget_event2_text, events.getOrNull(1), context, 221)
        }

        views.setTextViewText(R.id.widget_create_button, context.getString(R.string.widget_create_story_title))
        views.setOnClickPendingIntent(
            R.id.widget_create_button,
            deepLinkPendingIntent(context, "moments://story/create", 230),
        )
        return views
    }

    private fun bindSecondaryChip(
        views: RemoteViews,
        rootId: Int,
        valueId: Int,
        labelId: Int,
        metric: MetricInfo?,
        context: Context,
        requestCode: Int,
    ) {
        if (metric == null) {
            views.setViewVisibility(rootId, View.GONE)
            return
        }
        views.setViewVisibility(rootId, View.VISIBLE)
        views.setTextViewText(valueId, metric.value.toString())
        views.setTextViewText(labelId, metric.label.uppercase())
        views.setOnClickPendingIntent(rootId, deepLinkPendingIntent(context, metric.deepLink, requestCode))
    }

    private fun bindEvent(
        views: RemoteViews,
        rootId: Int,
        textId: Int,
        event: WidgetEvent?,
        context: Context,
        requestCode: Int,
    ) {
        if (event == null) {
            views.setViewVisibility(rootId, View.GONE)
            return
        }
        views.setViewVisibility(rootId, View.VISIBLE)
        views.setTextViewText(textId, event.text)
        views.setOnClickPendingIntent(rootId, deepLinkPendingIntent(context, event.deepLink, requestCode))
    }

    private fun heroMetric(snapshot: MomentsWidgetStore.Snapshot): MetricInfo? {
        return listOf(
            MetricInfo("messages", "Chats", snapshot.unreadMessages, "moments://messages"),
            MetricInfo("echoes", "Echoes", snapshot.unreadEchoes, "moments://echoes"),
            MetricInfo("tags", "Tags", snapshot.unreadTags, "moments://notifications"),
            MetricInfo("notifications", "Notifs", snapshot.unreadNotifications, "moments://notifications"),
        ).sortedByDescending { it.value }.firstOrNull { it.value > 0 }
    }

    private fun buildEvents(context: Context, snapshot: MomentsWidgetStore.Snapshot): List<WidgetEvent> {
        val events = mutableListOf<WidgetEvent>()
        if (snapshot.unreadEchoes > 0) {
            events += WidgetEvent(
                text = countText(
                    context,
                    snapshot.unreadEchoes,
                    R.string.widget_echoes_singular,
                    R.string.widget_echoes_plural,
                ),
                deepLink = "moments://echoes",
            )
        }
        if (snapshot.unreadTags > 0) {
            events += WidgetEvent(
                text = countText(
                    context,
                    snapshot.unreadTags,
                    R.string.widget_tags_singular,
                    R.string.widget_tags_plural,
                ),
                deepLink = "moments://notifications",
            )
        }
        if (snapshot.profileVisitsToday > 0) {
            events += WidgetEvent(
                text = countText(
                    context,
                    snapshot.profileVisitsToday,
                    R.string.widget_visits_singular,
                    R.string.widget_visits_plural,
                ),
                deepLink = "moments://profile/visits",
            )
        }
        if (snapshot.unreadMessages > 0) {
            events += WidgetEvent(
                text = countText(
                    context,
                    snapshot.unreadMessages,
                    R.string.widget_messages_singular,
                    R.string.widget_messages_plural,
                ),
                deepLink = "moments://messages",
            )
        }
        if (snapshot.unreadNotifications > 0) {
            events += WidgetEvent(
                text = countText(
                    context,
                    snapshot.unreadNotifications,
                    R.string.widget_notifications_singular,
                    R.string.widget_notifications_plural,
                ),
                deepLink = "moments://notifications",
            )
        }
        if (snapshot.newStoriesCount > 0) {
            events += WidgetEvent(
                text = countText(
                    context,
                    snapshot.newStoriesCount,
                    R.string.widget_stories_singular,
                    R.string.widget_stories_plural,
                ),
                deepLink = "moments://stories",
            )
        }
        if (snapshot.pendingMessageRequests > 0) {
            events += WidgetEvent(
                text = countText(
                    context,
                    snapshot.pendingMessageRequests,
                    R.string.widget_message_requests_singular,
                    R.string.widget_message_requests_plural,
                ),
                deepLink = "moments://messages",
            )
        }
        return events
    }

    private fun countText(context: Context, count: Int, singular: Int, plural: Int): String {
        return if (count == 1) {
            context.getString(singular)
        } else {
            context.getString(plural, count)
        }
    }

    private fun deepLinkPendingIntent(context: Context, uri: String, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun fetchProfileBitmap(urlString: String?): Bitmap? {
        if (urlString.isNullOrBlank()) return null
        return runCatching {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                instanceFollowRedirects = true
            }
            connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)
            }.also { connection.disconnect() }
        }.getOrNull()
    }

    private fun roundRectBitmap(source: Bitmap, cornerDp: Float, context: Context): Bitmap {
        val size = min(source.width, source.height)
        val x = (source.width - size) / 2
        val y = (source.height - size) / 2
        val square = Bitmap.createBitmap(source, max(0, x), max(0, y), size, size)
        val outSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            105f,
            context.resources.displayMetrics,
        ).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(square, outSize, outSize, true)
        val output = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            cornerDp,
            context.resources.displayMetrics,
        )
        val path = Path().apply {
            addRoundRect(0f, 0f, outSize.toFloat(), outSize.toFloat(), radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(path)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        if (square != source) square.recycle()
        if (scaled != square) scaled.recycle()
        return output
    }
}
