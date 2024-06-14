package com.tealium.tests.common

import com.tealium.core.api.Collector
import com.tealium.core.api.data.TealiumBundle
import io.mockk.spyk

class TestCollector(
    override val name: String,
    override val version: String = "1.0",
    private var onCollect: () -> TealiumBundle = { TealiumBundle.EMPTY_BUNDLE }
) : Collector {
    override fun collect(): TealiumBundle = onCollect.invoke()

    companion object {
        fun mock(
            name: String,
            version: String = "1.0",
            onCollect: () -> TealiumBundle = { TealiumBundle.EMPTY_BUNDLE }
        ): TestCollector {
            return spyk(TestCollector(name, version, onCollect))
        }
    }
}