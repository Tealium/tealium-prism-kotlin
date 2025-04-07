package com.tealium.core.internal.settings

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Rule
import com.tealium.core.api.settings.ModuleSettingsBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleSettingsTests {

    private fun ModuleSettings.Converter.convert(dataObject: DataObject): ModuleSettings? =
        convert(dataObject.asDataItem())

    @Test
    fun convert_Sets_Enabled_To_True() {
        val settingsObject = ModuleSettingsBuilder()
            .setEnabled(true)
            .build()

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertTrue(settings.enabled)
    }

    @Test
    fun convert_Sets_Enabled_To_False() {
        val settingsObject = ModuleSettingsBuilder()
            .setEnabled(false)
            .build()

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertFalse(settings.enabled)
    }

    @Test
    fun convert_Defaults_To_Enabled_When_Omitted() {
        val settingsObject = ModuleSettingsBuilder().build()

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertTrue(settings.enabled)
    }

    @Test
    fun convert_Sets_Configuration_To_Empty_Object_When_Omitted() {
        val settingsObject = ModuleSettingsBuilder().build()

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertEquals(DataObject.EMPTY_OBJECT, settings.configuration)
    }

    @Test
    fun convert_Sets_Configuration_To_Object_When_Provided() {
        val configuration = DataObject.create {
            put("prop1", "val1")
            put("prop2", "val2")
        }
        val settingsObject = DataObject.create {
            put(ModuleSettings.KEY_CONFIGURATION, configuration)
        }

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertEquals(configuration, settings.configuration)
    }

    @Test
    fun convert_Sets_Rules_To_Null_When_Omitted() {
        val settingsObject = ModuleSettingsBuilder().build()

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertNull(settings.rules)
    }

    @Test
    fun convert_Sets_Rules_To_Given_Rules() {
        val rule = Rule.all(Rule.just("rule1"), Rule.just("rule2"))
        val settingsObject = ModuleSettingsBuilder()
            .setRules(rule)
            .build()

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertEquals(rule, settings.rules)
    }
}