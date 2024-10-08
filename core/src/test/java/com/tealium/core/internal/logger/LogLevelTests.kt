package com.tealium.core.internal.logger

import com.tealium.core.api.logger.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogLevelTests {

    @Test
    fun verifyValidSerDe() {
        val traceSer = LogLevel.TRACE.asDataItem()
        val traceDe = LogLevel.Converter.convert(traceSer)

        assertEquals(LogLevel.TRACE, traceDe)

        val debugSer = LogLevel.DEBUG.asDataItem()
        val debugDe = LogLevel.Converter.convert(debugSer)

        assertEquals(LogLevel.DEBUG, debugDe)

        val infoSer = LogLevel.INFO.asDataItem()
        val infoDe = LogLevel.Converter.convert(infoSer)

        assertEquals(LogLevel.INFO, infoDe)

        val warnSer = LogLevel.WARN.asDataItem()
        val warnDe = LogLevel.Converter.convert(warnSer)

        assertEquals(LogLevel.WARN, warnDe)

        val errorSer = LogLevel.ERROR.asDataItem()
        val errorDe = LogLevel.Converter.convert(errorSer)

        assertEquals(LogLevel.ERROR, errorDe)

        val silentSer = LogLevel.SILENT.asDataItem()
        val silentDe = LogLevel.Converter.convert(silentSer)

        assertEquals(LogLevel.SILENT, silentDe)
    }
}