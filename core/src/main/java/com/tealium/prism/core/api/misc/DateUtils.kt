package com.tealium.prism.core.api.misc

import android.os.Build
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs


/**
 * A class for formatting [Instant] and [Date] objects into common [String] formats.
 */
interface DateFormatter {
    /**
     * Returns an ISO-8601 date format using UTC as the time zone
     *
     * e.g. '2011-12-03T10:15:30Z'
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun iso8601Utc(instant: Instant): String

    /**
     * Returns an ISO-8601 date format using device default as the time zone
     *
     * e.g. '2011-12-03T10:15:30'
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun iso8601Local(instant: Instant, timeZone: ZoneId): String

    /**
     * Returns an ISO-8601 date format using device default as the time zone, including the timezone
     * offset.
     *
     * e.g. '2011-12-03T10:15:30+01:00'
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun iso8601LocalWithOffset(instant: Instant, timeZone: ZoneId): String

    /**
     * Returns the timezone offset in hours, as a [Double]. Where the integral part of the returned
     * [Double] is the whole number of hours, and the fractional part is the minutes.
     *
     * e.g.
     * 1    => +01:00
     * 5.75 => +05:45
     * -4.5 => -04:30
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun offsetInHours(instant: Instant, timeZone: ZoneId): Double

    /**
     * Returns an ISO-8601 date format using UTC as the time zone
     *
     * e.g. '2011-12-03T10:15:30Z'
     */
    fun iso8601Utc(date: Date): String

    /**
     * Returns an ISO-8601 date format using device default as the time zone
     *
     * e.g. '2011-12-03T10:15:30'
     */
    fun iso8601Local(date: Date, timeZone: TimeZone): String

    /**
     * Returns an ISO-8601 date format using device default as the time zone, including the timezone
     * offset.
     *
     * e.g. '2011-12-03T10:15:30+01:00'
     */
    fun iso8601LocalWithOffset(date: Date, timeZone: TimeZone): String

    /**
     * Returns the timezone offset in hours, as a [Double]. Where the integral part of the returned
     * [Double] is the whole number of hours, and the fractional part is the minutes.
     *
     * e.g.
     * 1    => +01:00
     * 5.75 => +05:45
     * -4.5 => -04:30
     */
    fun offsetInHours(date: Date, timeZone: TimeZone): Double
}

/**
 * Utility object for consistent time and date formatting across the SDK.
 */
object DateUtils : DateFormatter {

    private val utcIso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
    private val localDateFormatters = ConcurrentHashMap<TimeZone, SimpleDateFormat>()

    init {
        utcIso8601Format.timeZone = TimeZone.getTimeZone("UTC")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun iso8601Utc(instant: Instant): String =
        DateTimeFormatter.ISO_INSTANT.format(instant)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun iso8601Local(instant: Instant, timeZone: ZoneId): String =
        DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(instant.atZone(timeZone))

    @RequiresApi(Build.VERSION_CODES.O)
    override fun iso8601LocalWithOffset(instant: Instant, timeZone: ZoneId): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(timeZone))

    @RequiresApi(Build.VERSION_CODES.O)
    override fun offsetInHours(instant: Instant, timeZone: ZoneId): Double =
        timeZone.rules.getOffset(instant).totalSeconds / 3600.0

    override fun iso8601Utc(date: Date): String =
        utcIso8601Format.format(date)

    override fun iso8601Local(date: Date, timeZone: TimeZone): String {
        val localIso8601Format = localDateFormatters[timeZone]
            ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
                this.timeZone = timeZone
                localDateFormatters.putIfAbsent(timeZone, this)
            }

        return localIso8601Format.format(date)
    }

    override fun iso8601LocalWithOffset(date: Date, timeZone: TimeZone): String {
        val localDateTimeString = iso8601Local(date, timeZone)
        // API 23 doesn't support `XXX` pattern to get "+00:00" formatted offset
        // so need to manually format and append
        val offset = offsetInHours(date, timeZone)
        return localDateTimeString + formatOffsetHours(offset)
    }

    private fun formatOffsetHours(offsetHours: Double): String {
        val totalMinutes = (offsetHours * 60).toInt()
        val sign = if (totalMinutes >= 0) "+" else "-"

        val absoluteMinutes = abs(totalMinutes)
        val hours = absoluteMinutes / 60
        val minutes = absoluteMinutes % 60

        return "%s%02d:%02d".format(Locale.ROOT, sign, hours, minutes)
    }

    override fun offsetInHours(date: Date, timeZone: TimeZone): Double =
        timeZone.getOffset(date.time).toDouble().div(3_600_000)
}