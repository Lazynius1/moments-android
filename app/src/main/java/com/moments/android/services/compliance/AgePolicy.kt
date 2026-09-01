package com.moments.android.services.compliance

import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.Locale

/** Edad mínima global de cuenta (16+) — paridad con iOS [MomentsAgePolicy]. */
object AgePolicy {
    const val DEFAULT_MINIMUM_ACCOUNT_AGE = 16
    const val INDIA_MINIMUM_ACCOUNT_AGE = 18
    const val PRIVACY_POLICY_VERSION = "2026-09-01-regional-age-policy"

    val currentCountryCode: String
        get() = Locale.getDefault().country.uppercase(Locale.ROOT).ifBlank { "ZZ" }

    fun minimumAccountAge(countryCode: String = currentCountryCode): Int =
        if (countryCode.equals("IN", ignoreCase = true)) INDIA_MINIMUM_ACCOUNT_AGE else DEFAULT_MINIMUM_ACCOUNT_AGE

    fun isEligibleForAccount(
        birthDate: Date,
        countryCode: String = currentCountryCode,
        referenceDate: Date = Date(),
    ): Boolean {
        return ageYears(birthDate, referenceDate) >= minimumAccountAge(countryCode)
    }

    fun defaultPickerBirthDate(countryCode: String = currentCountryCode, referenceDate: Date = Date()): Date {
        return shiftYears(referenceDate, -minimumAccountAge(countryCode))
    }

    fun maximumSelectableBirthDate(countryCode: String = currentCountryCode, referenceDate: Date = Date()): Date =
        defaultPickerBirthDate(countryCode, referenceDate)

    fun minimumSelectableBirthDate(referenceDate: Date = Date()): Date =
        shiftYears(referenceDate, -120)

    fun normalizedBirthDate(date: Date): Date = startOfDay(date)

    fun ageYears(birthDate: Date, referenceDate: Date = Date()): Int {
        val birth = startOfDay(birthDate)
        val reference = startOfDay(referenceDate)
        val birthCal = utcCalendar().apply { time = birth }
        val refCal = utcCalendar().apply { time = reference }
        var years = refCal.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
        if (refCal.get(Calendar.DAY_OF_YEAR) < birthCal.get(Calendar.DAY_OF_YEAR)) {
            years -= 1
        }
        return years
    }

    private fun shiftYears(date: Date, years: Int): Date {
        val cal = utcCalendar().apply { time = startOfDay(date) }
        cal.add(Calendar.YEAR, years)
        return cal.time
    }

    private fun startOfDay(date: Date): Date {
        val cal = utcCalendar().apply { time = date }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun utcCalendar(): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC"))
}
