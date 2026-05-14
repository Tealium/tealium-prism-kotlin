package com.tealium.prism.core.api.transform

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TransformationScopeTests {

    @Test
    fun afterCollectors_StringValue_Matches_AfterCollectors() {
        assertEquals("aftercollectors", TransformationScope.AfterCollectors.STRING_VALUE)
    }

    @Test
    fun afterCollectors_AsDataItem_Returns_StringValue_As_DataItem() {
        assertEquals(
            "aftercollectors".asDataItem(),
            TransformationScope.AfterCollectors.asDataItem()
        )
    }

    @Test
    fun afterCollectors_AsDataItem_Returns_Same_Instance() {
        val dataItem1 = TransformationScope.AfterCollectors.asDataItem()
        val dataItem2 = TransformationScope.AfterCollectors.asDataItem()

        assertSame(dataItem1, dataItem2)
    }

    @Test
    fun allDispatchers_StringValue_Matches_AllDispatchers() {
        assertEquals("alldispatchers", TransformationScope.AllDispatchers.STRING_VALUE)
    }

    @Test
    fun allDispatchers_AsDataItem_Returns_StringValue_As_DataItem() {
        assertEquals(
            "alldispatchers".asDataItem(),
            TransformationScope.AllDispatchers.asDataItem()
        )
    }

    @Test
    fun allDispatchers_AsDataItem_Returns_Same_Instance() {
        val dataItem1 = TransformationScope.AllDispatchers.asDataItem()
        val dataItem2 = TransformationScope.AllDispatchers.asDataItem()

        assertSame(dataItem1, dataItem2)
    }

    @Test
    fun dispatchers_Sets_Init_DispatcherIds_Correctly() {
        val ids = listOf("d1", "d2", "d3")
        val dispatchers = TransformationScope.Dispatchers(ids)

        assertEquals(listOf("d1", "d2", "d3"), dispatchers.dispatcherIds)
    }

    @Test
    fun dispatchers_Sets_Init_DispatcherIds_From_Varargs() {
        val dispatchers = TransformationScope.Dispatchers("d1", "d2", "d3")

        assertEquals(listOf("d1", "d2", "d3"), dispatchers.dispatcherIds)
    }

    @Test
    fun dispatchers_AsDataItem_Returns_DataList_With_All_DispatcherIds() {
        val dispatchers = TransformationScope.Dispatchers("d1", "d2", "d3")
            .asDataItem()

        val dataList = dispatchers.getDataList()!!
        assertEquals("d1", dataList.getString(0))
        assertEquals("d2", dataList.getString(1))
        assertEquals("d3", dataList.getString(2))
    }

    @Test
    fun converter_Returns_AfterCollectors_When_String_Is_AfterCollectors() {
        val dataItem = TransformationScope.AfterCollectors.asDataItem()
        val scope = TransformationScope.Converter.convert(dataItem)

        assertSame(TransformationScope.AfterCollectors, scope)
    }

    @Test
    fun converter_Returns_AllDispatchers_When_String_Is_AllDispatchers() {
        val dataItem = TransformationScope.AllDispatchers.asDataItem()
        val scope = TransformationScope.Converter.convert(dataItem)

        assertSame(TransformationScope.AllDispatchers, scope)
    }

    @Test
    fun converter_Returns_Null_When_String_Is_Unsupported() {
        val dataItem = "unsupported".asDataItem()
        val scope = TransformationScope.Converter.convert(dataItem)

        assertNull(scope)
    }

    @Test
    fun converter_Returns_Null_When_Value_Is_Not_String_Or_List() {
        val dataItem = 1.asDataItem()
        val scope = TransformationScope.Converter.convert(dataItem)

        assertNull(scope)
    }

    @Test
    fun converter_Returns_DispatcherId_List_When_Value_Is_DataList() {
        val dataItem = DataList.create {
            add("d1")
            add("d2")
        }.asDataItem()
        val dispatchers = TransformationScope.Converter.convert(dataItem) as TransformationScope.Dispatchers

        assertEquals(listOf("d1","d2"), dispatchers.dispatcherIds)
    }

    @Test
    fun converter_Ignores_Invalid_DispatcherIds() {
        val dataItem = DataList.create {
            add("d1")
            add("d2")
            add(DataObject.EMPTY_OBJECT)
            add(true)
            add(1)
        }.asDataItem()
        val dispatchers = TransformationScope.Converter.convert(dataItem) as TransformationScope.Dispatchers

        assertEquals(listOf("d1","d2"), dispatchers.dispatcherIds)
    }
}