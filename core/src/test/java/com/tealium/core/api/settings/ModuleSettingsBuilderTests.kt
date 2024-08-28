package com.tealium.core.api.settings

import com.tealium.core.api.data.TealiumValue
import org.junit.Assert.*

import org.junit.Test

class ModuleSettingsBuilderTests {

    @Test
    fun setEnabled_True_Sets_Enabled_True_In_Bundle() {
        val settings = TestSettingsBuilder()
            .setEnabled(true)
            .build()

        assertTrue(settings.getBoolean(ModuleSettingsBuilder.KEY_ENABLED)!!)
    }

    @Test
    fun setEnabled_False_Sets_Enabled_False_In_Bundle() {
        val settings = TestSettingsBuilder()
            .setEnabled(false)
            .build()

        assertFalse(settings.getBoolean(ModuleSettingsBuilder.KEY_ENABLED)!!)
    }

    @Test
    fun build_Returns_Bundle_With_Custom_Properties() {
        val settings = TestSettingsBuilder()
            .setProperty("my_string", "my_string")
            .setProperty("my_number", 100)
            .build()

        assertEquals("my_string", settings.getString("my_string"))
        assertEquals(100, settings.getInt("my_number"))
    }

    private class TestSettingsBuilder: ModuleSettingsBuilder() {
        fun setProperty(key: String, value: Any): TestSettingsBuilder = apply {
            builder.put(key, TealiumValue.convert(value))
        }
    }
}