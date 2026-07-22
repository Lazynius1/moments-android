package com.moments.android.utilities

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateFormat
import com.moments.android.R
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Formato centralizado de fechas, tiempos, conteos y distancias.
 */
object MomentsFormat {
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun ctx(): Context =
        appContext ?: error("MomentsFormat.initialize(context) required")

    enum class RelativeTimeStyle {
        /** Compact feed style: `5 min ago` / `hace 5 min`. */
        COMPACT,
        COMPACT_BARE,
        /** Locale-native relative wording, limited to a single unit. */
        CONVERSATIONAL,
    }

    enum class DateContext {
        FEED_TIMESTAMP,
        CHAT_SEPARATOR,
        STORY_ARCHIVE,
        DETAIL_HEADER,
        MESSAGE_ABSOLUTE,
        MONTH_YEAR_LABEL,
        MONTH_ABBREVIATED,
        DAY_MONTH_LABEL,
        WEEKDAY_NARROW,
        TIME_ONLY,
        INBOX_TIMESTAMP,
        MEDIUM_DATE,
        MEDIUM_DATE_TIME,
        LONG_DATE,
        FULL_DATE_TIME,
        NUMERIC_DATE,
        NUMERIC_DAY_MONTH,
    }

    enum class CountStyle {
        /** Profile stats: exact below 10K; abbreviated from 10K. */
        PROFILE_STAT,
        /** Likes, reactions, reels: abbreviated from 1K. */
        SOCIAL_METRIC,
        /** Always exact with thousands separator. */
        EXACT,
    }

    fun relativeTime(
        from: Date,
        style: RelativeTimeStyle = RelativeTimeStyle.COMPACT,
        relativeTo: Date = Date(),
    ): String {
        return when (style) {
            RelativeTimeStyle.COMPACT -> compactRelativeTime(from, relativeTo)
            RelativeTimeStyle.COMPACT_BARE -> compactBareRelativeTime(from, relativeTo)
            RelativeTimeStyle.CONVERSATIONAL -> singleUnitRelativeTime(from, relativeTo)
        }
    }

