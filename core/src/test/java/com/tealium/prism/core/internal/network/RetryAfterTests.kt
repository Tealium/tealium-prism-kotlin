package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.network.HttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RetryAfterTests {

    private var nowCalls = 0
    private var currentTime = RETRY_DATE_MILLIS

    private val now: () -> Long = {
        nowCalls++
        currentTime
    }

    // region seconds

    @Test
    fun retryAfterMillis_Returns_Seconds_In_Millis_When_Header_Is_Integer() {
        assertEquals(7_000L, retryAfterMillis(response("Retry-After" to "7"), now))
    }

    @Test
    fun retryAfterMillis_Trims_Whitespace_When_Header_Is_Integer() {
        assertEquals(7_000L, retryAfterMillis(response("Retry-After" to " 7 "), now))
        assertEquals(7_000L, retryAfterMillis(response("Retry-After" to "\t7\t"), now))
    }

    @Test
    fun retryAfterMillis_Returns_Zero_When_Header_Is_Zero() {
        assertEquals(0L, retryAfterMillis(response("Retry-After" to "0"), now))
    }

    @Test
    fun retryAfterMillis_Returns_Null_When_Header_Is_Negative() {
        assertNull(retryAfterMillis(response("Retry-After" to "-5"), now))
    }

    @Test
    fun retryAfterMillis_Saturates_When_Seconds_Overflow_Millis() {
        val seconds = Long.MAX_VALUE / 10

        assertEquals(Long.MAX_VALUE, retryAfterMillis(response("Retry-After" to "$seconds"), now))
    }

    @Test
    fun retryAfterMillis_Does_Not_Call_Now_When_Header_Is_Integer() {
        retryAfterMillis(response("Retry-After" to "7"), now)

        assertEquals(0, nowCalls)
    }

    // endregion

    // region HTTP date

    @Test
    fun retryAfterMillis_Measures_Against_Date_Header_When_Header_Is_Http_Date() {
        val response = response("Retry-After" to RETRY_DATE_HEADER, "Date" to httpDate(-30))

        assertEquals(30_000L, retryAfterMillis(response, now))
    }

    @Test
    fun retryAfterMillis_Measures_Against_Now_When_Date_Header_Is_Missing() {
        currentTime = RETRY_DATE_MILLIS - 12_000

        assertEquals(12_000L, retryAfterMillis(response("Retry-After" to RETRY_DATE_HEADER), now))
    }

    @Test
    fun retryAfterMillis_Prefers_Date_Header_When_Device_Clock_Is_Skewed() {
        currentTime = RETRY_DATE_MILLIS - 5 * 3_600_000
        val response = response("Retry-After" to RETRY_DATE_HEADER, "Date" to httpDate(-30))

        assertEquals(30_000L, retryAfterMillis(response, now))
        assertEquals(0, nowCalls)
    }

    @Test
    fun retryAfterMillis_Measures_Against_Now_When_Date_Header_Is_Invalid() {
        currentTime = RETRY_DATE_MILLIS - 12_000

        for (date in listOf("not a date", "", "Wed Oct 21 07:28:00 2026")) {
            val response = response("Retry-After" to RETRY_DATE_HEADER, "Date" to date)
            assertEquals("Date: $date", 12_000L, retryAfterMillis(response, now))
        }
    }

    @Test
    fun retryAfterMillis_Returns_Zero_When_Http_Date_Is_In_The_Past() {
        currentTime = RETRY_DATE_MILLIS + 60_000

        assertEquals(0L, retryAfterMillis(response("Retry-After" to RETRY_DATE_HEADER), now))
        assertEquals(
            0L,
            retryAfterMillis(response("Retry-After" to RETRY_DATE_HEADER, "Date" to httpDate(60)), now)
        )
    }

    @Test
    fun retryAfterMillis_Trims_Whitespace_When_Header_Is_Http_Date() {
        val response = response("Retry-After" to " $RETRY_DATE_HEADER ", "Date" to " ${httpDate(-30)} ")

        assertEquals(30_000L, retryAfterMillis(response, now))
    }

    @Test
    fun retryAfterMillis_Returns_Null_When_Header_Is_Obsolete_Date_Format() {
        currentTime = RETRY_DATE_MILLIS - 30_000

        for (value in listOf("Wednesday, 21-Oct-26 07:28:00 GMT", "Wed Oct 21 07:28:00 2026")) {
            assertNull("Retry-After: $value", retryAfterMillis(response("Retry-After" to value), now))
        }
    }

    @Test
    fun retryAfterMillis_Returns_Null_When_Header_Is_Garbage() {
        val values = listOf(
            "soon",
            "",
            "   ",
            "1.5",
            "7s",
            "Wed, 99 Foo 2026 07:28:00 GMT",
            "Wed, 21 Oct 2026 25:28:00 GMT",
            "Wed, 21 Oct 2026 07:28:00 PST",
            "$RETRY_DATE_HEADER trailing",
            "9223372036854775808",
        )
        for (value in values) {
            assertNull("Retry-After: '$value'", retryAfterMillis(response("Retry-After" to value), now))
        }
    }

    // endregion

    // region header lookup

    @Test
    fun retryAfterMillis_Returns_Null_When_Header_Is_Missing() {
        assertNull(retryAfterMillis(response(), now))
        assertNull(retryAfterMillis(response("Date" to httpDate(0)), now))
    }

    @Test
    fun retryAfterMillis_Returns_Null_When_Header_Has_No_Values() {
        val response = responseWithHeaders(mapOf("Retry-After" to emptyList()))

        assertNull(retryAfterMillis(response, now))
    }

    @Test
    fun retryAfterMillis_Matches_Header_Names_Case_Insensitively() {
        for (name in listOf("Retry-After", "retry-after", "RETRY-AFTER", "rEtRy-AfTeR")) {
            assertEquals("header $name", 7_000L, retryAfterMillis(response(name to "7"), now))
        }
    }

    @Test
    fun retryAfterMillis_Matches_Date_Header_Case_Insensitively() {
        currentTime = 0
        for (name in listOf("Date", "date", "DATE")) {
            val response = response("Retry-After" to RETRY_DATE_HEADER, name to httpDate(-30))
            assertEquals("header $name", 30_000L, retryAfterMillis(response, now))
        }
    }

    @Test
    fun retryAfterMillis_Uses_First_Value_When_Header_Has_Multiple_Values() {
        val response = responseWithHeaders(mapOf("Retry-After" to listOf("7", "20")))

        assertEquals(7_000L, retryAfterMillis(response, now))
    }

    @Test
    fun retryAfterMillis_Ignores_Null_Header_Key_When_Status_Line_Is_Present() {
        // HttpURLConnection maps the status line to a null key
        val headers = mapOf<String?, List<String>>(
            null to listOf("HTTP/1.1 503 Service Unavailable"),
            "Retry-After" to listOf("7")
        )

        @Suppress("UNCHECKED_CAST")
        val response = responseWithHeaders(headers as Map<String, List<String>>)

        assertEquals(7_000L, retryAfterMillis(response, now))
    }

    // endregion

    private fun response(vararg headers: Pair<String, String>): HttpResponse =
        responseWithHeaders(headers.associate { (name, value) -> name to listOf(value) })

    private fun responseWithHeaders(headers: Map<String, List<String>>): HttpResponse =
        HttpResponse(localhost, 503, "", headers)

    /**
     * Returns the IMF-fixdate of [RETRY_DATE_MILLIS] shifted by [secondsFromRetryDate].
     */
    private fun httpDate(secondsFromRetryDate: Long): String {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        return format.format(Date(RETRY_DATE_MILLIS + secondsFromRetryDate * 1000))
    }

    private companion object {
        const val RETRY_DATE_MILLIS = 1_792_567_680_000L
        const val RETRY_DATE_HEADER = "Wed, 21 Oct 2026 07:28:00 GMT"
    }
}
