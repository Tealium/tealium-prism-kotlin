package com.tealium.core.internal.settings.consent

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataList
import com.tealium.core.api.settings.ConsentConfigurationBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsentConfigurationTests {

    private lateinit var configurationBuilder: ConsentConfigurationBuilder

    @Before
    fun setUp() {
        configurationBuilder = ConsentConfigurationBuilder()
    }

    @Test
    fun constructor_Sets_Properties_Correctly() {
        val tealiumPurposeId = "test_tealium_purpose_id"
        val refireDispatcherIds = setOf("dispatcher1", "dispatcher2")
        val purposes = mapOf(
            "purpose1" to ConsentPurpose("purpose1", setOf("dispatcher1")),
            "purpose2" to ConsentPurpose("purpose2", setOf("dispatcher2"))
        )

        val configuration = ConsentConfiguration(tealiumPurposeId, refireDispatcherIds, purposes)

        assertEquals(tealiumPurposeId, configuration.tealiumPurposeId)
        assertEquals(refireDispatcherIds, configuration.refireDispatcherIds)
        assertEquals(purposes, configuration.purposes)
    }

    @Test
    fun converter_Converts_DataItem_To_ConsentConfiguration_Correctly_With_All_Fields() {
        val tealiumPurposeId = "test_tealium_purpose_id"
        val refireDispatcherIds = setOf("dispatcher1", "dispatcher2")
        val dataObject = configurationBuilder.setTealiumPurposeId(tealiumPurposeId)
            .setRefireDispatcherIds(refireDispatcherIds)
            .addPurpose("purpose1", setOf("dispatcher1"))
            .addPurpose("purpose2", setOf("dispatcher2"))
            .build()

        val configuration = ConsentConfiguration.Converter.convert(dataObject.asDataItem())!!

        assertEquals(tealiumPurposeId, configuration.tealiumPurposeId)
        assertEquals(refireDispatcherIds, configuration.refireDispatcherIds)
        val expectedPurposes = mapOf(
            "purpose1" to ConsentPurpose("purpose1", setOf("dispatcher1")),
            "purpose2" to ConsentPurpose("purpose2", setOf("dispatcher2"))
        )
        assertEquals(expectedPurposes, configuration.purposes)
    }

    @Test
    fun converter_Returns_Null_When_DataItem_Is_Null() {
        val configuration = ConsentConfiguration.Converter.convert(DataItem.NULL)

        assertNull(configuration)
    }

    @Test
    fun converter_Returns_Null_When_DataItem_Is_Not_DataObject() {
        val configuration = ConsentConfiguration.Converter.convert(DataList.EMPTY_LIST.asDataItem())

        assertNull(configuration)
    }

    @Test
    fun converter_Returns_Null_When_TealiumPurposeId_Is_Missing() {
        val dataObject = configurationBuilder
            .setRefireDispatcherIds(setOf("dispatcher1", "dispatcher2"))
            .addPurpose("purpose1", setOf("dispatcher1"))
            .addPurpose("purpose2", setOf("dispatcher2"))
            .build()

        val configuration = ConsentConfiguration.Converter.convert(dataObject.asDataItem())

        assertNull(configuration)
    }

    @Test
    fun converter_Returns_With_Empty_Refire_Ids_When_RefireDispatcherIds_Is_Missing() {
        val dataObject = configurationBuilder.setTealiumPurposeId("tealium_purpose_id")
            .addPurpose("purpose1", setOf("dispatcher1"))
            .addPurpose("purpose2", setOf("dispatcher2"))
            .build()

        val configuration = ConsentConfiguration.Converter.convert(dataObject.asDataItem())!!

        assertTrue(configuration.refireDispatcherIds.isEmpty())
    }
}
