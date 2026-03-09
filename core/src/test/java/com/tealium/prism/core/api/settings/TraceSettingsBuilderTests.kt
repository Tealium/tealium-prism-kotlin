package com.tealium.prism.core.api.settings

import com.tealium.prism.core.api.settings.modules.TraceSettingsBuilder
import com.tealium.prism.core.internal.modules.trace.TraceModuleConfiguration
import com.tealium.prism.core.internal.settings.ModuleSettings
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TraceSettingsBuilderTests {
    private lateinit var builder: TraceSettingsBuilder

    @Before
    fun setUp() {
        builder = TraceSettingsBuilder()
    }

    @Test
    fun build_Returns_Empty_DataObject_When_No_Overrides_Provided() {
        val config = builder.build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertNull(config.getBoolean(TraceModuleConfiguration.KEY_TRACK_ERRORS))
    }

    @Test
    fun setShouldTrackErrors_Sets_TrackErrors_In_DataObject() {
        val config = builder.setTrackErrors(true).build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertTrue(config.getBoolean(TraceModuleConfiguration.KEY_TRACK_ERRORS)!!)
    }
}