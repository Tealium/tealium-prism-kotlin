package com.tealium.prism.core.internal.logger

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.LogHandler
import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Subject
import io.mockk.Called
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoggerTests {

    private lateinit var handler: LogHandler
    private lateinit var onLogLevel: Subject<LogLevel>
    private val category = "cat"

    @Before
    fun setUp() {
        handler = spyk(object : LogHandler {
            override fun log(category: String, message: String, logLevel: LogLevel) {
                println("$logLevel - $category - $message")
            }
        })
        onLogLevel = spyk(Observables.replaySubject())
    }

    @Test
    fun shouldLog_Returns_False_When_LogLevel_Too_High() {
        val logger = LoggerImpl(handler, onLogLevel)

        onLogLevel.onNext(LogLevel.DEBUG)
        assertFalse(logger.shouldLog(LogLevel.TRACE))
        onLogLevel.onNext(LogLevel.INFO)
        assertFalse(logger.shouldLog(LogLevel.DEBUG))
        onLogLevel.onNext(LogLevel.WARN)
        assertFalse(logger.shouldLog(LogLevel.INFO))
        onLogLevel.onNext(LogLevel.ERROR)
        assertFalse(logger.shouldLog(LogLevel.WARN))
        onLogLevel.onNext(LogLevel.SILENT)
        assertFalse(logger.shouldLog(LogLevel.ERROR))
    }

    @Test
    fun shouldLog_Returns_False_When_LogLevel_Silent() {
        val logger = LoggerImpl(handler, onLogLevel)

        LogLevel.values().forEach { level ->
            onLogLevel.onNext(level)
            assertFalse(logger.shouldLog(LogLevel.SILENT))
        }
    }

    @Test
    fun shouldLog_Returns_True_When_No_LogLevel_Set() {
        val logger = LoggerImpl(handler, onLogLevel)

        LogLevel.values().filter { it != LogLevel.SILENT }.forEach { level ->
            assertTrue(logger.shouldLog(level))
        }
    }

    @Test
    fun logger_Buffers_Until_LogLevel_Chosen() {
        val logger = LoggerImpl(handler, onLogLevel)

        logger.debug("", "")

        verify {
            handler wasNot Called
        }
    }

    @Test
    fun logger_Logs_When_LogLevel_Chosen() {
        val logger = LoggerImpl(handler, onLogLevel)

        logger.debug(category, "message")
        onLogLevel.onNext(LogLevel.DEBUG)

        verify {
            handler.log(category, "message", LogLevel.DEBUG)
        }
    }

    @Test
    fun logger_Drops_Logs_When_Chosen_LogLevel_Too_High() {
        val logger = LoggerImpl(handler, onLogLevel)

        logger.debug(category, "message")
        onLogLevel.onNext(LogLevel.INFO)

        verify(inverse = true) {
            handler.log(category, "message", LogLevel.DEBUG)
        }
    }

    @Test
    fun logger_Does_Not_Subscribe_To_LogLevel_Updates_When_Level_Preset() {
        val logger = LoggerImpl(handler, onLogLevel, LogLevel.ERROR)

        onLogLevel.onNext(LogLevel.TRACE)
        logger.debug(category, "message")

        verify(inverse = true) {
            onLogLevel.subscribe(any())
            handler.log(category, "message", LogLevel.DEBUG)
        }
    }

    @Test
    fun trace_Does_Not_Log_When_Level_Is_Higher() {
        val logger = LoggerImpl(handler, onLogLevel)

        onLogLevel.onNext(LogLevel.DEBUG)
        logger.trace(category, "trace")
        onLogLevel.onNext(LogLevel.INFO)
        logger.trace(category, "trace")
        onLogLevel.onNext(LogLevel.WARN)
        logger.trace(category, "trace")
        onLogLevel.onNext(LogLevel.ERROR)
        logger.trace(category, "trace")
        onLogLevel.onNext(LogLevel.SILENT)
        logger.trace(category, "trace")

        verify {
            handler wasNot Called
        }
    }

    @Test
    fun trace_Does_Log_When_Level_Is_Trace() {
        val logger = LoggerImpl(handler, onLogLevel)

        onLogLevel.onNext(LogLevel.TRACE)
        logger.trace(category, "trace")

        verify(exactly = 1) {
            handler.log(category, "trace", LogLevel.TRACE)
        }
    }

    @Test
    fun debug_Does_Not_Log_When_Level_Is_Higher() {
        val logger = LoggerImpl(handler, onLogLevel)

        onLogLevel.onNext(LogLevel.INFO)
        logger.debug(category, "debug")
        onLogLevel.onNext(LogLevel.WARN)
        logger.debug(category, "debug")
        onLogLevel.onNext(LogLevel.ERROR)
        logger.debug(category, "debug")
        onLogLevel.onNext(LogLevel.SILENT)
        logger.debug(category, "debug")

        verify {
            handler wasNot Called
        }
    }

    @Test
    fun debug_Does_Log_When_Level_Is_Debug_Or_Lower() {
        val logger = LoggerImpl(handler, onLogLevel)

        onLogLevel.onNext(LogLevel.TRACE)
        logger.debug(category, "debug")
        onLogLevel.onNext(LogLevel.DEBUG)
        logger.debug(category, "debug")

        verify(exactly = 2) {
            handler.log(category, "debug", LogLevel.DEBUG)
        }
    }

    @Test
    fun info_Does_Not_Log_When_Level_Is_Higher() {
        val logger = LoggerImpl(handler, onLogLevel)

        onLogLevel.onNext(LogLevel.WARN)
        logger.info(category, "info")
        onLogLevel.onNext(LogLevel.ERROR)
        logger.info(category, "info")
        onLogLevel.onNext(LogLevel.SILENT)
        logger.info(category, "info")

        verify {
            handler wasNot Called
        }
    }

    @Test
    fun info_Does_Log_When_Level_Is_Info_Or_Lower() {
        val logger = LoggerImpl(handler, onLogLevel)

        onLogLevel.onNext(LogLevel.TRACE)
        logger.info(category, "info")
        onLogLevel.onNext(LogLevel.DEBUG)
        logger.info(category, "info")
        onLogLevel.onNext(LogLevel.INFO)
        logger.info(category, "info")

        verify(exactly = 3) {
            handler.log(category, "info", LogLevel.INFO)
        }
    }

    @Test
    fun warn_Does_Not_Log_When_Level_Is_Higher() {
        val logger = LoggerImpl(handler, onLogLevel)

        onLogLevel.onNext(LogLevel.ERROR)
        logger.warn(category, "warn")
        onLogLevel.onNext(LogLevel.SILENT)
        logger.warn(category, "warn")

        verify {
            handler wasNot Called
        }
    }

    @Test
    fun warn_Does_Log_When_Level_Is_Warn_Or_Lower() {
        val logger = LoggerImpl(handler, onLogLevel)

        onLogLevel.onNext(LogLevel.TRACE)
        logger.warn(category, "warn")
        onLogLevel.onNext(LogLevel.DEBUG)
        logger.warn(category, "warn")
        onLogLevel.onNext(LogLevel.INFO)
        logger.warn(category, "warn")
        onLogLevel.onNext(LogLevel.WARN)
        logger.warn(category, "warn")

        verify(exactly = 4) {
            handler.log(category, "warn", LogLevel.WARN)
        }
    }

    @Test
    fun error_Does_Not_Log_When_Level_Is_Higher() {
        val logger = LoggerImpl(handler, onLogLevel)

        onLogLevel.onNext(LogLevel.SILENT)
        logger.error(category, "error")

        verify {
            handler wasNot Called
        }
    }

    @Test
    fun error_Does_Log_When_Level_Is_Error_Or_Lower() {
        val logger = LoggerImpl(handler, onLogLevel)

        onLogLevel.onNext(LogLevel.TRACE)
        logger.error(category, "error")
        onLogLevel.onNext(LogLevel.DEBUG)
        logger.error(category, "error")
        onLogLevel.onNext(LogLevel.INFO)
        logger.error(category, "error")
        onLogLevel.onNext(LogLevel.WARN)
        logger.error(category, "error")
        onLogLevel.onNext(LogLevel.ERROR)
        logger.error(category, "error")

        verify(exactly = 5) {
            handler.log(category, "error", LogLevel.ERROR)
        }
    }

    @Test
    fun nothing_Is_Logged_When_Level_Is_Silent() {
        val logger = LoggerImpl(handler, onLogLevel)

        onLogLevel.onNext(LogLevel.SILENT)
        logger.trace(category, "trace")
        logger.debug(category, "debug")
        logger.info(category, "info")
        logger.warn(category, "warn")
        logger.error(category, "error")

        verify {
            handler wasNot Called
        }
    }

    @Test
    fun logger_Formats_LogMessages_When_Args_Provided() {
        val dataObject = DataObject.create {
            put("key", "value")
        }
        val logger = LoggerImpl(handler, onLogLevel)
        onLogLevel.onNext(LogLevel.TRACE)

        logger.trace(category, "Message %s formatted", dataObject)

        verify {
            handler.log(category, "Message $dataObject formatted", LogLevel.TRACE)
        }
    }

    @Test
    fun logger_Formats_LogMessages_When_Multiple_Args_Provided() {
        val string = "string"
        val ten = 10
        val pi = 3.1415
        val logger = LoggerImpl(handler, onLogLevel)
        onLogLevel.onNext(LogLevel.TRACE)

        logger.trace(category, "string:%s int:%d, double:%.2f", string, ten, pi)

        verify {
            handler.log(category, "string:string int:10, double:3.14", LogLevel.TRACE)
        }
    }

    @Test
    fun logger_With_Supplier_Does_Not_Evaluate_Supplier_If_Level_Too_Low() {
        val logger = LoggerImpl(handler, onLogLevel)
        val supplier = mockk<() -> String>()

        onLogLevel.onNext(LogLevel.SILENT)
        logger.trace(category, supplier)
        logger.debug(category, supplier)
        logger.info(category, supplier)
        logger.warn(category, supplier)
        logger.error(category, supplier)

        verify {
            supplier wasNot Called
        }
    }

    @Test
    fun logger_With_Supplier_Does_Not_Evaluate_Supplier_If_Buffering() {
        val logger = LoggerImpl(handler, onLogLevel)
        val supplier = mockk<() -> String>()

        logger.trace(category, supplier)
        logger.debug(category, supplier)
        logger.info(category, supplier)
        logger.warn(category, supplier)
        logger.error(category, supplier)

        verify {
            supplier wasNot Called
        }
    }

    @Test
    fun logger_With_Supplier_Does_Evaluate_Supplier_When_LogLevel_Correct() {
        val logger = LoggerImpl(handler, onLogLevel)
        val supplier = { "message" }

        onLogLevel.onNext(LogLevel.TRACE)
        logger.trace(category, supplier)
        logger.debug(category, supplier)
        logger.info(category, supplier)
        logger.warn(category, supplier)
        logger.error(category, supplier)

        verify {
            handler.log(category, "message", LogLevel.TRACE)
            handler.log(category, "message", LogLevel.DEBUG)
            handler.log(category, "message", LogLevel.INFO)
            handler.log(category, "message", LogLevel.WARN)
            handler.log(category, "message", LogLevel.ERROR)
        }
    }

    @Test
    fun logger_With_LogLevel_And_Supplier_Does_Not_Evaluate_Supplier_If_Level_Too_Low() {
        val logger = LoggerImpl(handler, onLogLevel)
        val supplier = mockk<() -> String>()

        onLogLevel.onNext(LogLevel.SILENT)
        logger.log(LogLevel.TRACE, category, supplier)
        logger.log(LogLevel.DEBUG, category, supplier)
        logger.log(LogLevel.INFO, category, supplier)
        logger.log(LogLevel.WARN, category, supplier)
        logger.log(LogLevel.ERROR, category, supplier)

        verify {
            supplier wasNot Called
        }
    }

    @Test
    fun logger_With_LogLevel_And_Supplier_Does_Not_Evaluate_Supplier_If_Buffering() {
        val logger = LoggerImpl(handler, onLogLevel)
        val supplier = mockk<() -> String>()

        logger.log(LogLevel.TRACE, category, supplier)
        logger.log(LogLevel.DEBUG, category, supplier)
        logger.log(LogLevel.INFO, category, supplier)
        logger.log(LogLevel.WARN, category, supplier)
        logger.log(LogLevel.ERROR, category, supplier)

        verify {
            supplier wasNot Called
        }
    }

    @Test
    fun logger_With_LogLevel_And_Supplier_Does_Evaluate_Supplier_When_LogLevel_Correct() {
        val logger = LoggerImpl(handler, onLogLevel)
        val supplier = { "message" }

        onLogLevel.onNext(LogLevel.TRACE)
        logger.log(LogLevel.TRACE, category, supplier)
        logger.log(LogLevel.DEBUG, category, supplier)
        logger.log(LogLevel.INFO, category, supplier)
        logger.log(LogLevel.WARN, category, supplier)
        logger.log(LogLevel.ERROR, category, supplier)

        verify {
            handler.log(category, "message", LogLevel.TRACE)
            handler.log(category, "message", LogLevel.DEBUG)
            handler.log(category, "message", LogLevel.INFO)
            handler.log(category, "message", LogLevel.WARN)
            handler.log(category, "message", LogLevel.ERROR)
        }
    }

    @Test
    fun logger_With_LogLevel_Silent_Does_Nothing() {
        val logger = LoggerImpl(handler, onLogLevel)
        val supplier = mockk<() -> String>()

        onLogLevel.onNext(LogLevel.TRACE)
        logger.log(LogLevel.SILENT, category, "")
        logger.log(LogLevel.SILENT, category, "%s", "")
        logger.log(LogLevel.SILENT, category, supplier)

        verify {
            handler wasNot Called
        }
    }
}
