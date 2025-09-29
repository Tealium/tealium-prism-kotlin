package com.tealium.core.internal.modules.time

import com.tealium.core.BuildConfig
import com.tealium.core.api.Modules
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext

class TimeDataModule(
    private val timeDataSupplier: TimeDataSupplier
) : Collector {

    override fun collect(dispatchContext: DispatchContext): DataObject {
        val timestampMilliseconds =
            dispatchContext.initialData.getLong(Dispatch.Keys.TEALIUM_TIMESTAMP_EPOCH_MILLISECONDS)
                ?: return DataObject.EMPTY_OBJECT

        return timeDataSupplier.getTimeData(timestampMilliseconds)
    }

    override val id: String = Modules.Types.TIME_DATA
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    object Factory : ModuleFactory {
        override val moduleType: String = Modules.Types.TIME_DATA

        override fun create(moduleId: String, context: TealiumContext, configuration: DataObject): Module? {
            val timeDataSupplier = TimeDataSupplier.getInstance()
            return TimeDataModule(timeDataSupplier)
        }
    }
}
