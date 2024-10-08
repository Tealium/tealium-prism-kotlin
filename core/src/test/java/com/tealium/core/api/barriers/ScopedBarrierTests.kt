package com.tealium.core.api.barriers

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataItem
import com.tealium.core.internal.misc.Converters.ScopedBarrierConverter.KEY_BARRIER_ID
import com.tealium.core.internal.misc.Converters.ScopedBarrierConverter.KEY_SCOPES
import com.tealium.core.internal.misc.Converters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScopedBarrierTests {

    val scopedBarrierConverter = Converters.ScopedBarrierConverter

    @Test
    fun convert_Returns_Null_When_Not_DataObject() {
        assertNull(scopedBarrierConverter.convert(DataItem.NULL))
        assertNull(
            scopedBarrierConverter.convert(
                DataItem.string(
                    """{
                "barrier_id": "test",
                "scopes": [
                    "${BarrierScope.All.value}", "some_dispatcher"
                ]
            }""".trimMargin()
                )
            )
        )
        assertNull(scopedBarrierConverter.convert(DataList.EMPTY_LIST.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Missing_BarriedId() {
        val dataObject = createBarrierDataObject(
            barrierId = null,
            scopes = listOf(BarrierScope.All.value, "some_dispatcher")
        )
        assertNull(scopedBarrierConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Missing_Scopes() {
        val dataObject = createBarrierDataObject(barrierId = "test", scopes = null)
        assertNull(scopedBarrierConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Ignores_Scopes_That_Arent_Strings() {
        val dataObject = createBarrierDataObject(
            barrierId = "test",
            scopes = listOf(BarrierScope.All.value, 1, "some_dispatcher")
        )

        val scopedBarrier = scopedBarrierConverter.convert(dataObject.asDataItem())!!
        assertEquals(2, scopedBarrier.scope.size)
        assertEquals(BarrierScope.All, scopedBarrier.scope.elementAt(0))
        assertEquals(BarrierScope.Dispatcher("some_dispatcher"), scopedBarrier.scope.elementAt(1))
    }

    @Test
    fun convert_Creates_New_Scoped_Barrier() {
        val dataObject = createBarrierDataObject(
            barrierId = "test",
            scopes = listOf(BarrierScope.All.value, "some_dispatcher", "other_dispatcher")
        )

        val scopedBarrier = scopedBarrierConverter.convert(dataObject.asDataItem())!!
        assertEquals("test", scopedBarrier.barrierId)
        assertEquals(3, scopedBarrier.scope.size)
        assertEquals(BarrierScope.All, scopedBarrier.scope.elementAt(0))
        assertEquals(BarrierScope.Dispatcher("some_dispatcher"), scopedBarrier.scope.elementAt(1))
        assertEquals(BarrierScope.Dispatcher("other_dispatcher"), scopedBarrier.scope.elementAt(2))
    }

    @Test
    fun asDataItem_Returns_All_Fields_As_DataObject() {
        val scopedBarrier =
            ScopedBarrier("testId", setOf(BarrierScope.All, BarrierScope.Dispatcher("dispatcher1")))

        val dataItem = scopedBarrier.asDataItem()
        val dataObject = dataItem.getDataObject()!!

        assertEquals("testId", dataObject.getString(KEY_BARRIER_ID))
        assertEquals(
            BarrierScope.All.value,
            dataObject.getDataList(KEY_SCOPES)!!.getString(0)
        )
        assertEquals(
            BarrierScope.Dispatcher("dispatcher1").value,
            dataObject.getDataList(KEY_SCOPES)!!.getString(1)
        )
    }

    @Test
    fun dataItemConvertible_Converted_Returns_Equal_Object() {
        val scopedBarrier =
            ScopedBarrier("testId", setOf(BarrierScope.All, BarrierScope.Dispatcher("dispatcher1")))

        val converted = scopedBarrierConverter.convert(scopedBarrier.asDataItem())

        assertEquals(scopedBarrier, converted)
    }


    private fun createBarrierDataObject(barrierId: String?, scopes: List<Any>?): DataObject {
        return DataObject.create {
            barrierId?.let {
                put(KEY_BARRIER_ID, it)
            }
            scopes?.let {
                put(KEY_SCOPES, DataItem.convert(scopes))
            }
        }
    }
}