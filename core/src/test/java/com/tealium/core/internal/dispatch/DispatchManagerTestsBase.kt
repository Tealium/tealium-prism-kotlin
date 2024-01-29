package com.tealium.core.internal.dispatch

import com.tealium.core.api.BarrierState
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import com.tealium.core.api.TealiumDispatchType
import com.tealium.core.api.Transformer
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.logger.Logger
import com.tealium.core.internal.consent.ConsentManager
import com.tealium.tests.common.TestDispatcher
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Before
import java.util.concurrent.Executors

/**
 * Test Base class to simplify test groups relating to the Dispatch Manager.
 *
 */
open class DispatchManagerTestsBase {

    @RelaxedMockK
    protected lateinit var logger: Logger

    @MockK
    protected lateinit var consentManager: ConsentManager

    @MockK
    protected lateinit var barrierCoordinator: BarrierCoordinator

    protected val coroutineDispatcher =
        Executors.newSingleThreadScheduledExecutor().asCoroutineDispatcher()
    protected lateinit var tealiumScope: CoroutineScope

    protected lateinit var dispatcher1: Dispatcher
    protected lateinit var dispatcher2: Dispatcher
    protected lateinit var dispatchers: MutableStateFlow<Set<Dispatcher>>
    protected lateinit var barrierFlow: MutableStateFlow<BarrierState>
    protected lateinit var queueManager: QueueManager
    protected lateinit var queue: MutableMap<String, MutableSet<Dispatch>>
    protected lateinit var inFlightEvents: MutableMap<String, MutableSet<String>>
    protected lateinit var onInFlightEvents: MutableSharedFlow<Map<String, Set<String>>>
    protected lateinit var transformerCoordinator: TransformerCoordinator
    protected lateinit var transformers: MutableSet<Transformer>
    protected lateinit var transformersFlow: MutableStateFlow<Set<ScopedTransformation>>
    protected lateinit var dispatchManager: DispatchManagerImpl

    protected val dispatch1: Dispatch =
        Dispatch.create("test1", TealiumDispatchType.Event, TealiumBundle.EMPTY_BUNDLE)
    protected val dispatch2: Dispatch =
        Dispatch.create("test2", TealiumDispatchType.Event, TealiumBundle.EMPTY_BUNDLE)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        tealiumScope = CoroutineScope(coroutineDispatcher)

        // Consent Defaulted to disabled
        every { consentManager.enabled } returns false
        every { consentManager.applyConsent(any()) } just Runs

        // Two available test dispatchers
        dispatcher1 = spyk(TestDispatcher("dispatcher_1"))
        dispatcher2 = spyk(TestDispatcher("dispatcher_2"))
        dispatchers = MutableStateFlow(setOf(dispatcher1, dispatcher2))

        // Queue defaulted to empty
        queue = mutableMapOf()
        onInFlightEvents = MutableSharedFlow(replay = 1)
        inFlightEvents = mutableMapOf()
        queueManager = spyk(
            VolatileQueueManagerImpl(
                scope = tealiumScope,
                queue = queue,
                _inFlightEvents = inFlightEvents,
                _onInFlightEvents = onInFlightEvents
            )
        )

        // Barriers defaulted to Open
        barrierFlow = MutableStateFlow(BarrierState.Open)
        every { barrierCoordinator.onBarriersState(any()) } returns barrierFlow

        transformersFlow = MutableStateFlow(setOf())
        transformers = mutableSetOf()
        transformerCoordinator = spyk(
            TransformerCoordinatorImpl(
                transformers, transformersFlow
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
        dispatchers: StateFlow<Set<Dispatcher>> = this.dispatchers,
        tealiumScope: CoroutineScope = this.tealiumScope,
        logger: Logger = this.logger,
        maxInFlight: Int = DispatchManagerImpl.MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER
    ): DispatchManagerImpl = DispatchManagerImpl(
        consentManager,
        barrierCoordinator,
        transformerCoordinator,
        queueManager,
        dispatchers,
        tealiumScope,
        logger,
        maxInFlight
    )


}