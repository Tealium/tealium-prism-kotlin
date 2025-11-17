package com.tealium.prism.core.api.data

interface JsonPathExtractable<TRoot: JsonPath.Component> {

    /**
     * Extracts a nested [DataItem] according to the given [jsonPath].
     *
     * If any path component is not found, either missing or unexpected type, then `null` will be
     * returned.
     *
     * @param jsonPath The [JsonPath] describing how to access the variable.
     * @return The required [DataItem] if available; else null
     */
    fun extract(jsonPath: JsonPath<TRoot>): DataItem?

    /**
     * Extracts a nested [String] according to the given [jsonPath].
     *
     * If any path component is not found, either missing or unexpected type, then `null` will be
     * returned.
     *
     * @param jsonPath The [JsonPath] describing how to access the variable.
     * @return The required [String] if available; else null
     */
    fun extractString(jsonPath: JsonPath<TRoot>): String? =
        extract(jsonPath)?.getString()

    /**
     * Extracts a nested [Int] according to the given [jsonPath].
     *
     * If any path component is not found, either missing or unexpected type, then `null` will be
     * returned.
     *
     * @param jsonPath The [JsonPath] describing how to access the variable.
     * @return The required [Int] if available; else null
     */
    fun extractInt(jsonPath: JsonPath<TRoot>): Int? =
        extract(jsonPath)?.getInt()

    /**
     * Extracts a nested [Double] according to the given [jsonPath].
     *
     * If any path component is not found, either missing or unexpected type, then `null` will be
     * returned.
     *
     * @param jsonPath The [JsonPath] describing how to access the variable.
     * @return The required [Double] if available; else null
     */
    fun extractDouble(jsonPath: JsonPath<TRoot>): Double? =
        extract(jsonPath)?.getDouble()

    /**
     * Extracts a nested [Long] according to the given [jsonPath].
     *
     * If any path component is not found, either missing or unexpected type, then `null` will be
     * returned.
     *
     * @param jsonPath The [JsonPath] describing how to access the variable.
     * @return The required [Long] if available; else null
     */
    fun extractLong(jsonPath: JsonPath<TRoot>): Long? =
        extract(jsonPath)?.getLong()

    /**
     * Extracts a nested [Boolean] according to the given [jsonPath].
     *
     * If any path component is not found, either missing or unexpected type, then `null` will be
     * returned.
     *
     * @param jsonPath The [JsonPath] describing how to access the variable.
     * @return The required [Boolean] if available; else null
     */
    fun extractBoolean(jsonPath: JsonPath<TRoot>): Boolean? =
        extract(jsonPath)?.getBoolean()

    /**
     * Extracts a nested [DataList] according to the given [jsonPath].
     *
     * If any path component is not found, either missing or unexpected type, then `null` will be
     * returned.
     *
     * @param jsonPath The [JsonPath] describing how to access the variable.
     * @return The required [DataList] if available; else null
     */
    fun extractDataList(jsonPath: JsonPath<TRoot>): DataList? =
        extract(jsonPath)?.getDataList()

    /**
     * Extracts a nested [DataObject] according to the given [jsonPath].
     *
     * If any path component is not found, either missing or unexpected type, then `null` will be
     * returned.
     *
     * @param jsonPath The [JsonPath] describing how to access the variable.
     * @return The required [DataObject] if available; else null
     */
    fun extractDataObject(jsonPath: JsonPath<TRoot>): DataObject? =
        extract(jsonPath)?.getDataObject()

    /**
     * Extracts a nested [DataItem] according to the given [jsonPath], and attempts to convert
     * it to the type [T] using the given [converter]
     *
     * If any path component is not found, either missing or unexpected type, then `null` will be
     * returned.
     *
     * @param jsonPath The [JsonPath] describing how to access the variable.
     * @return The required [DataItem] converted to type [T] if available; else null
     */
    fun <T> extract(jsonPath: JsonPath<TRoot>, converter: DataItemConverter<T>): T? {
        val dataItem = extract(jsonPath)
            ?: return null

        return converter.convert(dataItem)
    }
}