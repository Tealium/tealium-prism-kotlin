package com.tealium.core.internal.utils

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


/**
 * A class for formatting [Instant] objects into common [String] formats.
 */
interface DateFormatter {
    /**
     * Returns an ISO-8601 date format using UTC as the time zone
     *
     * e.g. '2011-12-03T10:15:30Z'
     */
    fun iso8601Utc(instant: Instant): String

    /**
     * Returns an ISO-8601 date format using device default as the time zone
     *
     * e.g. '2011-12-03T10:15:30'
     */
    fun iso8601Local(instant: Instant, timeZone: ZoneId): String

    /**
     * Returns an ISO-8601 date format using device default as the time zone, including the timezone
     * offset.
     *
     * e.g. '2011-12-03T10:15:30+01:00'
     */
    fun iso8601LocalWithOffset(instant: Instant, timeZone: ZoneId): String
}

object DateUtils : DateFormatter {

    override fun iso8601Utc(instant: Instant): String =
        DateTimeFormatter.ISO_INSTANT.format(instant)

    override fun iso8601Local(instant: Instant, timeZone: ZoneId): String =
        DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(instant.atZone(timeZone))

    override fun iso8601LocalWithOffset(instant: Instant, timeZone: ZoneId): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(timeZone))

}