package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.modules.DataLayer
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.api.pubsub.Subscribable
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.pubsub.Observable

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

    override val id: String
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

        override val id: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: TealiumBundle): Module? {
            val dataStore = context.storageProvider.getModuleStore(this)
            return DataLayerImpl(dataStore)
        }
    }
}
