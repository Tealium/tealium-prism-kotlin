package com.tealium.prism.core.api.settings.barriers

import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.internal.settings.BarrierSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarrierSettingsBuilderTests {

    @Test
    fun setScope_Sets_Scope_List() {
        val builder = BarrierSettingsBuilder()
        val scope: BarrierScope = BarrierScope.Dispatchers("dispatcher1", "dispatcher2")
        val result = builder.setScope(scope).build()

        val resultScope = result.get(BarrierSettings.Converter.KEY_SCOPE, BarrierScope.Converter)!!
        val dispatchersScope = resultScope as BarrierScope.Dispatchers
        assertEquals(2, dispatchersScope.dispatcherIds.size)
        assertEquals("dispatcher1", dispatchersScope.dispatcherIds[0])
        assertEquals("dispatcher2", dispatchersScope.dispatcherIds[1])
    }

    @Test
    fun setScope_With_Empty_List_Of_DispatcherIds() {
        val builder = BarrierSettingsBuilder()
        val result = builder.setScope(BarrierScope.Dispatchers(emptyList())).build()

        val resultScope = result.get(BarrierSettings.Converter.KEY_SCOPE, BarrierScope.Converter)
        assertEquals(0, (resultScope as BarrierScope.Dispatchers).dispatcherIds.size)
    }

    @Test
    fun setScope_And_Build_Work_Together() {
        val builder = BarrierSettingsBuilder()
        val scopes: BarrierScope = BarrierScope.All
        val result = builder.setScope(scopes).build()

        val resultScope = result.get(BarrierSettings.Converter.KEY_SCOPE, BarrierScope.Converter)!!
        assertEquals(BarrierScope.All, resultScope)
    }

    @Test
    fun build_With_Empty_Builder_Returns_Minimal_Structure() {
        val builder = BarrierSettingsBuilder()
        val result = builder.build()

        val configuration = result.getDataObject(BarrierSettings.Converter.KEY_CONFIGURATION)!!
        assertEquals(DataObject.EMPTY_OBJECT, configuration)
        assertNull(result.getString(BarrierSettings.Converter.KEY_BARRIER_ID))
        assertNull(result.get(BarrierSettings.Converter.KEY_SCOPE))
    }
}
