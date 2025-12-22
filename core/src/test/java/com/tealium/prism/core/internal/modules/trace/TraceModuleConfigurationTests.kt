package com.tealium.prism.core.internal.modules.trace

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.settings.modules.TraceSettingsBuilder
import com.tealium.prism.core.internal.settings.ModuleSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TraceModuleConfigurationTests {

    @Test
    fun fromDataObject_UsesDefaultValues_WhenNoOverridesProvided() {
        val config = TraceModuleConfiguration.fromDataObject(
            createConfigurationObject { }
        )

        assertEquals(TraceModuleConfiguration.DEFAULT_TRACK_ERRORS, config.trackErrors)
    }

    @Test
    fun fromDataObject_UsesProvidedOverrides() {
        val config = TraceModuleConfiguration.fromDataObject(
            createConfigurationObject {
                it.setTrackErrors(true)
            }
        )

        assertEquals(true, config.trackErrors)
    }
}

inline fun createConfigurationObject(
    block: (TraceSettingsBuilder) -> Unit
): DataObject {
    val builder = TraceSettingsBuilder()
    block.invoke(builder)
    return builder.build().getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
}
