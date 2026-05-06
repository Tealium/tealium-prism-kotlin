package com.tealium.prism.core.api.barriers

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.internal.dispatch.barrier
import com.tealium.prism.core.internal.dispatch.barrierFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarrierFactoryTests {

    @Test
    fun default_GetEnforcedSettings_Returns_Empty_Data_Object() {
        val factory = barrierFactory(
            barrier("test-barrier", Observables.just(BarrierState.Open)),
            defaultScope = BarrierScope.All
        )
        assertEquals(DataObject.EMPTY_OBJECT, factory.getEnforcedSettings())
    }

    @Test
    fun custom_GetEnforcedSettings_Returns_Enforced_Settings() {
        val settings = DataObject.create {
            put("test_key", "test_value")
        }
        val factory = barrierFactory(
            barrier("test-barrier", Observables.just(BarrierState.Open)),
            defaultScope = BarrierScope.All,
            enforcedSettings = settings
        )

        assertEquals(settings, factory.getEnforcedSettings())
    }

    @Test
    fun factory_Id_Matches_Barrier_Id() {
        val barrierId = "test-barrier"
        val factory = barrierFactory(
            barrier(barrierId, Observables.just(BarrierState.Open)),
            defaultScope = BarrierScope.All
        )
        assertEquals(barrierId, factory.id)
    }

    @Test
    fun defaultScope_Returns_Configured_Scope() {
        val expectedScope =
            BarrierScope.Dispatchers("dispatcher1", "dispatcher2")

        val factory = barrierFactory(
            barrier("test-barrier", Observables.just(BarrierState.Open)),
            defaultScope = expectedScope
        )

        assertEquals(expectedScope, factory.defaultScope())
    }

    @Test
    fun defaultScope_Returns_Single_Scope_When_Configured() {
        val expectedScope = BarrierScope.Dispatchers("collect")
        val factory = barrierFactory(
            barrier("test-barrier", Observables.just(BarrierState.Open)),
            defaultScope = expectedScope
        )

        assertEquals(expectedScope, factory.defaultScope())
    }

    @Test
    fun defaultScope_Returns_Empty_List_When_No_Default_Scope() {
        val factory = barrierFactory(
            barrier("test-barrier", Observables.just(BarrierState.Open)),
            defaultScope = BarrierScope.Dispatchers(emptyList())
        )

        assertTrue(factory.defaultScope() is BarrierScope.Dispatchers)
        val dispatchers = (factory.defaultScope() as BarrierScope.Dispatchers).dispatcherIds
        assertTrue(dispatchers.isEmpty())
    }

    @Test
    fun factory_Id_Matches_Correct_Id() {
        val customId = "custom_barrier_id"
        val factory = barrierFactory(
            barrier(customId, Observables.just(BarrierState.Open)),
            defaultScope = BarrierScope.All
        )

        assertEquals(customId, factory.id)
    }
}
