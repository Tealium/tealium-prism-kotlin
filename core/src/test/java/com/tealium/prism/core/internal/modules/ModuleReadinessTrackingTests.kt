package com.tealium.prism.core.internal.modules

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.internal.barriers.BarrierCoordinator
import com.tealium.prism.core.internal.dispatch.DispatchManagerImpl
import com.tealium.prism.core.internal.dispatch.MappingsEngine
import com.tealium.prism.core.internal.dispatch.QueueManager
import com.tealium.prism.core.internal.dispatch.TransformerCoordinator
import com.tealium.prism.core.internal.misc.TrackerImpl
import com.tealium.prism.core.internal.rules.LoadRuleEngine
import com.tealium.prism.core.internal.session.SessionManager
import com.tealium.prism.core.internal.settings.CoreSettingsImpl
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestCollector
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.TestModule
import com.tealium.tests.common.TestModuleFactory
import com.tealium.tests.common.ModuleSettingRow
import com.tealium.tests.common.getDefaultConfig
import com.tealium.tests.common.settingsPass
import com.tealium.tests.common.testSchedulers
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Regression tests for MT-2164: a module tracking from its own `create()` or
 * `updateConfiguration()` runs while [ModuleManagerImpl.updateModuleSettings] is still building the
 * module list of that pass, so the event has to be addressed against the modules of that pass rather
 * than against the list the previous one published.
 *
 * The consumers reach the modules only through the readiness gate - `observeModules` for the
 * collectors and the dispatchers, and `unsafeModules` read at collect time, once the tracker has
 * already waited for the pass. Feeding any of them the raw module list instead turns these tests
 * red again.
 */
@RunWith(RobolectricTestRunner::class)
class ModuleReadinessTrackingTests {

    private lateinit var moduleManager: ModuleManagerImpl
    private lateinit var queueManager: QueueManager
    private lateinit var transformerCoordinator: TransformerCoordinator
    private lateinit var loadRuleEngine: LoadRuleEngine
    private lateinit var barrierCoordinator: BarrierCoordinator
    private lateinit var mappingsEngine: MappingsEngine
    private lateinit var sessionManager: SessionManager
    private lateinit var dispatchManager: DispatchManagerImpl
    private lateinit var tracker: TrackerImpl
    private lateinit var context: TealiumContext

    private val oldDispatcher = TestDispatcher.mock("old_dispatcher")
    private val newDispatcher = TestDispatcher.mock("new_dispatcher")
    private val oldCollector = TestCollector.mock("old_collector") {
        DataObject.create { put("old_collector_ran", true) }
    }
    private val newCollector = TestCollector.mock("new_collector") {
        DataObject.create { put("new_collector_ran", true) }
    }

    @Before
    fun setUp() {
        moduleManager = ModuleManagerImpl(Scheduler.SYNCHRONOUS, Observables.stateSubject(listOf()))

        queueManager = mockk(relaxed = true)

        transformerCoordinator = mockk<TransformerCoordinator>()
        every {
            transformerCoordinator.transform(any<Dispatch>(), any<DispatchScope>(), any<(Dispatch?) -> Unit>())
        } answers {
            val dispatch = firstArg<Dispatch>()
            val completion = thirdArg<(Dispatch?) -> Unit>()
            completion(dispatch)
        }

        loadRuleEngine = mockk<LoadRuleEngine>()
        every { loadRuleEngine.rulesAllow(any(), any()) } returns true

        barrierCoordinator = mockk(relaxed = true)
        mappingsEngine = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)

        dispatchManager = DispatchManagerImpl(
            onDispatchers = moduleManager.observeModules(Dispatcher::class.java),
            barrierCoordinator = barrierCoordinator,
            transformerCoordinator = transformerCoordinator,
            queueManager = queueManager,
            loadRuleEngine = loadRuleEngine,
            mappingsEngine = mappingsEngine,
            consentManager = null,
            logger = SystemLogger
        )

        // Mirrors the TealiumImpl wiring exactly: TrackerImpl must consume the gated stream, not the
        // raw module list, or it would race a settings pass same as the production bug.
        tracker = TrackerImpl(
            moduleManager.observeModules(Collector::class.java),
            dispatchManager,
            loadRuleEngine,
            sessionManager,
            SystemLogger
        )

        context = tealiumContext()

        moduleManager.addModuleFactory(TestModuleFactory.forModule(oldDispatcher))
        moduleManager.addModuleFactory(TestModuleFactory.forModule(newDispatcher))
        moduleManager.addModuleFactory(TestModuleFactory.forModule(oldCollector))
        moduleManager.addModuleFactory(TestModuleFactory.forModule(newCollector))
        moduleManager.addModuleFactory(
            TestModuleFactory.singleInstance(TRACKING_MODULE_ID) { moduleId, ctx, _ ->
                ctx.tracker.track(
                    Dispatch.create("launch", DispatchType.Event, DataObject.EMPTY_OBJECT),
                    DispatchContext.Source.module(TestModule::class.java)
                )
                TestModule(moduleId)
            }
        )

