package com.tealium.prism.core.internal.modules.time

import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.settings.modules.TimeDataSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext

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

    class Factory(
        settings: DataObject? = null
    ) : ModuleFactory {

        private val enforcedSettings: List<DataObject> =
            settings?.let { listOf(it) } ?: emptyList()

        constructor(settingsBuilder: TimeDataSettingsBuilder) : this(settingsBuilder.build())

        override fun getEnforcedSettings(): List<DataObject> = enforcedSettings
        override val moduleType: String = Modules.Types.TIME_DATA

        override fun create(moduleId: String, context: TealiumContext, configuration: DataObject): Module? {
            val timeDataSupplier = TimeDataSupplier.getInstance()
            return TimeDataModule(timeDataSupplier)
        }
    }
}
