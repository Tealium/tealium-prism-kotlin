package com.tealium.core.internal

import com.tealium.core.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogLevelTests {

    @Test
    fun verifyValidSerDe() {
        val traceSer = LogLevel.TRACE.asTealiumValue()
        val traceDe = LogLevel.Deserializer.deserialize(traceSer)

        assertEquals(LogLevel.TRACE, traceDe)

        val debugSer = LogLevel.DEBUG.asTealiumValue()
        val debugDe = LogLevel.Deserializer.deserialize(debugSer)

        assertEquals(LogLevel.DEBUG, debugDe)

        val infoSer = LogLevel.INFO.asTealiumValue()
        val infoDe = LogLevel.Deserializer.deserialize(infoSer)

        assertEquals(LogLevel.INFO, infoDe)

        val warnSer = LogLevel.WARN.asTealiumValue()
        val warnDe = LogLevel.Deserializer.deserialize(warnSer)

        assertEquals(LogLevel.WARN, warnDe)

        val errorSer = LogLevel.ERROR.asTealiumValue()
        val errorDe = LogLevel.Deserializer.deserialize(errorSer)

        assertEquals(LogLevel.ERROR, errorDe)

        val silentSer = LogLevel.SILENT.asTealiumValue()
        val silentDe = LogLevel.Deserializer.deserialize(silentSer)

        assertEquals(LogLevel.SILENT, silentDe)
    }
}