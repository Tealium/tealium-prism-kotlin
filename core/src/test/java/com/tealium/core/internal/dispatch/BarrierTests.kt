package com.tealium.core.internal.dispatch

import app.cash.turbine.test
import com.tealium.core.api.Barrier
import com.tealium.core.api.BarrierState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test


class BarrierTests {

    private lateinit var allState1: MutableStateFlow<BarrierState>
    private lateinit var allState2: MutableStateFlow<BarrierState>
    private lateinit var allState3: MutableStateFlow<BarrierState>
    private lateinit var dispatcher1State: MutableStateFlow<BarrierState>
    private lateinit var dispatcher2State: MutableStateFlow<BarrierState>
    private lateinit var registeredBarriers: Set<Barrier>
    private lateinit var scopedBarriers: Set<ScopedBarrier>
    private lateinit var barrierCoordinator: BarrierCoordinatorImpl

    @Before
    fun setUp() {
        // Default: all barriers Open
        allState1 = MutableStateFlow(BarrierState.Open)
        allState2 = MutableStateFlow(BarrierState.Open)
        allState3 = MutableStateFlow(BarrierState.Open)
        dispatcher1State = MutableStateFlow(BarrierState.Open)
        dispatcher2State = MutableStateFlow(BarrierState.Open)
        registeredBarriers = setOf(
            barrier("all_1", allState1.asStateFlow()),
            barrier("all_2", allState2.asStateFlow()),
            barrier("all_3", allState3.asStateFlow()),
            barrier("dispatcher_1", dispatcher1State.asStateFlow()),
            barrier("dispatcher_2", dispatcher2State.asStateFlow()),
        )
        scopedBarriers = setOf(
            ScopedBarrier("all_1", setOf(BarrierScope.All)),
            ScopedBarrier("all_2", setOf(BarrierScope.All)),
            ScopedBarrier("all_3", setOf(BarrierScope.All)),
            ScopedBarrier("dispatcher_1", setOf(BarrierScope.Dispatcher("dispatcher_1"))),
            ScopedBarrier("dispatcher_2", setOf(BarrierScope.Dispatcher("dispatcher_2")))
        )

        barrierCoordinator = BarrierCoordinatorImpl(
            registeredBarriers, scopedBarriers
        )
    }

    @Test
    fun getAllBarriers_ReturnsAllBarriers_ScopedToAll() {
        val barriers = barrierCoordinator.getAllBarriers(scopedBarriers, "dispatcher_1")

        assertNotNull(barriers.find { it.id == "all_1" })
        assertNotNull(barriers.find { it.id == "all_2" })
        assertNotNull(barriers.find { it.id == "all_3" })
    }

    @Test
    fun getAllBarriers_ReturnsOnlyBarriersForGivenDispatcher() {
        val barriers = barrierCoordinator.getAllBarriers(scopedBarriers, "dispatcher_1")

        assertNotNull(barriers.find { it.id == "dispatcher_1" })
        assertNull(barriers.find { it.id == "dispatcher_2" })
    }

    @Test
    fun getBarriers_AllScope_Returns_OnlyBarriersWithAllScope() {
        val barriers = barrierCoordinator.getBarriers(scopedBarriers, BarrierScope.All)

        assertNotNull(barriers.find { it.id == "all_1" })
        assertNotNull(barriers.find { it.id == "all_2" })
        assertNotNull(barriers.find { it.id == "all_3" })
        assertEquals(3, barriers.size)
    }

    @Test
    fun getBarriers_DispatcherScope_Returns_OnlyBarriersWithGivenDispatcherScope() {
        val barriers =
            barrierCoordinator.getBarriers(scopedBarriers, BarrierScope.Dispatcher("dispatcher_1"))

        assertNotNull(barriers.find { it.id == "dispatcher_1" })
        assertNull(barriers.find { it.id == "dispatcher_2" })
    }

    @Test
    fun getBarriers_PrefersFirstBarrierWithGivenId() {
        val allState1 = barrier("all_1", allState1)
        val allState1Duplicate = barrier("all_1", allState2)
        val barrierScopes = setOf(
            ScopedBarrier("all_1", setOf(BarrierScope.All))
        )
        val barrierCoordinator = BarrierCoordinatorImpl(
            setOf(
                allState1, allState1Duplicate
            ), setOf()
        )

        val barriers = barrierCoordinator.getBarriers(barrierScopes, BarrierScope.All)

        assertEquals(allState1, barriers.first())
        assertEquals(1, barriers.size)
    }

    @Test
    fun onBarrierState_IsOpen_WhenAllBarriersAreOpen() = runTest {
        barrierCoordinator.onBarriersState("dispatcher_1").test {

            val isOpen = awaitItem()
            assertEquals(BarrierState.Open, isOpen)
        }
    }

    @Test
    fun onBarrierState_IsOpen_WhenOtherDispatchersBarriersAreClosed() = runTest {
        dispatcher2State.value = BarrierState.Closed

        barrierCoordinator.onBarriersState("dispatcher_1").test {
            val isOpen = awaitItem()
            assertEquals(BarrierState.Open, isOpen)
        }
    }

    @Test
    fun onBarrierState_IsClosed_WhenAnyAllScopeBarrierIsClosed() = runTest {
        allState1.value = BarrierState.Closed

        barrierCoordinator.onBarriersState("dispatcher_1").test {
            val isOpen = awaitItem()
            assertEquals(BarrierState.Closed, isOpen)
        }
    }

