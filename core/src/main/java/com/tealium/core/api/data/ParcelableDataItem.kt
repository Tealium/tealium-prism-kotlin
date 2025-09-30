@file:JvmName("ParcelableUtils")

package com.tealium.core.api.data

import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable

/**
 * [Parcelable] wrapper class for [DataItem] to allow easy interop with Android [Bundle]s
 *
 * This parcelable will write the [DataItem] as a single [String] field in the [Parcel], taken from
 * [DataItem.toString]
 *
 * @param dataItem The [DataItem] to write as a [Parcelable]
 */
class ParcelableDataItem(val dataItem: DataItem): Parcelable {

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(dataItem.toString())
    }

    companion object CREATOR : Parcelable.Creator<ParcelableDataItem> {
        override fun createFromParcel(parcel: Parcel): ParcelableDataItem? {
            val dataItemString = parcel.readString() ?: return null
            val dataItem = try {
                DataItem.parse(dataItemString)
            } catch (ignore: UnsupportedDataItemException) {
                return null
            }

            return ParcelableDataItem(dataItem)
        }

        override fun newArray(size: Int): Array<ParcelableDataItem?> {
            return arrayOfNulls(size)
        }
    }
}

/**
 * Returns a [ParcelableDataItem] that wraps the [DataItem].
 */
fun DataItem.asParcelable(): ParcelableDataItem =
    ParcelableDataItem(this)

/**
 * Returns a [ParcelableDataItem] that wraps the [DataItem].
 */
fun DataList.asParcelable(): ParcelableDataItem =
    asDataItem()
        .asParcelable()

/**
 * Returns a [ParcelableDataItem] that wraps the [DataItem].
 */
fun DataObject.asParcelable(): ParcelableDataItem =
    asDataItem()
        .asParcelable()

/**
 * Utility method to retrieve a [DataItem] from a [Bundle]. This method assumes that the
 * object was originally placed into the [Bundle] as a [ParcelableDataItem]
 */
fun Bundle.getDataItem(key: String): DataItem? {
    val parcelableDataItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, ParcelableDataItem::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key)
    }

    return parcelableDataItem?.dataItem
}

/**
 * Utility method to retrieve a [DataList] from a [Bundle]. This method assumes that the
 * object was originally placed into the [Bundle] as a [ParcelableDataItem]
 */
fun Bundle.getDataList(key: String): DataList? =
    getDataItem(key)
        ?.getDataList()

/**
 * Utility method to retrieve a [DataObject] from a [Bundle]. This method assumes that the
 * object was originally placed into the [Bundle] as a [ParcelableDataItem]
 */
fun Bundle.getDataObject(key: String): DataObject? =
    getDataItem(key)
        ?.getDataObject()

/**
 * Utility method to retrieve a [DataItemConvertible] from a [Bundle]. This method assumes that the
 * object was originally placed into the [Bundle] as a [ParcelableDataItem]
 */
fun <T> Bundle.getDataItemConvertible(key: String, converter: DataItemConverter<T>) : T? {
    val dataItem = getDataItem(key)
        ?: return null
    return converter.convert(dataItem)
}

/**
 * Utility method to place a [DataItem] directly into a [Bundle]. This method will serialize as a
 * [ParcelableDataItem] to store in the [Bundle]
 */
fun Bundle.putDataItem(key: String, dataItem: DataItem) =
    putParcelable(key, dataItem.asParcelable())

/**
 * Utility method to place a [DataList] directly into a [Bundle]. This method will serialize as a
 * [ParcelableDataItem] to store in the [Bundle]
 */
fun Bundle.putDataList(key: String, dataList: DataList) =
    putParcelable(key, dataList.asParcelable())

/**
 * Utility method to place a [DataObject] directly into a [Bundle]. This method will serialize as a
 * [ParcelableDataItem] to store in the [Bundle]
 */
fun Bundle.putDataObject(key: String, dataObject: DataObject) =
    putParcelable(key, dataObject.asParcelable())

/**
 * Utility method to place a [DataItemConvertible] directly into a [Bundle]. This method will
 * serialize as a [ParcelableDataItem] to store in the [Bundle]
 */
fun Bundle.putDataItemConvertible(key: String, dataItemConvertible: DataItemConvertible) =
    putDataItem(key, dataItemConvertible.asDataItem())

