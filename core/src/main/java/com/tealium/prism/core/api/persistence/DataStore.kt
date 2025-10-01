package com.tealium.prism.core.api.persistence

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.persistence.DataStore.Editor.EditorClosedException
import com.tealium.prism.core.api.pubsub.Observable

/**
 * Generic data storage for storing and retrieving [DataItem] objects.
 *
 * Implementations are not guaranteed to be persistent. For instance, in cases where there may be
 * insufficient storage space on the device, or other reasons such as write permissions etc.
 *
 * Stored data requires an [Expiry] to be provided when storing, and expired data will not be
 * included in any retrieval operations; that is, expired data won't be returned by [get] or [getAll]
 * but it will also not be included in any aggregate methods such as [keys] or [count]
 *
 * @see [Expiry]
 */
interface DataStore : ReadableDataStore, Iterable<Map.Entry<String, DataItem>> {

    /**
     * Enables editing multiple entries in the module storage in a transactional way.
     *
     * All updating/reading methods will throw [EditorClosedException] if the [Editor] has already
     * been closed.
     */
    interface Editor: ReadableDataStore, AutoCloseable {

        /**
         * Adds all key-value pairs from the [dataObject] into the storage.
         *
         * @param dataObject A [DataObject] containing the key-value pairs to be stored
         * @param expiry The time frame for this data to remain stored
         *
         * @return Editor to continue editing this storage
         */
        fun putAll(dataObject: DataObject, expiry: Expiry): Editor

        /**
         * Adds a single key-value pair to the storage
         *
         * @param key The key to store the [value] under
         * @param value The value to be stored
         * @param expiry The time frame for this data to remain stored
         *
         * @return Editor to continue editing this storage
         */
        fun put(key: String, value: DataItem, expiry: Expiry): Editor

        /**
         * Adds a single key-value pair to the storage
         *
         * @param key The key to store the [value] under
         * @param value The value to be stored
         * @param expiry The time frame for this data to remain stored
         *
         * @return Editor to continue editing this storage
         */
        fun put(key: String, value: DataItemConvertible, expiry: Expiry): Editor =
            put(key, value.asDataItem(), expiry)

        /**
         * Adds a single key-value pair to the storage
         *
         * @param key The key to store the [value] under
         * @param value The value to be stored
         * @param expiry The time frame for this data to remain stored
         *
         * @return Editor to continue editing this storage
         */
        fun put(key: String, value: String, expiry: Expiry): Editor =
            put(key, DataItem.string(value), expiry)

        /**
         * Adds a single key-value pair to the storage
         *
         * @param key The key to store the [value] under
         * @param value The value to be stored
         * @param expiry The time frame for this data to remain stored
         *
         * @return Editor to continue editing this storage
         */
        fun put(key: String, value: Int, expiry: Expiry): Editor =
            put(key, DataItem.int(value), expiry)

        /**
         * Adds a single key-value pair to the storage
         *
         * @param key The key to store the [value] under
         * @param value The value to be stored
         * @param expiry The time frame for this data to remain stored
         *
         * @return Editor to continue editing this storage
         */
        fun put(key: String, value: Double, expiry: Expiry): Editor =
            put(key, DataItem.double(value), expiry)

        /**
         * Adds a single key-value pair to the storage
         *
         * @param key The key to store the [value] under
         * @param value The value to be stored
         * @param expiry The time frame for this data to remain stored
         *
         * @return Editor to continue editing this storage
         */
        fun put(key: String, value: Long, expiry: Expiry): Editor =
            put(key, DataItem.long(value), expiry)

        /**
         * Adds a single key-value pair to the storage
         *
         * @param key The key to store the [value] under
         * @param value The value to be stored
         * @param expiry The time frame for this data to remain stored
         *
         * @return Editor to continue editing this storage
         */
        fun put(key: String, value: Boolean, expiry: Expiry): Editor =
            put(key, DataItem.boolean(value), expiry)

        /**
         * Adds a single key-value pair to the storage
         *
         * @param key The key to store the [value] under
         * @param value The value to be stored
         * @param expiry The time frame for this data to remain stored
         *
         * @return Editor to continue editing this storage
         */
        fun put(key: String, value: DataList, expiry: Expiry): Editor =
            put(key, value.asDataItem(), expiry)

        /**
         * Adds a single key-value pair to the storage
         *
         * @param key The key to store the [value] under
         * @param value The value to be stored
         * @param expiry The time frame for this data to remain stored
         *
         * @return Editor to continue editing this storage
         */
        fun put(key: String, value: DataObject, expiry: Expiry): Editor =
            put(key, value.asDataItem(), expiry)

        /**
         * Removes and individual key from storage
         *
         * @param key the key to remove from storage
         *
         * @return Editor to continue editing this storage
         */
        fun remove(key: String): Editor

        /**
         * Removes multiple keys from storage
         *
         * @param keys The list of keys to remove from storage
         *
         * @return Editor to continue editing this storage
         */
        fun remove(keys: List<String>): Editor

        /**
         * Clears all entries from storage before then adding any any key-value pairs
         * added to this editor.
         *
         * @return Editor to continue editing this storage
         */
        fun clear(): Editor

        /**
         * Writes the updates to disk.
         *
         * Calling this method multiple times is not supported, and subsequent executions
         * are ignored.
         */
        @Throws(PersistenceException::class)
        fun commit()

        /**
         * This exception signifies that the [Editor] was interacted with after it has been closed.
         */
        class EditorClosedException(message: String): TealiumException(message)
    }

    /**
     * Returns an Editor able to mutate the data in this ModuleStorage
     *
     * @return Editor to update the stored data
     */
    fun edit(): Editor

    /**
     * Flow of key-value pairs from this [DataStore] that have been updated.
     */
    val onDataUpdated: Observable<DataObject>

    /**
     * Flow of keys from this [DataStore] that have been removed.
     */
    val onDataRemoved: Observable<List<String>>

}