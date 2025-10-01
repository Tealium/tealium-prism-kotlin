package com.tealium.prism.core.api.data


object DataItemUtils {

    // Primitive conversions

    /**
     * Convenience method to convert a [String] to a [DataItem]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    fun String?.asDataItem(): DataItem = DataItem.string(this)

    /**
     * Convenience method to convert a [Int] to a [DataItem]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    fun Int?.asDataItem(): DataItem = DataItem.int(this)

    /**
     * Convenience method to convert a [Long] to a [DataItem]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    fun Long?.asDataItem(): DataItem = DataItem.long(this)

    /**
     * Convenience method to convert a [Double] to a [DataItem]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    fun Double?.asDataItem(): DataItem = DataItem.double(this)

    /**
     * Convenience method to convert a [Boolean] to a [DataItem]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    fun Boolean?.asDataItem(): DataItem = DataItem.boolean(this)

    // DataList utils

    /**
     * Convenience method to convert any [Collection] to a [DataList]
     *
     * Unsupported values found within the collection or any of the contained objects will throw.
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataListFromCollection")
    @Throws(UnsupportedDataItemException::class)
    fun Collection<*>.asDataList() =
        DataList.fromCollection(this)

    /**
     * Convenience method to convert a [Collection] of [String] values to a [DataList]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataListFromStringCollection")
    fun Collection<String?>.asDataList(): DataList =
        DataList.fromStringCollection(this)

    /**
     * Convenience method to convert a [Collection] of [Int] values to a [DataList]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataListFromIntCollection")
    fun Collection<Int?>.asDataList(): DataList =
        DataList.fromIntCollection(this)

    /**
     * Convenience method to convert a [Collection] of [Long] values to a [DataList]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataListFromLongCollection")
    fun Collection<Long?>.asDataList(): DataList =
        DataList.fromLongCollection(this)

    /**
     * Convenience method to convert a [Collection] of [Double] values to a [DataList]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataListFromDoubleCollection")
    fun Collection<Double?>.asDataList(): DataList =
        DataList.fromDoubleCollection(this)

    /**
     * Convenience method to convert a [Collection] of [Boolean] values to a [DataList]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataListFromBooleanCollection")
    fun Collection<Boolean?>.asDataList(): DataList =
        DataList.fromBooleanCollection(this)

    /**
     * Convenience method to convert a [Collection] of [DataItem] values to a [DataList]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataListFromDataItemCollection")
    fun Collection<DataItem?>.asDataList(): DataList =
        DataList.fromDataItemCollection(this)

    /**
     * Convenience method to convert a [Collection] of [DataList] values to a [DataList]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataListFromDataListCollection")
    fun Collection<DataList?>.asDataList(): DataList =
        DataList.fromDataListCollection(this)

    /**
     * Convenience method to convert a [Collection] of [DataObject] values to a [DataList]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataListFromDataObjectCollection")
    fun Collection<DataObject?>.asDataList(): DataList =
        DataList.fromDataObjectCollection(this)


    // DataObject Utils

    /**
     * Convenience method to convert any [Map] of [String]-keyed values to a [DataObject]
     *
     * Unsupported values found within the map or any of its contained objects will throw.
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMap")
    @Throws(UnsupportedDataItemException::class)
    fun Map<String, *>.asDataObject() =
        DataObject.fromMap(this)

    /**
     * Convenience method to convert any [Map] of any-keyed values to a [DataObject]
     *
     * This method will throw when either:
     *  - Keys are not [String]s
     *  - Unsupported values are found within the map or any of its contained objects
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromStringKeyedMap")
    @Throws(UnsupportedDataItemException::class)
    fun Map<*, *>.asDataObject() =
        DataObject.fromMap(this)

    /**
     * Convenience method to convert any [Map] of [String] keys and [String] values to a [DataObject]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMapOfStrings")
    fun Map<String, String?>.asDataObject(): DataObject =
        DataObject.fromMapOfStrings(this)

    /**
     * Convenience method to convert any [Map] of [String] keys and [Int] values to a [DataObject]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMapOfInts")
    fun Map<String, Int?>.asDataObject(): DataObject =
        DataObject.fromMapOfInts(this)

    /**
     * Convenience method to convert any [Map] of [String] keys and [Long] values to a [DataObject]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMapOfLongs")
    fun Map<String, Long?>.asDataObject(): DataObject =
        DataObject.fromMapOfLongs(this)

    /**
     * Convenience method to convert any [Map] of [String] keys and [Double] values to a [DataObject]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMapOfDoubles")
    fun Map<String, Double?>.asDataObject(): DataObject =
        DataObject.fromMapOfDoubles(this)

    /**
     * Convenience method to convert any [Map] of [String] keys and [Boolean] values to a [DataObject]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMapOfBooleans")
    fun Map<String, Boolean?>.asDataObject(): DataObject =
        DataObject.fromMapOfBooleans(this)

    /**
     * Convenience method to convert any [Map] of [String] keys and [DataItem] values to a [DataObject]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMapOfDataItems")
    fun Map<String, DataItem?>.asDataObject(): DataObject =
        DataObject.fromMapOfDataItems(this)

    /**
     * Convenience method to convert any [Map] of [String] keys and [DataList] values to a [DataObject]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMapOfDataLists")
    fun Map<String, DataList?>.asDataObject(): DataObject =
        DataObject.fromMapOfDataLists(this)

    /**
     * Convenience method to convert any [Map] of [String] keys and [DataObject] values to a [DataObject]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMapOfDataObjects")
    fun Map<String, DataObject?>.asDataObject(): DataObject =
        DataObject.fromMapOfDataObjects(this)

    /**
     * Convenience method to convert any [Map] of [String] keys and [String] collection values to a [DataObject]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMapOfStringCollections")
    fun Map<String, Collection<String?>>.asDataObject(): DataObject =
        DataObject.fromMapOfStringCollections(this)

    /**
     * Convenience method to convert any [Map] of [String] keys and [Int] collection values to a [DataObject]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMapOfIntCollections")
    fun Map<String, Collection<Int?>>.asDataObject(): DataObject =
        DataObject.fromMapOfIntCollections(this)

    /**
     * Convenience method to convert any [Map] of [String] keys and [Long] collection values to a [DataObject]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMapOfLongCollections")
    fun Map<String, Collection<Long?>>.asDataObject(): DataObject =
        DataObject.fromMapOfLongCollections(this)

    /**
     * Convenience method to convert any [Map] of [String] keys and [Double] collection values to a [DataObject]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMapOfDoubleCollections")
    fun Map<String, Collection<Double?>>.asDataObject(): DataObject =
        DataObject.fromMapOfDoubleCollections(this)

    /**
     * Convenience method to convert any [Map] of [String] keys and [Boolean] collection values to a [DataObject]
     *
     * `null` values will return [DataItem.NULL]
     */
    @JvmStatic
    @JvmName("dataObjectFromMapOfBooleanCollections")
    fun Map<String, Collection<Boolean?>>.asDataObject(): DataObject =
        DataObject.fromMapOfBooleanCollections(this)

}