    @Test
    fun onBarrierState_IsClosed_WhenDispatcherScopeBarrierIsClosed() = runTest {
        dispatcher1State.value = BarrierState.Closed

        barrierCoordinator.onBarriersState("dispatcher_1").test {
            val isOpen = awaitItem()
            assertEquals(BarrierState.Closed, isOpen)
        }
    }

    @Test
    fun onBarrierState_EmitsClosed_WhenAllScopeBarrier_BecomesClosed() = runTest {
        barrierCoordinator.onBarriersState("dispatcher_1").test {
            val isOpen = awaitItem()
            assertEquals(BarrierState.Open, isOpen)

            allState1.value = BarrierState.Closed

            val isClosed = awaitItem()
            assertEquals(BarrierState.Closed, isClosed)
        }
    }

    @Test
    fun onBarrierState_EmitsClosed_WhenDispatcherScopeBarrier_BecomesClosed() = runTest {
        barrierCoordinator.onBarriersState("dispatcher_1").test {
            val isOpen = awaitItem()
            assertEquals(BarrierState.Open, isOpen)

            dispatcher1State.value = BarrierState.Closed

            val isClosed = awaitItem()
            assertEquals(BarrierState.Closed, isClosed)
        }
    }

    @Test
    fun onBarrierState_EmitsOpen_WhenAllScopeBarrier_BecomesOpen() = runTest {
        allState1.value = BarrierState.Closed

        barrierCoordinator.onBarriersState("dispatcher_1").test {
            val isClosed = awaitItem()
            assertEquals(BarrierState.Closed, isClosed)

            allState1.value = BarrierState.Open

            val isOpen = awaitItem()
            assertEquals(BarrierState.Open, isOpen)
        }
    }

    @Test
    fun onBarrierState_EmitsOpen_WhenDispatcherScopeBarrier_BecomesOpen() = runTest {
        dispatcher1State.value = BarrierState.Closed

        barrierCoordinator.onBarriersState("dispatcher_1").test {
            val isClosed = awaitItem()
            assertEquals(BarrierState.Closed, isClosed)

            dispatcher1State.value = BarrierState.Open

            val isOpen = awaitItem()
            assertEquals(BarrierState.Open, isOpen)
        }
    }

    @Test
    fun onBarrierState_EmitsNothing_WhenScopedBarriersChange_ButResultHasNot() = runTest {
        barrierCoordinator.onBarriersState("dispatcher_1").test {
            dispatcher1State.value = BarrierState.Open

            val isOpen = awaitItem()
            assertEquals(BarrierState.Open, isOpen)

            barrierCoordinator.scopedBarriers.emit(scopedBarriers.filter {
                it.scope.contains(
                    BarrierScope.All
                )
            }.toSet())

            expectNoEvents()
        }
    }

    @Test
    fun onBarrierState_EmitsUpdate_WhenScopedBarriersChange_AndResultHasToo() = runTest {
        allState1.value = BarrierState.Closed
        barrierCoordinator.scopedBarriers.emit(scopedBarriers.remove("all_1"))

        barrierCoordinator.onBarriersState("dispatcher_1").test {
            val isOpen = awaitItem()
            assertEquals(BarrierState.Open, isOpen)

            barrierCoordinator.scopedBarriers.emit(scopedBarriers)

            val isClosed = awaitItem()
            assertEquals(BarrierState.Closed, isClosed)
        }
    }

    @Test
    fun onBarrierState_DoesNotEmitUpdate_WhenOldBarriersUpdate() = runTest {
        barrierCoordinator.onBarriersState("dispatcher_1").test {
            val isOpen = awaitItem()
            assertEquals(BarrierState.Open, isOpen)

            barrierCoordinator.scopedBarriers.emit(scopedBarriers.remove("all_1"))
            allState1.emit(BarrierState.Closed)

            expectNoEvents()
        }
    }

    @Test
    fun onBarrierState_EmitsOpen_WhenBarriersAreEmpty() = runTest {
        val barrierCoordinator = BarrierCoordinatorImpl(setOf(), scopedBarriers)

        barrierCoordinator.onBarriersState("dispatcher_1").test {
            val isOpen = awaitItem()
            assertEquals(BarrierState.Open, isOpen)
        }
    }

    @Test
    fun onBarrierState_EmitsOpen_WhenScopedBarriersAreEmpty() = runTest {
        val barrierCoordinator = BarrierCoordinatorImpl(registeredBarriers, setOf())

        barrierCoordinator.onBarriersState("dispatcher_1").test {
            val isOpen = awaitItem()
            assertEquals(BarrierState.Open, isOpen)
        }
    }

    @Test
    fun onBarrierState_EmitsClosed_WhenScopedBarriersWereEmpty_ButClosedIsAdded() = runTest {
        allState1.emit(BarrierState.Closed)
        val barrierCoordinator = BarrierCoordinatorImpl(registeredBarriers, setOf())

        barrierCoordinator.onBarriersState("dispatcher_1").test {
            val isOpen = awaitItem()
            assertEquals(BarrierState.Open, isOpen)

            barrierCoordinator.scopedBarriers.emit(
                setOf(
                    ScopedBarrier(
                        "all_1",
                        setOf(BarrierScope.All)
                    )
                )
            )

            val isClosed = awaitItem()
            assertEquals(BarrierState.Closed, isClosed)
        }
    }

    private fun Set<ScopedBarrier>.remove(barrierId: String): Set<ScopedBarrier> =
        filterNot { it.barrierId == barrierId }.toSet()


    private fun barrier(id: String, flow: Flow<BarrierState>): Barrier {
        return object : Barrier {
            override val id: String
                get() = id
            override val onState: Flow<BarrierState>
                get() = flow
        }
    }
}