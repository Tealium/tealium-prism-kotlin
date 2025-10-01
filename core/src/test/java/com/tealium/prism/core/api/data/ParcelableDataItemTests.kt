package com.tealium.prism.core.api.data

import android.os.Bundle
import android.os.Parcel
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 33])
class ParcelableDataItemTests {

    private lateinit var parcel: Parcel
    private lateinit var bundle: Bundle

    @Before
    fun setUp() {
        parcel = Parcel.obtain()
        bundle = Bundle()
    }

    @Test
    fun describeContents_Returns_Zero() {
        val dataItem = "string".asDataItem()
            .asParcelable()

        assertEquals(0, dataItem.describeContents())
    }

    @Test
    fun asParcelable_Can_Be_Recreated_To_String_DataItem() {
        val dataItem = "string".asDataItem()
        dataItem.asParcelable().writeToParcel(parcel, 0)

        assertEquals(dataItem, readFromParcel(parcel)?.dataItem)
    }

    @Test
    fun asParcelable_Can_Be_Recreated_To_Int_DataItem() {
        val dataItem = 1.asDataItem()
        dataItem.asParcelable().writeToParcel(parcel, 0)

        assertEquals(dataItem, readFromParcel(parcel)?.dataItem)
    }

    @Test
    fun asParcelable_Can_Be_Recreated_To_Double_DataItem() {
        val dataItem = 1.1.asDataItem()
        dataItem.asParcelable().writeToParcel(parcel, 0)

        assertEquals(dataItem, readFromParcel(parcel)?.dataItem)
    }

    @Test
    fun asParcelable_Can_Be_Recreated_To_Long_DataItem() {
        val dataItem = 100L.asDataItem()
        dataItem.asParcelable().writeToParcel(parcel, 0)

        assertEquals(dataItem, readFromParcel(parcel)?.dataItem)
    }

    @Test
    fun asParcelable_Can_Be_Recreated_To_Boolean_DataItem() {
        val dataItem = true.asDataItem()
        dataItem.asParcelable().writeToParcel(parcel, 0)

        assertEquals(dataItem, readFromParcel(parcel)?.dataItem)
    }

    @Test
    fun asParcelable_Can_Be_Recreated_To_DataList_DataItem() {
        val dataList = DataList.create {
            add("string"); add(1); add(true)
        }
        dataList.asParcelable().writeToParcel(parcel, 0)

        assertEquals(dataList, readFromParcel(parcel)?.dataItem?.getDataList())
    }

    @Test
    fun asParcelable_Can_Be_Recreated_To_DataObject_DataItem() {
        val dataObject = DataObject.create {
            put("string", "value"); put("int", 1); put("bool", true)
        }
        dataObject.asParcelable().writeToParcel(parcel, 0)

        assertEquals(dataObject, readFromParcel(parcel)?.dataItem?.getDataObject())
    }

    @Test
    fun bundle_Can_Read_And_Write_String_DataItems() {
        val dataItem = "string".asDataItem()
        bundle.putDataItem("key", dataItem)

        assertEquals(dataItem, bundle.getDataItem("key"))
    }

    @Test
    fun bundle_Can_Read_And_Write_Int_DataItems() {
        val dataItem = 1.asDataItem()
        bundle.putDataItem("key", dataItem)

        assertEquals(dataItem, bundle.getDataItem("key"))
    }

    @Test
    fun bundle_Can_Read_And_Write_Double_DataItems() {
        val dataItem = 1.1.asDataItem()
        bundle.putDataItem("key", dataItem)

        assertEquals(dataItem, bundle.getDataItem("key"))
    }

    @Test
    fun bundle_Can_Read_And_Write_Long_DataItems() {
        val dataItem = 100L.asDataItem()
        bundle.putDataItem("key", dataItem)

        assertEquals(dataItem, bundle.getDataItem("key"))
    }

    @Test
    fun bundle_Can_Read_And_Write_Boolean_DataItems() {
        val dataItem = true.asDataItem()
        bundle.putDataItem("key", dataItem)

        assertEquals(dataItem, bundle.getDataItem("key"))
    }

    @Test
    fun bundle_Can_Read_And_Write_DataList_DataItems() {
        val dataList = DataList.create {
            add("string"); add(1); add(true)
        }
        bundle.putDataList("key", dataList)

        assertEquals(dataList, bundle.getDataList("key"))
    }

    @Test
    fun bundle_Can_Read_And_Write_DataObject_DataItems() {
        val dataObject = DataObject.create {
            put("string", "value"); put("int", 1); put("bool", true)
        }
        bundle.putDataObject("key", dataObject)

        assertEquals(dataObject, bundle.getDataObject("key"))
    }

    @Test
    fun bundle_Can_Read_And_Write_DataItemConvertible_DataItems() {
        val convertible = TestDataObjectConvertible("string", 100)
        bundle.putDataItemConvertible("key", convertible)

        assertEquals(
            convertible,
            bundle.getDataItemConvertible("key", TestDataObjectConvertible.Converter)
        )
    }

    @Test
    fun getDataItem_Returns_Null_When_Not_A_Parcelable_DataItem() {
        bundle.putString("key", "value")

        assertNull(bundle.getDataItem("key"))
    }

    @Test
    fun getDataList_Returns_Null_When_Not_A_Parcelable_DataItem() {
        bundle.putString("key", "value")

        assertNull(bundle.getDataList("key"))
    }

    @Test
    fun getDataObject_Returns_Null_When_Not_A_Parcelable_DataItem() {
        bundle.putString("key", "value")

        assertNull(bundle.getDataObject("key"))
    }

    @Test
    fun getDataItemConvertible_Returns_Null_When_Not_A_Parcelable_DataItem() {
        bundle.putString("key", "value")

        assertNull(bundle.getDataItemConvertible("key", TestDataObjectConvertible.Converter))
    }

    private fun readFromParcel(parcel: Parcel): ParcelableDataItem? {
        parcel.setDataPosition(0)
        return ParcelableDataItem.CREATOR.createFromParcel(parcel)
    }
}