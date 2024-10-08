package com.tealium.core.api.persistence

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.pubsub.Observable

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
interface DataStore : Iterable<Map.Entry<String, DataItem>> {

    /**
     * Enables editing multiple entries in the module storage in a transactional way.
     */
    interface Editor {

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

    }

    /**
     * Returns an Editor able to mutate the data in this ModuleStorage
     *
     * @return Editor to update the stored data
     */
    fun edit(): Editor

    /**
     * Gets the [DataItem] stored at the given [key] if there is one
     *
     * @param key The key for the required value
     *
     * @return The [DataItem] or null
     */
    fun get(key: String): DataItem?

    /**
     * Gets the [DataItem] stored at the given [key] if there is one, and uses the given
     * [converter] to translate it into an instance of type [T]
     *
     * @param key The key for the required value
     * @param converter The [DataItemConverter] implementation for converting the [DataItem] to the required type.
     *
     * @return The [DataItem] or null
     */
    fun <T> get(key: String, converter: DataItemConverter<T>): T?

    /**
     * Gets the String stored at the given [key] if there is one
     *
     * @param key The key for the required value
     *
     * @return The [String] or null
     */
    fun getString(key: String): String? = get(key)?.getString()

    /**
     * Gets the Int stored at the given [key] if there is one
     *
     * @param key The key for the required value
     *
     * @return The [Int] or null
     */
    fun getInt(key: String): Int? = get(key)?.getInt()

    /**
     * Gets the Double stored at the given [key] if there is one
     *
     * @param key The key for the required value
     *
     * @return The [Double] or null
     */
    fun getDouble(key: String): Double? = get(key)?.getDouble()

    /**
     * Gets the Long stored at the given [key] if there is one
     *
     * @param key The key for the required value
     *
     * @return The [Long] or null
     */
    fun getLong(key: String): Long? = get(key)?.getLong()

    /**
     * Gets the Boolean stored at the given [key] if there is one
     *
     * @param key The key for the required value
     *
     * @return The [Boolean] or null
     */
    fun getBoolean(key: String): Boolean? = get(key)?.getBoolean()

    /**
     * Gets the [DataList] stored at the given [key] if there is one
     *
     * @param key The key for the required value
     *
     * @return The [DataList] or null
     */
    fun getDataList(key: String): DataList? = get(key)?.getDataList()

    /**
     * Gets the [DataObject] stored at the given [key] if there is one
     *
     * @param key The key for the required value
     *
     * @return The [DataObject] or null
     */
    fun getDataObject(key: String): DataObject? = get(key)?.getDataObject()

    /**
     * Gets the entire [DataObject] containing all data stored.
     *
     * @return The [DataObject] containing all key-value pairs
     */
    fun getAll(): DataObject

    /**
     * Returns all keys stored in this DataStore
     *
     * @return A list of all string keys present in the DataStore
     */
    fun keys(): List<String>

    /**
     * Returns the number of entries in this DataStore
     *
     * @return the count of all key-value pairs in the DataStore
     */
    fun count(): Int

    /**
     * Flow of key-value pairs from this [DataStore] that have been updated.
     */
    val onDataUpdated: Observable<DataObject>

    /**
     * Flow of keys from this [DataStore] that have been removed.
     */
    val onDataRemoved: Observable<List<String>>

}