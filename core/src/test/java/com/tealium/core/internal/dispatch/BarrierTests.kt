package com.tealium.core.internal.dispatch

import com.tealium.core.api.barriers.Barrier
import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.barriers.ScopedBarrier
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Subject
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test


class BarrierTests {

    private lateinit var allState1: Subject<BarrierState>
    private lateinit var allState2: Subject<BarrierState>
    private lateinit var allState3: Subject<BarrierState>
    private lateinit var dispatcher1State: Subject<BarrierState>
    private lateinit var dispatcher2State: Subject<BarrierState>
    private lateinit var registeredBarriers: Set<Barrier>
    private lateinit var scopedBarriers: Set<ScopedBarrier>
    private lateinit var scopedBarriersSubject: Subject<Set<ScopedBarrier>>
    private lateinit var barrierCoordinator: BarrierCoordinatorImpl

    @Before
    fun setUp() {
        // Default: all barriers Open
        allState1 = Observables.stateSubject(BarrierState.Open)
        allState2 = Observables.stateSubject(BarrierState.Open)
        allState3 = Observables.stateSubject(BarrierState.Open)
        dispatcher1State = Observables.stateSubject(BarrierState.Open)
        dispatcher2State = Observables.stateSubject(BarrierState.Open)
        registeredBarriers = setOf(
            barrier("all_1", allState1),
            barrier("all_2", allState2),
            barrier("all_3", allState3),
            barrier("dispatcher_1", dispatcher1State),
            barrier("dispatcher_2", dispatcher2State),
        )
        scopedBarriers = setOf(
            ScopedBarrier("all_1", setOf(BarrierScope.All)),
            ScopedBarrier("all_2", setOf(BarrierScope.All)),
            ScopedBarrier("all_3", setOf(BarrierScope.All)),
            ScopedBarrier("dispatcher_1", setOf(BarrierScope.Dispatcher("dispatcher_1"))),
            ScopedBarrier("dispatcher_2", setOf(BarrierScope.Dispatcher("dispatcher_2")))
        )
        scopedBarriersSubject = Observables.stateSubject(scopedBarriers)

        barrierCoordinator = BarrierCoordinatorImpl(
            registeredBarriers, scopedBarriersSubject
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
            ), scopedBarriersSubject
        )

        val barriers = barrierCoordinator.getBarriers(barrierScopes, BarrierScope.All)

        assertEquals(allState1, barriers.first())
        assertEquals(1, barriers.size)
    }

    @Test
    fun onBarrierState_IsOpen_WhenAllBarriersAreOpen() {
        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { isOpen ->
            assertEquals(BarrierState.Open, isOpen)
        }
    }

    @Test
    fun onBarrierState_IsOpen_WhenOtherDispatchersBarriersAreClosed() = runTest {
        dispatcher2State.onNext(BarrierState.Closed)

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { isOpen ->
            assertEquals(BarrierState.Open, isOpen)
        }
    }

    @Test
    fun onBarrierState_IsClosed_WhenAnyAllScopeBarrierIsClosed() = runTest {
        allState1.onNext(BarrierState.Closed)

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { isOpen ->
            assertEquals(BarrierState.Closed, isOpen)
        }
    }

    @Test
    fun onBarrierState_IsClosed_WhenDispatcherScopeBarrierIsClosed() = runTest {
        dispatcher1State.onNext(BarrierState.Closed)

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { isOpen ->
            assertEquals(BarrierState.Closed, isOpen)
        }
    }

    @Test
    fun onBarrierState_EmitsClosed_WhenAllScopeBarrier_BecomesClosed() = runTest {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        barrierCoordinator.onBarriersState("dispatcher_1")
            .subscribe(verifier)

        allState1.onNext(BarrierState.Closed)

        verify {
            verifier(BarrierState.Open)
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun onBarrierState_EmitsClosed_WhenDispatcherScopeBarrier_BecomesClosed() = runTest {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        barrierCoordinator.onBarriersState("dispatcher_1")
            .subscribe(verifier)

        dispatcher1State.onNext(BarrierState.Closed)

        verify {
            verifier(BarrierState.Open)
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun onBarrierState_EmitsOpen_WhenAllScopeBarrier_BecomesOpen() = runTest {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        allState1.onNext(BarrierState.Closed)

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe(verifier)
        allState1.onNext(BarrierState.Open)

        verify {
            verifier(BarrierState.Closed)
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun onBarrierState_EmitsOpen_WhenDispatcherScopeBarrier_BecomesOpen() = runTest {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        dispatcher1State.onNext(BarrierState.Closed)

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe(verifier)
        dispatcher1State.onNext(BarrierState.Open)

        verify {
            verifier(BarrierState.Closed)
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun onBarrierState_EmitsNothing_WhenScopedBarriersChange_ButResultHasNot() = runTest {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        dispatcher1State.onNext(BarrierState.Open)
        barrierCoordinator.onBarriersState("dispatcher_1").subscribe(verifier)

        scopedBarriersSubject.onNext(scopedBarriers.filter {
            it.scope.contains(
                BarrierScope.All
            )
        }.toSet())

        verify(exactly = 1) {
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun onBarrierState_EmitsUpdate_WhenScopedBarriersChange_AndResultHasToo() = runTest {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        allState1.onNext(BarrierState.Closed)
        scopedBarriersSubject.onNext(scopedBarriers.remove("all_1"))

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe(verifier)

        scopedBarriersSubject.onNext(scopedBarriers)

        verify {
            verifier(BarrierState.Closed)
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun onBarrierState_DoesNotEmitUpdate_WhenOldBarriersUpdate() = runTest {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        barrierCoordinator.onBarriersState("dispatcher_1").subscribe(verifier)

        scopedBarriersSubject.onNext(scopedBarriers.remove("all_1"))
        allState1.onNext(BarrierState.Closed)

        verify {
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun onBarrierState_EmitsOpen_WhenBarriersAreEmpty() = runTest {
        val barrierCoordinator = BarrierCoordinatorImpl(setOf(), scopedBarriersSubject)

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { isOpen ->
            assertEquals(BarrierState.Open, isOpen)
        }
    }

    @Test
    fun onBarrierState_EmitsOpen_WhenScopedBarriersAreEmpty() = runTest {
        val barrierCoordinator = BarrierCoordinatorImpl(registeredBarriers, Observables.stateSubject(
            setOf()))

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { isOpen ->
            assertEquals(BarrierState.Open, isOpen)
        }
    }

    @Test
    fun onBarrierState_EmitsClosed_WhenScopedBarriersWereEmpty_ButClosedIsAdded() = runTest {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        allState1.onNext(BarrierState.Closed)
        scopedBarriersSubject = Observables.stateSubject(setOf())
        val barrierCoordinator = BarrierCoordinatorImpl(registeredBarriers, scopedBarriersSubject)

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe(verifier)
        scopedBarriersSubject.onNext(
            setOf(
                ScopedBarrier(
                    "all_1",
                    setOf(BarrierScope.All)
                )
            )
        )

        verify {
            verifier(BarrierState.Open)
            verifier(BarrierState.Closed)
        }
    }

    private fun Set<ScopedBarrier>.remove(barrierId: String): Set<ScopedBarrier> =
        filterNot { it.barrierId == barrierId }.toSet()


    private fun barrier(id: String, flow: Observable<BarrierState>): Barrier {
        return object : Barrier {
            override val id: String
                get() = id
            override val onState: Observable<BarrierState>
                get() = flow
        }
    }
}