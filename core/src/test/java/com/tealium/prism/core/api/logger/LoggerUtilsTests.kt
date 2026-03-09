package com.tealium.prism.core.api.logger

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.logger.LoggerImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoggerUtilsTests {

    private val category = "category"
    private lateinit var handler: LogHandler
    private lateinit var onLogLevel: Subject<LogLevel>
    private lateinit var supplier: () -> String
    private val scheduler: Scheduler = Scheduler.SYNCHRONOUS

    @Before
    fun setUp() {
        onLogLevel = Observables.replaySubject()
        handler = mockk(relaxed = true)

        supplier = mockk()
        every { supplier.invoke() } returns "message"
    }

    @Test
    fun logIfTraceEnabled_Does_Not_Call_Logger_When_Not_Trace_Logging() {
        listOf(
            LogLevel.DEBUG,
            LogLevel.INFO,
            LogLevel.WARN,
            LogLevel.ERROR,
            LogLevel.SILENT
        ).forEach { logLevel ->
            val logger = spyk(LoggerImpl(scheduler, handler, onLogLevel, logLevel))

            logger.logIfTraceEnabled(category, supplier)

            verify(inverse = true) {
                logger.trace(category, "message")
            }
        }
    }

    @Test
    fun logIfTraceEnabled_Does_Call_Logger_When_LogLevel_Is_Trace() {
        val logger = spyk(LoggerImpl(scheduler, handler, onLogLevel, LogLevel.TRACE))

        logger.logIfTraceEnabled(category, supplier)

        verify {
            logger.trace(category, "message")
        }
    }

    @Test
    fun logIfDebugEnabled_Does_Not_Call_Logger_When_LogLevel_Is_Too_High() {
        listOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR, LogLevel.SILENT).forEach { logLevel ->
            val logger = spyk(LoggerImpl(scheduler, handler, onLogLevel, logLevel))

            logger.logIfDebugEnabled(category, supplier)

            verify(inverse = true) {
                logger.debug(category, "message")
            }
        }
    }

    @Test
    fun logIfDebugEnabled_Does_Call_Logger_When_LogLevel_Is_Debug_Or_Lower() {
        listOf(LogLevel.TRACE, LogLevel.DEBUG).forEach { logLevel ->
            val logger = spyk(LoggerImpl(scheduler, handler, onLogLevel, logLevel))

            logger.logIfDebugEnabled(category, supplier)

            verify {
                logger.debug(category, "message")
            }
        }
    }

    @Test
    fun logIfInfoEnabled_Does_Not_Call_Logger_When_LogLevel_Too_High() {
        listOf(LogLevel.WARN, LogLevel.ERROR, LogLevel.SILENT).forEach { logLevel ->
            val logger = spyk(LoggerImpl(scheduler, handler, onLogLevel, logLevel))

            logger.logIfInfoEnabled(category, supplier)

            verify(inverse = true) {
                logger.info(category, "message")
            }
        }
    }

    @Test
    fun logIfInfoEnabled_Does_Call_Logger_When_LogLevel_Is_Info_Or_Lower() {
        listOf(LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO).forEach { logLevel ->
            val logger = spyk(LoggerImpl(scheduler, handler, onLogLevel, logLevel))

            logger.logIfInfoEnabled(category, supplier)

            verify {
                logger.info(category, "message")
            }
        }
    }

    @Test
    fun logIfWarnEnabled_Does_Not_Call_Logger_When_LogLevel_Too_High() {
        listOf(LogLevel.ERROR, LogLevel.SILENT).forEach { logLevel ->
            val logger = spyk(LoggerImpl(scheduler, handler, onLogLevel, logLevel))

            logger.logIfWarnEnabled(category, supplier)

            verify(inverse = true) {
                logger.warn(category, "message")
            }
        }
    }

    @Test
    fun logIfWarnEnabled_Does_Call_Logger_When_LogLevel_Is_Info_Or_Lower() {
        listOf(LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN).forEach { logLevel ->
            val logger = spyk(LoggerImpl(scheduler, handler, onLogLevel, logLevel))

            logger.logIfWarnEnabled(category, supplier)

            verify {
                logger.warn(category, "message")
            }
        }
    }

    @Test
    fun logIfErrorEnabled_Does_Not_Call_Logger_When_LogLevel_Too_High() {
        listOf(LogLevel.SILENT).forEach { logLevel ->
            val logger = spyk(LoggerImpl(scheduler, handler, onLogLevel, logLevel))

            logger.logIfErrorEnabled(category, supplier)

            verify(inverse = true) {
                logger.error(category, "message")
            }
        }
    }

    @Test
    fun logIfErrorEnabled_Does_Call_Logger_When_LogLevel_Is_Info_Or_Lower() {
        listOf(LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN).forEach { logLevel ->
            val logger = spyk(LoggerImpl(scheduler, handler, onLogLevel, logLevel))

            logger.logIfErrorEnabled(category, supplier)

            verify {
                logger.error(category, "message")
            }
        }
    }

    @Test
    fun isTraceLogging_True_When_LogLevel_Is_Trace() {
        val logger = LoggerImpl(mockk(), mockk(), onLogLevel)
        onLogLevel.onNext(LogLevel.TRACE)

        assertTrue(logger.isTraceLogging)
    }

    @Test
    fun isTraceLogging_False_When_LogLevel_Too_High() {
        val logger = LoggerImpl(mockk(), mockk(), onLogLevel)

        onLogLevel.onNext(LogLevel.DEBUG)
        assertFalse(logger.isTraceLogging)
        onLogLevel.onNext(LogLevel.INFO)
        assertFalse(logger.isTraceLogging)
        onLogLevel.onNext(LogLevel.WARN)
        assertFalse(logger.isTraceLogging)
        onLogLevel.onNext(LogLevel.ERROR)
        assertFalse(logger.isTraceLogging)
        onLogLevel.onNext(LogLevel.SILENT)
        assertFalse(logger.isTraceLogging)
    }

    @Test
    fun isDebugLogging_True_When_LogLevel_Is_Debug_Or_Lower() {
        val logger = LoggerImpl(mockk(), mockk(), onLogLevel)

        onLogLevel.onNext(LogLevel.TRACE)
        assertTrue(logger.isDebugLogging)
        onLogLevel.onNext(LogLevel.DEBUG)
        assertTrue(logger.isDebugLogging)
    }

    @Test
    fun isDebugLogging_True_When_LogLevel_Is_Too_High() {
        val logger = LoggerImpl(mockk(), mockk(), onLogLevel)

        onLogLevel.onNext(LogLevel.INFO)
        assertFalse(logger.isDebugLogging)
        onLogLevel.onNext(LogLevel.WARN)
        assertFalse(logger.isDebugLogging)
        onLogLevel.onNext(LogLevel.ERROR)
        assertFalse(logger.isDebugLogging)
        onLogLevel.onNext(LogLevel.SILENT)
        assertFalse(logger.isDebugLogging)
    }

    @Test
    fun isInfoLogging_True_When_LogLevel_Is_Info_Or_Lower() {
        val logger = LoggerImpl(mockk(), mockk(), onLogLevel)

        onLogLevel.onNext(LogLevel.TRACE)
        assertTrue(logger.isInfoLogging)
        onLogLevel.onNext(LogLevel.DEBUG)
        assertTrue(logger.isInfoLogging)
        onLogLevel.onNext(LogLevel.INFO)
        assertTrue(logger.isInfoLogging)
    }

    @Test
    fun isInfoLogging_True_When_LogLevel_Is_Too_High() {
        val logger = LoggerImpl(mockk(), mockk(), onLogLevel)

        onLogLevel.onNext(LogLevel.WARN)
        assertFalse(logger.isInfoLogging)
        onLogLevel.onNext(LogLevel.ERROR)
        assertFalse(logger.isInfoLogging)
        onLogLevel.onNext(LogLevel.SILENT)
        assertFalse(logger.isInfoLogging)
    }

    @Test
    fun isWarnLogging_True_When_LogLevel_Is_Warn_Or_Lower() {
        val logger = LoggerImpl(mockk(), mockk(), onLogLevel)

        onLogLevel.onNext(LogLevel.TRACE)
        assertTrue(logger.isWarnLogging)
        onLogLevel.onNext(LogLevel.DEBUG)
        assertTrue(logger.isWarnLogging)
        onLogLevel.onNext(LogLevel.INFO)
        assertTrue(logger.isWarnLogging)
        onLogLevel.onNext(LogLevel.WARN)
        assertTrue(logger.isWarnLogging)
    }

    @Test
    fun isWarnLogging_True_When_LogLevel_Is_Too_High() {
        val logger = LoggerImpl(mockk(), mockk(), onLogLevel)

        onLogLevel.onNext(LogLevel.ERROR)
        assertFalse(logger.isWarnLogging)
        onLogLevel.onNext(LogLevel.SILENT)
        assertFalse(logger.isWarnLogging)
    }

    @Test
    fun isErrorLogging_True_When_LogLevel_Is_Error_Or_Lower() {
        val logger = LoggerImpl(mockk(), mockk(), onLogLevel)

        onLogLevel.onNext(LogLevel.TRACE)
        assertTrue(logger.isErrorLogging)
        onLogLevel.onNext(LogLevel.DEBUG)
        assertTrue(logger.isErrorLogging)
        onLogLevel.onNext(LogLevel.INFO)
        assertTrue(logger.isErrorLogging)
        onLogLevel.onNext(LogLevel.WARN)
        assertTrue(logger.isErrorLogging)
        onLogLevel.onNext(LogLevel.ERROR)
        assertTrue(logger.isErrorLogging)
    }

    @Test
    fun isErrorLogging_True_When_LogLevel_Is_Too_High() {
        val logger = LoggerImpl(mockk(), mockk(), onLogLevel)

        onLogLevel.onNext(LogLevel.SILENT)
        assertFalse(logger.isErrorLogging)
    }
}