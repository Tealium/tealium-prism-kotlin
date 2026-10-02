package com.tealium.prism.lifecycle

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.pubsub.ReplaySubject
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.internal.barriers.BarrierCoordinator
import com.tealium.prism.core.internal.dispatch.DispatchManagerImpl
import com.tealium.prism.core.internal.dispatch.MappingsEngine
import com.tealium.prism.core.internal.dispatch.QueueManager
import com.tealium.prism.core.internal.dispatch.TransformerCoordinator
import com.tealium.prism.core.internal.misc.TrackerImpl
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.rules.LoadRuleEngine
import com.tealium.prism.core.internal.session.SessionManager
import com.tealium.prism.lifecycle.internal.LifecycleConfiguration
import com.tealium.prism.lifecycle.internal.LifecycleModule
import com.tealium.prism.lifecycle.internal.LifecycleService
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestCollector
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.ModuleSettingRow
import com.tealium.tests.common.TestModuleFactory
import com.tealium.tests.common.settingsPass
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression tests for MT-2164, proving the readiness gate from real shipped module code
 * (`LifecycleModule`) rather than from a synthetic test module.
 *
 * `LifecycleModule` tracks `launch` from its own `init`, via `subscribeToActivityUpdates` ->
 * `registerLifecycleEvent`, so the event is issued while the settings pass that creates it is still
 * building the module list. It has to be addressed against the modules of that pass - both the
 * dispatchers it is enqueued for and the collectors that enrich it - rather than against the list the
 * previous pass published.
 *
 * Separately, a module *absent* from the new settings has to be shut down as well as dropped from the
 * list, or `activitiesSubscription` stays live and it keeps calling `tracker.track` after removal.
 */
@RunWith(RobolectricTestRunner::class)
class LifecycleModuleReadinessTests {

    private lateinit var moduleManager: ModuleManagerImpl
    private lateinit var modulesSubject: StateSubject<List<Module>>
    private lateinit var queueManager: QueueManager
    private lateinit var transformerCoordinator: TransformerCoordinator
    private lateinit var loadRuleEngine: LoadRuleEngine
    private lateinit var barrierCoordinator: BarrierCoordinator
    private lateinit var mappingsEngine: MappingsEngine
    private lateinit var sessionManager: SessionManager
    private lateinit var dispatchManager: DispatchManagerImpl
    private lateinit var tracker: TrackerImpl
    private lateinit var context: TealiumContext

    private lateinit var applicationStatus: ReplaySubject<ActivityManager.ApplicationStatus>
    private lateinit var lifecycleService: LifecycleService
    private lateinit var lifecycleConfiguration: LifecycleConfiguration

    private val oldDispatcher = TestDispatcher.mock("old_dispatcher")
    private val newDispatcher = TestDispatcher.mock("new_dispatcher")

    @Before
    fun setUp() {
        modulesSubject = Observables.stateSubject(listOf())
        moduleManager = ModuleManagerImpl(Scheduler.SYNCHRONOUS, modulesSubject)

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

        context = mockk()
        every { context.logger } returns SystemLogger
        every { context.tracker } returns tracker

        // Mirrors ActivityManagerImpl: applicationStatus is a ReplaySubject fed by a StateSubject
        // seeded with the launch status (ActivityManagerImpl.kt:43-46,59-64). Emitting Init before
        // the module subscribes lets the subscription replay it synchronously on subscribe.
        applicationStatus = Observables.replaySubject()
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())

        lifecycleConfiguration = LifecycleConfiguration.fromDataObject(DataObject.EMPTY_OBJECT)
        lifecycleService = mockk()
        every { lifecycleService.registerLaunch(any()) } returns DataObject.create { put("launch", true) }
        every { lifecycleService.registerSleep(any()) } returns DataObject.create { put("sleep", true) }
        every { lifecycleService.lastLifecycleEvent } returns LifecycleEvent.Launch

