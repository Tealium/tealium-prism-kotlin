package com.tealium.core.internal.network

import com.tealium.core.api.data.TealiumDeserializable
import com.tealium.core.api.data.TealiumSerializable
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.api.network.ResourceCache
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry

class ResourceCacheImpl<T: TealiumSerializable>(
    private val dataStore: DataStore,
    private val fileName: String,
    private val deserializer: TealiumDeserializable<T>
): ResourceCache<T> {

    private val etagKey: String
        get() = "${fileName}_etag"

    override val resource: T?
        get() = dataStore.get(fileName, deserializer)

    override val etag: String?
        get() = dataStore.getString(etagKey)

    override fun saveResource(resource: T, etag: String?) {
        val editor = dataStore.edit()
            .put(fileName, resource.asTealiumValue(), Expiry.FOREVER)

        if (etag != null) {
            editor.put(etagKey, TealiumValue.string(etag), Expiry.FOREVER)
        } else {
            editor.remove(etagKey)
        }

        editor.commit()
    }
}