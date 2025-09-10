package com.tealium.core.internal.modules.time

import android.os.Build
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.tracking.Dispatch

/**
 * Utility implementation for consistently generating formatted timestamps and other related time data.
 *
 * Implementations are expected to supply the following data by default
 *  - [Dispatch.Keys.TEALIUM_TIMESTAMP_EPOCH]
 *  - [Dispatch.Keys.TEALIUM_TIMESTAMP_EPOCH_MILLISECONDS]
 *  - [Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL]
 *  - [Dispatch.Keys.TEALIUM_TIMESTAMP_LOCAL_WITH_OFFSET]
 *  - [Dispatch.Keys.TEALIUM_TIMESTAMP_OFFSET]
 *  - [Dispatch.Keys.TEALIUM_TIMESTAMP_TIMEZONE]
 *  - [Dispatch.Keys.TEALIUM_TIMESTAMP_UTC]
 */
interface TimeDataSupplier {

    /**
     *  Returns a [DataObject] containing all formatted timestamps and time zone data.
     */
    fun getTimeData(timestampEpochMilliseconds: Long): DataObject

    companion object {

        /**
         * Returns a relevant [TimeDataSupplier] for the platform Android version. If the `java.time`
         * API's are supported, then that will be preferred. Otherwise an instance backed by the
         * legacy `java.util.Date` API's will be returned.
         */
        @JvmStatic
        fun getInstance() : TimeDataSupplier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            InstantTimeDataSupplier()
        } else {
            DateTimeDataSupplier()
        }
    }
}