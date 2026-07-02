package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.days
import com.tealium.prism.core.api.misc.TimeFrameUtils.minutes
import com.tealium.prism.core.api.settings.CoreSettingsBuilder
import com.tealium.prism.core.internal.session.SessionManagerImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreSettingsImplTests {

    private fun buildSettings(block: (CoreSettingsBuilder.() -> Unit)? = null): CoreSettingsImpl {
        val builder = CoreSettingsBuilder()
        if (block != null) builder.apply(block)
        return CoreSettingsImpl.fromDataObject(builder.build())
    }

    // Data class .equals(..) will fail on non matching units - `compareTo` will take units into account
    private fun assertTimeFrameEquals(expected: TimeFrame, actual: TimeFrame) {
        assertEquals(0, expected.compareTo(actual))
    }

    // fromDataObject

    @Test
    fun fromDataObject_Sets_Default_LogLevel_When_Omitted() {
        val settings = buildSettings()

        assertEquals(CoreSettingsImpl.DEFAULT_LOG_LEVEL, settings.logLevel)
    }

    @Test
    fun fromDataObject_Sets_Default_LogLevel_When_Invalid_Value() {
        val dataObject = DataObject.create {
            put(CoreSettingsImpl.KEY_LOG_LEVEL, 123)
        }
        val settings = CoreSettingsImpl.fromDataObject(dataObject)

        assertEquals(CoreSettingsImpl.DEFAULT_LOG_LEVEL, settings.logLevel)
    }

    @Test
    fun fromDataObject_Sets_LogLevel_When_Valid_Value() {
        val settings = buildSettings {
            setLogLevel(LogLevel.TRACE)
        }

        assertEquals(LogLevel.TRACE, settings.logLevel)
    }

    @Test
    fun fromDataObject_Sets_Default_MaxQueueSize_When_Omitted() {
        val settings = buildSettings()

        assertEquals(CoreSettingsImpl.DEFAULT_MAX_QUEUE_SIZE, settings.maxQueueSize)
    }

    @Test
    fun fromDataObject_Sets_Default_MaxQueueSize_When_Invalid_Value() {
        val dataObject = DataObject.create {
            put(CoreSettingsImpl.KEY_MAX_QUEUE_SIZE, "max")
        }
        val settings = CoreSettingsImpl.fromDataObject(dataObject)

        assertEquals(CoreSettingsImpl.DEFAULT_MAX_QUEUE_SIZE, settings.maxQueueSize)
    }

    @Test
    fun fromDataObject_Sets_MaxQueueSize_When_Valid_Value() {
        val settings = buildSettings {
            setMaxQueueSize(500)
        }

        assertEquals(500, settings.maxQueueSize)
    }

    @Test
    fun fromDataObject_Sets_Default_Expiration_When_Omitted() {
        val settings = buildSettings()

        assertEquals(CoreSettingsImpl.DEFAULT_EXPIRATION_DAYS.days, settings.expiration)
    }

    @Test
    fun fromDataObject_Sets_Default_Expiration_When_Invalid_Value() {
        val dataObject = DataObject.create {
            put(CoreSettingsImpl.KEY_EXPIRATION, "expiration")
        }
        val settings = CoreSettingsImpl.fromDataObject(dataObject)

        assertEquals(CoreSettingsImpl.DEFAULT_EXPIRATION_DAYS.days, settings.expiration)
    }

    @Test
    fun fromDataObject_Sets_Expiration_When_Valid_Value() {
        val settings = buildSettings {
            setExpiration(10.days)
        }

        assertTimeFrameEquals(10.days, settings.expiration)
    }

    @Test
    fun fromDataObject_Sets_Default_RefreshInterval_When_Omitted() {
        val settings = buildSettings()

        assertEquals(CoreSettingsImpl.DEFAULT_REFRESH_INTERVAL_MINUTES.minutes, settings.refreshInterval)
    }

    @Test
    fun fromDataObject_Sets_Default_RefreshInterval_When_Invalid_Value() {
        val dataObject = DataObject.create {
            put(CoreSettingsImpl.KEY_REFRESH_INTERVAL, "refresh")
        }
        val settings = CoreSettingsImpl.fromDataObject(dataObject)

        assertEquals(CoreSettingsImpl.DEFAULT_REFRESH_INTERVAL_MINUTES.minutes, settings.refreshInterval)
    }

    @Test
    fun fromDataObject_Sets_RefreshInterval_When_Valid_Value() {
        val settings = buildSettings {
            setRefreshInterval(10.days)
        }

        assertTimeFrameEquals(10.days, settings.refreshInterval)
    }

    @Test
    fun fromDataObject_Sets_Null_VisitorIdentityKey_When_Omitted() {
        val settings = buildSettings()

        assertNull(settings.visitorIdentityKey)
    }

    @Test
    fun fromDataObject_Sets_Null_VisitorIdentityKey_When_Invalid_Value() {
        val dataObject = DataObject.create {
            put(CoreSettingsImpl.KEY_VISITOR_IDENTITY_KEY, 12345)
        }
        val settings = CoreSettingsImpl.fromDataObject(dataObject)

        assertNull(settings.visitorIdentityKey)
    }

    @Test
    fun fromDataObject_Sets_VisitorIdentityKey_When_Valid_Value() {
        val settings = buildSettings {
            setVisitorIdentityKey("customer_id")
        }

        assertEquals("customer_id", settings.visitorIdentityKey)
    }

    @Test
    fun fromDataObject_Sets_Default_SessionTimeout_When_Omitted() {
        val settings = buildSettings()

        assertEquals(SessionManagerImpl.DEFAULT_SESSION_TIMEOUT, settings.sessionTimeout)
    }

    @Test
    fun fromDataObject_Sets_Default_SessionTimeout_When_Invalid_Value() {
        val dataObject = DataObject.create {
            put(CoreSettingsImpl.KEY_SESSION_TIMEOUT, "12345")
        }
        val settings = CoreSettingsImpl.fromDataObject(dataObject)

        assertEquals(SessionManagerImpl.DEFAULT_SESSION_TIMEOUT, settings.sessionTimeout)
    }

    @Test
    fun fromDataObject_Sets_SessionTimeout_When_Valid_Value() {
        val settings = buildSettings {
            setSessionTimeout(10.days)
        }

        assertTimeFrameEquals(10.days, settings.sessionTimeout)
    }
}