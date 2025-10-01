package com.tealium.prism.core.api.network

import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.persistence.DataStore

/**
 * Utility class to automatically manage reading and writing resources from a [DataStore] as well
 * as providing `etag` storage support.
 */
interface ResourceCache<T: DataItemConvertible> {

    /**
     * Reads the resource from disk and and returns it after converting it using the provide
     * [DataItemConverter]
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
