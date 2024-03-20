package com.tealium.tests.common

import com.tealium.core.api.Collector
import com.tealium.core.api.Module
import com.tealium.core.api.data.TealiumBundle
import io.mockk.spyk

class TestModule(
    override val name: String,
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