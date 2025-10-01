package com.tealium.prism.core.internal.modules.time

import android.os.Build
import androidx.annotation.RequiresApi
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.misc.DateFormatter
import com.tealium.prism.core.api.misc.DateUtils
import java.time.Instant
import java.time.ZoneId

/**
 * [TimeDataSupplier] implementation backed by the [Instant] class, and therefore only
 * supported on API levels 26 (O) and above.
 */
@RequiresApi(Build.VERSION_CODES.O)
class InstantTimeDataSupplier(
    private val dateFormatter: DateFormatter = DateUtils
) : TimeDataSupplier {

    override fun getTimeData(timestampEpochMilliseconds: Long): DataObject {
        val instant = Instant.ofEpochMilli(timestampEpochMilliseconds)
        val timeZone = ZoneId.systemDefault()

        return DataObject.create {
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_EPOCH_MILLISECONDS, instant.toEpochMilli())
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_EPOCH, instant.epochSecond)
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_UTC, dateFormatter.iso8601Utc(instant))
            put(
                Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL,
                dateFormatter.iso8601Local(instant, timeZone)
            )
            put(
                Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL_WITH_OFFSET,
                dateFormatter.iso8601LocalWithOffset(instant, timeZone)
            )
            put(
                Dispatch.Keys.TEALIUM_TIMESTAMP_OFFSET,
                dateFormatter.offsetInHours(instant, timeZone)
            )
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_TIMEZONE, timeZone.id)
        }
    }
}