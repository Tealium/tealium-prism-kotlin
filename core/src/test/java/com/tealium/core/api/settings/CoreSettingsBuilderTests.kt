package com.tealium.core.api.settings

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.LogLevel
import com.tealium.core.api.misc.TimeFrameUtils.days
import com.tealium.core.api.misc.TimeFrameUtils.minutes
import com.tealium.core.internal.settings.CoreSettingsImpl
import org.junit.Assert.*
import org.junit.Before

import org.junit.Test

class CoreSettingsBuilderTests {

    private lateinit var builder: CoreSettingsBuilder

    @Before
    fun setUp() {
        builder = CoreSettingsBuilder()
    }

    @Test
    fun setLogLevel_Sets_LogLevel_In_DataObject() {
        val error = builder.setLogLevel(LogLevel.ERROR).build()
        val warn = builder.setLogLevel(LogLevel.WARN).build()

        assertEquals("error", error.getString(CoreSettingsImpl.KEY_LOG_LEVEL))
        assertEquals("warn", warn.getString(CoreSettingsImpl.KEY_LOG_LEVEL))
    }

    @Test
    fun setMaxQueueSize_Sets_MaxQueueSize_In_DataObject() {
        val settings = builder.setMaxQueueSize(200).build()

        assertEquals(200, settings.getInt(CoreSettingsImpl.KEY_MAX_QUEUE_SIZE))
    }

    @Test
    fun setExpiration_Sets_Expiration_InSeconds_In_DataObject() {
        val oneDay = builder.setExpiration(1.days).build()
        val tenMinutes = builder.setExpiration(10.minutes).build()

        assertEquals(86_400, oneDay.getInt(CoreSettingsImpl.KEY_EXPIRATION))
        assertEquals(600, tenMinutes.getInt(CoreSettingsImpl.KEY_EXPIRATION))
    }

    @Test
    fun setRefreshInterval_Sets_RefreshInterval_InSeconds_In_DataObject() {
        val oneDay = builder.setRefreshInterval(1.days).build()
        val tenMinutes = builder.setRefreshInterval(10.minutes).build()

        assertEquals(86_400, oneDay.getInt(CoreSettingsImpl.KEY_REFRESH_INTERVAL))
        assertEquals(600, tenMinutes.getInt(CoreSettingsImpl.KEY_REFRESH_INTERVAL))
    }

    @Test
    fun setVisitorIdentityKey_Sets_VisitorIdentityKey_In_DataObject() {
        val settings = builder.setVisitorIdentityKey("identity").build()

        assertEquals("identity", settings.getString(CoreSettingsImpl.KEY_VISITOR_IDENTITY_KEY))
    }

    @Test
    fun setSessionTimeout_Sets_SessionTimeout_InSeconds_In_DataObject() {
        val fiveMinutes = builder.setSessionTimeout(5.minutes).build()
        val thirtyMinutes = builder.setSessionTimeout(30.minutes).build()

        assertEquals(300, fiveMinutes.getInt(CoreSettingsImpl.KEY_SESSION_TIMEOUT))
        assertEquals(1800, thirtyMinutes.getInt(CoreSettingsImpl.KEY_SESSION_TIMEOUT))
    }

    @Test
    fun build_Returns_EmptyDataObject_When_Nothing_Set() {
        val settings = builder.build()
        assertEquals(DataObject.EMPTY_OBJECT, settings)
    }

    @Test
    fun build_Returns_DataObject_Without_Unset_Properties() {
        val settings = builder.setMaxQueueSize(10)
            .setLogLevel(LogLevel.WARN)
            .build()

        assertEquals(10, settings.getInt(CoreSettingsImpl.KEY_MAX_QUEUE_SIZE))
        assertEquals(LogLevel.WARN, settings.get(CoreSettingsImpl.KEY_LOG_LEVEL, LogLevel.Converter))

        assertNull(settings.get(CoreSettingsImpl.KEY_EXPIRATION))
        assertNull(settings.get(CoreSettingsImpl.KEY_VISITOR_IDENTITY_KEY))
        assertNull(settings.get(CoreSettingsImpl.KEY_REFRESH_INTERVAL))
    }
}