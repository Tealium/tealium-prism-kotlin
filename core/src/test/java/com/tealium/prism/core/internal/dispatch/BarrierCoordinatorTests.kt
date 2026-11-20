package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.barriers.ConfigurableBarrier
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.misc.QueueMetrics
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.ReplaySubject
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.barriers.BarrierCoordinatorImpl
import com.tealium.prism.core.internal.barriers.ScopedBarrier
import com.tealium.tests.common.VirtualTimeScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner


@RunWith(RobolectricTestRunner::class)
class BarrierCoordinatorTests {

    private lateinit var appStatus: ReplaySubject<ActivityManager.ApplicationStatus>
    private lateinit var allState1: Subject<BarrierState>
    private lateinit var allState2: Subject<BarrierState>
    private lateinit var allState3: Subject<BarrierState>
    private lateinit var dispatcher1State: Subject<BarrierState>
    private lateinit var dispatcher2State: Subject<BarrierState>
    private lateinit var barriers: List<Pair<ConfigurableBarrier, BarrierScope>>
    private lateinit var barrierSubject: StateSubject<List<ScopedBarrier>>
    private lateinit var barrierCoordinator: BarrierCoordinatorImpl
    private lateinit var queueSize: Subject<Int>
    private lateinit var queueMetrics: QueueMetrics
    private lateinit var scheduler: VirtualTimeScheduler

