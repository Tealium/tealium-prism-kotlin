package com.tealium.prism.core.internal.modules.datalayer

import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.settings.modules.DataLayerSettingsBuilder
import com.tealium.prism.core.api.tracking.DispatchContext

class DataLayerModule(
    val dataStore: DataStore,
    val defaultExpiry: Expiry = DEFAULT_EXPIRY
) : Collector {

    override val id: String = Modules.Types.DATA_LAYER
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    override fun collect(dispatchContext: DispatchContext): DataObject {
        return dataStore.getAll()
    }

    companion object {
        val DEFAULT_EXPIRY = Expiry.FOREVER
    }

    class Factory(
        settings: DataObject? = null
    ) : ModuleFactory {

        override fun canBeDisabled(): Boolean = false

        private val enforcedSettings: List<DataObject> =
            settings?.let { listOf(it) } ?: emptyList()

        constructor(settingsBuilder: DataLayerSettingsBuilder) : this(settingsBuilder.build())

        override fun getEnforcedSettings(): List<DataObject> = enforcedSettings
        override val moduleType: String = Modules.Types.DATA_LAYER

        override fun create(moduleId: String, context: TealiumContext, configuration: DataObject): Module? {
            val dataStore = context.storageProvider.getModuleStore(moduleId)
            return DataLayerModule(dataStore)
        }
    }
}