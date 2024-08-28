package com.tealium.tests.common

import com.tealium.core.api.modules.Module
import io.mockk.spyk

class TestModule(
    override val id: String,
    override val version: String = "0.0"
): Module {
    companion object {
        fun mock(
            name: String,
            version: String = "1.0",
        ): TestModule {
            return spyk(TestModule(name, version))
        }
    }
}