package com.tealium.prism.core.api.barriers

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarrierScopeTests {

    private val converter = BarrierScope.Converter

    @Test
    fun convert_Returns_All_When_String_Is_All() {
        val dataItem = DataItem.string(BarrierScope.All.STRING_VALUE)
        val result = converter.convert(dataItem)

        assertEquals(BarrierScope.All, result)
    }

    @Test
    fun convert_Returns_All_When_String_Is_Uppercase_ALL() {
        val result = converter.convert(DataItem.string("ALL"))
        assertEquals(BarrierScope.All, result)
    }

    @Test
    fun convert_Returns_All_When_String_Is_Mixed_Case() {
        val result = converter.convert(DataItem.string("All"))
        assertEquals(BarrierScope.All, result)
    }

    @Test
    fun convert_Returns_Null_When_String_Is_Invalid() {
        val dataItem = DataItem.string("invalid")
        val result = converter.convert(dataItem)

        assertNull(result)
    }

    @Test
    fun convert_Returns_Null_When_DataItem_Is_Null() {
        val result = converter.convert(DataItem.NULL)

        assertNull(result)
    }

    @Test
    fun convert_Returns_Dispatchers_With_Empty_List_When_DataList_Is_Empty() {
        val dataItem = DataList.EMPTY_LIST.asDataItem()
        val result = converter.convert(dataItem)

        assertTrue(result is BarrierScope.Dispatchers)
        val dispatchers = result as BarrierScope.Dispatchers
        assertTrue(dispatchers.dispatcherIds.isEmpty())
    }

    @Test
    fun convert_Filters_Out_Null_Elements_From_DataList() {
        val dataList = DataList.create {
            add("dispatcher1")
            add(DataItem.NULL)
            add("dispatcher2")
        }
        val result = converter.convert(dataList.asDataItem())

        assertTrue(result is BarrierScope.Dispatchers)
        val dispatchers = result as BarrierScope.Dispatchers
        assertEquals(2, dispatchers.dispatcherIds.size)
        assertEquals("dispatcher1", dispatchers.dispatcherIds[0])
        assertEquals("dispatcher2", dispatchers.dispatcherIds[1])
    }

    @Test
    fun convert_Filters_Out_Non_String_Elements_From_DataList() {
        val dataList = DataList.create {
            add("dispatcher1")
            add(123)
            add(true)
            add("dispatcher2")
        }
        val result = converter.convert(dataList.asDataItem())

        assertTrue(result is BarrierScope.Dispatchers)
        val dispatchers = result as BarrierScope.Dispatchers
        assertEquals(2, dispatchers.dispatcherIds.size)
        assertEquals("dispatcher1", dispatchers.dispatcherIds[0])
        assertEquals("dispatcher2", dispatchers.dispatcherIds[1])
    }

    @Test
    fun convert_Returns_Dispatchers_With_Mixed_Valid_Invalid_Elements() {
        val dataList = DataList.create {
            add("valid1")
            add(DataItem.NULL)
            add("valid2")
            add(false)
            add("valid3")
            add(456.78)
        }
        val result = converter.convert(dataList.asDataItem())

        assertTrue(result is BarrierScope.Dispatchers)
        val dispatchers = result as BarrierScope.Dispatchers
        assertEquals(3, dispatchers.dispatcherIds.size)
        assertEquals("valid1", dispatchers.dispatcherIds[0])
        assertEquals("valid2", dispatchers.dispatcherIds[1])
        assertEquals("valid3", dispatchers.dispatcherIds[2])
    }

    @Test
    fun convert_Returns_Null_When_DataItem_Is_Number() {
        val dataItem = DataItem.int(123)
        val result = converter.convert(dataItem)

        assertNull(result)
    }

    @Test
    fun convert_Returns_Null_When_DataItem_Is_Boolean() {
        val dataItem = DataItem.boolean(true)
        val result = converter.convert(dataItem)

        assertNull(result)
    }

    @Test
    fun convert_Returns_Dispatchers_With_Single_Element() {
        val dataList = DataList.create {
            add("single-dispatcher")
        }
        val result = converter.convert(dataList.asDataItem())

        assertTrue(result is BarrierScope.Dispatchers)
        val dispatchers = result as BarrierScope.Dispatchers
        assertEquals(1, dispatchers.dispatcherIds.size)
        assertEquals("single-dispatcher", dispatchers.dispatcherIds[0])
    }

    @Test
    fun convert_Returns_Dispatchers_With_Multiple_Elements() {
        val dataList = DataList.create {
            add("dispatcher1")
            add("dispatcher2")
            add("dispatcher3")
        }
        val result = converter.convert(dataList.asDataItem())

        assertTrue(result is BarrierScope.Dispatchers)
        val dispatchers = result as BarrierScope.Dispatchers
        assertEquals(3, dispatchers.dispatcherIds.size)
        assertEquals("dispatcher1", dispatchers.dispatcherIds[0])
        assertEquals("dispatcher2", dispatchers.dispatcherIds[1])
        assertEquals("dispatcher3", dispatchers.dispatcherIds[2])
    }

    @Test
    fun asDataItem_All_Returns_String_DataItem() {
        val dataItem = BarrierScope.All.asDataItem()
        val stringValue = dataItem.getString()

        assertEquals(BarrierScope.All.STRING_VALUE, stringValue)
    }

    @Test
    fun asDataItem_Dispatchers_Returns_DataList() {
        val scope = BarrierScope.Dispatchers("dispatcher1", "dispatcher2")
        val dataItem = scope.asDataItem()
        val dataList = dataItem.getDataList()

        assertEquals(2, dataList?.size)
        assertEquals("dispatcher1", dataList?.getString(0))
        assertEquals("dispatcher2", dataList?.getString(1))
    }

    @Test
    fun roundtrip_All_Conversion() {
        val converted = converter.convert(BarrierScope.All.asDataItem())

        assertEquals(BarrierScope.All, converted)
    }

    @Test
    fun roundtrip_Dispatchers_Conversion() {
        val original = BarrierScope.Dispatchers("dispatcher1", "dispatcher2", "dispatcher3")
        val dataItem = original.asDataItem()
        val converted = converter.convert(dataItem)

        assertEquals(original, converted)
    }

    @Test
    fun roundtrip_Dispatchers_Empty_List() {
        val original = BarrierScope.Dispatchers(emptyList())
        val dataItem = original.asDataItem()
        val converted = converter.convert(dataItem)

        assertEquals(original, converted)
    }
}
