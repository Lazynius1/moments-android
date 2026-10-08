package com.moments.android.utilities

import android.content.Context
import android.icu.text.DateFormat as IcuDateFormat
import android.icu.text.DisplayContext
import android.icu.text.MeasureFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.TimeZone as IcuTimeZone
import android.icu.util.ULocale
import android.text.format.DateFormat
import com.moments.android.R
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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

    /** Para formatters (p. ej. tamaño de fichero) en modelos sin Context de UI. */
    fun requireContext(): Context = ctx()

    enum class RelativeTimeStyle {
        /**
         * Corto sin «hace» (comentarios, chats, historias, notificaciones, Echoes, mapas):
         * `ahora`, `5 min`, `3 h`, `2 d`, `4 sem`, `1 a` (≡ iOS). Unidades y plurales del sistema.
         * En inglés: `now`, `5m`, `3h`, `2d`, `2w`, `1y` (ver [compactWidth]).
         */
        COMPACT,
        /**
         * Relativo largo del sistema con una sola unidad (pasado o futuro): `hace 3 horas`.
         * Sesiones, Nova y pantalla de actividad.
         */
        CONVERSATIONAL,
    }

    enum class DateContext {
        /** Feed y detalle del post: `ahora` · `hace 5 minutos` · `12 de septiembre` (≡ iOS). */
        FEED_TIMESTAMP,
        /** Separador del chat: `14:32` · `Ayer, 14:32` · `lun 14:32` · `12 sept 14:32` (≡ iOS). */
        CHAT_SEPARATOR,
        STORY_ARCHIVE,
        DETAIL_HEADER,
        MESSAGE_ABSOLUTE,
        MONTH_YEAR_LABEL,
        MONTH_ABBREVIATED,
        DAY_MONTH_LABEL,
        /** `miércoles, 8 de octubre` (sin año). */
        WEEKDAY_DAY_MONTH,
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
            RelativeTimeStyle.COMPACT -> compactElapsed(from, relativeTo)
            RelativeTimeStyle.CONVERSATIONAL -> singleUnitRelativeTime(from, relativeTo)
        }
    }

    fun smartDate(
        from: Date,
        context: DateContext,
        relativeTo: Date = Date(),
    ): String {
        val sameYear = isSameYear(from, relativeTo)

        return when (context) {
            DateContext.FEED_TIMESTAMP -> feedTimestamp(from, relativeTo)

            DateContext.CHAT_SEPARATOR -> {
                // Por días de calendario (no transcurrido): agrupa mensajes por día ≡ iOS.
                val dayDiff = calendarDaysBetween(from, relativeTo)
                when {
                    dayDiff <= 0 -> formatTime(from)
                    dayDiff == 1 -> relativeFormatter(sentenceStart = true).let { formatter ->
                        formatter.combineDateAndTime(
                            formatter.format(
                                RelativeDateTimeFormatter.Direction.LAST,
                                RelativeDateTimeFormatter.AbsoluteUnit.DAY,
                            ),
                            formatTime(from),
                        )
                    }
                    dayDiff < 7 -> formatSkeleton(from, "EEE", withTime = true)
                    sameYear -> formatSkeleton(from, "MMMd", withTime = true)
                    else -> formatSkeleton(from, "yMMMd", withTime = true)
                }
            }

            DateContext.STORY_ARCHIVE -> {
                when {
                    isToday(from, relativeTo) -> ctx().getString(R.string.archived_stories_today)
                    isYesterday(from, relativeTo) -> ctx().getString(R.string.archived_stories_yesterday)
                    sameYear -> formatSkeleton(from, "MMMMd")
                    else -> formatSkeleton(from, "yMMMMd")
                }
            }

            DateContext.DETAIL_HEADER -> {
                when {
                    isToday(from, relativeTo) -> formatTime(from)
                    sameYear -> formatSkeleton(from, "MMMd", withTime = true)
                    else -> formatSkeleton(from, "yMMMd", withTime = true)
                }
            }

            DateContext.MESSAGE_ABSOLUTE -> {
                when {
                    isToday(from, relativeTo) -> formatTime(from)
                    isSameWeek(from, relativeTo) -> formatSkeleton(from, "EEEE", withTime = true)
                    sameYear -> formatSkeleton(from, "MMMd", withTime = true)
                    else -> formatSkeleton(from, "yyMd", withTime = true)
                }
            }

            DateContext.MONTH_YEAR_LABEL -> formatSkeleton(from, "yMMM")
            DateContext.MONTH_ABBREVIATED -> formatSkeleton(from, "MMM")
            DateContext.DAY_MONTH_LABEL -> formatSkeleton(from, "dMMM")
            DateContext.WEEKDAY_DAY_MONTH -> formatSkeleton(from, "MMMMEEEEd")
            DateContext.WEEKDAY_NARROW -> narrowWeekdaySymbol(from)
            DateContext.TIME_ONLY -> formatTime(from)
            DateContext.INBOX_TIMESTAMP -> {
                when {
                    isToday(from, relativeTo) -> formatTime(from)
                    isYesterday(from, relativeTo) ->
                        ctx().getString(R.string.notifications_date_yesterday)
                    else -> formatSkeleton(from, "yyMd")
                }
            }

            DateContext.MEDIUM_DATE -> formatSkeleton(from, "yMMMd")
            DateContext.MEDIUM_DATE_TIME -> formatSkeleton(from, "yMMMd", withTime = true)
            DateContext.LONG_DATE -> formatSkeleton(from, "yMMMMd")
            DateContext.FULL_DATE_TIME -> formatSkeleton(from, "yMMMMEEEEd", withTime = true)
            DateContext.NUMERIC_DATE -> formatSkeleton(from, "yyMd")
            DateContext.NUMERIC_DAY_MONTH -> formatSkeleton(from, "Md")
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

    // Tiempo transcurrido (no calendario) para min/h/d/sem; años = días / 365 ≡ iOS.
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L

    private fun nowString(): String =
        relativeFormatter().format(
            RelativeDateTimeFormatter.Direction.PLAIN,
            RelativeDateTimeFormatter.AbsoluteUnit.NOW,
        )

    /** Feed: < 1 min `ahora`; < 7 d relativo largo; después fecha `d MMMM` (+ año si no es el actual). */
    private fun feedTimestamp(from: Date, relativeTo: Date): String {
        val seconds = secondsBetween(from, relativeTo)
        if (seconds < SECONDS_PER_MINUTE) return nowString()
        val days = seconds / SECONDS_PER_DAY
        if (days >= 7) {
            return formatSkeleton(from, if (isSameYear(from, relativeTo)) "MMMMd" else "yMMMMd")
        }
        val (value, unit) = when {
            seconds < SECONDS_PER_HOUR ->
                seconds / SECONDS_PER_MINUTE to RelativeDateTimeFormatter.RelativeUnit.MINUTES
            seconds < SECONDS_PER_DAY ->
                seconds / SECONDS_PER_HOUR to RelativeDateTimeFormatter.RelativeUnit.HOURS
            else -> days to RelativeDateTimeFormatter.RelativeUnit.DAYS
        }
        return relativeFormatter().format(
            value.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            unit,
        )
    }

    /** Corto sin «hace»: `ahora`, `5 min`, `3 h`, `2 d`, `4 sem`; años solo desde 365 días. */
    private fun compactElapsed(from: Date, relativeTo: Date): String {
        val seconds = secondsBetween(from, relativeTo)
        if (seconds < SECONDS_PER_MINUTE) return nowString()
        val days = seconds / SECONDS_PER_DAY
        val measure = when {
            seconds < SECONDS_PER_HOUR -> Measure(seconds / SECONDS_PER_MINUTE, MeasureUnit.MINUTE)
            seconds < SECONDS_PER_DAY -> Measure(seconds / SECONDS_PER_HOUR, MeasureUnit.HOUR)
            days < 7 -> Measure(days, MeasureUnit.DAY)
            days < 365 -> Measure(days / 7, MeasureUnit.WEEK)
            else -> Measure(days / 365, MeasureUnit.YEAR)
        }
        return MeasureFormat.getInstance(Locale.getDefault(), compactWidth()).format(measure)
    }

    /**
     * Ancho del corto ≡ iOS. Inglés usa NARROW (`5m`, `3h`, `2d`, `2w`, `1y`): en SHORT
     * ICU daría `3 hr`, `2 days`, `2 wks`. El resto usa SHORT, que ya da la forma corta
     * habitual del idioma (`3 h`, `2 d`, `2 sem`, `3時間`…); NARROW en otros idiomas
     * pega número y unidad o usa símbolos poco legibles (p. ej. es `3h`, `2sem`).
     * Japonés, chino y coreano también usan NARROW: es la forma sin espacio (`5分`), ≡ iOS.
     */
    private fun compactWidth(): MeasureFormat.FormatWidth =
        if (Locale.getDefault().language in setOf("en", "ja", "zh", "ko")) {
            MeasureFormat.FormatWidth.NARROW
        } else {
            MeasureFormat.FormatWidth.SHORT
        }

    /** `true` si el corto de [from] sería `ahora` (< 1 min), para frases como «Visto ahora». */
    fun isCompactNow(from: Date, relativeTo: Date = Date()): Boolean =
        secondsBetween(from, relativeTo) < SECONDS_PER_MINUTE

    /**
     * Recibo de lectura en el menú del mensaje (≡ iOS): `Visto hoy, 14:32` ·
     * `Visto ayer, 23:00` · `Visto el lun, 14:32` · `Visto el 24 sept, 14:32`
     * (con año si no es el actual). Días de calendario; hora respetando 12/24 h.
     */
    fun seenReceipt(from: Date, relativeTo: Date = Date()): String {
        val time = formatTime(from)
        val dayDiff = calendarDaysBetween(from, relativeTo)
        return when {
            dayDiff <= 0 -> ctx().getString(R.string.chat_seen_at_today, time)
            dayDiff == 1 -> ctx().getString(R.string.chat_seen_at_yesterday, time)
            else -> {
                val skeleton = when {
                    dayDiff < 7 -> "EEE"
                    isSameYear(from, relativeTo) -> "MMMd"
                    else -> "yMMMd"
                }
                ctx().getString(R.string.chat_seen_at_date, formatSkeleton(from, skeleton, withTime = true))
            }
        }
    }

    private fun singleUnitRelativeTime(from: Date, relativeTo: Date): String {
        val formatter = relativeFormatter()
        val signed = secondsBetween(from, relativeTo)
        val seconds = abs(signed)
        // Menos de un minuto → «ahora» (≡ iOS longElapsedTime).
        if (seconds < SECONDS_PER_MINUTE) {
            return formatter.format(
                RelativeDateTimeFormatter.Direction.PLAIN,
                RelativeDateTimeFormatter.AbsoluteUnit.NOW,
            )
        }
        // Pasado → «hace…»; futuro (p. ej. caducidad) → «dentro de…».
        val direction = if (signed >= 0) {
            RelativeDateTimeFormatter.Direction.LAST
        } else {
            RelativeDateTimeFormatter.Direction.NEXT
        }
        val days = seconds / SECONDS_PER_DAY
        val (value, unit) = when {
            seconds < SECONDS_PER_MINUTE -> seconds to RelativeDateTimeFormatter.RelativeUnit.SECONDS
            seconds < SECONDS_PER_HOUR ->
                seconds / SECONDS_PER_MINUTE to RelativeDateTimeFormatter.RelativeUnit.MINUTES
            seconds < SECONDS_PER_DAY ->
                seconds / SECONDS_PER_HOUR to RelativeDateTimeFormatter.RelativeUnit.HOURS
            days < 7 -> days to RelativeDateTimeFormatter.RelativeUnit.DAYS
            days < 35 -> days / 7 to RelativeDateTimeFormatter.RelativeUnit.WEEKS
            days < 365 -> maxOf(1L, days / 30) to RelativeDateTimeFormatter.RelativeUnit.MONTHS
            else -> days / 365 to RelativeDateTimeFormatter.RelativeUnit.YEARS
        }
        return formatter.format(value.toDouble(), direction, unit)
    }

    private fun relativeFormatter(sentenceStart: Boolean = false): RelativeDateTimeFormatter =
        RelativeDateTimeFormatter.getInstance(
            ULocale.forLocale(Locale.getDefault()),
            null,
            RelativeDateTimeFormatter.Style.LONG,
            if (sentenceStart) {
                DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE
            } else {
                DisplayContext.CAPITALIZATION_NONE
            },
        )

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

    private fun narrowWeekdaySymbol(date: Date): String {
        val locale = Locale.getDefault()
        val symbols = java.text.DateFormatSymbols.getInstance(locale)
        val weekdays = symbols.shortWeekdays
        val cal = Calendar.getInstance().apply { time = date }
        val index = cal.get(Calendar.DAY_OF_WEEK)
        return weekdays.getOrElse(index) { "" }
    }

    private val skeletonFormats = ConcurrentHashMap<String, IcuDateFormat>()

    /**
     * Fecha localizada por esqueleto (orden, separadores y formas del idioma).
     * [withTime] añade la hora respetando el ajuste 12/24 h del sistema.
     */
    private fun formatSkeleton(date: Date, skeleton: String, withTime: Boolean = false): String {
        val fullSkeleton = if (withTime) skeleton + timeSkeleton() else skeleton
        val locale = Locale.getDefault()
        val cached = skeletonFormats.getOrPut("${locale.toLanguageTag()}|$fullSkeleton") {
            IcuDateFormat.getInstanceForSkeleton(fullSkeleton, locale)
        }
        // Copia por llamada: DateFormat no es thread-safe y la zona horaria puede cambiar.
        val format = cached.clone() as IcuDateFormat
        format.timeZone = IcuTimeZone.getDefault()
        return format.format(date)
    }

    private fun timeSkeleton(): String =
        if (DateFormat.is24HourFormat(ctx())) "Hmm" else "hmm"

    private fun formatTime(date: Date): String =
        DateFormat.getTimeFormat(ctx()).format(date)

    private fun isSameYear(date: Date, reference: Date): Boolean {
        val a = Calendar.getInstance().apply { time = date }
        val b = Calendar.getInstance().apply { time = reference }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
    }

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

    /** Días de calendario entre medianoches (solo separadores del chat). */
    private fun calendarDaysBetween(from: Date, to: Date): Int {
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
        // Redondeo: los días con cambio de hora duran 23 o 25 h.
        return Math.round((b.timeInMillis - a.timeInMillis) / (24.0 * 60 * 60 * 1000)).toInt()
    }
}
