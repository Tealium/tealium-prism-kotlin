package com.tealium.core.internal

import com.tealium.core.BuildConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.Collector
import com.tealium.core.api.DataLayer
import com.tealium.core.api.DataStore
import com.tealium.core.api.Expiry
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleManager
import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.api.listeners.Subscribable
import com.tealium.core.api.listeners.TealiumCallback
import com.tealium.core.internal.modules.ModuleProxy
import com.tealium.core.internal.observables.Observable

class DataLayerWrapper(
    private val moduleProxy: ModuleProxy<DataLayerImpl>
) : DataLayer {
    constructor(
        moduleManager: ModuleManager
    ) : this(ModuleProxy(DataLayerImpl::class.java, moduleManager))

    override fun edit(block: TealiumCallback<DataStore.Editor>) {
        moduleProxy.getModule { dataLayer ->
            dataLayer?.edit {
                block.onComplete(it)
            }
        }
    }

    override fun put(bundle: TealiumBundle, expiry: Expiry) {
        moduleProxy.getModule { dataLayer ->
            dataLayer?.edit {
                it.putAll(bundle, expiry)
            }
        }
    }

    override fun get(key: String, callback: TealiumCallback<TealiumValue?>) {
        moduleProxy.getModule { dataLayer ->
            val value = dataLayer?.get(key)
            callback.onComplete(value)
        }
    }

    override fun remove(key: String) {
        moduleProxy.getModule { dataLayer ->
            dataLayer?.edit {
                remove(key)
            }
        }
    }

    override val onDataUpdated: Subscribable<TealiumBundle>
        get() = moduleProxy.getModule()
            .flatMap { it.onDataUpdated }

    override val onDataRemoved: Subscribable<List<String>>
        get() = moduleProxy.getModule()
            .flatMap { it.onDataRemoved }
}

class DataLayerImpl(
    private val dataStore: DataStore,
) : Collector {

    val onDataUpdated: Observable<TealiumBundle>
        get() = dataStore.onDataUpdated
    val onDataRemoved: Observable<List<String>>
        get() = dataStore.onDataRemoved

    override val name: String
        get() = moduleName
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    fun edit(block: (DataStore.Editor) -> Unit) {
        dataStore.edit()
            .apply(block)
            .commit()
    }

    override fun collect(): TealiumBundle {
        return dataStore.getAll()
    }

    fun get(key: String): TealiumValue? {
        return dataStore.get(key)
    }

    companion object : ModuleFactory {
        private const val moduleName = "DataLayer"

        override val name: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: ModuleSettings): Module? {
            val dataStore = context.storageProvider.getModuleStore(this)
            return DataLayerImpl(dataStore)
        }
    }
}