        // Pass 1: the modules the next pass reconfigures, and what a consumer reading the raw list
        // would be answered with while that pass is running.
        moduleManager.updateModuleSettings(
            context,
            settingsPass(
                ModuleSettingRow(oldDispatcher.id, order = 0),
                ModuleSettingRow(oldCollector.id, order = 1)
            )
        )
    }

    @Test
    fun track_Reaches_Dispatcher_When_Dispatcher_Enabled_In_The_Same_Settings_Pass() {
        val processorsSlot = slot<Set<String>>()
        every { queueManager.storeDispatches(any(), capture(processorsSlot)) } just Runs

        moduleManager.updateModuleSettings(
            context,
            settingsPass(
                ModuleSettingRow(oldDispatcher.id, order = 0),
                ModuleSettingRow(oldCollector.id, order = 1),
                ModuleSettingRow(newDispatcher.id, order = 2),
                ModuleSettingRow(TRACKING_MODULE_ID, order = 3)
            )
        )

        assertEquals(setOf(oldDispatcher.id, newDispatcher.id), processorsSlot.captured)
    }

    @Test
    fun track_Is_Enriched_By_Collector_When_Collector_Enabled_In_The_Same_Settings_Pass() {
        val dispatchesSlot = slot<List<Dispatch>>()
        every { queueManager.storeDispatches(capture(dispatchesSlot), any()) } just Runs

        moduleManager.updateModuleSettings(
            context,
            settingsPass(
                ModuleSettingRow(oldDispatcher.id, order = 0),
                ModuleSettingRow(newCollector.id, order = 1),
                ModuleSettingRow(TRACKING_MODULE_ID, order = 2)
            )
        )

        val trackedDispatch = dispatchesSlot.captured.first()
        assertEquals(true, trackedDispatch.payload().getBoolean("new_collector_ran"))
    }

    /**
     * With the only dispatcher disabled by the very pass the event is tracked from, there is no
     * processor to enqueue for. Storing for an empty set is a no-op, so the dispatch is reported as
     * dropped rather than stored nowhere and reported as accepted.
     */
    @Test
    fun track_Does_Not_Reach_Dispatcher_When_Dispatcher_Disabled_In_The_Same_Settings_Pass() {
        moduleManager.updateModuleSettings(
            context,
            settingsPass(
                ModuleSettingRow(oldDispatcher.id, order = 0, enabled = false),
                ModuleSettingRow(oldCollector.id, order = 1),
                ModuleSettingRow(TRACKING_MODULE_ID, order = 2)
            )
        )

        verify { oldDispatcher.onShutdown() }
        verify(exactly = 0) { queueManager.storeDispatches(any(), any()) }
    }

    @Test
    fun track_Is_Not_Enriched_By_Collector_When_Collector_Disabled_In_The_Same_Settings_Pass() {
        val dispatchesSlot = slot<List<Dispatch>>()
        every { queueManager.storeDispatches(capture(dispatchesSlot), any()) } just Runs

        moduleManager.updateModuleSettings(
            context,
            settingsPass(
                ModuleSettingRow(oldDispatcher.id, order = 0),
                ModuleSettingRow(oldCollector.id, order = 1, enabled = false),
                ModuleSettingRow(TRACKING_MODULE_ID, order = 2)
            )
        )

        verify { oldCollector.onShutdown() }
        val trackedDispatch = dispatchesSlot.captured.first()
        assertNull(trackedDispatch.payload().getBoolean("old_collector_ran"))
    }

    /**
     * [TealiumDataModule] reports the enabled modules by reading `unsafeModules` at collect time. The
     * tracker has already waited for the pass by then, so the payload has to list every module of it -
     * including the ones created after the module that issued the track.
     */
    @Test
    fun track_From_A_Module_Init_Carries_Enabled_Modules_Of_The_Same_Pass() {
        val dispatchesSlot = slot<List<Dispatch>>()
        every { queueManager.storeDispatches(capture(dispatchesSlot), any()) } just Runs
        moduleManager.addModuleFactory(TealiumDataModule.Factory())

        moduleManager.updateModuleSettings(
            context,
            settingsPass(
                ModuleSettingRow(oldDispatcher.id, order = 0),
                ModuleSettingRow(TRACKING_MODULE_ID, order = 1),
                ModuleSettingRow(Modules.Types.TEALIUM_DATA, order = 2)
            )
        )

        val enabledModules = dispatchesSlot.captured.first()
            .payload()
            .getDataList(Dispatch.Keys.ENABLED_MODULES)!!
        assertEquals(3, enabledModules.size)
        assertEquals(oldDispatcher.id, enabledModules.getString(0))
        assertEquals(TRACKING_MODULE_ID, enabledModules.getString(1))
        assertEquals(Modules.Types.TEALIUM_DATA, enabledModules.getString(2))
    }

    private fun tealiumContext(): TealiumContext = TealiumContext.create(
        context = RuntimeEnvironment.getApplication(),
        config = getDefaultConfig(RuntimeEnvironment.getApplication()),
        logger = SystemLogger,
        storageProvider = mockk(relaxed = true),
        network = mockk(relaxed = true),
        coreSettings = Observables.stateSubject(CoreSettingsImpl()),
        tracker = tracker,
        schedulers = testSchedulers,
        activityManager = mockk(relaxed = true),
        transformerRegistrar = mockk(relaxed = true),
        barrierRegistrar = mockk(relaxed = true),
        moduleManager = moduleManager,
        queueMetrics = mockk(relaxed = true),
        sessionRegistry = mockk(relaxed = true),
        dataLayer = mockk(relaxed = true),
        unsafeModules = moduleManager.modules
    )

    private companion object {
        const val TRACKING_MODULE_ID = "tracking_module"
    }
}
