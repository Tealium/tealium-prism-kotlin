package com.tealium.core.api.modules

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.pubsub.Single
import com.tealium.core.api.pubsub.Subscribable

/**
 * The [DataLayer] is available to store key-value data that should be present on every event
 * tracked through the Tealium SDK.
 *
 * There are a variety of getter/setter methods to store/retrieve common data types. All methods operate
 * on the Tealium thread.
 *
 * For updating multiple entries at once, the [transactionally] method is available to use.
 */
interface DataLayer {

    /**
     * Allows editing of the DataLayer using a [DataStore.Editor] to enable more fine-grained control
     * over key-value additions and removals.
     *
     * Callers are expected to call [DataStore.Editor.commit] when ready to save all updates. Exiting
     * the block without committing will do nothing.
     *
     * The [DataStore.Editor] supplied to the [block] will be closed upon exiting, and any usage outside
     * the provided [block] will throw.
     *
     * @param block The block of code used to update the [DataLayer]
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun transactionally(block: TealiumCallback<DataStore.Editor>): Single<TealiumResult<Unit>>

    /**
     * Attempts to update all key-value pairs from the [data] and inserts them into the [DataStore].
     *
     * The data will be expired according to the given [Expiry]
     *
     * @param data The [DataObject] containing multiple items to be stored.
     * @param expiry The expiration policy for these [DataItem]s
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(data: DataObject, expiry: Expiry): Single<TealiumResult<Unit>>

    /**
     * Attempts to update all key-value pairs from the [data] and inserts them into the [DataStore].
     *
     * The data will be expired according to the default [Expiry]
     *
     * @param data The [DataObject] containing multiple items to be stored.
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(data: DataObject): Single<TealiumResult<Unit>>

    /**
     * Puts a single key-value pair into the [DataStore]
     *
     * @param key The identifier to store the [value] under
     * @param value The [DataItem] to be stored
     * @param expiry The expiration policy for this [DataItem]
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: DataItem, expiry: Expiry): Single<TealiumResult<Unit>>

    /**
     * Puts a single key-value pair into the [DataStore]
     *
     * The data will be expired according to the default [Expiry]
     *
     * @param key The identifier to store the [value] under
     * @param value The [DataItem] to be stored
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: DataItem): Single<TealiumResult<Unit>>

    /**
     * Gets a single [DataItem] if available.
     *
     * @param key The identifier for the requested [DataItem]
     * @return A Single which can be used to subscribe a block of code to receive the result
     */
    fun get(key: String): Single<TealiumResult<DataItem?>>

    /**
     * Gets all entries in the [DataLayer] and returns them as a [DataObject]
     *
     * @return A Single which can be used to subscribe a block of code to receive the result
     */
    fun getAll(): Single<TealiumResult<DataObject>>

    /**
     * Stores a [DataItemConvertible] [value] at the given [key]. The [value] will be eagerly converted
     * to a [DataItem] on the caller thread.
     *
     * The data will be expired according to the given [Expiry]
     *
     * @param key The key to store the value under for future retrieval
     * @param value The value to store
     * @param expiry The time to store the value for
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: DataItemConvertible, expiry: Expiry): Single<TealiumResult<Unit>>

    /**
     * Stores a [DataItemConvertible] [value] at the given [key]. The [value] will be eagerly converted
     * to a [DataItem] on the caller thread.
     *
     * The data will be expired according to the default [Expiry]
     *
     * @param key The key to store the value under for future retrieval
     * @param value The value to store
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: DataItemConvertible): Single<TealiumResult<Unit>>

    /**
     * Stores a [String] [value] at the given [key].
     *
     * The data will be expired according to the given [Expiry]
     *
     * @param key The key to store the value under for future retrieval
     * @param value The value to store
     * @param expiry The time to store the value for
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: String, expiry: Expiry): Single<TealiumResult<Unit>>

    /**
     * Stores a [String] [value] at the given [key].
     *
     * The data will be expired according to the default [Expiry]
     *
     * @param key The key to store the value under for future retrieval
     * @param value The value to store
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: String): Single<TealiumResult<Unit>>

    /**
     * Stores an [Int] [value] at the given [key].
     *
     * The data will be expired according to the given [Expiry]
     *
     * @param key The key to store the value under for future retrieval
     * @param value The value to store
     * @param expiry The time to store the value for
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: Int, expiry: Expiry): Single<TealiumResult<Unit>>

    /**
     * Stores an [Int] [value] at the given [key].
     *
     * The data will be expired according to the default [Expiry]
     *
     * @param key The key to store the value under for future retrieval
     * @param value The value to store
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: Int): Single<TealiumResult<Unit>>

    /**
     * Stores a [Double] [value] at the given [key].
     *
     * The data will be expired according to the given [Expiry]
     *
     * @param key The key to store the value under for future retrieval
     * @param value The value to store
     * @param expiry The time to store the value for
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: Double, expiry: Expiry): Single<TealiumResult<Unit>>

    /**
     * Stores a [Double] [value] at the given [key].
     *
     * The data will be expired according to the default [Expiry]
     *
     * @param key The key to store the value under for future retrieval
     * @param value The value to store
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: Double): Single<TealiumResult<Unit>>

    /**
     * Stores a [Long] [value] at the given [key].
     *
     * The data will be expired according to the given [Expiry]
     *
     * @param key The key to store the value under for future retrieval
     * @param value The value to store
     * @param expiry The time to store the value for
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: Long, expiry: Expiry): Single<TealiumResult<Unit>>

    /**
     * Stores a [Long] [value] at the given [key].
     *
     * The data will be expired according to the default [Expiry]
     *
     * @param key The key to store the value under for future retrieval
     * @param value The value to store
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: Long): Single<TealiumResult<Unit>>

    /**
     * Stores a [Boolean] [value] at the given [key].
     *
     * The data will be expired according to the given [Expiry]
     *
     * @param key The key to store the value under for future retrieval
     * @param value The value to store
     * @param expiry The time to store the value for
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: Boolean, expiry: Expiry): Single<TealiumResult<Unit>>

    /**
     * Stores a [Boolean] [value] at the given [key].
     *
     * The data will be expired according to the default [Expiry]
     *
     * @param key The key to store the value under for future retrieval
     * @param value The value to store
     * @return A Single which can be used to subscribe a block of code to receive any errors that occur
     */
    fun put(key: String, value: Boolean): Single<TealiumResult<Unit>>

