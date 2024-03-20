package com.tealium.core.internal.dispatch

import com.tealium.core.api.BarrierState
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import com.tealium.core.api.Scheduler
import com.tealium.core.api.TealiumDispatchType
import com.tealium.core.api.Transformer
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.logger.Logger
import com.tealium.core.internal.consent.ConsentManager
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.StateSubject
import com.tealium.core.internal.observables.Subject
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.spyk
import org.junit.After
import org.junit.Before
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Test Base class to simplify test groups relating to the Dispatch Manager.
 *
 */
open class DispatchManagerTestsBase {


    @MockK
    protected lateinit var consentManager: ConsentManager

    @MockK
    protected lateinit var barrierCoordinator: BarrierCoordinator

    protected lateinit var logger: Logger
    protected lateinit var scheduler: Scheduler
    protected lateinit var dispatcher1: Dispatcher
    protected lateinit var dispatcher2: Dispatcher
    protected lateinit var dispatchers: StateSubject<Set<Dispatcher>>
    protected lateinit var barrierFlow: Subject<BarrierState>
    protected lateinit var queueManager: QueueManager
    protected lateinit var queue: MutableMap<String, MutableSet<Dispatch>>
    protected lateinit var inFlightEvents: MutableMap<String, MutableSet<String>>
    protected lateinit var onInFlightEvents: Subject<Map<String, Set<String>>>
    protected lateinit var transformerCoordinator: TransformerCoordinator
    protected lateinit var transformers: MutableSet<Transformer>
    protected lateinit var transformersFlow: StateSubject<Set<ScopedTransformation>>
    protected lateinit var dispatchManager: DispatchManagerImpl

    protected val dispatch1: Dispatch =
        Dispatch.create("test1", TealiumDispatchType.Event, TealiumBundle.EMPTY_BUNDLE)
    protected val dispatch2: Dispatch =
        Dispatch.create("test2", TealiumDispatchType.Event, TealiumBundle.EMPTY_BUNDLE)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        logger = SystemLogger()
        scheduler = testTealiumScheduler

        // Consent Defaulted to disabled
        every { consentManager.enabled } returns false
        every { consentManager.applyConsent(any()) } just Runs

        // Two available test dispatchers
        dispatcher1 = spyk(TestDispatcher("dispatcher_1"))
        dispatcher2 = spyk(TestDispatcher("dispatcher_2"))
        dispatchers = Observables.stateSubject(setOf(dispatcher1, dispatcher2))

        // Queue defaulted to empty
        // concurrent due to test async nature.
        queue = ConcurrentHashMap()
        inFlightEvents = ConcurrentHashMap()
        onInFlightEvents = Observables.replaySubject(cacheSize = 1)
        queueManager = spyk(
            VolatileQueueManagerImpl(
                queue = queue,
                _inFlightEvents = inFlightEvents,
                _onInFlightEvents = onInFlightEvents
            )
        )

        // Barriers defaulted to Open
        barrierFlow = Observables.stateSubject(BarrierState.Open)
        every { barrierCoordinator.onBarriersState(any()) } returns barrierFlow

        transformersFlow = Observables.stateSubject(setOf())
        transformers = mutableSetOf()
        transformerCoordinator = spyk(
            TransformerCoordinatorImpl(
                transformers, transformersFlow, testTealiumScheduler
            )
        )

        dispatchManager = createDispatchManager()

        onAfterSetup()
    }


    /**
     * JUnit won't guarantee the execution order of multiple @Before annotated methods.
     * So inheriting classes cannot make their own and have them reliably execute after the default
     * from this base class.
     */
    protected open fun onAfterSetup() {
    }

    protected fun createDispatchManager(
        consentManager: ConsentManager = this.consentManager,
        barrierCoordinator: BarrierCoordinator = this.barrierCoordinator,
        transformerCoordinator: TransformerCoordinator = this.transformerCoordinator,
        queueManager: QueueManager = this.queueManager,
        dispatchers: StateSubject<Set<Dispatcher>> = this.dispatchers,
        logger: Logger = this.logger,
        maxInFlight: Int = DispatchManagerImpl.MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER
    ): DispatchManagerImpl = DispatchManagerImpl(
        consentManager,
        barrierCoordinator,
        transformerCoordinator,
        queueManager,
        dispatchers,
        logger,
        maxInFlight
    )

}
