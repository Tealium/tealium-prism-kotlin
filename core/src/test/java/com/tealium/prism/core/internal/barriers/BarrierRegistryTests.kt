package com.tealium.prism.core.internal.barriers

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.internal.barriers.BarrierRegistry
import com.tealium.prism.core.internal.barriers.BatchingBarrier
import com.tealium.prism.core.internal.dispatch.barrier
import com.tealium.prism.core.internal.dispatch.barrierFactory
import com.tealium.prism.core.internal.network.ConnectivityBarrier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarrierRegistryTests {

    private val registry = BarrierRegistry

    // When creating new barriers, make sure to add them here
    private val installedBarriers = listOf(
        ConnectivityBarrier.BARRIER_ID,
        BatchingBarrier.BARRIER_ID
    )

    @After
    fun tearDown() {
        registry.clearAdditionalBarriers()
    }

    @Test
    fun defaultBarriers_Contains_All_Installed_Barriers() {
        val defaultBarriers = registry.defaultBarriers
        val defaultBarriersIds = defaultBarriers.map { it.id }
        assertTrue(installedBarriers.all { it in defaultBarriersIds })
    }

    @Test
    fun defaultBarriers_Returns_Correct_Default_Scopes_For_Installed_Barriers() {
        val defaultBarriers = registry.defaultBarriers
        val connectivityFactory = defaultBarriers.firstOrNull { it.id == ConnectivityBarrier.BARRIER_ID }
        val batchingFactory = defaultBarriers.firstOrNull { it.id == BatchingBarrier.BARRIER_ID }

        assertEquals(
            setOf(BarrierScope.Dispatcher(Modules.Types.COLLECT)),
            connectivityFactory?.defaultScopes()
        )
        assertEquals(
            emptySet<BarrierScope>(),
            batchingFactory?.defaultScopes()
        )
    }

    @Test
    fun addDefaultBarrier_Increases_DefaultBarriers_Count_When_Barrier_Added() {
        assertEquals(installedBarriers.size, registry.defaultBarriers.size)

        val mockBarrier = barrierFactory(
            barrier("MockBarrier", Observables.just(BarrierState.Open)),
            defaultScopes = setOf(BarrierScope.All)
        )
        registry.addDefaultBarrier(mockBarrier)

        assertEquals(installedBarriers.size + 1, registry.defaultBarriers.size)
        assertTrue(registry.defaultBarriers.any { it.id == mockBarrier.id })
    }

    @Test
    fun addDefaultBarriers_Adds_All_Barriers_When_List_Provided() {
        assertEquals(installedBarriers.size, registry.defaultBarriers.size)

        val mockBarrier1 = barrierFactory(
            barrier("MockBarrier1", Observables.just(BarrierState.Open)),
            defaultScopes = setOf(BarrierScope.All)
        )
        val mockBarrier2 = barrierFactory(
            barrier("MockBarrier2", Observables.just(BarrierState.Open)),
            defaultScopes = setOf(BarrierScope.Dispatcher("test"))
        )
        registry.addDefaultBarriers(listOf(mockBarrier1, mockBarrier2))

        assertEquals(installedBarriers.size + 2, registry.defaultBarriers.size)
        assertTrue(registry.defaultBarriers.any { it.id == mockBarrier1.id })
        assertTrue(registry.defaultBarriers.any { it.id == mockBarrier2.id })
    }

    @Test
    fun defaultBarriers_Includes_Both_Core_And_Additional_Barriers_When_Additional_Added() {
        val mockBarrier = barrierFactory(
            barrier("MockBarrier", Observables.just(BarrierState.Open)),
            defaultScopes = setOf(BarrierScope.All)
        )
        registry.addDefaultBarrier(mockBarrier)

        val defaultBarriers = registry.defaultBarriers
        val coreBarrierIds = installedBarriers.toSet()
        val additionalBarrierIds = setOf(mockBarrier.id)
        val defaultBarrierIds = defaultBarriers.map { it.id }.toSet()

        assertTrue(coreBarrierIds.all { it in defaultBarrierIds })
        assertTrue(additionalBarrierIds.all { it in defaultBarrierIds })
        assertEquals(coreBarrierIds.size + additionalBarrierIds.size, defaultBarrierIds.size)
    }

    @Test
    fun clearAdditionalBarriers_Removes_All_Additional_Barriers_When_Called() {
        val mockBarrier1 = barrierFactory(
            barrier("mockBarrier1", Observables.just(BarrierState.Open)),
            defaultScopes = setOf(BarrierScope.All)
        )
        val mockBarrier2 = barrierFactory(
            barrier("mockBarrier2", Observables.just(BarrierState.Open)),
            defaultScopes = setOf(BarrierScope.Dispatcher("test"))
        )

        registry.addDefaultBarrier(mockBarrier1)
        registry.addDefaultBarrier(mockBarrier2)
        assertEquals(installedBarriers.size + 2, registry.defaultBarriers.size)

        registry.clearAdditionalBarriers()
        assertEquals(installedBarriers.size, registry.defaultBarriers.size)

        val defaultBarrierIds = registry.defaultBarriers.map { it.id }
        assertTrue(installedBarriers.all { it in defaultBarrierIds })
        assertTrue(!defaultBarrierIds.contains(mockBarrier1.id))
        assertTrue(!defaultBarrierIds.contains(mockBarrier2.id))
    }

    @Test
    fun defaultBarriers_Returns_New_List_When_Called_Each_Time() {
        val list1 = registry.defaultBarriers
        val list2 = registry.defaultBarriers

        // Should be equal but not the same instance if it's mutable
        assertEquals(list1.size, list2.size)
        assertTrue(list1.map { it.id }.containsAll(list2.map { it.id }))
    }
}