    /**
     * Retrieves a value of type [T] at the given [key] using the provided [converter] to convert from
     * a [DataItem] to an instance of [T]
     *
     * The [callback] will receive the value of [T] or `null` if the value could not be converted. The result
     * will be received on the Tealium thread.
     *
     * @param key The key to retrieve the value from
     * @param converter The [DataItemConverter] implementation for reconstituting the value
     * @return A Single which can be used to subscribe a block of code to receive the result
     */
    fun <T> get(key: String, converter: DataItemConverter<T>): Single<TealiumResult<T?>>

    /**
     * Retrieves a [String] value at the given [key].
     *
     * The [callback] will receive the [String] value or `null` if the value was either not a [String] or
     * not present in the [DataLayer]. The result will be received on the Tealium thread.
     *
     * @param key The key to retrieve the value from
     * @return A Single which can be used to subscribe a block of code to receive the result
     */
    fun getString(key: String): Single<TealiumResult<String?>>

    /**
     * Retrieves an [Int] value at the given [key].
     *
     * The [callback] will receive the [Int] value or `null` if the value was either not a [Number] or
     * not present in the [DataLayer]. The result will be received on the Tealium thread.
     *
     * @param key The key to retrieve the value from
     * @return A Single which can be used to subscribe a block of code to receive the result
     */
    fun getInt(key: String): Single<TealiumResult<Int?>>

    /**
     * Retrieves a [Double] value at the given [key].
     *
     * The [callback] will receive the [Double] value or `null` if the value was either not a [Number] or
     * not present in the [DataLayer]. The result will be received on the Tealium thread.
     *
     * @param key The key to retrieve the value from
     * @return A Single which can be used to subscribe a block of code to receive the result
     */
    fun getDouble(key: String): Single<TealiumResult<Double?>>

    /**
     * Retrieves a [Long] value at the given [key].
     *
     * The [callback] will receive the [Long] value or `null` if the value was either not a [Number] or
     * not present in the [DataLayer]. The result will be received on the Tealium thread.
     *
     * @param key The key to retrieve the value from
     * @return A Single which can be used to subscribe a block of code to receive the result
     */
    fun getLong(key: String): Single<TealiumResult<Long?>>

    /**
     * Retrieves a [Boolean] value at the given [key].
     *
     * The [callback] will receive the [Boolean] value or `null` if the value was either not a [Boolean] or
     * not present in the [DataLayer]. The result will be received on the Tealium thread.
     *
     * @param key The key to retrieve the value from
     * @return A Single which can be used to subscribe a block of code to receive the result
     */
    fun getBoolean(key: String): Single<TealiumResult<Boolean?>>

    /**
     * Retrieves a [DataList] value at the given [key].
     *
     * The [callback] will receive the [DataList] value or `null` if the value was either not a [DataList] or
     * not present in the [DataLayer]. The result will be received on the Tealium thread.
     *
     * @param key The key to retrieve the value from
     * @return A Single which can be used to subscribe a block of code to receive the result
     */
    fun getDataList(key: String): Single<TealiumResult<DataList?>>

    /**
     * Retrieves a [DataObject] value at the given [key].
     *
     * The [callback] will receive the [DataObject] value or `null` if the value was either not a [DataObject] or
     * not present in the [DataLayer]. The result will be received on the Tealium thread.
     *
     * @param key The key to retrieve the value from
     * @return A Single which can be used to subscribe a block of code to receive the result
     */
    fun getDataObject(key: String): Single<TealiumResult<DataObject?>>

    /**
     * Removes the entry from the [DataLayer], identified by the given [key]
     *
     * @param key The key to remove.
     */
    fun remove(key: String): Single<TealiumResult<Unit>>

    /**
     * Removes all entries from the [DataLayer], identified by the given [keys]
     *
     * @param keys A list of all keys to remove.
     */
    fun remove(keys: List<String>): Single<TealiumResult<Unit>>

    /**
     * Removes all entries from the [DataLayer].
     */
    fun clear(): Single<TealiumResult<Unit>>

    /**
     * Returns a [Subscribable] object with which to receive notifications of [DataLayer] entries
     * being updated.
     */
    val onDataUpdated: Subscribable<DataObject>

    /**
     * Returns a [Subscribable] object with which to receive notifications of [DataLayer] entries
     * being removed.
     */
    val onDataRemoved: Subscribable<List<String>>
}
