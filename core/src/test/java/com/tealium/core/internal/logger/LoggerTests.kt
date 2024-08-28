package com.tealium.core.internal.logger

import com.tealium.core.api.logger.LogLevel
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.logger.LogHandler
import com.tealium.core.internal.settings.SdkSettings
import com.tealium.core.internal.settings.SettingsProvider
import com.tealium.core.internal.settings.CoreSettingsImpl
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoggerTests {

    private lateinit var logger: LoggerImpl

    @RelaxedMockK
    private lateinit var logHandler: LogHandler

    @RelaxedMockK
    private lateinit var settingsProvider: SettingsProvider

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        logger = LoggerImpl(
            logHandler,
            LogLevel.DEBUG,
            settingsProvider.onSdkSettingsUpdated,
        )
    }

    @Test
    fun loggerShouldReturnValidLoggersForDebug() {
        assertNull(logger.trace)
        assertNotNull(logger.debug)
        assertNotNull(logger.info)
        assertNotNull(logger.warn)
        assertNotNull(logger.error)
    }

    @Test
    fun loggerShouldReturnTrueForValidLogLevels() {
        assertFalse(logger.shouldLog(LogLevel.TRACE))
        assertTrue(logger.shouldLog(LogLevel.DEBUG))
        assertTrue(logger.shouldLog(LogLevel.INFO))
        assertTrue(logger.shouldLog(LogLevel.WARN))
        assertTrue(logger.shouldLog(LogLevel.ERROR))
    }

    @Test
    fun logSentToLogHandlerForValidLogger() {
        logger.debug?.log("testCategory", "testMessage")

        verify {
            logHandler.log("testCategory", "testMessage", LogLevel.DEBUG)
        }
    }

    @Test
    fun logNotSentToLogHandlerForInvalidLogger() {
        logger.trace?.log("testCategory", "testMessage")

        verify {
            logHandler wasNot Called
        }
    }

    @Test
    fun updateLogLevelToSilentReturnsNoLoggers() {
        logger.onSettingsUpdated(
            SdkSettings(
                moduleSettings = mapOf(
                    "core" to TealiumBundle.create {
                        put(CoreSettingsImpl.KEY_LOG_LEVEL, LogLevel.SILENT)
                    }
                )
            )
        )
        assertNull(logger.trace)
        assertNull(logger.debug)
        assertNull(logger.info)
        assertNull(logger.warn)
        assertNull(logger.error)
    }

    @Test
    fun updateLogLevelToSilentAlsoUpdatesLogsInstances() {
        val warnLogger = logger.warn!!

        logger.onSettingsUpdated(
            SdkSettings(
                moduleSettings = mapOf(
                    "core" to TealiumBundle.create {
                        put(CoreSettingsImpl.KEY_LOG_LEVEL, LogLevel.SILENT)
                    }
                )
            )
        )
        warnLogger.log("test", "test")

        verify {
            logHandler wasNot Called
        }
    }
}