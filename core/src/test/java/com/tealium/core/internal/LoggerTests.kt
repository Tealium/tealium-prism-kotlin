package com.tealium.core.internal

import com.tealium.core.Environment
import com.tealium.core.LogLevel
import com.tealium.core.api.logger.LogHandler
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

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        logger = LoggerImpl(logHandler, LogLevel.DEBUG)
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
            coreSettings = CoreSettingsImpl(
                "test",
                "test",
                Environment.DEV,
                logLevel = LogLevel.SILENT
            ), moduleSettings = emptyMap()
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
            coreSettings = CoreSettingsImpl(
                "test",
                "test",
                Environment.DEV,
                logLevel = LogLevel.SILENT
            ), moduleSettings = emptyMap()
        )
        warnLogger.log("test", "test")

        verify {
            logHandler wasNot Called
        }
    }
}