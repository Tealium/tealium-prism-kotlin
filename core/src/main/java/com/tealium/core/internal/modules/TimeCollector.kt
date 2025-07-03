package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext
import com.tealium.core.internal.utils.DateFormatter
import com.tealium.core.internal.utils.DateUtils
import java.time.Instant
import java.time.ZoneId

class TimeCollector(
    private val dateFormatter: DateFormatter = DateUtils
) : Collector {

    override fun collect(dispatchContext: DispatchContext): DataObject {
        val timestampMilliseconds =
            dispatchContext.initialData.getLong(Dispatch.Keys.TEALIUM_TIMESTAMP_EPOCH_MILLISECONDS)
                ?: return DataObject.EMPTY_OBJECT

        val date = Instant.ofEpochMilli(timestampMilliseconds)
        val timeZone = ZoneId.systemDefault()

        return DataObject.create {
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_UTC, dateFormatter.iso8601Utc(date))
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL, dateFormatter.iso8601Local(date, timeZone))
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL_WITH_OFFSET, dateFormatter.iso8601LocalWithOffset(date, timeZone))
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_OFFSET, calculateOffsetInHours(date, timeZone))
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_TIMEZONE, timeZone.id)
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_EPOCH_MILLISECONDS, date.toEpochMilli())
            put(Dispatch.Keys.TEALIUM_TIMESTAMP_EPOCH, date.epochSecond)
        }
    }

    private fun calculateOffsetInHours(instant: Instant, timeZone: ZoneId): Double =
        timeZone.rules.getOffset(instant).totalSeconds / 3600.0

    override val id: String
        get() = Factory.id
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    object Factory: ModuleFactory {
        override val id: String
            get() = "Time"

        override fun create(context: TealiumContext, configuration: DataObject): Module? =
            TimeCollector()
    }
}