    fun smartDate(
        from: Date,
        context: DateContext,
        relativeTo: Date = Date(),
    ): String {
        val calendar = Calendar.getInstance()
        calendar.time = relativeTo
        val refCal = calendar.clone() as Calendar
        calendar.time = from
        val dateCal = calendar.clone() as Calendar

        return when (context) {
            DateContext.FEED_TIMESTAMP -> {
                val dayDiff = daysBetween(from, relativeTo)
                if (dayDiff >= 7 || dateCal.get(Calendar.YEAR) != refCal.get(Calendar.YEAR)) {
                    if (dateCal.get(Calendar.YEAR) == refCal.get(Calendar.YEAR)) {
                        formatDate(from, "MMM d")
                    } else {
                        formatDate(from, "MMM d, yyyy")
                    }
                } else {
                    compactRelativeTime(from, relativeTo)
                }
            }

            DateContext.CHAT_SEPARATOR -> {
                when {
                    isToday(from, relativeTo) -> ctx().getString(R.string.chat_date_today)
                    isYesterday(from, relativeTo) -> ctx().getString(R.string.chat_date_yesterday)
                    dateCal.get(Calendar.YEAR) == refCal.get(Calendar.YEAR) ->
                        formatDate(from, "MMM d")
                    else -> formatDate(from, "MMM d, yyyy")
                }
            }

            DateContext.STORY_ARCHIVE -> {
                when {
                    isToday(from, relativeTo) -> ctx().getString(R.string.archived_stories_today)
                    isYesterday(from, relativeTo) -> ctx().getString(R.string.archived_stories_yesterday)
                    dateCal.get(Calendar.YEAR) == refCal.get(Calendar.YEAR) ->
                        formatDate(from, "d MMMM")
                    else -> formatDate(from, "d MMMM yyyy")
                }
            }

            DateContext.DETAIL_HEADER -> {
                when {
                    isToday(from, relativeTo) -> formatTime(from)
                    dateCal.get(Calendar.YEAR) == refCal.get(Calendar.YEAR) ->
                        formatDateTime(from, "MMM d, HH:mm")
                    else -> formatDateTime(from, "MMM d, yyyy, HH:mm")
                }
            }

            DateContext.MESSAGE_ABSOLUTE -> {
                when {
                    isToday(from, relativeTo) -> formatTime(from)
                    isSameWeek(from, relativeTo) -> formatDateTime(from, "EEEE, HH:mm")
                    dateCal.get(Calendar.YEAR) == refCal.get(Calendar.YEAR) ->
                        formatDateTime(from, "MMM d, HH:mm")
                    else -> formatDateTime(from, "M/d/yy, HH:mm")
                }
            }

            DateContext.MONTH_YEAR_LABEL -> localizedDateString(from, "yMMM")
            DateContext.MONTH_ABBREVIATED -> localizedDateString(from, "MMM")
            DateContext.DAY_MONTH_LABEL -> localizedDateString(from, "dMMM")
            DateContext.WEEKDAY_NARROW -> narrowWeekdaySymbol(from)
            DateContext.TIME_ONLY -> formatTime(from)
            DateContext.INBOX_TIMESTAMP -> {
                when {
                    isToday(from, relativeTo) -> formatTime(from)
                    isYesterday(from, relativeTo) ->
                        ctx().getString(R.string.notifications_date_yesterday)
                    else -> formatDate(from, "M/d/yy")
                }
            }

            DateContext.MEDIUM_DATE -> formatDate(from, "MMM d, yyyy")
            DateContext.MEDIUM_DATE_TIME -> formatDateTime(from, "MMM d, yyyy, HH:mm")
            DateContext.LONG_DATE -> formatDate(from, "MMMM d, yyyy")
            DateContext.FULL_DATE_TIME -> formatDateTime(from, "EEEE, MMMM d, yyyy, HH:mm")
            DateContext.NUMERIC_DATE -> formatDate(from, "M/d/yy")
            DateContext.NUMERIC_DAY_MONTH -> localizedDateString(from, "Md")
        }
    }

    fun count(value: Int, style: CountStyle): String {
        return when (style) {
            CountStyle.EXACT -> formattedInteger(value)
            CountStyle.PROFILE_STAT -> {
                if (value < 10_000) formattedInteger(value)
                else abbreviatedCount(value, wholeThousandsThreshold = 10_000)
            }
            CountStyle.SOCIAL_METRIC -> {
                if (value < 1_000) value.toString()
                else abbreviatedCount(value, wholeThousandsThreshold = 10_000)
            }
        }
    }

    fun distance(meters: Double): String {
        val locale = Locale.getDefault()
        val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
        val unit = if (locale.country == "US") MeasureUnit.MILE else MeasureUnit.KILOMETER
        val value = if (locale.country == "US") meters / 1609.344 else meters / 1000.0
        return if (abs(meters) < 1000 && locale.country != "US") {
            format.format(Measure(meters, MeasureUnit.METER))
        } else {
            format.format(Measure(value, unit))
        }
    }

    // MARK: - Private

    private fun compactUnitString(from: Date, relativeTo: Date): String? {
        val c = Calendar.getInstance()
        c.time = relativeTo
        val ref = c.clone() as Calendar
        c.time = from
        val start = c.clone() as Calendar

        val years = fieldDiff(start, ref, Calendar.YEAR)
        if (years > 0) {
            val unit = ctx().getString(if (years == 1) R.string.time_unit_yr else R.string.time_unit_yrs)
            return compactValueAndUnit(years, unit)
        }
        val months = monthsBetween(from, relativeTo)
        if (months > 0) {
            val unit = ctx().getString(if (months == 1) R.string.time_unit_mo else R.string.time_unit_mos)
            return compactValueAndUnit(months, unit)
        }
        val weeks = weeksBetween(from, relativeTo)
        if (weeks > 0) {
            val unit = ctx().getString(R.string.time_unit_wk)
            return compactValueAndUnit(weeks, unit)
        }
        val days = daysBetween(from, relativeTo)
        if (days > 0) {
            val unit = ctx().getString(R.string.time_unit_d)
            return compactValueAndUnit(days, unit)
        }
        val hours = hoursBetween(from, relativeTo)
        if (hours > 0) {
            val unit = ctx().getString(R.string.time_unit_h)
            return compactValueAndUnit(hours, unit)
        }
        val minutes = minutesBetween(from, relativeTo)
        if (minutes > 0) {
            val unit = ctx().getString(R.string.time_unit_min)
            return compactValueAndUnit(minutes, unit)
        }
        return null
    }

