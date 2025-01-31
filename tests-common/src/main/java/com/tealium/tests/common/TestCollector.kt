package com.tealium.tests.common

import com.tealium.core.api.modules.Collector
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.tracking.DispatchContext
import io.mockk.spyk

class TestCollector(
    override val id: String,
    override val version: String = "1.0",
    private var onCollect: () -> DataObject = { DataObject.EMPTY_OBJECT }
) : Collector {
    override fun collect(dispatchContext: DispatchContext): DataObject = onCollect.invoke()

    companion object {
        fun mock(
            name: String,
            version: String = "1.0",
            onCollect: () -> DataObject = { DataObject.EMPTY_OBJECT }
        ): TestCollector {
            return spyk(TestCollector(name, version, onCollect))
        }
    }
}