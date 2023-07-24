package com.tealium.core.internal.persistence

import com.tealium.core.api.Expiry


/**
 * Generic storage strategy for reading and writing key-value pairs of data
 *
 * Implementations are expected to not return Expired data based on the [Expiry] provided to any
 * methods that require it. This should be consistent across all methods; i.e. [keys] should not
 * return a list containing entries that are expired.
 *
 * Calls to the editing methods ([insert]/[update]/[upsert]) are expected to persist immediately -
 * where multiple data updates are required to be transactional, then [transactionally] should be
 * used.
 */
interface DataStorageStrategy<K, T> {

    /**
     * Runs all methods in a single transaction, and be notified of exceptions.
     */
    fun transactionally(exceptionHandler: (Exception) -> Unit, block: (DataStorageStrategy<K, T>) -> Unit)

    /**
     * Runs all methods in a single transaction.
     */
    fun transactionally(block: (DataStorageStrategy<K, T>) -> Unit)

    /**
     * Fetch and item given its [key]
     */
    fun get(key: K): T?

    /**
     * Fetch all items in the storage. Returning as a map of key/value pairs.
     */
    fun getAll(): Map<K, T>

    /**
     * Attempts to save an item in the storage, should not check if an item exists already with the
     * same key - see [upsert]
     *
     * @param key The key to be updated
     * @param value The value insert
     * @param expiry The expiry to update
     */
    fun insert(key: K, value: T, expiry: Expiry)

    /**
     * Attempts to update an existing entry in the storage, should not check if an item with the
     * given key already exists - see [upsert]
     *
     * @param key The key to be updated
     * @param value The value replace any existing value
     * @param expiry The expiry to update
     */
    fun update(key: K, value: T, expiry: Expiry)

    /**
     * Removes and item from storage given the [key].
     */
    fun delete(key: K)

    /**
     * Should check whether an item exists at the given key before choosing to [insert] or [update]
     * accordingly.
     *
     * @param key The key to be updated
     * @param value The value insert or replace
     * @param expiry The expiry to update
     */
    fun upsert(key: K, value: T, expiry: Expiry)

    /**
     * Removes all entries from the storage.
     */
    fun clear()

    /**
     * Returns a list of keys identifying the current set of items stored.
     */
    fun keys(): List<K>

    /**
     * Returns the number of items currently stored.
     */
    fun count(): Int

    /**
     * Returns true if an item with the given [key] is currently stored, else returns false.
     */
    fun contains(key: K): Boolean

    /**
     * Returns the [Expiry] for the given key if it exists.
     */
    fun getExpiry(key: K): Expiry?

}