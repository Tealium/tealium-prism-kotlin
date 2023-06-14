package com.tealium.core.internal.persistence

import com.tealium.core.api.Expiry


interface PersistentStorage<K, T> {

    /**
     * Runs all methods in a single transaction, and be notified of exceptions.
     */
    fun transactionally(exceptionHandler: (Exception) -> Unit, block: (PersistentStorage<K, T>) -> Unit)

    /**
     * Runs all methods in a single transaction.
     */
    fun transactionally(block: (PersistentStorage<K, T>) -> Unit)

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
     * @param expiry The expiry to update; null values should leave the existing existing expiry
     * unaffected
     */
    fun insert(key: K, value: T, expiry: Expiry?)

    /**
     * Attempts to update an existing entry in the storage, should not check if an item with the
     * given key already exists - see [upsert]
     *
     * @param key The key to be updated
     * @param value The value replace any existing value
     * @param expiry The expiry to update; null values should leave the existing existing expiry
     * unaffected
     */
    fun update(key: K, value: T, expiry: Expiry?)

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
     * @param expiry The expiry to update; null values should leave the existing existing expiry
     * unaffected
     */
    fun upsert(key: K, value: T, expiry: Expiry?)

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

}