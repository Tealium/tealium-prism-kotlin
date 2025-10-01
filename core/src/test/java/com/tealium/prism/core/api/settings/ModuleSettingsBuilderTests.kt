package com.tealium.prism.core.api.settings

import com.tealium.prism.core.internal.settings.ModuleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModuleSettingsBuilderTests {

    @Test
    fun setEnabled_True_Sets_Enabled_True_In_DataObject() {
        val settings = TestSettingsBuilder()
            .setEnabled(true)
            .build()

        assertTrue(settings.getBoolean(ModuleSettings.KEY_ENABLED)!!)
    }

    @Test
    fun setEnabled_False_Sets_Enabled_False_In_DataObject() {
        val settings = TestSettingsBuilder()
            .setEnabled(false)
            .build()

        assertFalse(settings.getBoolean(ModuleSettings.KEY_ENABLED)!!)
    }

    @Test
    fun setModuleId_Sets_ModuleId_In_DataObject() {
        val settings = TestSettingsBuilder()
            .setModuleId("custom-id")
            .build()

        assertEquals("custom-id", settings.getString(ModuleSettings.KEY_MODULE_ID)!!)
    }

    @Test
    fun build_Returns_DataObject_With_Custom_Properties_In_Configuration_Key() {
        val settings = TestSettingsBuilder()
            .setProperty("my_string", "my_string")
            .setProperty("my_number", 100)
            .build()

        val configurations = settings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals("my_string", configurations.getString("my_string"))
        assertEquals(100, configurations.getInt("my_number"))
    }

}