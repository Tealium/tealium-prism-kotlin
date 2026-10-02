package com.tealium.prism.core.internal.consent

import com.tealium.prism.core.api.consent.ConsentDecision
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.internal.dispatch.QueueManager
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.settings.consent.ConsentConfiguration
import com.tealium.prism.core.internal.settings.consent.ConsentPurpose
import com.tealium.prism.core.internal.settings.consent.ConsentSettings
import com.tealium.tests.common.ModuleSettingRow
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.TestModule
import com.tealium.tests.common.TestModuleFactory
import com.tealium.tests.common.settingsPass
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class ConsentIntegrationManagerTests {

    private lateinit var dispatch: Dispatch
    private lateinit var consentSettings: StateSubject<ConsentSettings?>
    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var queueManager: QueueManager
    private lateinit var cmpAdapter: MockCmpAdapter
    private lateinit var cmpSelector: CmpConfigurationSelector
    private lateinit var consentManager: ConsentIntegrationManager

    @Before
    fun setUp() {
        dispatch = Dispatch.create("test")
        consentSettings = Observables.stateSubject(
            consentSettings(refireDispatcherIds = setOf("dispatcher_1"))
        )
        modules = Observables.stateSubject(
            listOf(
                TestDispatcher("dispatcher_1"),
                TestDispatcher("dispatcher_2")
            )
        )
        cmpAdapter = MockCmpAdapter(id = "MockCmp", setOf("tealium", "1", "2", "3"))
        cmpSelector = CmpConfigurationSelector(consentSettings, cmpAdapter, Scheduler.SYNCHRONOUS)
        queueManager = mockk(relaxed = true)

        consentManager = ConsentIntegrationManager(modules, queueManager, cmpSelector, SystemLogger)
    }

    @Test
    fun applyConsent_Returns_Accepted_When_Implicitly_Not_Tealium_Consented() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Implicit, emptySet())

        val result = consentManager.applyConsent(dispatch)

        assertEquals(TrackResult.Status.Accepted, result.status)
    }

    @Test
    fun applyConsent_Returns_Dropped_When_Explicitly_Not_Tealium_Consented() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, emptySet())

        val result = consentManager.applyConsent(dispatch)

        assertEquals(TrackResult.Status.Dropped, result.status)
    }

    @Test
    fun applyConsent_Returns_Accepted_When_Tealium_Consented() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("tealium"))

        val result = consentManager.applyConsent(dispatch)

        assertEquals(TrackResult.Status.Accepted, result.status)
    }

    @Test
    fun applyConsent_Returns_Dropped_When_Unprocessed_Purposes_Empty() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("tealium"))

        dispatch.addAll(DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("tealium").asDataList())
        })

        val result = consentManager.applyConsent(dispatch)

        assertEquals(TrackResult.Status.Dropped, result.status)
    }

    @Test
    fun applyConsent_Enqueues_For_Consent_When_Missing_Configuration() {
        consentSettings.onNext(ConsentSettings(emptyMap()))

        consentManager.applyConsent(dispatch)

        verify {
            queueManager.storeDispatches(listOf(dispatch), setOf(ConsentIntegrationManager.ID))
        }
    }

    @Test
    fun applyConsent_Enqueues_For_Consent_When_Tealium_Implicitly_Not_Consented() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Implicit, emptySet())

        consentManager.applyConsent(dispatch)

        verify {
            queueManager.storeDispatches(listOf(dispatch), setOf(ConsentIntegrationManager.ID))
        }
    }

    @Test
    fun applyConsent_Enqueues_For_Refire_When_Unprocessed_Purposes_Are_Available() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Implicit, setOf("tealium", "1", "2"))
        dispatch.addAll(DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("1").asDataList())
        })

        consentManager.applyConsent(dispatch)

        verify {
            queueManager.storeDispatches(
                listOf(dispatch),
                setOf("dispatcher_1", "dispatcher_2", ConsentIntegrationManager.ID)
            )
        }
    }

    @Test
    fun applyConsent_Does_Not_Enqueue_When_Tealium_Explicitly_Blocked() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, emptySet())

        consentManager.applyConsent(dispatch)

        verify(inverse = true) {
            queueManager.storeDispatches(listOf(dispatch), any())
        }
    }

    @Test
    fun applyConsent_Does_Not_Enqueue_When_No_Additional_Purposes_To_Process() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("1", "2"))
        dispatch.addAll(DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("1", "2").asDataList())
        })

        consentManager.applyConsent(dispatch)

        verify(inverse = true) {
            queueManager.storeDispatches(listOf(dispatch), any())
        }
    }

    @Test
    fun consentDecision_Deletes_Consent_Queue_When_ConsentDecision_Changes() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("tealium", "1"))

        verify { queueManager.deleteAllDispatches(ConsentIntegrationManager.ID) }
    }

    @Test
    fun consentDecision_Does_Not_ReQueue_When_Tealium_Explicitly_Blocked() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, emptySet())

        verify(inverse = true) {
            queueManager.storeDispatches(any(), any())
        }
    }

    @Test
    fun consentDecision_Does_Not_ReQueue_When_No_Dispatches_Queued() {
        every {
            queueManager.dequeueDispatches(
                -1,
                ConsentIntegrationManager.ID
            )
        } returns emptyList()
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("tealium", "1", "2"))

        verify(inverse = true) {
            queueManager.storeDispatches(any(), any())
        }
    }

    @Test
    fun consentDecision_Enqueues_For_Refire_When_Dispatch_Has_New_Purposes() {
        val event1 = Dispatch.create("event_1")
        val refireable = Dispatch.create("refireable", dataObject = DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("tealium", "1").asDataList())
        })
        every { queueManager.dequeueDispatches(-1, ConsentIntegrationManager.ID) } returns listOf(
            event1, refireable
        )
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Implicit, setOf("tealium", "1", "2"))

        verify {
            queueManager.storeDispatches(
                match { it.size == 1 && it[0].tealiumEvent == "event_1" },
                setOf("dispatcher_1", "dispatcher_2")
            )
            queueManager.storeDispatches(
                match {
                    it.size == 1
                            && it[0].id == "${refireable.id}-refire"
                            && it[0].tealiumEvent == "refireable"
                },
                setOf("dispatcher_1")
            )
        }
    }

    /**
     * `ModuleManager` publishes the modules only at the end of a settings pass, so `dispatchers` read
     * synchronously while a pass is running is answered with the dispatchers of the *previous* one -
     * here with the very dispatcher that this pass is disabling.
     */
    @Test
    @Ignore(
        "MT-2164 follow-up: `dispatchers.value` is read synchronously, so a dispatch handled during a settings pass is enqueued for the dispatchers of the previous one - here for the very dispatcher that this pass is disabling. Un-ignore once the enqueue waits for the modules to be ready."
    )
    fun applyConsent_Does_Not_Enqueue_For_The_Dispatchers_Of_The_Previous_Pass() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("tealium"))
        val moduleManager = ModuleManagerImpl(Scheduler.SYNCHRONOUS, Observables.stateSubject(listOf()))
        val manager = consentManager(moduleManager.modules)
        moduleManager.addModuleFactory(TestModuleFactory.forModule(TestDispatcher("dispatcher_1")))
        moduleManager.addModuleFactory(TestModuleFactory.forModule(TestDispatcher("dispatcher_2")))
        // created by the second pass, and applies consent to an event while that pass is still running
        moduleManager.addModuleFactory(
            TestModuleFactory.singleInstance(CONSENTING_MODULE_ID) { moduleId, _, _ ->
                manager.applyConsent(dispatch)
                TestModule(moduleId)
            }
        )
        // first pass: dispatcher_1 is the only dispatcher
        moduleManager.updateModuleSettings(
            moduleContext(),
            settingsPass(ModuleSettingRow("dispatcher_1", order = 0))
        )
        val processorsSlot = slot<Set<String>>()
        every { queueManager.storeDispatches(any(), capture(processorsSlot)) } just Runs

        // second pass: dispatcher_1 is replaced by dispatcher_2
        moduleManager.updateModuleSettings(
            moduleContext(),
            settingsPass(
                ModuleSettingRow("dispatcher_1", order = 0, enabled = false),
                ModuleSettingRow("dispatcher_2", order = 1),
                ModuleSettingRow(CONSENTING_MODULE_ID, order = 2)
            )
        )

        assertEquals(
            "The dispatch must reach the dispatchers of the pass that is running, not the previous ones",
            setOf("dispatcher_2"),
            processorsSlot.captured
        )
    }

    /**
     * `storeDispatches` no-ops on an empty processors set, but the result still claims accepted, so the
     * caller is told the dispatch was accepted while it was stored nowhere.
     */
    @Test
    @Ignore(
        "MT-2164 follow-up: `storeDispatches` no-ops on the empty processors set, so the dispatch is stored nowhere while the caller is told it was accepted. Un-ignore once the empty set is reported as dropped."
    )
    fun applyConsent_Does_Not_Report_Accepted_When_The_Dispatch_Is_Enqueued_For_Nobody() {
        // no settings pass has created the dispatchers yet
        val manager = consentManager(Observables.stateSubject(emptyList()))
        // explicit, so no refire: the processors are only the ones from the modules
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("tealium"))

        val result = manager.applyConsent(dispatch)

        assertEquals(TrackResult.Status.Dropped, result.status)
    }

    /**
     * `refireDispatcherIds` comes from the consent settings, so it can name a dispatcher that is not
     * enabled - a typo, or a module that is not installed. Enqueueing for it stores the dispatches for a
     * processor that no dequeue loop reads, so they are never sent and take up part of the queue.
     */
    @Test
    @Ignore(
        "MT-2164 follow-up: the refire ids are used as processors without being checked against the enabled dispatchers. Un-ignore once the unknown ids are dropped, and logged, instead of being enqueued for."
    )
    fun enqueueDispatches_Does_Not_Store_For_Refire_Dispatchers_That_Are_Not_Enabled() {
        consentSettings.onNext(
            consentSettings(refireDispatcherIds = setOf("dispatcher_1", "not_a_dispatcher"))
        )
        // already processed purposes, so this dispatch takes the refire path
        val refireable = Dispatch.create("refireable", dataObject = DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("tealium", "1").asDataList())
        })
        every { queueManager.dequeueDispatches(-1, ConsentIntegrationManager.ID) } returns listOf(refireable)

        cmpAdapter.setDecision(ConsentDecision.DecisionType.Implicit, setOf("tealium", "1", "2"))

        // the enabled refire dispatcher is kept, and the unknown id dropped
        verify {
            queueManager.storeDispatches(
                match { it.size == 1 && it[0].id == "${refireable.id}-refire" },
                setOf("dispatcher_1")
            )
        }
    }

    /**
     * The dispatches held for consent are dequeued, re-enqueued for nobody as no dispatcher exists yet,
     * and then deleted by the `finally` regardless.
     */
    @Test
    @Ignore(
        "MT-2164 follow-up: the dispatches are dequeued from the consent queue, the re-enqueue no-ops on the empty processors set, and the `finally` deletes them anyway. Un-ignore once the deletion only happens after a successful store."
    )
    fun handleConsentInspectorChange_Does_Not_Lose_The_Dispatches_When_No_Dispatcher_Exists_Yet() {
        // no settings pass has created the dispatchers yet
        val manager = consentManager(Observables.stateSubject(emptyList()))
        val storedFor = mutableListOf<Set<String>>()
        every { queueManager.storeDispatches(any(), capture(storedFor)) } just Runs
        every { queueManager.dequeueDispatches(-1, ConsentIntegrationManager.ID) } returns listOf(dispatch)
        manager.applyConsent(dispatch)
        assertEquals(
            "Precondition: the dispatch is held for consent",
            listOf(setOf(ConsentIntegrationManager.ID)),
            storedFor
        )

        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("tealium"))

        // the consented dispatch must be kept: either enqueued for a dispatcher, or still held for consent
        val enqueuedForADispatcher = storedFor.drop(1).any { processors ->
            (processors - ConsentIntegrationManager.ID).isNotEmpty()
        }
        if (!enqueuedForADispatcher) {
            verify(exactly = 0) { queueManager.deleteAllDispatches(ConsentIntegrationManager.ID) }
        }
    }

    /**
     * Replaces the manager from [setUp] with one reading the given [modules]. The first one is disposed,
     * along with its selector, so only the new one reacts to [cmpAdapter].
     */
    private fun consentManager(modules: ObservableState<List<Module>>): ConsentIntegrationManager {
        consentManager.dispose()
        cmpSelector = CmpConfigurationSelector(consentSettings, cmpAdapter, Scheduler.SYNCHRONOUS)
        return ConsentIntegrationManager(modules, queueManager, cmpSelector, SystemLogger)
    }

    private fun consentSettings(refireDispatcherIds: Set<String>): ConsentSettings =
        ConsentSettings(
            mapOf(
                "MockCmp" to ConsentConfiguration(
                    "tealium", refireDispatcherIds, mapOf(
                        "1" to ConsentPurpose("1", setOf("dispatcher_1")),
                        "2" to ConsentPurpose("2", setOf("dispatcher_2"))
                    )
                )
            )
        )

    private fun moduleContext(): TealiumContext =
        mockk<TealiumContext>().also { context ->
            every { context.logger } returns SystemLogger
        }

    private companion object {
        const val CONSENTING_MODULE_ID = "consenting_module"
    }
}
