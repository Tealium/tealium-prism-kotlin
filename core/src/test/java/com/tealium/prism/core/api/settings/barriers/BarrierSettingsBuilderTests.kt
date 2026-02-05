package com.tealium.prism.core.api.settings.barriers

import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.internal.settings.BarrierSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarrierSettingsBuilderTests {

    @Test
    fun setScopes_Sets_Scopes_Array() {
        val builder = BarrierSettingsBuilder()
        val scopes: Set<BarrierScope> = setOf(BarrierScope.All, BarrierScope.Dispatcher("test-dispatcher"))
        val result = builder.setScopes(scopes).build()

        val resultScopes = result.getDataList(BarrierSettings.Converter.KEY_SCOPES)!!
        assertEquals(2, resultScopes.size)
        assertEquals(BarrierScope.All.value, resultScopes.getString(0))
        assertEquals("test-dispatcher", resultScopes.getString(1))
    }

    @Test
    fun setScopes_With_Empty_Set() {
        val builder = BarrierSettingsBuilder()
        val result = builder.setScopes(emptySet()).build()

        val resultScopes = result.getDataList(BarrierSettings.Converter.KEY_SCOPES)!!
        assertEquals(0, resultScopes.size)
    }

    @Test
    fun setScopes_And_Build_Work_Together() {
        val builder = BarrierSettingsBuilder()
        val scopes: Set<BarrierScope> = setOf(BarrierScope.All)
        val result = builder.setScopes(scopes).build()

        val resultScopes = result.getDataList(BarrierSettings.Converter.KEY_SCOPES)!!
        assertEquals(1, resultScopes.size)
        assertEquals(BarrierScope.All.value, resultScopes.getString(0))
    }

    @Test
    fun build_With_Empty_Builder_Returns_Minimal_Structure() {
        val builder = BarrierSettingsBuilder()
        val result = builder.build()

        val configuration = result.getDataObject(BarrierSettings.Converter.KEY_CONFIGURATION)!!
        assertEquals(DataObject.EMPTY_OBJECT, configuration)
        assertNull(result.getString(BarrierSettings.Converter.KEY_BARRIER_ID))
        assertNull(result.getDataList(BarrierSettings.Converter.KEY_SCOPES))
    }
}
