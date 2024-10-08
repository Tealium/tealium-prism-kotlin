package com.tealium.core.api.modules

import com.tealium.core.api.data.DataObject
import org.junit.Assert.assertSame
import org.junit.Test

class ModuleTests {

    @Test
    fun updateSettings_Returns_Self() {
        val module = createModule()

        assertSame(module, module.updateSettings(DataObject.EMPTY_OBJECT))
    }

    private fun createModule(): Module {
        return object: Module {
            override val id: String = "test"
            override val version: String = "1.0.0"
        }
    }
}