    @Before
    fun setUp() {
        // have at least one event for flushing
        queueSize = Observables.stateSubject(1)
        queueMetrics = mockk()
        every { queueMetrics.queueSizePendingDispatch(any()) } returns queueSize
        appStatus = Observables.replaySubject(1)

        // Default: all barriers Open
        allState1 = Observables.stateSubject(BarrierState.Open)
        allState2 = Observables.stateSubject(BarrierState.Open)
        allState3 = Observables.stateSubject(BarrierState.Open)
        dispatcher1State = Observables.stateSubject(BarrierState.Open)
        dispatcher2State = Observables.stateSubject(BarrierState.Open)
        barriers = listOf(
            barrier("all_1", allState1) to BarrierScope.All,
            barrier("all_2", allState2) to BarrierScope.All,
            barrier("all_3", allState2) to BarrierScope.All,
            barrier(
                "dispatcher_1",
                dispatcher1State
            ) to BarrierScope.Dispatchers("dispatcher_1"),
            barrier(
                "dispatcher_2",
                dispatcher2State
            ) to BarrierScope.Dispatchers("dispatcher_2")
        )
        barrierSubject = Observables.stateSubject(barriers)

        scheduler = VirtualTimeScheduler()
        barrierCoordinator = BarrierCoordinatorImpl(
            barrierSubject,
            appStatus,
            queueMetrics,
            flushDebounceMilliseconds = 0,
            scheduler = scheduler
        )
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
        barrierSubject.onNext(barrierSubject.value.map { (barrier, _) -> barrier to BarrierScope.Dispatchers(emptyList()) })

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
            barrier("unscoped", Observables.just(BarrierState.Closed)) to BarrierScope.Dispatchers(emptyList())

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe(verifier)
        barrierSubject.onNext(barrierSubject.value + unscopedBarrier)

        verify {
            verifier(BarrierState.Open)
        }
        verify(inverse = true) {
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun flush_Ignores_Barriers_That_Are_Flushable() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        val blockingBarrier =
            barrier("blocking", Observables.just(BarrierState.Closed), flushable = true)
        barrierSubject.onNext(listOf(ScopedBarrier(blockingBarrier, BarrierScope.All)))

        barrierCoordinator.onBarriersState("dispatcher_1")
            .subscribe(verifier)

        verify(inverse = true) {
            verifier(BarrierState.Open)
        }

        barrierCoordinator.flush()
        verify {
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun flush_Does_Not_Ignore_Barriers_That_Are_Not_Flushable() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        val blockingBarrier =
            barrier("blocking", Observables.just(BarrierState.Closed), flushable = false)
        barrierSubject.onNext(listOf(ScopedBarrier(blockingBarrier, BarrierScope.All)))

        barrierCoordinator.onBarriersState("dispatcher_1")
            .subscribe(verifier)
        barrierCoordinator.flush()

        verify(inverse = true) {
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun flush_Only_Ignores_Barriers_That_Are_Flushable() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        val flushable =
            barrier("flushable", Observables.just(BarrierState.Closed), flushable = true)
        val notFlushable =
            barrier("nonFlushable", Observables.just(BarrierState.Closed), flushable = false)
        barrierSubject.onNext(
            listOf(
                ScopedBarrier(flushable, BarrierScope.All),
                ScopedBarrier(notFlushable, BarrierScope.All)
            )
        )

        barrierCoordinator.onBarriersState("dispatcher_1")
            .subscribe(verifier)

        barrierCoordinator.flush()
        verify(inverse = true) {
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun flush_Closes_When_Queue_Size_Reaches_Zero() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        val blockingBarrier =
            barrier("blocking", Observables.just(BarrierState.Closed), flushable = true)
        barrierSubject.onNext(listOf(ScopedBarrier(blockingBarrier, BarrierScope.All)))

        barrierCoordinator.onBarriersState("dispatcher_1")
            .subscribe(verifier)
        barrierCoordinator.flush()

        verify(exactly = 1) {
            verifier(BarrierState.Closed)
            verifier(BarrierState.Open)
        }

        queueSize.onNext(0)
        verify(exactly = 2) {
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun flush_Opens_If_Barrier_IsFlushable_Becomes_True() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        val flushable = Observables.stateSubject(false)
        val blockingBarrier =
            barrier("blocking", Observables.just(BarrierState.Closed), flushable)
        barrierSubject.onNext(listOf(ScopedBarrier(blockingBarrier, BarrierScope.All)))

        barrierCoordinator.onBarriersState("dispatcher_1")
            .subscribe(verifier)
        barrierCoordinator.flush()

        verify(inverse = true) {
            verifier(BarrierState.Open)
        }

        flushable.onNext(true)
        verify(exactly = 1) {
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun flush_Closes_If_Barrier_IsFlushable_Becomes_False() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        val flushable = Observables.stateSubject(true)
        val blockingBarrier =
            barrier("blocking", Observables.just(BarrierState.Closed), flushable)
        barrierSubject.onNext(listOf(ScopedBarrier(blockingBarrier, BarrierScope.All)))

        barrierCoordinator.onBarriersState("dispatcher_1")
            .subscribe(verifier)
        barrierCoordinator.flush()

        verifyOrder {
            verifier(BarrierState.Closed)
            verifier(BarrierState.Open)
        }

        flushable.onNext(false)
        verify(exactly = 2) {
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun flush_Remains_Open_If_Barrier_Open_And_Is_Not_Flushable() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        val flushable = Observables.stateSubject(false)
        val blockingBarrier =
            barrier("blocking", Observables.just(BarrierState.Open), flushable)
        barrierSubject.onNext(listOf(ScopedBarrier(blockingBarrier, BarrierScope.All)))

        barrierCoordinator.onBarriersState("dispatcher_1")
            .subscribe(verifier)
        barrierCoordinator.flush()

        verify(inverse = true) {
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun flush_Resumes_When_Paused_By_Non_Flushable_Barrier_And_Barrier_Reopens() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        val flushable =
            barrier("flushable", Observables.just(BarrierState.Closed), flushable = true)
        val nonFlushableState = Observables.stateSubject(BarrierState.Open)
        val notFlushable =
            barrier("nonFlushable", nonFlushableState, flushable = false)
        barrierSubject.onNext(
            listOf(
                ScopedBarrier(flushable, BarrierScope.All),
                ScopedBarrier(notFlushable, BarrierScope.All)
            )
        )

        barrierCoordinator.onBarriersState("dispatcher_1")
            .subscribe(verifier)
        barrierCoordinator.flush()

        verify(exactly = 1) {
            verifier(BarrierState.Closed)
            verifier(BarrierState.Open)
        }

        nonFlushableState.onNext(BarrierState.Closed)
        verify(exactly = 2) {
            verifier(BarrierState.Closed)
        }

        nonFlushableState.onNext(BarrierState.Open)
        verify(exactly = 2) {
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun flush_Pauses_When_Non_Flushable_Barrier_Closes() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        val barrierState = Observables.stateSubject(BarrierState.Open)
        val nonFlushable =
            barrier("blocking", barrierState, flushable = false)
        barrierSubject.onNext(listOf(ScopedBarrier(nonFlushable, BarrierScope.All)))

        barrierCoordinator.onBarriersState("dispatcher_1")
            .subscribe(verifier)
        barrierCoordinator.flush()

        verify(exactly = 1) {
            verifier(BarrierState.Open)
        }
        verify(inverse = true) {
            verifier(BarrierState.Closed)
        }

        barrierState.onNext(BarrierState.Closed)
        verify(exactly = 1) {
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun appStatus_Changes_Trigger_Flush() {
        val blockingBarrier =
            barrier("blocking", Observables.just(BarrierState.Closed), flushable = true)
        barrierSubject.onNext(listOf(ScopedBarrier(blockingBarrier, BarrierScope.All)))
        val states = mutableListOf<BarrierState>()

        barrierCoordinator.onBarriersState("dispatcher_1")
            .subscribe { states.add(it) }
        assertEquals(listOf(BarrierState.Closed), states)

        listOf(
            ActivityManager.ApplicationStatus.Init(),
            ActivityManager.ApplicationStatus.Backgrounded(),
            ActivityManager.ApplicationStatus.Foregrounded()
        ).forEach { status ->
            states.clear()

            appStatus.onNext(status)
            assertEquals(listOf(BarrierState.Open), states)

            queueSize.onNext(0) // the flush ends
            assertEquals(listOf(BarrierState.Open, BarrierState.Closed), states)
            queueSize.onNext(1)
        }
    }

    // region Shared flush state

    @Test
    fun reemitting_The_Barriers_After_The_Flush_Ended_Does_Not_Start_A_New_Flush() {
        val states = mutableListOf<BarrierState>()
        subscribeToClosedFlushableBarrierAfterLaunchFlush { states.add(it) }
        queueSize.onNext(1)

        reemitBarriers()

        assertEquals("Re-emitting the barriers must not flush the pending dispatches", listOf(BarrierState.Closed), states)
    }

    @Test
    fun a_Second_Barriers_State_Subscription_After_The_Flush_Ended_Does_Not_Start_A_Flush() {
        subscribeToClosedFlushableBarrierAfterLaunchFlush { }
        queueSize.onNext(1)

        val states = mutableListOf<BarrierState>()
        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { states.add(it) }

        assertEquals("A new subscriber must not flush the pending dispatches", listOf(BarrierState.Closed), states)
    }

    @Test
    fun the_Flush_State_Is_Per_Dispatcher() {
        val blockingBarrier =
            barrier("blocking", Observables.just(BarrierState.Closed), flushable = true)
        barrierSubject.onNext(listOf(ScopedBarrier(blockingBarrier, BarrierScope.All)))
        val queueSize1 = Observables.stateSubject(0)
        val queueSize2 = Observables.stateSubject(0)
        every { queueMetrics.queueSizePendingDispatch("dispatcher_1") } returns queueSize1
        every { queueMetrics.queueSizePendingDispatch("dispatcher_2") } returns queueSize2
        appStatus.onNext(ActivityManager.ApplicationStatus.Init())
        val states1 = mutableListOf<BarrierState>()
        val states2 = mutableListOf<BarrierState>()
        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { states1.add(it) }
        barrierCoordinator.onBarriersState("dispatcher_2").subscribe { states2.add(it) }

        queueSize1.onNext(1)
        appStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())

        assertEquals(
            "The dispatcher with pending dispatches flushes",
            listOf(BarrierState.Closed, BarrierState.Open),
            states1
        )
        assertEquals(
            "The other dispatcher's flushable barrier stays closed",
            listOf(BarrierState.Closed),
            states2
        )
    }

    @Test
    fun resubscribing_During_A_Flush_Keeps_The_Flush_State_Without_Closing_The_Barriers() {
        val blockingBarrier =
            barrier("blocking", Observables.just(BarrierState.Closed), flushable = true)
        barrierSubject.onNext(listOf(ScopedBarrier(blockingBarrier, BarrierScope.All)))
        appStatus.onNext(ActivityManager.ApplicationStatus.Init())
        val firstStates = mutableListOf<BarrierState>()
        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { firstStates.add(it) }
        assertEquals("The launch flush is in progress", listOf(BarrierState.Open), firstStates)

        val secondStates = mutableListOf<BarrierState>()
        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { secondStates.add(it) }
        reemitBarriers()

        assertEquals(
            "A new subscriber receives the flushing state, without closing the barriers first",
            listOf(BarrierState.Open),
            secondStates
        )
        assertEquals("Re-emitting the barriers must not close them again", listOf(BarrierState.Open), firstStates)
    }

    @Test
    fun the_Initialized_Status_Flushes_On_Launch_Without_Publishing_A_New_Status() {
        val blockingBarrier =
            barrier("blocking", Observables.just(BarrierState.Closed), flushable = true)
        barrierSubject.onNext(listOf(ScopedBarrier(blockingBarrier, BarrierScope.All)))
        appStatus.onNext(ActivityManager.ApplicationStatus.Init())
        val states = mutableListOf<BarrierState>()

        barrierCoordinator.onBarriersState("dispatcher_1").subscribe { states.add(it) }

        // With a 0 ms debounce the flush detection runs synchronously, so the shared state is already true when the first subscriber attaches and Closed is never emitted. With a real debounce the first subscriber sees Closed, then Open.
        assertEquals(listOf(BarrierState.Open), states)
    }

    @Test
    fun a_New_Status_After_Resubscribing_Still_Causes_A_Flush() {
        val states = mutableListOf<BarrierState>()
        subscribeToClosedFlushableBarrierAfterLaunchFlush { states.add(it) }
        queueSize.onNext(1)
        reemitBarriers()
        assertEquals("No flush without a new status", listOf(BarrierState.Closed), states)

        appStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())

        assertEquals(listOf(BarrierState.Closed, BarrierState.Open), states)
    }

    @Test
    fun a_Manual_Flush_After_Resubscribing_Still_Causes_A_Flush() {
        val states = mutableListOf<BarrierState>()
        subscribeToClosedFlushableBarrierAfterLaunchFlush { states.add(it) }
        queueSize.onNext(1)
        reemitBarriers()
        assertEquals("No flush without a new status or manual flush", listOf(BarrierState.Closed), states)

        barrierCoordinator.flush()

        assertEquals(listOf(BarrierState.Closed, BarrierState.Open), states)
    }

    // endregion

    // region Timeout and debounce

    @Test
    fun flush_Ends_After_Timeout_When_Queue_Size_Never_Reaches_Zero() {
        val states = mutableListOf<BarrierState>()
        subscribeWithTimes(debounceMilliseconds = 20, timeoutMilliseconds = 100) { states.add(it) }

        scheduler.advanceTimeBy(20)
        assertEquals(listOf(BarrierState.Closed, BarrierState.Open), states)

        scheduler.advanceTimeBy(79)
        assertEquals(
            "The flush must last until the timeout",
            listOf(BarrierState.Closed, BarrierState.Open),
            states
        )

        scheduler.advanceTimeBy(1)
        assertEquals(listOf(BarrierState.Closed, BarrierState.Open, BarrierState.Closed), states)
    }

    @Test
    fun flush_Does_Not_End_When_Queue_Size_Changes_Within_Debounce() {
        val states = mutableListOf<BarrierState>()
        subscribeWithTimes(debounceMilliseconds = 20) { states.add(it) }
        scheduler.advanceTimeBy(20)

        // The queue empties and fills up again synchronously, well within the debounce time.
        queueSize.onNext(0)
        queueSize.onNext(1)
        scheduler.advanceTimeBy(100)

        assertEquals(listOf(BarrierState.Closed, BarrierState.Open), states)
    }

    @Test
    fun flush_Ends_When_Queue_Size_Stays_Zero_For_The_Debounce_Time() {
        val states = mutableListOf<BarrierState>()
        subscribeWithTimes(debounceMilliseconds = 20) { states.add(it) }
        scheduler.advanceTimeBy(20)

        queueSize.onNext(0)
        scheduler.advanceTimeBy(19)
        assertEquals(
            "The flush must wait for the queue to stay empty for the debounce time",
            listOf(BarrierState.Closed, BarrierState.Open),
            states
        )

        scheduler.advanceTimeBy(1)
        assertEquals(listOf(BarrierState.Closed, BarrierState.Open, BarrierState.Closed), states)
    }

    @Test
    fun flush_Starts_When_Queue_Size_Keeps_Changing_Within_Debounce() {
        val states = mutableListOf<BarrierState>()
        subscribeWithTimes(debounceMilliseconds = 20) { states.add(it) }

        repeat(8) { i ->
            scheduler.advanceTimeBy(10)
            queueSize.onNext(2 + i)
            if (i == 1) {
                assertEquals(
                    "Positive changes must not postpone the start of the flush",
                    listOf(BarrierState.Closed, BarrierState.Open),
                    states
                )
            }
        }

        assertEquals(listOf(BarrierState.Closed, BarrierState.Open), states)
    }

    // endregion

    // region Shutdown

    @Test
    fun shutdown_Stops_Flushing_On_New_Status() {
        val states = mutableListOf<BarrierState>()
        subscribeToClosedFlushableBarrierAfterLaunchFlush { states.add(it) }
        queueSize.onNext(1)

        barrierCoordinator.shutdown()
        appStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())

        assertEquals("No flush after shutdown", listOf(BarrierState.Closed), states)
    }

    // endregion

    /**
     * Subscribes to a closed flushable barrier, after a launch flush that ended immediately because the queue was empty.
     */
    private fun subscribeToClosedFlushableBarrierAfterLaunchFlush(onState: (BarrierState) -> Unit) {
        val blockingBarrier =
            barrier("blocking", Observables.just(BarrierState.Closed), flushable = true)
        barrierSubject.onNext(listOf(ScopedBarrier(blockingBarrier, BarrierScope.All)))
        queueSize.onNext(0)
        appStatus.onNext(ActivityManager.ApplicationStatus.Init())
        barrierCoordinator.onBarriersState("dispatcher_1").subscribe(onState)
    }

    /**
     * Emits the same barriers again, as a settings refresh does even when nothing changed.
     */
    private fun reemitBarriers() {
        barrierSubject.onNext(barrierSubject.value)
    }

    /**
     * Builds a coordinator with the given times, and subscribes to a closed flushable barrier while the queue is not empty, so the launch status starts a flush.
     */
    private fun subscribeWithTimes(
        debounceMilliseconds: Int,
        timeoutMilliseconds: Int = 5000,
        onState: (BarrierState) -> Unit
    ) {
        val coordinator = BarrierCoordinatorImpl(
            barrierSubject,
            appStatus,
            queueMetrics,
            flushDebounceMilliseconds = debounceMilliseconds,
            flushTimeoutMilliseconds = timeoutMilliseconds,
            scheduler = scheduler
        )
        val blockingBarrier =
            barrier("blocking", Observables.just(BarrierState.Closed), flushable = true)
        barrierSubject.onNext(listOf(ScopedBarrier(blockingBarrier, BarrierScope.All)))
        queueSize.onNext(1)
        appStatus.onNext(ActivityManager.ApplicationStatus.Init())
        coordinator.onBarriersState("dispatcher_1").subscribe(onState)
    }
}
