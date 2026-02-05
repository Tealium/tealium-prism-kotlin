package com.tealium.prism.core.api.barriers

import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.internal.dispatch.barrier
import com.tealium.prism.core.internal.dispatch.barrierFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarrierFactoryTests {

    @Test
    fun default_getEnforcedSettings_returns_empty_data_object() {
        val factory = barrierFactory(
            barrier("test-barrier", Observables.just(BarrierState.Open)),
            defaultScopes = setOf(BarrierScope.All)
        )
        assertEquals(DataObject.EMPTY_OBJECT, factory.getEnforcedSettings())
    }

    @Test
    fun custom_getEnforcedSettings_returns_enforced_settings() {
        val settings = DataObject.create {
            put("test_key", "test_value")
        }
        val factory = barrierFactory(
            barrier("test-barrier", Observables.just(BarrierState.Open)),
            defaultScopes = setOf(BarrierScope.All),
            enforcedSettings = settings
        )

        assertEquals(settings, factory.getEnforcedSettings())
    }

    @Test
    fun factory_id_matches_barrier_id() {
        val barrierId = "test-barrier"
        val factory = barrierFactory(
            barrier(barrierId, Observables.just(BarrierState.Open)),
            defaultScopes = setOf(BarrierScope.All)
        )
        assertEquals(barrierId, factory.id)
    }

    @Test
    fun defaultScopes_returns_configured_scopes() {
        val expectedScopes = setOf(
            BarrierScope.All,
            BarrierScope.Dispatcher("dispatcher1")
        )
        val factory = barrierFactory(
            barrier("test-barrier", Observables.just(BarrierState.Open)),
            defaultScopes = expectedScopes
        )

        assertEquals(expectedScopes, factory.defaultScopes())
    }

    @Test
    fun defaultScopes_returns_single_scope_when_configured() {
        val expectedScopes = setOf(BarrierScope.Dispatcher("collect"))
        val factory = barrierFactory(
            barrier("test-barrier", Observables.just(BarrierState.Open)),
            defaultScopes = expectedScopes
        )

        assertEquals(expectedScopes, factory.defaultScopes())
    }

    @Test
    fun defaultScopes_returns_empty_set_when_no_default_scopes() {
        val factory = barrierFactory(
            barrier("test-barrier", Observables.just(BarrierState.Open)),
            defaultScopes = emptySet()
        )

        assertTrue(factory.defaultScopes().isEmpty())
    }

    @Test
    fun factory_with_custom_barrier_id_uses_correct_id() {
        val customId = "custom_barrier_id"
        val factory = barrierFactory(
            barrier(customId, Observables.just(BarrierState.Open)),
            defaultScopes = setOf(BarrierScope.All)
        )

        assertEquals(customId, factory.id)
    }
}
