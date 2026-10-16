package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.api.tracking.TrackResultListener
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.persistence.repositories.QueueRepository
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.core.internal.settings.SdkSettings
import com.tealium.tests.common.ModuleSettingRow
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.TestModuleFactory
import com.tealium.tests.common.settingsPass
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class DispatchManagerTrackTests : DispatchManagerTestsBase() {

    private val dispatch3: Dispatch =
        Dispatch.create("test3", DispatchType.Event, DataObject.EMPTY_OBJECT)

    @Test
    fun dispatchManager_SlowDispatcher_DoesNotDelayOthers() {
        val slowDispatcher = TestDispatcher.mock(dispatcher2Name) { dispatches, callback ->
            scheduler.schedule(TimeFrame(500, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
            }
        }

        scheduler.execute {
            dispatchers.onNext(listOf(dispatcher1, slowDispatcher))

            queue[dispatcher1.id] = mutableSetOf(
                dispatch1,
                dispatch2
            )
            queue[slowDispatcher.id] = mutableSetOf(
                dispatch1,
                dispatch2
            )

            dispatchManager.startDispatchLoop()

            dispatchManager.track(dispatch3)
        }

        verify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch2), any())
            dispatcher1.dispatch(listOf(dispatch3), any())

            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch3), dispatcher1Name)
        }
        verify(timeout = 5000) {
            slowDispatcher.dispatch(listOf(dispatch1), any())
            slowDispatcher.dispatch(listOf(dispatch2), any())
            slowDispatcher.dispatch(listOf(dispatch3), any())

            queueManager.deleteDispatches(listOf(dispatch1), dispatcher2Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher2Name)
            queueManager.deleteDispatches(listOf(dispatch3), dispatcher2Name)
        }
    }

    @Test
    fun dispatchManager_SendsMultipleDispatchesInFlight_WhenDelayed() {
        dispatcher1 = TestDispatcher.mock(dispatcher1Name) { dispatches, callback ->
            scheduler.schedule(TimeFrame(500, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
            }
        }

        scheduler.execute {
            dispatchers.onNext(listOf(dispatcher1))
            queue[dispatcher1.id] = mutableSetOf(
                dispatch1,
                dispatch2
            )

            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch3)
        }

        verify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch2), any())
            dispatcher1.dispatch(listOf(dispatch3), any())

            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch3), dispatcher1Name)
        }
    }

    /**
     * Storing for an empty processor set is a no-op, so reporting the track as accepted would claim the
     * dispatch had been enqueued when it was in fact stored nowhere.
     */
    @Test
    fun track_Reports_Dropped_When_No_Dispatcher_Is_Enabled() {
        dispatchers.onNext(listOf())
        val result = slot<TrackResult>()
        val listener = mockk<TrackResultListener>(relaxed = true)
        every { listener.onTrackResultReady(capture(result)) } returns Unit

        dispatchManager.track(dispatch1, listener)

        assertEquals(TrackResult.Status.Dropped, result.captured.status)
        verify(exactly = 0) {
            queueManager.storeDispatches(any(), any())
        }
    }

    /**
     * A failed queue insert leaves the dispatch stored nowhere, so reporting it as accepted would
     * claim it had been enqueued when it is in fact lost.
     */
    @Test
    fun track_Reports_Dropped_When_Store_Fails() {
        every {
            queueManager.storeDispatches(any(), any())
        } throws PersistenceException("boom", RuntimeException())
        val result = slot<TrackResult>()
        val listener = mockk<TrackResultListener>(relaxed = true)
        every { listener.onTrackResultReady(capture(result)) } returns Unit

        dispatchManager.track(dispatch1, listener)

        assertEquals(TrackResult.Status.Dropped, result.captured.status)
        assertTrue(result.captured.info, result.captured.info.contains("Failed to persist"))
    }

    /**
     * Same as above, but with the failure raised by the repository itself and propagated through a
     * real [QueueManagerImpl], so the whole path from the failed insert to the caller is exercised.
     */
    @Test
    fun track_Reports_Dropped_When_Repository_Store_Fails() {
        dispatchManager = createDispatchManager(queueManager = queueManagerWithFailingRepository())
        val result = slot<TrackResult>()
        val listener = mockk<TrackResultListener>(relaxed = true)
        every { listener.onTrackResultReady(capture(result)) } returns Unit

        dispatchManager.track(dispatch1, listener)

        assertEquals(TrackResult.Status.Dropped, result.captured.status)
        assertTrue(result.captured.info, result.captured.info.contains("Failed to persist"))
    }

    @Test
    fun track_Reports_Accepted_When_Store_Succeeds() {
        val result = slot<TrackResult>()
        val listener = mockk<TrackResultListener>(relaxed = true)
        every { listener.onTrackResultReady(capture(result)) } returns Unit

        dispatchManager.track(dispatch1, listener)

        assertEquals(TrackResult.Status.Accepted, result.captured.status)
        assertTrue(result.captured.info, result.captured.info.contains("Enqueued for processors"))
    }

    /**
     * A module tracking from its own `updateConfiguration` runs while the settings pass is still
     * building the module list, so the enqueue has to wait for that pass rather than be answered with
     * the dispatchers the previous one published - here the very dispatcher this pass is disabling.
     */
    @Test
    fun track_Does_Not_Enqueue_For_The_Dispatchers_Of_The_Previous_Pass() {
        val moduleManager = ModuleManagerImpl(Scheduler.SYNCHRONOUS)
        dispatchManager =
            createDispatchManager(moduleManager.observeModules(Dispatcher::class.java))
        val context = mockk<TealiumContext>()
        every { context.logger } returns SystemLogger

        moduleManager.addModuleFactory(TestModuleFactory.forModule(dispatcher1))
        moduleManager.addModuleFactory(TestModuleFactory.forModule(dispatcher2))
        moduleManager.addModuleFactory(
            TestModuleFactory.singleInstance(TRACKING_MODULE_ID) { moduleId, _, _ ->
                TrackingOnUpdateModule(moduleId) { dispatchManager.track(dispatch1) }
            }
        )

        moduleManager.updateModuleSettings(
            context,
            settingsPass(
                ModuleSettingRow(dispatcher1Name, order = 0),
                ModuleSettingRow(TRACKING_MODULE_ID, order = 1)
            )
        )

        val processors = slot<Set<String>>()
        every { queueManager.storeDispatches(any(), capture(processors)) } returns Unit

        moduleManager.updateModuleSettings(
            context,
            settingsPass(
                ModuleSettingRow(dispatcher1Name, order = 0, enabled = false),
                ModuleSettingRow(TRACKING_MODULE_ID, order = 1),
                ModuleSettingRow(dispatcher2Name, order = 2)
            )
        )

        assertEquals(setOf(dispatcher2Name), processors.captured)
    }

    /**
     * Tracks from [updateConfiguration], i.e. from within the settings pass that reconfigures it, so
     * that the event is issued while the module list of that pass is still being built.
     */
    private class TrackingOnUpdateModule(
        override val id: String,
        private val onUpdate: () -> Unit
    ) : Module {
        override val version: String = "1.0"

        override fun updateConfiguration(configuration: DataObject): Module {
            onUpdate()
            return this
        }
    }

    /**
     * A real [QueueManagerImpl] over a repository whose every insert fails.
     */
    private fun queueManagerWithFailingRepository(): QueueManager {
        val failingRepository = mockk<QueueRepository>(relaxed = true)
        every {
            failingRepository.storeDispatch(any(), any())
        } throws PersistenceException("boom", RuntimeException())

        return QueueManagerImpl(
            queueRepository = failingRepository,
            settings = coreSettings,
            processors = processors,
            logger = SystemLogger
        )
    }

    private companion object {
        const val TRACKING_MODULE_ID = "tracking_module"
    }
}
