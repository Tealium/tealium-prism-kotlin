package com.tealium.core.internal.modules.time

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.misc.DateFormatter
import com.tealium.core.api.misc.DateUtils
import java.util.Date
import java.util.TimeZone

/**
 * [TimeDataSupplier] implementation backed by the legacy [Date] class.
 */
class DateTimeDataSupplier(
    private val dateFormatter: DateFormatter = DateUtils
) : TimeDataSupplier {

    override fun getTimeData(timestampEpochMilliseconds: Long): DataObject {
        val date = Date(timestampEpochMilliseconds)
        val timeZone = TimeZone.getDefault()

        return DataObject.create {
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_EPOCH_MILLISECONDS, timestampEpochMilliseconds)
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_EPOCH, timestampEpochMilliseconds / 1000)
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_UTC, dateFormatter.iso8601Utc(date))
            put(
                Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL,
                dateFormatter.iso8601Local(date, timeZone)
            )
            put(
                Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL_WITH_OFFSET,
                dateFormatter.iso8601LocalWithOffset(date, timeZone)
            )
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_OFFSET, dateFormatter.offsetInHours(date, timeZone))
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_TIMEZONE, timeZone.id)
        }
    }
}