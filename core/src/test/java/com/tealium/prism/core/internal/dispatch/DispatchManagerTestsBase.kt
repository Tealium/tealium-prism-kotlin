package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.api.settings.CoreSettings
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.api.transform.Transformer
import com.tealium.prism.core.internal.consent.ConsentManager
import com.tealium.prism.core.internal.modules.InternalModuleManager
import com.tealium.prism.core.internal.persistence.repositories.QueueRepository
import com.tealium.prism.core.internal.persistence.repositories.VolatileQueueRepository
import com.tealium.prism.core.internal.rules.LoadRuleEngine
import com.tealium.prism.core.internal.settings.CoreSettingsImpl
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.TestTransformer
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.spyk
import org.junit.Before
import java.util.concurrent.ConcurrentHashMap

/**
 * Test Base class to simplify test groups relating to the Dispatch Manager.
 *
 */
open class DispatchManagerTestsBase {

    @MockK
    protected lateinit var barrierCoordinator: BarrierCoordinator

    @MockK
    protected lateinit var moduleManager: InternalModuleManager

    @MockK
    protected lateinit var loadRuleEngine: LoadRuleEngine

    protected var consentManager: ConsentManager? = null
    protected lateinit var logger: Logger
    protected lateinit var scheduler: Scheduler
    protected lateinit var dispatcher1Name: String
    protected lateinit var dispatcher2Name: String
    protected lateinit var dispatcher1: Dispatcher
    protected lateinit var dispatcher2: Dispatcher
    protected lateinit var modules: StateSubject<List<Module>>
    protected lateinit var barrierFlow: Subject<BarrierState>
    protected lateinit var processors: Subject<Set<String>>
    protected lateinit var queueManager: QueueManager
    protected lateinit var queue: MutableMap<String, MutableSet<Dispatch>>
    protected lateinit var queueRepository: QueueRepository
    protected lateinit var inFlightEvents: StateSubject<Map<String, Set<Dispatch>>>
    protected lateinit var transformerCoordinator: TransformerCoordinator
    protected lateinit var transformers: StateSubject<List<Transformer>>
    protected lateinit var transformations: StateSubject<Set<TransformationSettings>>
    protected lateinit var coreSettings: StateSubject<CoreSettings>
    protected lateinit var dispatchManager: DispatchManagerImpl
    protected lateinit var mappings: StateSubject<Map<String, List<MappingOperation>>>
    protected lateinit var mappingsEngine: MappingsEngine

    protected val dispatch1: Dispatch =
        Dispatch.create("test1", DispatchType.Event, DataObject.EMPTY_OBJECT)
    protected val dispatch2: Dispatch =
        Dispatch.create("test2", DispatchType.Event, DataObject.EMPTY_OBJECT)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        logger = SystemLogger
        scheduler = testTealiumScheduler

        // Two available test dispatchers
        dispatcher1Name =
            "dispatcher_1" // Workaround; spyk in verify blocks fails on `Dispatcher.name`
        dispatcher2Name = "dispatcher_2" // but direct string values dont.
        dispatcher1 = spyk(TestDispatcher(dispatcher1Name))
        dispatcher2 = spyk(TestDispatcher(dispatcher2Name))
        modules = Observables.stateSubject(listOf(dispatcher1, dispatcher2))
        every { moduleManager.modules } returns modules
        every { moduleManager.getModulesOfType(Dispatcher::class.java) } answers { modules.value.filterIsInstance<Dispatcher>() }

        every { loadRuleEngine.evaluateLoadRules(any(), any()) } answers {
            DispatchSplit(arg(1), emptyList())
        }

        // Queue defaulted to empty
        // concurrent due to test async nature.
        queue = ConcurrentHashMap()
        queueRepository = VolatileQueueRepository(queue)
        inFlightEvents = Observables.stateSubject(mutableMapOf())
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

        transformations = Observables.stateSubject(setOf())
        transformers = Observables.stateSubject(emptyList())
        transformerCoordinator = spyk(
            TransformerCoordinatorImpl(
                transformers, transformations, Scheduler.SYNCHRONOUS, SystemLogger
            )
        )

        mappings = Observables.stateSubject(emptyMap())
        mappingsEngine = MappingsEngine(mappings)

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
        loadRuleEngine: LoadRuleEngine = this.loadRuleEngine,
        mappingsEngine: MappingsEngine = this.mappingsEngine,
        consentManager: ConsentManager? = this.consentManager,
        logger: Logger = this.logger,
        maxInFlight: Int = DispatchManagerImpl.MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER
    ): DispatchManagerImpl = DispatchManagerImpl(
        moduleManager,
        barrierCoordinator,
        transformerCoordinator,
        queueManager,
        loadRuleEngine,
        mappingsEngine,
        consentManager,
        logger,
        maxInFlight
    )

    protected fun testDispatch(
        event: String,
        type: DispatchType = DispatchType.Event,
        data: DataObject = DataObject.EMPTY_OBJECT
    ): Dispatch =
        Dispatch.create(event, type, data)

    /**
     * utility that appends a new Transformer and a new transformation scoped to the given [scope]
     */
    protected fun registerTransformation(
        id: String = "test",
        transformerId: String = "test",
        scope: Set<TransformationScope> = setOf(TransformationScope.AllDispatchers),
        onTransform: (
            transformation: TransformationSettings,
            dispatch: Dispatch,
            scope: DispatchScope
        ) -> Dispatch?
    ) {
        transformers.onNext(transformers.value + TestTransformer(transformerId, transformHandler = onTransform))
        transformations.onNext(transformations.value + TransformationSettings(id, transformerId, scope))
    }
}