    private fun compactRelativeTime(from: Date, relativeTo: Date): String {
        val timeString = compactUnitString(from, relativeTo)
            ?: return ctx().getString(R.string.time_now)
        return ctx().getString(R.string.time_ago, timeString)
    }

    private fun compactBareRelativeTime(from: Date, relativeTo: Date): String {
        if (weeksBetween(from, relativeTo) >= 1) {
            val refCal = Calendar.getInstance().apply { time = relativeTo }
            val dateCal = Calendar.getInstance().apply { time = from }
            return if (dateCal.get(Calendar.YEAR) == refCal.get(Calendar.YEAR)) {
                formatDate(from, "MMM d")
            } else {
                formatDate(from, "MMM d, yyyy")
            }
        }
        return compactUnitString(from, relativeTo) ?: ctx().getString(R.string.time_now)
    }

    private fun singleUnitRelativeTime(from: Date, relativeTo: Date): String {
        val formatter = RelativeDateTimeFormatter.getInstance(Locale.getDefault())
        val seconds = secondsBetween(from, relativeTo)
        return when {
            abs(seconds) < 10 -> formatter.format(
                RelativeDateTimeFormatter.Direction.PLAIN,
                RelativeDateTimeFormatter.AbsoluteUnit.NOW,
            )
            abs(seconds) < 60 -> formatter.format(
                abs(seconds).toDouble(),
                RelativeDateTimeFormatter.Direction.LAST,
                RelativeDateTimeFormatter.RelativeUnit.SECONDS,
            )
            abs(minutesBetween(from, relativeTo)) < 60 -> formatter.format(
                abs(minutesBetween(from, relativeTo)).toDouble(),
                RelativeDateTimeFormatter.Direction.LAST,
                RelativeDateTimeFormatter.RelativeUnit.MINUTES,
            )
            abs(hoursBetween(from, relativeTo)) < 24 -> formatter.format(
                hoursBetween(from, relativeTo).toDouble(),
                RelativeDateTimeFormatter.Direction.LAST,
                RelativeDateTimeFormatter.RelativeUnit.HOURS,
            )
            abs(daysBetween(from, relativeTo)) < 7 -> formatter.format(
                daysBetween(from, relativeTo).toDouble(),
                RelativeDateTimeFormatter.Direction.LAST,
                RelativeDateTimeFormatter.RelativeUnit.DAYS,
            )
            abs(weeksBetween(from, relativeTo)) < 5 -> formatter.format(
                weeksBetween(from, relativeTo).toDouble(),
                RelativeDateTimeFormatter.Direction.LAST,
                RelativeDateTimeFormatter.RelativeUnit.WEEKS,
            )
            abs(monthsBetween(from, relativeTo)) < 12 -> formatter.format(
                monthsBetween(from, relativeTo).toDouble(),
                RelativeDateTimeFormatter.Direction.LAST,
                RelativeDateTimeFormatter.RelativeUnit.MONTHS,
            )
            else -> formatter.format(
                fieldDiff(
                    Calendar.getInstance().apply { time = from },
                    Calendar.getInstance().apply { time = relativeTo },
                    Calendar.YEAR,
                ).toDouble(),
                RelativeDateTimeFormatter.Direction.LAST,
                RelativeDateTimeFormatter.RelativeUnit.YEARS,
            )
        }
    }

