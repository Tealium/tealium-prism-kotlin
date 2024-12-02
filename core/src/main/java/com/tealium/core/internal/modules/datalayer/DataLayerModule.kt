package com.tealium.core.internal.modules.datalayer

import com.tealium.core.BuildConfig
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry

class DataLayerModule(
    val dataStore: DataStore,
    val defaultExpiry: Expiry = DEFAULT_EXPIRY
) : Collector {

    override val id: String
        get() = Companion.id
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    override fun collect(): DataObject {
        return dataStore.getAll()
    }

    companion object : ModuleFactory {
        val DEFAULT_EXPIRY = Expiry.FOREVER

        override val id: String
            get() = "DataLayer"

        override fun create(context: TealiumContext, settings: DataObject): Module? {
            val dataStore = context.storageProvider.getModuleStore(this)
            return DataLayerModule(dataStore)
        }
    }
}