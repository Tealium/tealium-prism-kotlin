package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.settings.modules.CollectorSettingsBuilder
import com.tealium.prism.core.api.settings.modules.DispatcherSettingsBuilder
import com.tealium.prism.core.api.settings.modules.ModuleSettingsBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModuleSettingsTests {

    private fun ModuleSettings.Converter.convert(dataObject: DataObject): ModuleSettings? =
        convert(dataObject.asDataItem())

    @Test
    fun convert_Sets_Enabled_To_True() {
        val settingsObject = ModuleSettingsBuilder("module")
            .setEnabled(true)
            .build()

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertTrue(settings.enabled)
    }

    @Test
    fun convert_Sets_Enabled_To_False() {
        val settingsObject = ModuleSettingsBuilder("module")
            .setEnabled(false)
            .build()

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertFalse(settings.enabled)
    }

    @Test
    fun convert_Defaults_To_Enabled_When_Omitted() {
        val settingsObject = ModuleSettingsBuilder("module").build()

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertTrue(settings.enabled)
    }

    @Test
    fun convert_Sets_Configuration_To_Empty_Object_When_Omitted() {
        val settingsObject = ModuleSettingsBuilder("module").build()

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
            put(ModuleSettings.KEY_MODULE_TYPE, "module")
            put(ModuleSettings.KEY_CONFIGURATION, configuration)
        }

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertEquals(configuration, settings.configuration)
    }

    @Test
    fun convert_Sets_Rules_To_Null_When_Omitted() {
        val settingsObject = ModuleSettingsBuilder("module").build()

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertNull(settings.rules)
    }

    @Test
    fun convert_Sets_Rules_To_Given_Rules() {
        val rule = Rule.all(Rule.just("rule1"), Rule.just("rule2"))
        val settingsObject = CollectorSettingsBuilder("module")
            .setRules(rule)
            .build()

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertEquals(rule, settings.rules)
    }

    @Test
    fun convert_Sets_Mappings_To_Null_When_Omitted() {
        val settingsObject = DispatcherSettingsBuilder("module")
            .build()

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        assertNull(settings.mappings)
    }

    @Test
    fun convert_Sets_Mappings_To_Given_Mappings() {
        val settingsObject = DispatcherSettingsBuilder("module")
            .setMappings {
                from("source", "destination")
                    .ifValueEquals("target")
                constant("other", "destination")
                    .ifValueEquals("source", "target")
            }
            .build()

        val settings = ModuleSettings.Converter.convert(settingsObject)!!
        val expected1 =
            MappingParameters(ReferenceContainer.key("source"), ValueContainer("target"), null)
        val expected2 = expected1.copy(mapTo = ValueContainer("other"))
        assertEquals(expected1, settings.mappings!![0].parameters)
        assertEquals(expected2, settings.mappings!![1].parameters)
    }
}