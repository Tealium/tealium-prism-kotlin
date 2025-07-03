package com.tealium.core.api.settings

import com.tealium.core.internal.settings.consent.ConsentConfiguration
import com.tealium.core.internal.settings.consent.ConsentPurpose
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConsentConfigurationBuilderTests {

    private lateinit var builder: ConsentConfigurationBuilder

    @Before
    fun setUp() {
        builder = ConsentConfigurationBuilder()
    }

    @Test
    fun setTealiumPurposeId_Sets_TealiumPurposeId_In_DataObject() {
        val consentConfig = builder.setTealiumPurposeId("tealium_purpose_id")
            .build()

        assertEquals(
            "tealium_purpose_id",
            consentConfig.getString(ConsentConfiguration.Converter.KEY_TEALIUM_PURPOSE_ID)
        )
    }

    @Test
    fun addPurpose_Sets_DispatcherPurposes_In_DataObject() {
        val consentConfig = builder.addPurpose("purpose1", setOf("dispatcher1"))
            .addPurpose("purpose2", setOf("dispatcher1"))
            .build()

        val purposeObject = consentConfig.getDataObject(ConsentConfiguration.Converter.KEY_PURPOSES)!!
        val purpose1 = purposeObject.get("purpose1", ConsentPurpose.Converter)!!
        val purpose2 = purposeObject.get("purpose2", ConsentPurpose.Converter)!!

        assertEquals("purpose1", purpose1.purposeId)
        assertEquals(setOf("dispatcher1"), purpose1.dispatcherIds)
        assertEquals("purpose2", purpose2.purposeId)
        assertEquals(setOf("dispatcher1"), purpose2.dispatcherIds)
    }

    @Test
    fun setRefireDispatcherIds_Sets_DispatcherPurposes_In_DataObject() {
        val consentConfig = builder.setRefireDispatcherIds(
            setOf("dispatcher_1", "dispatcher_2")
        ).build()

        val refireList =
            consentConfig.getDataList(ConsentConfiguration.Converter.KEY_REFIRE_DISPATCHER_IDS)!!

        assertEquals("dispatcher_1", refireList.getString(0))
        assertEquals("dispatcher_2", refireList.getString(1))
    }
}