        moduleManager.addModuleFactory(TestModuleFactory.forModule(oldDispatcher))
        moduleManager.addModuleFactory(TestModuleFactory.forModule(newDispatcher))
    }

    @Test
    fun track_Reaches_Dispatcher_When_Dispatcher_Enabled_In_The_Same_Settings_Pass_As_Lifecycle() {
        moduleManager.addModuleFactory(lifecycleModuleFactory())

        moduleManager.updateModuleSettings(
            context,
            settingsPass(ModuleSettingRow(oldDispatcher.id, order = 0))
        )

        val processorsSlot = slot<Set<String>>()
        every { queueManager.storeDispatches(any(), capture(processorsSlot)) } just Runs

        moduleManager.updateModuleSettings(
            context,
            settingsPass(
                ModuleSettingRow(oldDispatcher.id, order = 0),
                ModuleSettingRow(newDispatcher.id, order = 1),
                ModuleSettingRow(Lifecycle.ID, order = 2)
            )
        )

        assertEquals(setOf(oldDispatcher.id, newDispatcher.id), processorsSlot.captured)
    }

    @Test
    fun track_Does_Not_Persist_Dispatch_When_Lifecycle_Omitted_From_Settings() {
        var createdModule: LifecycleModule? = null
        moduleManager.addModuleFactory(lifecycleModuleFactory { createdModule = it })

        moduleManager.updateModuleSettings(
            context,
            settingsPass(
                ModuleSettingRow(oldDispatcher.id, order = 0),
                ModuleSettingRow(Lifecycle.ID, order = 1)
            )
        )

        // Pass 1 already tracked a launch event; reset recorded calls (but keep the stub) so
        // the next assertion only reflects calls made after pass 2.
        clearMocks(queueManager, answers = false)
        val trackedDispatches = mutableListOf<List<Dispatch>>()
        every { queueManager.storeDispatches(capture(trackedDispatches), any()) } just Runs

        moduleManager.updateModuleSettings(
            context,
            settingsPass(ModuleSettingRow(oldDispatcher.id, order = 0))
        )

        val resolved = mutableListOf<LifecycleModule?>()
        moduleManager.getModuleOfType(LifecycleModule::class.java) { resolved.add(it) }
        assertNull(resolved.single())

        applicationStatus.onNext(ActivityManager.ApplicationStatus.Backgrounded())

        assertEquals(
            "Removed LifecycleModule still tracked: ${trackedDispatches.flatten().map { it.tealiumEvent }}",
            emptyList<List<Dispatch>>(),
            trackedDispatches
        )
        assertNotNull(createdModule)
        assertNull(
            "activitiesSubscription must be disposed and nulled by onShutdown() when the module is omitted from settings",
            createdModule?.activitiesSubscription
        )
    }

    /**
     * `LifecycleModule` tracks `launch` from its `init`, so with the collector ordered after it the
     * collector does not exist yet when the event is issued. The track waits for the pass, so the
     * launch event still carries the collector's data.
     */
    @Test
    fun launch_Is_Enriched_By_The_Collector_Created_After_Lifecycle_In_The_Same_Pass() {
        val lateCollector = TestCollector.mock("late_collector") {
            DataObject.create { put("late_collector_ran", true) }
        }
        moduleManager.addModuleFactory(lifecycleModuleFactory())
        moduleManager.addModuleFactory(TestModuleFactory.forModule(lateCollector))

        val dispatchesSlot = slot<List<Dispatch>>()
        every { queueManager.storeDispatches(capture(dispatchesSlot), any()) } just Runs

        moduleManager.updateModuleSettings(
            context,
            settingsPass(
                ModuleSettingRow(Lifecycle.ID, order = 0),
                ModuleSettingRow(lateCollector.id, order = 1),
                ModuleSettingRow(oldDispatcher.id, order = 2)
            )
        )

        val launch = dispatchesSlot.captured.single()
        assertEquals(LifecycleEvent.Launch.event, launch.tealiumEvent)
        assertEquals(true, launch.payload().getBoolean("late_collector_ran"))
    }

    // The primary 5-arg constructor is used (rather than LifecycleModule.Factory.create()) so the
    // real init -> subscribeToActivityUpdates -> registerLifecycleEvent -> tracker.track path
    // (LifecycleModule.kt:88-90,161-170,116-142) is exercised, without dragging in the
    // DataStore/Robolectric-DB plumbing that the Factory would require.
    private fun lifecycleModuleFactory(onCreated: (LifecycleModule) -> Unit = {}) =
        TestModuleFactory.singleInstance(Lifecycle.ID) { _, ctx, _ ->
            LifecycleModule(
                lifecycleConfiguration,
                lifecycleService,
                applicationStatus,
                ctx.tracker,
                SystemLogger
            ).also(onCreated)
        }
}
