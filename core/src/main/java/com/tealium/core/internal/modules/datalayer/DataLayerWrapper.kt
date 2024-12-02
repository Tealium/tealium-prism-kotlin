package com.tealium.core.internal.modules.datalayer

import com.tealium.core.api.Tealium
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.modules.DataLayer
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.pubsub.Subscribable

class DataLayerWrapper(
    private val moduleProxy: ModuleProxy<DataLayerModule>
) : DataLayer {
    constructor(
        tealium: Tealium,
    ) : this(tealium.createModuleProxy(DataLayerModule::class.java))

    override fun transactionally(block: TealiumCallback<DataStore.Editor>) {
        moduleProxy.getModule { dataLayer ->
            if (dataLayer == null) return@getModule

            dataLayer.dataStore.edit().use { editor ->
                editor.apply {
                    block.onComplete(this)
                }
            }
        }
    }

    override fun put(data: DataObject) =
        putAllInternal(data, null)

    override fun put(data: DataObject, expiry: Expiry) =
        putAllInternal(data, expiry)

    private fun putAllInternal(data: DataObject, expiry: Expiry?) {
        moduleProxy.getModule { dataLayer ->
            if (dataLayer == null) return@getModule

            dataLayer.dataStore.edit()
                .putAll(data, expiry ?: dataLayer.defaultExpiry)
                .commit()
        }
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

    private fun putInternal(key: String, value: DataItem, expiry: Expiry?) {
        moduleProxy.getModule { dataLayer ->
            if (dataLayer == null) return@getModule

            dataLayer.dataStore.edit()
                .put(key, value, expiry ?: dataLayer.defaultExpiry)
                .commit()
        }
    }

    override fun get(key: String, callback: TealiumCallback<DataItem?>) {
        moduleProxy.getModule { dataLayer ->
            val value = dataLayer?.dataStore?.get(key)
            callback.onComplete(value)
        }
    }

    override fun getAll(callback: TealiumCallback<DataObject?>) {
        moduleProxy.getModule { dataLayer ->
            val data = dataLayer?.dataStore?.getAll()
            callback.onComplete(data)
        }
    }

    override fun <T> get(
        key: String,
        converter: DataItemConverter<T>,
        callback: TealiumCallback<T?>
    ) = get(key) { item ->
        if (item == null) {
            callback.onComplete(null)
            return@get
        }

        callback.onComplete(converter.convert(item))
    }

    override fun getString(key: String, callback: TealiumCallback<String?>) = get(key) {
        callback.onComplete(it?.getString())
    }

    override fun getInt(key: String, callback: TealiumCallback<Int?>) = get(key) {
        callback.onComplete(it?.getInt())
    }

    override fun getDouble(key: String, callback: TealiumCallback<Double?>) = get(key) {
        callback.onComplete(it?.getDouble())
    }

    override fun getLong(key: String, callback: TealiumCallback<Long?>) = get(key) {
        callback.onComplete(it?.getLong())
    }

    override fun getBoolean(key: String, callback: TealiumCallback<Boolean?>) = get(key) {
        callback.onComplete(it?.getBoolean())
    }

    override fun getDataList(key: String, callback: TealiumCallback<DataList?>) = get(key) {
        callback.onComplete(it?.getDataList())
    }

    override fun getDataObject(key: String, callback: TealiumCallback<DataObject?>) = get(key) {
        callback.onComplete(it?.getDataObject())
    }

    override fun remove(key: String) = remove(listOf(key))

    override fun remove(keys: List<String>) {
        moduleProxy.getModule { dataLayer ->
            if (dataLayer == null) return@getModule

            dataLayer.dataStore.edit()
                .remove(keys)
                .commit()
        }
    }

    override fun clear() {
        moduleProxy.getModule { dataLayer ->
            if (dataLayer == null) return@getModule

            dataLayer.dataStore.edit()
                .clear()
                .commit()
        }
    }

    override val onDataUpdated: Subscribable<DataObject>
        get() = moduleProxy.observeModule { it.dataStore.onDataUpdated }

    override val onDataRemoved: Subscribable<List<String>>
        get() = moduleProxy.observeModule { it.dataStore.onDataRemoved }
}
