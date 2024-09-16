package com.tealium.core.api.network

import com.tealium.core.api.data.TealiumDeserializable
import com.tealium.core.api.data.TealiumSerializable
import com.tealium.core.api.persistence.DataStore

/**
 * Utility class to automatically manage reading and writing resources from a [DataStore] as well
 * as providing `etag` storage support.
 */
interface ResourceCache<T: TealiumSerializable> {

    /**
     * Reads the resource from disk and and returns it after deserializing it using the provide
     * [TealiumDeserializable]
     */
    val resource: T?

    /**
     * Reads the `etag` that's been stored alongside the resource, if one is available.
     */
    val etag: String?

    /**
     * Saves the resource and its `etag` into a [DataStore] for reading later.
     *
     * @param resource The object to store in the [DataStore]
     * @param etag The `etag` for this object; null values will also remove a previously stored `etag`
     */
    fun saveResource(resource: T, etag: String?)
}
