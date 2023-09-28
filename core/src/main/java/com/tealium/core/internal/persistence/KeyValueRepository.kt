package com.tealium.core.internal.persistence

import com.tealium.core.api.Expiry
import com.tealium.core.api.PersistenceException
import com.tealium.core.api.data.TealiumValue


/**
 * Generic storage strategy for reading and writing key-value pairs of data
 *
 * Implementations are expected to not return Expired data based on the [Expiry] provided to any
 * methods that require it. This should be consistent across all methods; i.e. [keys] should not
 * return a list containing entries that are expired.
 *
 * Calls to the editing methods ([upsert]/[remove]/[clear]) are expected to persist immediately -
 * where multiple data updates are required to be transactional, then [transactionally] should be
 * used.
 */
interface KeyValueRepository {

    /**
     * Runs all methods in a single transaction, and be notified of exceptions.
     */
    fun transactionally(exceptionHandler: (Exception) -> Unit, block: (KeyValueRepository) -> Unit)

    /**
     * Runs all methods in a single transaction.
     */
    @Throws(PersistenceException::class)
    fun transactionally(block: (KeyValueRepository) -> Unit)

    /**
     * Fetch and item given its [key]
     *
     * @param key The key to use to lookup the value
     * @return Then value for the given key, else null
     */
    fun get(key: String): TealiumValue?

    /**
     * Fetch all items in the storage. Returning as a map of key/value pairs.
     */
    fun getAll(): Map<String, TealiumValue>

    /**
     * Removes and item from storage given the [key].
     *
     * @param key The storage key to remove
     * @return Then number of rows removed
     */
    @Throws(PersistenceException::class)
    fun delete(key: String): Int

    /**
     * Should check whether an item exists at the given key before choosing to [insert] or [update]
     * accordingly.
     *
     * @param key The key to be updated
     * @param value The value insert or replace
     * @param expiry The expiry to update
     * @return The id of the newly added data. Negative values indicate no values written.
     */
    @Throws(PersistenceException::class)
    fun upsert(key: String, value: TealiumValue, expiry: Expiry): Long

    /**
     * Removes all entries from the storage.
     */
    @Throws(PersistenceException::class)
    fun clear()

    /**
     * Returns a list of keys identifying the current set of items stored.
     */
    fun keys(): List<String>

    /**
     * Returns the number of items currently stored.
     */
    fun count(): Int

    /**
     * Returns true if an item with the given [key] is currently stored, else returns false.
     */
    fun contains(key: String): Boolean

    /**
     * Returns the [Expiry] for the given key if it exists.
     */
    fun getExpiry(key: String): Expiry?

}