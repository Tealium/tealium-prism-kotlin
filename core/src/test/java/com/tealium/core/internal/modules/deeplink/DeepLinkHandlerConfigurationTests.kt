package com.tealium.core.internal.modules.deeplink

import com.tealium.core.api.data.DataObject
import org.junit.Assert.*
import org.junit.Test

class DeepLinkHandlerConfigurationTests {

    @Test
    fun constructor_Creates_Configuration_With_Provided_Values() {
        val config = DeepLinkHandlerConfiguration(
            automaticDeepLinkTracking = true,
            sendDeepLinkEvent = false,
            deepLinkTraceEnabled = true
        )

        assertTrue(config.automaticDeepLinkTracking)
        assertFalse(config.sendDeepLinkEvent)
        assertTrue(config.deepLinkTraceEnabled)
    }

    @Test
    fun fromDataObject_Returns_Default_Values_When_Given_Empty_DataObject() {
        val config = DeepLinkHandlerConfiguration.fromDataObject(DataObject.EMPTY_OBJECT)

        assertTrue(config.automaticDeepLinkTracking) // Default is true
        assertFalse(config.sendDeepLinkEvent) // Default is false
        assertTrue(config.deepLinkTraceEnabled) // Default is true
    }

    @Test
    fun fromDataObject_Returns_Values_From_DataObject_When_All_Keys_Present() {
        val dataObject = DataObject.create {
            put(DeepLinkHandlerConfiguration.KEY_AUTOMATIC_DEEPLINK_TRACKING, false)
            put(DeepLinkHandlerConfiguration.KEY_SEND_DEEPLINK_EVENT, true)
            put(DeepLinkHandlerConfiguration.KEY_DEEPLINK_TRACE_ENABLED, false)
        }

        val config = DeepLinkHandlerConfiguration.fromDataObject(dataObject)

        assertFalse(config.automaticDeepLinkTracking)
        assertTrue(config.sendDeepLinkEvent)
        assertFalse(config.deepLinkTraceEnabled)
    }

    @Test
    fun fromDataObject_Returns_Mixed_Values_When_Some_Keys_Present() {
        val dataObject = DataObject.create {
            put(DeepLinkHandlerConfiguration.KEY_SEND_DEEPLINK_EVENT, true)
        }

        val config = DeepLinkHandlerConfiguration.fromDataObject(dataObject)

        assertTrue(config.automaticDeepLinkTracking) // Default is true
        assertTrue(config.sendDeepLinkEvent) // From DataObject
        assertTrue(config.deepLinkTraceEnabled) // Default is true
    }

    @Test
    fun fromDataObject_Ignores_Non_Boolean_Values() {
        val dataObject = DataObject.create {
            put(DeepLinkHandlerConfiguration.KEY_AUTOMATIC_DEEPLINK_TRACKING, "not a boolean")
            put(DeepLinkHandlerConfiguration.KEY_SEND_DEEPLINK_EVENT, 123)
            put(DeepLinkHandlerConfiguration.KEY_DEEPLINK_TRACE_ENABLED, DataObject.EMPTY_OBJECT)
        }

        val config = DeepLinkHandlerConfiguration.fromDataObject(dataObject)

        // should use defaults since none of the values are booleans
        assertTrue(config.automaticDeepLinkTracking)
        assertFalse(config.sendDeepLinkEvent)
        assertTrue(config.deepLinkTraceEnabled)
    }
}
