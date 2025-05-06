package com.tealium.core.api.settings

import com.tealium.core.internal.modules.deeplink.DeepLinkHandlerConfiguration
import com.tealium.tests.common.buildConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeepLinkSettingsBuilderTests {

    private lateinit var builder: DeepLinkSettingsBuilder

    @Before
    fun setup() {
        builder = DeepLinkSettingsBuilder()
    }

    @Test
    fun setAutomaticDeepLinkTrackingEnabled_Sets_Configuration_When_Given_True() {
        val configuration = builder.setAutomaticDeepLinkTrackingEnabled(true)
            .buildConfiguration()

        assertTrue(configuration.getBoolean(DeepLinkHandlerConfiguration.KEY_AUTOMATIC_DEEPLINK_TRACKING)!!)
    }

    @Test
    fun setAutomaticDeepLinkTrackingEnabled_Sets_Configuration_When_Given_False() {
        val configuration = builder.setAutomaticDeepLinkTrackingEnabled(false)
            .buildConfiguration()

        assertFalse(configuration.getBoolean(DeepLinkHandlerConfiguration.KEY_AUTOMATIC_DEEPLINK_TRACKING)!!)
    }

    @Test
    fun setSendDeepLinkEventEnabled_Sets_Configuration_When_Given_True() {
        val configuration = builder.setSendDeepLinkEventEnabled(true)
            .buildConfiguration()

        assertTrue(configuration.getBoolean(DeepLinkHandlerConfiguration.KEY_SEND_DEEPLINK_EVENT)!!)
    }

    @Test
    fun setSendDeepLinkEventEnabled_Sets_Configuration_When_Given_False() {
        val configuration = builder.setSendDeepLinkEventEnabled(false)
            .buildConfiguration()

        assertFalse(configuration.getBoolean(DeepLinkHandlerConfiguration.KEY_SEND_DEEPLINK_EVENT)!!)
    }

    @Test
    fun setDeepLinkTraceEnabled_Sets_Configuration_When_Given_True() {
        val configuration = builder.setDeepLinkTraceEnabled(true)
            .buildConfiguration()

        assertTrue(configuration.getBoolean(DeepLinkHandlerConfiguration.KEY_DEEPLINK_TRACE_ENABLED)!!)
    }

    @Test
    fun setDeepLinkTraceEnabled_Sets_Configuration_When_Given_False() {
        val configuration = builder.setDeepLinkTraceEnabled(false)
            .buildConfiguration()

        assertFalse(configuration.getBoolean(DeepLinkHandlerConfiguration.KEY_DEEPLINK_TRACE_ENABLED)!!)
    }

    @Test
    fun builder_Methods_Return_Builder_Instance_For_Method_Chaining() {
        val returnedBuilder = builder.setAutomaticDeepLinkTrackingEnabled(true)
            .setSendDeepLinkEventEnabled(true)
            .setDeepLinkTraceEnabled(true)

        assertSame(builder, returnedBuilder)
    }

    @Test
    fun build_Creates_Configuration_With_All_Settings() {
        val configuration = builder.setAutomaticDeepLinkTrackingEnabled(true)
            .setSendDeepLinkEventEnabled(false)
            .setDeepLinkTraceEnabled(true)
            .buildConfiguration()

        // Then
        assertEquals(3, configuration.size)
        assertEquals(
            true,
            configuration.getBoolean(DeepLinkHandlerConfiguration.KEY_AUTOMATIC_DEEPLINK_TRACKING)
        )
        assertEquals(
            false,
            configuration.getBoolean(DeepLinkHandlerConfiguration.KEY_SEND_DEEPLINK_EVENT)
        )
        assertEquals(
            true,
            configuration.getBoolean(DeepLinkHandlerConfiguration.KEY_DEEPLINK_TRACE_ENABLED)
        )
    }
}

