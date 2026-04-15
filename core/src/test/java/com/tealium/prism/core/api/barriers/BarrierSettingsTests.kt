package com.tealium.prism.core.api.barriers

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.internal.settings.BarrierSettings
import com.tealium.prism.core.internal.settings.BarrierSettings.Converter.KEY_BARRIER_ID
import com.tealium.prism.core.internal.settings.BarrierSettings.Converter.KEY_SCOPES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
                "scopes": [
                    "${BarrierScope.All.value}", "some_dispatcher"
                ]
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
            scopes = listOf(BarrierScope.All.value, "some_dispatcher")
        )
        assertNull(barrierSettingsConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Returns_ValidSettings_When_Scopes_Are_Missing() {
        val dataObject = createBarrierDataObject(barrierId = "test", scopes = null)
        val settings = barrierSettingsConverter.convert(dataObject.asDataItem())
        assertNotNull(settings)
        assertNull(settings?.scopes)
    }

    @Test
    fun convert_Ignores_NonString_Entries_When_Scopes_Contain_NonStrings() {
        val dataObject = createBarrierDataObject(
            barrierId = "test",
            scopes = listOf(BarrierScope.All.value, 1, "some_dispatcher")
        )

        val barrierSettings = barrierSettingsConverter.convert(dataObject.asDataItem())!!
        assertEquals(2, barrierSettings.scopes?.size)
        assertEquals(BarrierScope.All, barrierSettings.scopes?.elementAt(0))
        assertEquals(BarrierScope.Dispatcher("some_dispatcher"), barrierSettings.scopes?.elementAt(1))
    }

    @Test
    fun convert_Returns_BarrierWithAllScopes_When_Data_Is_Valid() {
        val dataObject = createBarrierDataObject(
            barrierId = "test",
            scopes = listOf(BarrierScope.All.value, "some_dispatcher", "other_dispatcher")
        )

        val barrierSettings = barrierSettingsConverter.convert(dataObject.asDataItem())!!
        assertEquals("test", barrierSettings.barrierId)
        assertEquals(3, barrierSettings.scopes?.size)
        assertEquals(BarrierScope.All, barrierSettings.scopes?.elementAt(0))
        assertEquals(BarrierScope.Dispatcher("some_dispatcher"), barrierSettings.scopes?.elementAt(1))
        assertEquals(BarrierScope.Dispatcher("other_dispatcher"), barrierSettings.scopes?.elementAt(2))
    }

    @Test
    fun asDataItem_Serializes_AllFields_When_Scopes_Are_Present() {
        val barrierSettings =
            BarrierSettings("testId", setOf(BarrierScope.All, BarrierScope.Dispatcher("dispatcher1")))

        val dataItem = barrierSettings.asDataItem()
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
    fun asDataItem_Omits_ScopesKey_When_Scopes_Are_Null() {
        val barrierSettings = BarrierSettings("testId", scopes = null)

        val dataObject = barrierSettings.asDataItem().getDataObject()!!

        assertEquals("testId", dataObject.getString(KEY_BARRIER_ID))
        assertNull(dataObject.get(KEY_SCOPES))
    }

    @Test
    fun convert_Returns_Equal_Object_When_Converting_AsDataItem_Result() {
        val barrierSettings =
            BarrierSettings("testId", setOf(BarrierScope.All, BarrierScope.Dispatcher("dispatcher1")))

        val converted = barrierSettingsConverter.convert(barrierSettings.asDataItem())

        assertEquals(barrierSettings, converted)
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