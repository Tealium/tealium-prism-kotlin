package com.tealium.core.api.modules

import com.tealium.core.internal.settings.ModuleSettingsImpl
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ModuleTests {

    @Test
    fun updateSettings_Default_Returns_Null_When_Disabled() {
        val module = createModule()

        assertNull(module.updateSettings(ModuleSettingsImpl(enabled = false)))
    }

    @Test
    fun updateSettings_Default_Returns_Self_When_Enabled() {
        val module = createModule()

        assertSame(module, module.updateSettings(ModuleSettingsImpl(enabled = true)))
    }

    private fun createModule(): Module {
        return object: Module {
            override val name: String = "test"
            override val version: String = "1.0.0"
        }
    }
}