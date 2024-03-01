package com.tealium.core.api

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.internal.observables.Observable

/**
 * Generic data storage for storing and retrieving [TealiumValue] objects.
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
interface DataStore : Iterable<Map.Entry<String, TealiumValue>> {

    /**
     * Enables editing multiple entries in the module storage in a transactional way.
     */
    interface Editor {

        /**
         * Adds all key-value pairs from the bundle into the storage.
         *
         * @param bundle A TealiumBundle containing the key-value pairs to be stored
         * @param expiry The time frame for this data to remain stored
         *
         * @return Editor to continue editing this storage
         */
        fun putAll(bundle: TealiumBundle, expiry: Expiry): Editor

        /**
         * Adds a single key-value pair to the storage
         *
         * @param key The key to store the [value] under
         * @param value The value to be stored
         * @param expiry The time frame for this data to remain stored
         *
         * @return Editor to continue editing this storage
         */
        fun put(key: String, value: TealiumValue, expiry: Expiry): Editor

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
     * Gets the [TealiumValue] stored at the given [key] if there is one
     *
     * @param key The key for the required value
     *
     * @return The [TealiumValue] or null
     */
    fun get(key: String): TealiumValue?

    /**
     * Gets the entire [TealiumBundle] containing all data stored.
     *
     * @return The [TealiumBundle] containing all key-value pairs
     */
    fun getAll(): TealiumBundle

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
    val onDataUpdated: Observable<TealiumBundle>

    /**
     * Flow of keys from this [DataStore] that have been removed.
     */
    val onDataRemoved: Observable<List<String>>

}