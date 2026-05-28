package com.tealium.prism.core.api.settings.barriers

import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.internal.barriers.BatchingBarrier
import com.tealium.prism.core.internal.settings.BarrierSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchingBarrierSettingsBuilderTests {

    @Test
    fun setBatchSize_Sets_Batch_Size() {
        val builder = BatchingBarrierSettingsBuilder()
        val result = builder.setBatchSize(5).build()

        val configuration = result.getDataObject(BarrierSettings.Converter.KEY_CONFIGURATION)!!
        assertEquals(5, configuration.getInt(BatchingBarrier.KEY_BATCH_SIZE))
    }

    @Test
    fun inheritance_From_Base_Builder_Works() {
        val result = BatchingBarrierSettingsBuilder()
            .setScope(BarrierScope.Dispatchers("test-dispatcher"))
            .setBatchSize(3)
            .build()

        val scope = result.get(BarrierSettings.Converter.KEY_SCOPE, BarrierScope.Converter)!!
        val dispatcherScope = scope as BarrierScope.Dispatchers
        assertEquals(1, dispatcherScope.dispatcherIds.size)
        assertEquals("test-dispatcher", dispatcherScope.dispatcherIds[0])

        val configuration = result.getDataObject(BarrierSettings.Converter.KEY_CONFIGURATION)!!
        assertEquals(3, configuration.getInt(BatchingBarrier.KEY_BATCH_SIZE))
    }
}
