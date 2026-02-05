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
            .setScopes(setOf(BarrierScope.Dispatcher("test-dispatcher")))
            .setBatchSize(3)
            .build()

        val scopes = result.getDataList(BarrierSettings.Converter.KEY_SCOPES)!!
        assertEquals(1, scopes.size)
        assertEquals("test-dispatcher", scopes.getString(0))

        val configuration = result.getDataObject(BarrierSettings.Converter.KEY_CONFIGURATION)!!
        assertEquals(3, configuration.getInt(BatchingBarrier.KEY_BATCH_SIZE))
    }
}
