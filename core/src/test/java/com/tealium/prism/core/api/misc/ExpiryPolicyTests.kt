package com.tealium.prism.core.api.misc

import com.tealium.prism.core.api.misc.TimeFrameUtils.minutes
import com.tealium.prism.core.api.persistence.Expiry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class ExpiryPolicyTests {

    @Test
    fun resolve_Returns_SessionExpiry() {
        val policy = ExpiryPolicy.SESSION
        val expiry = policy.resolve()
        assertEquals(Expiry.SESSION, expiry)
    }

    @Test
    fun resolve_Returns_ForeverExpiry() {
        val policy = ExpiryPolicy.FOREVER
        val expiry = policy.resolve()
        assertEquals(Expiry.FOREVER, expiry)
    }

    @Test
    fun resolve_Returns_UntilRestartExpiry() {
        val policy = ExpiryPolicy.UNTIL_RESTART
        val expiry = policy.resolve()
        assertEquals(Expiry.UNTIL_RESTART, expiry)
    }

    @Test
    fun resolve_Returns_DurationExpiry() {
        val policy = ExpiryPolicy.duration(10.minutes)
        val expiry = policy.resolve()
        val expectedExpiry = Expiry.afterTimeUnit(10, TimeUnit.MINUTES)
        // Allow a small delta due to possible clock differences
        val delta = 1000L
        val expiryTime = expiry.expiryTime()
        val expectedTime = expectedExpiry.expiryTime()
        val calculatedDelta = abs(expiryTime - expectedTime)
        assertTrue(calculatedDelta <= delta)
    }

    @Test
    fun converter_Converts_SessionDataItem() {
        val dataItem = ExpiryPolicy.SESSION.asDataItem()
        val policy = ExpiryPolicy.Converter.convert(dataItem)
        assertEquals(-2L, policy?.value)
        assertEquals(ExpiryPolicy.SESSION, policy)
    }

    @Test
    fun converter_Converts_ForeverDataItem() {
        val dataItem = ExpiryPolicy.FOREVER.asDataItem()
        val policy = ExpiryPolicy.Converter.convert(dataItem)
        assertEquals(-1L, policy?.value)
        assertEquals(ExpiryPolicy.FOREVER, policy)
    }

    @Test
    fun converter_Converts_UntilRestartDataItem() {
        val dataItem = ExpiryPolicy.UNTIL_RESTART.asDataItem()
        val policy = ExpiryPolicy.Converter.convert(dataItem)
        assertEquals(-3L, policy?.value)
        assertEquals(ExpiryPolicy.UNTIL_RESTART, policy)
    }

    @Test
    fun converter_Converts_DurationDataItem() {
        val policy = ExpiryPolicy.duration(10.minutes)
        val dataItem = policy.asDataItem()
        val convertedPolicy = ExpiryPolicy.Converter.convert(dataItem)
        assertEquals(600L, policy.value)
        assertEquals(policy, convertedPolicy)
    }

    @Test
    fun converter_Returns_Null_For_NegativeDuration() {
        val policy = ExpiryPolicy.duration((-100).minutes)
        val dataItem = policy.asDataItem()
        val convertedPolicy = ExpiryPolicy.Converter.convert(dataItem)
        assertEquals(null, convertedPolicy)
    }
}
