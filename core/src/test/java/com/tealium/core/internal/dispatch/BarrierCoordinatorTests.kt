package com.tealium.core.internal.dispatch

import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.barriers.ConfigurableBarrier
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.api.pubsub.Subject
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner


@RunWith(RobolectricTestRunner::class)
class BarrierCoordinatorTests {

    private lateinit var allState1: Subject<BarrierState>
    private lateinit var allState2: Subject<BarrierState>
    private lateinit var allState3: Subject<BarrierState>
    private lateinit var dispatcher1State: Subject<BarrierState>
    private lateinit var dispatcher2State: Subject<BarrierState>
    private lateinit var barriers: List<Pair<ConfigurableBarrier, Set<BarrierScope>>>
    private lateinit var barrierSubject: StateSubject<List<ScopedBarrier>>
    private lateinit var barrierCoordinator: BarrierCoordinatorImpl

    @Before
    fun setUp() {
        // Default: all barriers Open
        allState1 = Observables.stateSubject(BarrierState.Open)
        allState2 = Observables.stateSubject(BarrierState.Open)
        allState3 = Observables.stateSubject(BarrierState.Open)
        dispatcher1State = Observables.stateSubject(BarrierState.Open)
        dispatcher2State = Observables.stateSubject(BarrierState.Open)
        barriers = listOf(
            barrier("all_1", allState1) to setOf(BarrierScope.All),
            barrier("all_2", allState2) to setOf(BarrierScope.All),
            barrier("all_3", allState2) to setOf(BarrierScope.All),
            barrier(
                "dispatcher_1",
                dispatcher1State
            ) to setOf(BarrierScope.Dispatcher("dispatcher_1")),
            barrier(
                "dispatcher_2",
                dispatcher2State
            ) to setOf(BarrierScope.Dispatcher("dispatcher_2"))
        )
        barrierSubject = Observables.stateSubject(barriers)

        barrierCoordinator = BarrierCoordinatorImpl(barrierSubject)
    }

    @Test
    fun onBarrierState_Is_Open_When_No_Barriers() {
        barrierSubject.onNext(emptyList())

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { isOpen ->
            assertEquals(BarrierState.Open, isOpen)
        }
    }


    @Test
    fun onBarrierState_Is_Open_When_All_Barriers_Are_Unscoped() {
        barrierSubject.onNext(barrierSubject.value.map { (barrier, _) -> barrier to emptySet() })

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { isOpen ->
            assertEquals(BarrierState.Open, isOpen)
        }
    }

    @Test
    fun onBarrierState_Is_Open_When_All_Barriers_Are_Open() {
        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { isOpen ->
            assertEquals(BarrierState.Open, isOpen)
        }
    }

    @Test
    fun onBarrierState_Is_Open_When_Other_Dispatchers_Barriers_Are_Closed() {
        dispatcher2State.onNext(BarrierState.Closed)

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { isOpen ->
            assertEquals(BarrierState.Open, isOpen)
        }
    }

    @Test
    fun onBarrierState_Is_Closed_When_Any_All_Scope_Barrier_Is_Closed() {
        allState1.onNext(BarrierState.Closed)

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { isOpen ->
            assertEquals(BarrierState.Closed, isOpen)
        }
    }

    @Test
    fun onBarrierState_Is_Closed_When_Dispatcher_Scope_Barrier_Is_Closed() {
        dispatcher1State.onNext(BarrierState.Closed)

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { isOpen ->
            assertEquals(BarrierState.Closed, isOpen)
        }
    }

    @Test
    fun onBarrierState_Emits_Closed_For_All_Dispatchers_When_All_Scope_Barrier_Becomes_Closed() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        barrierCoordinator.onBarriersState("dispatcher_1")
            .subscribe(verifier)
        barrierCoordinator.onBarriersState("dispatcher_2")
            .subscribe(verifier)

        allState1.onNext(BarrierState.Closed)

        verify(exactly = 2) {
            verifier(BarrierState.Open)
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun onBarrierState_Emits_Closed_When_Dispatcher_Scope_Barrier_Becomes_Closed() {
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
    fun onBarrierState_Emits_Open_When_All_Scope_Barrier_Becomes_Open() {
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
    fun onBarrierState_Emits_Open_When_Dispatcher_Scope_Barrier_Becomes_Open() {
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
    fun onBarrierState_Does_Not_Emit_Update_When_State_Has_Not_Changed() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        barrierCoordinator.onBarriersState("dispatcher_1").subscribe(verifier)

        val newBarriers = barriers.filter { (barrier, _) -> barrier.id != "all_1" }
        barrierSubject.onNext(newBarriers)
        allState1.onNext(BarrierState.Closed)

        verify {
            verifier(BarrierState.Open)
        }
        verify(inverse = true) {
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun onBarrierState_Ignores_Barriers_Not_Scoped_To_Any_Dispatchers() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        val unscopedBarrier: ScopedBarrier =
            barrier("unscoped", Observables.just(BarrierState.Closed)) to emptySet()

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe(verifier)
        barrierSubject.onNext(barrierSubject.value + unscopedBarrier)

        verify {
            verifier(BarrierState.Open)
        }
        verify(inverse = true) {
            verifier(BarrierState.Closed)
        }
    }
}