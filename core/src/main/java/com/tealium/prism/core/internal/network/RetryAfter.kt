package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.network.HttpResponse
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val RETRY_AFTER_HEADER = "Retry-After"
private const val DATE_HEADER = "Date"

private const val IMF_FIXDATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss 'GMT'"

/**
 * Returns the delay, in milliseconds, requested by the `Retry-After` header of the [response], or
 * `null` when the header is missing or in an unsupported format.
 *
 * Supported formats are:
 *  - a non-negative integer number of seconds, e.g. `Retry-After: 120`
 *  - an IMF-fixdate, e.g. `Retry-After: Wed, 21 Oct 2026 07:28:00 GMT`. The delay is measured
 *  against the response's `Date` header when it is a valid IMF-fixdate, else against [now]; a date
 *  in the past is a delay of `0`.
 *
 * Negative integers and the obsolete RFC 850 and asctime date formats are not supported.
 * Header names are matched case-insensitively, and only the first value of a header is used.
 *
 * @param response The response whose headers are inspected.
 * @param now Returns the current time, in milliseconds since the epoch.
 */
internal fun retryAfterMillis(response: HttpResponse, now: () -> Long): Long? {
    val header = response.firstHeader(RETRY_AFTER_HEADER)?.trim() ?: return null

    val seconds = header.toLongOrNull()
    if (seconds != null) {
        return if (seconds >= 0) secondsToMillis(seconds) else null
    }

    val retryAt = parseImfFixdate(header) ?: return null
    val reference = response.firstHeader(DATE_HEADER)?.trim()?.let(::parseImfFixdate) ?: now()

    return (retryAt - reference).coerceAtLeast(0)
}

/**
 * Returns the first value of the header with the given [name], compared case-insensitively.
 *
 * [java.net.HttpURLConnection.getHeaderFields] keeps the server's casing for header names, and
 * maps the status line to a `null` key, so neither a plain lookup nor a non-null key is assumed.
 */
private fun HttpResponse.firstHeader(name: String): String? {
    for ((key: String?, values) in headers) {
        if (key != null && key.equals(name, ignoreCase = true)) {
            return values.firstOrNull()
        }
    }
    return null
}

private fun secondsToMillis(seconds: Long): Long =
    if (seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000

/**
 * Parses an IMF-fixdate (RFC 9110), returning its time in milliseconds since the epoch, or `null`
 * when [value] is not entirely a valid IMF-fixdate.
 *
 * A new [SimpleDateFormat] is created for every call, as it is not thread-safe.
 */
private fun parseImfFixdate(value: String): Long? {
    val format = SimpleDateFormat(IMF_FIXDATE_PATTERN, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
        isLenient = false
    }
    val position = ParsePosition(0)
    val date = format.parse(value, position)
    if (date == null || position.index != value.length) return null

    return date.time
}
