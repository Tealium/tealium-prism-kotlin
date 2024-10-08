package com.tealium.core.internal.network

import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.network.ResourceCache
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry

class ResourceCacheImpl<T: DataItemConvertible>(
    private val dataStore: DataStore,
    private val fileName: String,
    private val converter: DataItemConverter<T>
): ResourceCache<T> {

    private val etagKey: String
        get() = "${fileName}_etag"

    override val resource: T?
        get() = dataStore.get(fileName, converter)

    override val etag: String?
        get() = dataStore.getString(etagKey)

    override fun saveResource(resource: T, etag: String?) {
        val editor = dataStore.edit()
            .put(fileName, resource.asDataItem(), Expiry.FOREVER)

        if (etag != null) {
            editor.put(etagKey, DataItem.string(etag), Expiry.FOREVER)
        } else {
            editor.remove(etagKey)
        }

        editor.commit()
    }
}