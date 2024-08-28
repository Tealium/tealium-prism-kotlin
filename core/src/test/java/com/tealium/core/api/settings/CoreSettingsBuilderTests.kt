package com.tealium.core.api.settings

import com.tealium.core.api.data.TealiumBundle
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
    fun setLogLevel_Sets_LogLevel_In_Bundle() {
        val error = builder.setLogLevel(LogLevel.ERROR).build()
        val warn = builder.setLogLevel(LogLevel.WARN).build()

        assertEquals("error", error.getString(CoreSettingsImpl.KEY_LOG_LEVEL))
        assertEquals("warn", warn.getString(CoreSettingsImpl.KEY_LOG_LEVEL))
    }

    @Test
    fun setDataSource_Sets_DataSource_In_Bundle() {
        val settings = builder.setDataSource("abc123").build()

        assertEquals("abc123", settings.getString(CoreSettingsImpl.KEY_DATA_SOURCE))
    }

    @Test
    fun setBatchSize_Sets_BatchSize_In_Bundle() {
        val five = builder.setBatchSize(5).build()
        val tooSmall = builder.setBatchSize(-100).build()
        val tooBig = builder.setBatchSize(100).build()

        assertEquals(5, five.getInt(CoreSettingsImpl.KEY_BATCH_SIZE))
        assertEquals(0, tooSmall.getInt(CoreSettingsImpl.KEY_BATCH_SIZE))
        assertEquals(
            CoreSettingsImpl.MAX_BATCH_SIZE,
            tooBig.getInt(CoreSettingsImpl.KEY_BATCH_SIZE)
        )
    }

    @Test
    fun setMaxQueueSize_Sets_MaxQueueSize_In_Bundle() {
        val settings = builder.setMaxQueueSize(200).build()

        assertEquals(200, settings.getInt(CoreSettingsImpl.KEY_MAX_QUEUE_SIZE))
    }

    @Test
    fun setExpiration_Sets_Expiration_InSeconds_In_Bundle() {
        val oneDay = builder.setExpiration(1.days).build()
        val tenMinutes = builder.setExpiration(10.minutes).build()

        assertEquals(86_400, oneDay.getInt(CoreSettingsImpl.KEY_EXPIRATION))
        assertEquals(600, tenMinutes.getInt(CoreSettingsImpl.KEY_EXPIRATION))
    }

    @Test
    fun setBatterySaver_Sets_BatterySaver_In_Bundle() {
        val enabled = builder.setBatterySaver(true).build()
        val disabled = builder.setBatterySaver(false).build()

        assertTrue(enabled.getBoolean(CoreSettingsImpl.KEY_BATTERY_SAVER)!!)
        assertFalse(disabled.getBoolean(CoreSettingsImpl.KEY_BATTERY_SAVER)!!)
    }

    @Test
    fun setWifiOnly_Sets_WifiOnly_In_Bundle() {
        val enabled = builder.setWifiOnly(true).build()
        val disabled = builder.setWifiOnly(false).build()

        assertTrue(enabled.getBoolean(CoreSettingsImpl.KEY_WIFI_ONLY)!!)
        assertFalse(disabled.getBoolean(CoreSettingsImpl.KEY_WIFI_ONLY)!!)
    }

    @Test
    fun setRefreshInterval_Sets_RefreshInterval_InSeconds_In_Bundle() {
        val oneDay = builder.setRefreshInterval(1.days).build()
        val tenMinutes = builder.setRefreshInterval(10.minutes).build()

        assertEquals(86_400, oneDay.getInt(CoreSettingsImpl.KEY_REFRESH_INTERVAL))
        assertEquals(600, tenMinutes.getInt(CoreSettingsImpl.KEY_REFRESH_INTERVAL))
    }

    @Test
    fun setDeepLinkTrackingEnabled_Sets_DeepLinkTrackingEnabled_In_Bundle() {
        val enabled = builder.setDeepLinkTrackingEnabled(true).build()
        val disabled = builder.setDeepLinkTrackingEnabled(false).build()

        assertTrue(enabled.getBoolean(CoreSettingsImpl.KEY_DEEPLINK_TRACKING_ENABLED)!!)
        assertFalse(disabled.getBoolean(CoreSettingsImpl.KEY_DEEPLINK_TRACKING_ENABLED)!!)
    }

    @Test
    fun setDisableLibrary_Sets_DisableLibrary_In_Bundle() {
        val disabled = builder.setDisableLibrary(true).build()
        val enabled = builder.setDisableLibrary(false).build()

        assertFalse(enabled.getBoolean(CoreSettingsImpl.KEY_DISABLE_LIBRARY)!!)
        assertTrue(disabled.getBoolean(CoreSettingsImpl.KEY_DISABLE_LIBRARY)!!)
    }

    @Test
    fun setVisitorIdentityKey_Sets_VisitorIdentityKey_In_Bundle() {
        val settings = builder.setVisitorIdentityKey("identity").build()

        assertEquals("identity", settings.getString(CoreSettingsImpl.KEY_VISITOR_IDENTITY_KEY))
    }

    @Test
    fun build_Returns_EmptyBundle_When_Nothing_Set() {
        val settings = builder.build()
        assertEquals(TealiumBundle.EMPTY_BUNDLE, settings)
    }

    @Test
    fun build_Returns_Bundle_Without_Unset_Properties() {
        val settings = builder.setBatchSize(10)
            .setBatterySaver(true)
            .build()

        assertEquals(10, settings.getInt(CoreSettingsImpl.KEY_BATCH_SIZE))
        assertTrue(settings.getBoolean(CoreSettingsImpl.KEY_BATTERY_SAVER)!!)

        assertNull(settings.get(CoreSettingsImpl.KEY_EXPIRATION))
        assertNull(settings.get(CoreSettingsImpl.KEY_DISABLE_LIBRARY))
        assertNull(settings.get(CoreSettingsImpl.KEY_LOG_LEVEL))
    }
}