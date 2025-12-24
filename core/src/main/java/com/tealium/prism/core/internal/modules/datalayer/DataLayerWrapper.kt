package com.tealium.prism.core.internal.modules.datalayer

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.modules.DataLayer
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.pubsub.Subscribable

class DataLayerWrapper(
    private val moduleProxy: ModuleProxy<DataLayerModule>
) : DataLayer {
    constructor(
        tealium: Tealium,
    ) : this(tealium.createModuleProxy(DataLayerModule::class.java))

    override fun transactionally(block: Callback<DataStore.Editor>) =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.edit().use { editor ->
                editor.apply {
                    block.onComplete(this)
                }
            }
            Unit
        }

    override fun put(data: DataObject) =
        putAllInternal(data, null)

    override fun put(data: DataObject, expiry: Expiry) =
        putAllInternal(data, expiry)

    private fun putAllInternal(data: DataObject, expiry: Expiry?) =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.edit()
                .putAll(data, expiry ?: dataLayer.defaultExpiry)
                .commit()
        }

    override fun put(key: String, value: DataItem) =
        putInternal(key, value, null)

    override fun put(key: String, value: DataItem, expiry: Expiry) =
        putInternal(key, value, expiry)

    override fun put(key: String, value: DataItemConvertible) =
        putInternal(key, value.asDataItem(), null)

    override fun put(key: String, value: DataItemConvertible, expiry: Expiry) =
        putInternal(key, value.asDataItem(), expiry)

    override fun put(key: String, value: String) =
        putInternal(key, DataItem.string(value), null)

    override fun put(key: String, value: String, expiry: Expiry) =
        putInternal(key, DataItem.string(value), expiry)

    override fun put(key: String, value: Int) =
        putInternal(key, DataItem.int(value), null)

    override fun put(key: String, value: Int, expiry: Expiry) =
        putInternal(key, DataItem.int(value), expiry)

    override fun put(key: String, value: Double) =
        putInternal(key, DataItem.double(value), null)

    override fun put(key: String, value: Double, expiry: Expiry) =
        putInternal(key, DataItem.double(value), expiry)

    override fun put(key: String, value: Long) =
        putInternal(key, DataItem.long(value), null)

    override fun put(key: String, value: Long, expiry: Expiry) =
        putInternal(key, DataItem.long(value), expiry)

    override fun put(key: String, value: Boolean) =
        putInternal(key, DataItem.boolean(value), null)

    override fun put(key: String, value: Boolean, expiry: Expiry) =
        putInternal(key, DataItem.boolean(value), expiry)

    private fun putInternal(key: String, value: DataItem, expiry: Expiry?) =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.edit()
                .put(key, value, expiry ?: dataLayer.defaultExpiry)
                .commit()
        }

    override fun get(key: String) =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.get(key)
        }

    override fun getAll() =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.getAll()
        }

    override fun <T> get(key: String, converter: DataItemConverter<T>) =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.get(key, converter)
        }

    override fun getString(key: String) =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.getString(key)
        }

    override fun getInt(key: String) =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.getInt(key)
        }

    override fun getDouble(key: String) =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.getDouble(key)
        }

    override fun getLong(key: String) =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.getLong(key)
        }

    override fun getBoolean(key: String) =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.getBoolean(key)
        }

    override fun getDataList(key: String) =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.getDataList(key)
        }

    override fun getDataObject(key: String) =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.getDataObject(key)
        }

    override fun remove(key: String) = remove(listOf(key))

    override fun remove(keys: List<String>) =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.edit()
                .remove(keys)
                .commit()
        }

    override fun clear() =
        moduleProxy.executeModuleTask { dataLayer ->
            dataLayer.dataStore.edit()
                .clear()
                .commit()
        }

    override val onDataUpdated: Subscribable<DataObject>
        get() = moduleProxy.observeModule { it.dataStore.onDataUpdated }

    override val onDataRemoved: Subscribable<List<String>>
        get() = moduleProxy.observeModule { it.dataStore.onDataRemoved }
}
