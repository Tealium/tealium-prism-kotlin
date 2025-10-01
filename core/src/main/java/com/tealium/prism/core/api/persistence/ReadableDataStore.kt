package com.tealium.prism.core.api.persistence

import com.tealium.prism.core.api.data.DataItemExtractor
import com.tealium.prism.core.api.data.DataObject

interface ReadableDataStore: DataItemExtractor {

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
}