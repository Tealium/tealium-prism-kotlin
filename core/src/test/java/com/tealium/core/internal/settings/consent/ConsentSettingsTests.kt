package com.tealium.core.internal.settings.consent

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.settings.ConsentConfigurationBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsentSettingsTests {

    @Test
    fun constructor_Sets_Properties_Correctly() {
        val configurations = mapOf(
            "vendor1" to ConsentConfiguration(
                "purpose1",
                setOf("dispatcher1"),
                mapOf("purpose1" to ConsentPurpose("purpose1", setOf("dispatcher1")))
            ),
            "vendor2" to ConsentConfiguration(
                "purpose2",
                setOf("dispatcher2"),
                mapOf("purpose2" to ConsentPurpose("purpose2", setOf("dispatcher2")))
            )
        )

        val settings = ConsentSettings(configurations)

        assertEquals(configurations, settings.configurations)
    }

    @Test
    fun converter_Converts_DataItem_To_ConsentSettings_Correctly() {
        val vendor1Configuration = ConsentConfigurationBuilder().setTealiumPurposeId("purpose1")
            .setRefireDispatcherIds(setOf("dispatcher1"))
            .addPurpose("purpose1", setOf("dispatcher1"))
            .build()

        val vendor2Configuration = ConsentConfigurationBuilder().setTealiumPurposeId("purpose2")
            .setRefireDispatcherIds(setOf("dispatcher2"))
            .addPurpose("purpose2", setOf("dispatcher2"))
            .build()

        val dataObject = DataObject.create {
            put(ConsentSettings.Converter.KEY_CONFIGURATIONS, DataObject.create {
                put("vendor1", vendor1Configuration)
                put("vendor2", vendor2Configuration)
            }.asDataItem())
        }

        val settings = ConsentSettings.Converter.convert(dataObject.asDataItem())!!

        assertEquals("purpose1", settings.configurations["vendor1"]?.tealiumPurposeId)
        assertEquals(setOf("dispatcher1"), settings.configurations["vendor1"]?.refireDispatcherIds)
        assertEquals(
            ConsentPurpose("purpose1", setOf("dispatcher1")),
            settings.configurations["vendor1"]?.purposes?.get("purpose1")
        )

        assertEquals("purpose2", settings.configurations["vendor2"]?.tealiumPurposeId)
        assertEquals(setOf("dispatcher2"), settings.configurations["vendor2"]?.refireDispatcherIds)
        assertEquals(
            ConsentPurpose("purpose2", setOf("dispatcher2")),
            settings.configurations["vendor2"]?.purposes?.get("purpose2")
        )
    }

    @Test
    fun converter_Returns_Null_When_DataItem_Is_Null() {
        val settings = ConsentSettings.Converter.convert(DataItem.NULL)

        assertNull(settings)
    }

    @Test
    fun converter_Returns_Null_When_Configurations_Is_Missing() {
        val settings = ConsentSettings.Converter.convert(DataObject.EMPTY_OBJECT.asDataItem())

        assertNull(settings)
    }
}