    private fun formattedInteger(value: Int): String {
        return NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)
    }

    private fun abbreviatedCount(count: Int, wholeThousandsThreshold: Int): String {
        val decimalSeparator = (NumberFormat.getNumberInstance(Locale.getDefault()) as java.text.DecimalFormat)
            .decimalFormatSymbols.decimalSeparator
        val numericValue = count.toDouble()

        if (count >= 1_000_000) {
            val millions = numericValue / 1_000_000
            if (count >= 10_000_000) return String.format(Locale.US, "%.0fM", millions)
            return trimTrailingZero(
                String.format(Locale.US, "%.1fM", millions),
                decimalSeparator,
                "M",
            )
        }

        val thousands = numericValue / 1_000
        if (count >= wholeThousandsThreshold) {
            return String.format(Locale.US, "%.0fK", thousands)
        }
        return trimTrailingZero(
            String.format(Locale.US, "%.1fK", thousands),
            decimalSeparator,
            "K",
        )
    }

    private fun trimTrailingZero(value: String, decimalSeparator: Char, suffix: String): String {
        var result = value
        if (decimalSeparator != '.') {
            result = result.replace('.', decimalSeparator)
        }
        val zeroSuffix = "$decimalSeparator" + "0$suffix"
        return result.replace(zeroSuffix, suffix)
    }

    private fun localizedDateString(date: Date, template: String): String {
        return android.text.format.DateFormat.format(
            DateFormat.getBestDateTimePattern(Locale.getDefault(), template),
            date,
        ).toString()
    }

    private fun narrowWeekdaySymbol(date: Date): String {
        val locale = Locale.getDefault()
        val symbols = java.text.DateFormatSymbols.getInstance(locale)
        val weekdays = symbols.shortWeekdays
        val cal = Calendar.getInstance().apply { time = date }
        val index = cal.get(Calendar.DAY_OF_WEEK)
        return weekdays.getOrElse(index) { "" }
    }

    private fun compactValueAndUnit(value: Int, unit: String): String {
        val separator = if (unit.length == 1) "" else " "
        return "$value$separator$unit"
    }

    private fun formatDate(date: Date, pattern: String): String =
        android.text.format.DateFormat.format(
            DateFormat.getBestDateTimePattern(Locale.getDefault(), pattern),
            date,
        ).toString()

    private fun formatTime(date: Date): String =
        DateFormat.getTimeFormat(ctx()).format(date)

    private fun formatDateTime(date: Date, pattern: String): String =
        formatDate(date, pattern)

    private fun isToday(date: Date, reference: Date): Boolean {
        val a = Calendar.getInstance().apply { time = date }
        val b = Calendar.getInstance().apply { time = reference }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(date: Date, reference: Date): Boolean {
        val b = Calendar.getInstance().apply { time = reference }
        b.add(Calendar.DAY_OF_YEAR, -1)
        val a = Calendar.getInstance().apply { time = date }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun isSameWeek(date: Date, reference: Date): Boolean {
        val a = Calendar.getInstance().apply { time = date }
        val b = Calendar.getInstance().apply { time = reference }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.WEEK_OF_YEAR) == b.get(Calendar.WEEK_OF_YEAR)
    }

    private fun secondsBetween(from: Date, to: Date): Long =
        (to.time - from.time) / 1000

    private fun minutesBetween(from: Date, to: Date): Int =
        (secondsBetween(from, to) / 60).toInt()

    private fun hoursBetween(from: Date, to: Date): Int =
        (secondsBetween(from, to) / 3600).toInt()

    private fun daysBetween(from: Date, to: Date): Int {
        val a = Calendar.getInstance().apply {
            time = from
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val b = Calendar.getInstance().apply {
            time = to
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return ((b.timeInMillis - a.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
    }

    private fun weeksBetween(from: Date, to: Date): Int = daysBetween(from, to) / 7

    private fun monthsBetween(from: Date, to: Date): Int {
        val a = Calendar.getInstance().apply { time = from }
        val b = Calendar.getInstance().apply { time = to }
        return (b.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 12 +
            (b.get(Calendar.MONTH) - a.get(Calendar.MONTH))
    }

    private fun fieldDiff(start: Calendar, end: Calendar, field: Int): Int =
        end.get(field) - start.get(field)
}
