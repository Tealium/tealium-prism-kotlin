package com.tealium.core.internal.settings.consent

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemUtils.asDataList
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsentPurposeTests {

    @Test
    fun constructor_Sets_Properties_Correctly() {
        val purposeId = "test_purpose_id"
        val dispatcherIds = setOf("dispatcher1", "dispatcher2")

        val consentPurpose = ConsentPurpose(purposeId, dispatcherIds)

        assertEquals(purposeId, consentPurpose.purposeId)
        assertEquals(dispatcherIds, consentPurpose.dispatcherIds)
    }

    @Test
    fun converter_Converts_DataItem_To_ConsentPurpose_Correctly() {
        val purposeId = "test_purpose_id"
        val dispatcherIds = setOf("dispatcher1", "dispatcher2")

        val dataObject = DataObject.create {
            put(ConsentPurpose.Converter.KEY_PURPOSE_ID, purposeId)
            put(ConsentPurpose.Converter.KEY_DISPATCHER_IDS, dispatcherIds.asDataList())
        }

        val consentPurpose = ConsentPurpose.Converter.convert(dataObject.asDataItem())!!

        assertEquals(purposeId, consentPurpose.purposeId)
        assertEquals(dispatcherIds, consentPurpose.dispatcherIds)
    }

    @Test
    fun converter_Returns_Null_When_DataItem_Is_Null() {
        val consentPurpose = ConsentPurpose.Converter.convert(DataItem.NULL)

        assertNull(consentPurpose)
    }

    @Test
    fun converter_Returns_Null_When_DataItem_Is_Not_DataObject() {
        val consentPurpose = ConsentPurpose.Converter.convert(DataList.EMPTY_LIST.asDataItem())

        assertNull(consentPurpose)
    }

    @Test
    fun converter_Returns_Null_When_PurposeId_Is_Missing() {
        val dispatcherIds = setOf("dispatcher1", "dispatcher2")

        val dataObject = DataObject.create {
            put(ConsentPurpose.Converter.KEY_DISPATCHER_IDS, dispatcherIds.asDataList())
        }

        val consentPurpose = ConsentPurpose.Converter.convert(dataObject.asDataItem())

        assertNull(consentPurpose)
    }

    @Test
    fun converter_Returns_Null_When_DispatcherIds_Is_Missing() {
        val purposeId = "test_purpose_id"

        val dataObject = DataObject.create {
            put(ConsentPurpose.Converter.KEY_PURPOSE_ID, purposeId)
        }

        val consentPurpose = ConsentPurpose.Converter.convert(dataObject.asDataItem())

        assertNull(consentPurpose)
    }
}