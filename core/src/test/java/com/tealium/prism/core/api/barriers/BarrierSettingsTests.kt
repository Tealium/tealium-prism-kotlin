package com.tealium.prism.core.api.barriers

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.internal.settings.BarrierSettings
import com.tealium.prism.core.internal.settings.BarrierSettings.Converter.KEY_BARRIER_ID
import com.tealium.prism.core.internal.settings.BarrierSettings.Converter.KEY_SCOPE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarrierSettingsTests {

    private val barrierSettingsConverter = BarrierSettings.Converter

    @Test
    fun convert_Returns_Null_When_Not_DataObject() {
        assertNull(barrierSettingsConverter.convert(DataItem.NULL))
        assertNull(
            barrierSettingsConverter.convert(
                DataItem.string(
                    """{
                "barrier_id": "test",
                "scope": "${BarrierScope.All.STRING_VALUE}"
            }""".trimMargin()
                )
            )
        )
        assertNull(barrierSettingsConverter.convert(DataList.EMPTY_LIST.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Missing_BarrierId() {
        val dataObject = createBarrierDataObject(
            barrierId = null,
            scope = BarrierScope.Dispatchers("dispatcher1", "dispatcher2")
        )
        assertNull(barrierSettingsConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Scope_Is_Missing() {
        val dataObject = createBarrierDataObject(barrierId = "test", scope = null)
        assertNull(barrierSettingsConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Returns_BarrierWithScope_When_Data_Is_Valid() {
        val dataObject = createBarrierDataObject(
            barrierId = "test",
            scope = BarrierScope.Dispatchers("dispatcher1", "dispatcher2", "dispatcher3")
        )

        val barrierSettings = barrierSettingsConverter.convert(dataObject.asDataItem())!!
        assertEquals("test", barrierSettings.barrierId)
        val dispatchScope = barrierSettings.scope as BarrierScope.Dispatchers
        assertEquals(3, dispatchScope.dispatcherIds.size)
        assertEquals("dispatcher1", dispatchScope.dispatcherIds[0])
        assertEquals("dispatcher2", dispatchScope.dispatcherIds[1])
        assertEquals("dispatcher3", dispatchScope.dispatcherIds[2])
    }

    @Test
    fun asDataItem_Serializes_AllFields_When_Scope_Is_Present() {
        val barrierSettings =
            BarrierSettings("testId",  BarrierScope.Dispatchers("dispatcher1", "dispatcher2"))

        val dataItem = barrierSettings.asDataItem()
        val dataObject = dataItem.getDataObject()!!

        assertEquals("testId", dataObject.getString(KEY_BARRIER_ID))
        assertEquals(
            "dispatcher1",
            dataObject.getDataList(KEY_SCOPE)!!.getString(0)
        )
        assertEquals(
            "dispatcher2",
            dataObject.getDataList(KEY_SCOPE)!!.getString(1)
        )
    }

    @Test
    fun convert_Returns_Equal_Object_When_Converting_AsDataItem_Result() {
        val dispatchersBarrier = BarrierSettings("testId", BarrierScope.Dispatchers("dispatcher1", "dispatcher2"))
        val allBarrier = BarrierSettings("testId", BarrierScope.All)

        assertEquals(dispatchersBarrier, barrierSettingsConverter.convert(dispatchersBarrier.asDataItem()))
        assertEquals(allBarrier, barrierSettingsConverter.convert(allBarrier.asDataItem()))
    }


    private fun createBarrierDataObject(barrierId: String?, scope: BarrierScope?): DataObject {
        return DataObject.create {
            barrierId?.let {
                put(KEY_BARRIER_ID, it)
            }
            scope?.let {
                put(KEY_SCOPE, it)
            }
        }
    }
}