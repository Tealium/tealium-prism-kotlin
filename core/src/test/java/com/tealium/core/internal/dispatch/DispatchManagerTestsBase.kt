package com.tealium.core.internal.dispatch

import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.api.settings.CoreSettings
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TealiumDispatchType
import com.tealium.core.api.transform.ScopedTransformation
import com.tealium.core.api.transform.Transformer
import com.tealium.core.internal.modules.InternalModuleManager
import com.tealium.core.internal.modules.consent.ConsentManager
import com.tealium.core.internal.persistence.VolatileQueueRepository
import com.tealium.core.internal.persistence.repositories.QueueRepository
import com.tealium.core.internal.settings.CoreSettingsImpl
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.spyk
import org.junit.Before
import java.util.concurrent.ConcurrentHashMap

/**
 * Test Base class to simplify test groups relating to the Dispatch Manager.
 *
 */
open class DispatchManagerTestsBase {


    @MockK
    protected lateinit var consentManager: ConsentManager

    @MockK
    protected lateinit var barrierCoordinator: BarrierCoordinator

    @MockK
    protected lateinit var moduleManager: InternalModuleManager

    protected lateinit var logger: Logger
    protected lateinit var scheduler: Scheduler
    protected lateinit var dispatcher1Name: String
    protected lateinit var dispatcher2Name: String
    protected lateinit var dispatcher1: Dispatcher
    protected lateinit var dispatcher2: Dispatcher
    protected lateinit var dispatchers: StateSubject<Set<Dispatcher>>
    protected lateinit var barrierFlow: Subject<BarrierState>
    protected lateinit var processors: Subject<Set<String>>
    protected lateinit var queueManager: QueueManager
    protected lateinit var queue: MutableMap<String, MutableSet<Dispatch>>
    protected lateinit var queueRepository: QueueRepository
    protected lateinit var inFlightEvents: StateSubject<Map<String, Set<Dispatch>>>
    protected lateinit var onInFlightEvents: Subject<Map<String, Set<String>>>
    protected lateinit var transformerCoordinator: TransformerCoordinator
    protected lateinit var transformers: MutableSet<Transformer>
    protected lateinit var transformersFlow: StateSubject<Set<ScopedTransformation>>
    protected lateinit var coreSettings: StateSubject<CoreSettings>
    protected lateinit var dispatchManager: DispatchManagerImpl

    protected val dispatch1: Dispatch =
        Dispatch.create("test1", TealiumDispatchType.Event, DataObject.EMPTY_OBJECT)
    protected val dispatch2: Dispatch =
        Dispatch.create("test2", TealiumDispatchType.Event, DataObject.EMPTY_OBJECT)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        logger = SystemLogger
        scheduler = testTealiumScheduler

        // Consent Defaulted to disabled
        every { consentManager.applyConsent(any()) } just Runs
        every { moduleManager.getModuleOfType(ConsentManager::class.java) } returns null

        // Two available test dispatchers
        dispatcher1Name = "dispatcher_1" // Workaround; spyk in verify blocks fails on `Dispatcher.name`
        dispatcher2Name = "dispatcher_2" // but direct string values dont.
        dispatcher1 = spyk(TestDispatcher(dispatcher1Name))
        dispatcher2 = spyk(TestDispatcher(dispatcher2Name))
        dispatchers = Observables.stateSubject(setOf(dispatcher1, dispatcher2))

        // Queue defaulted to empty
        // concurrent due to test async nature.
        queue = ConcurrentHashMap()
        queueRepository = VolatileQueueRepository(queue)
        inFlightEvents = Observables.stateSubject(mutableMapOf())
        onInFlightEvents = Observables.replaySubject(cacheSize = 1)
        processors = Observables.replaySubject(1)
        coreSettings = Observables.stateSubject(CoreSettingsImpl())
        queueManager = spyk(
            QueueManagerImpl(
                queueRepository = queueRepository,
                settings = coreSettings,
                processors = processors,
                inFlightDispatches = inFlightEvents,
                logger = SystemLogger
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
        moduleManager: InternalModuleManager = this.moduleManager,
        barrierCoordinator: BarrierCoordinator = this.barrierCoordinator,
        transformerCoordinator: TransformerCoordinator = this.transformerCoordinator,
        queueManager: QueueManager = this.queueManager,
        dispatchers: StateSubject<Set<Dispatcher>> = this.dispatchers,
        logger: Logger = this.logger,
        maxInFlight: Int = DispatchManagerImpl.MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER
    ): DispatchManagerImpl = DispatchManagerImpl(
        moduleManager,
        barrierCoordinator,
        transformerCoordinator,
        queueManager,
        dispatchers,
        logger,
        maxInFlight
    )

    protected fun enableConsent() {
        every { moduleManager.getModuleOfType(ConsentManager::class.java) } returns consentManager
    }

    protected fun disableConsent() {
        every { moduleManager.getModuleOfType(ConsentManager::class.java) } returns null
    }
}
