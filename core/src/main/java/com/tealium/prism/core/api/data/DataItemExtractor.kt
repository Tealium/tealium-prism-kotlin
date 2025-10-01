package com.tealium.prism.core.api.data

interface DataItemExtractor {

    /**
     * Gets the [DataItem] stored at the given [key] if there is one
     *
     * @param key The key for the required value
     *
     * @return The [DataItem] or null
     */
    fun get(key: String): DataItem?

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
     * Gets the [DataItem] stored at the given [key] if there is one, and uses the given
     * [converter] to translate it into an instance of type [T]
     *
     * @param key The key for the required value
     * @param converter The [DataItemConverter] implementation for converting the [DataItem] to the required type.
     *
     * @return The [DataItem] or null
     */
    fun <T> get(key: String, converter: DataItemConverter<T>): T? = get(key)?.let { item ->
        converter.convert(item)
    }

}