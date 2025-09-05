package com.tealium.core.internal.misc

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.modules.Module
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.api.tracking.TrackResultListener
import com.tealium.core.internal.dispatch.DispatchManager
import com.tealium.core.internal.rules.LoadRuleEngine
import com.tealium.core.internal.session.SessionManager
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestCollector
import com.tealium.tests.common.TestDispatcher
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackerImplTest {

    @RelaxedMockK
    private lateinit var dispatchManager: DispatchManager
    @RelaxedMockK
    private lateinit var sessionManager: SessionManager
    @MockK
    private lateinit var loadRuleEngine: LoadRuleEngine

    private lateinit var dispatch: Dispatch
    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var source: DispatchContext.Source
    private lateinit var tracker: TrackerImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        modules = Observables.stateSubject(listOf())
        dispatch = Dispatch.create("test", dataObject = DataObject.EMPTY_OBJECT)
        source = DispatchContext.Source.application()

        // default load rules allow all
        every { loadRuleEngine.rulesAllow(any(), any()) } returns true

        // default tealium purpose not blocked
        every { dispatchManager.tealiumPurposeExplicitlyBlocked } returns false

        tracker = TrackerImpl(modules, dispatchManager, loadRuleEngine, sessionManager, SystemLogger)
    }

    @Test
    fun track_Waits_When_No_Modules_Emissions() {
        val originalPayload = dispatch.payload()
        tracker.track(dispatch, source)

        assertEquals(originalPayload, dispatch.payload())
        verify(inverse = true) {
            dispatchManager.track(any())
            dispatchManager.track(any(), any())
        }
    }

    @Test
    fun track_Waits_When_Modules_Empty_For_Init_And_Shutdown() {
        val originalPayload = dispatch.payload()
        modules.onNext(emptyList())

        tracker.track(dispatch, source)

        assertEquals(originalPayload, dispatch.payload())
        verify(inverse = true) {
            dispatchManager.track(any())
            dispatchManager.track(any(), any())
        }
    }

    @Test
    fun track_Waits_Until_Modules_Emitted_To_Collect() {
        tracker.track(dispatch, source)

        val collectedData = DataObject.Builder().put("key", "value").build()
        val collector = collector(collectedData)
        modules.onNext(listOf(collector))

        assertEquals("value", dispatch.payload().getString("key"))
        verify {
            dispatchManager.track(dispatch, any())
        }
    }

    @Test
    fun track_Enriches_Dispatch_With_Collected_Data_When_Collectors_Are_Available() {
        val collectedData = DataObject.Builder().put("key", "value").build()
        val collector = collector(collectedData)
        modules.onNext(listOf(collector))

        tracker.track(dispatch, source)

        assertEquals("value", dispatch.payload().getString("key"))
    }

    @Test
    fun track_Calls_Dispatch_Manager_Even_With_No_Collectors() {
        modules.onNext(listOf(TestDispatcher("dispatcher")))

        tracker.track(dispatch, source)

        verify {
            dispatchManager.track(dispatch, any())
        }
    }

    @Test
    fun track_Enriches_Data_From_All_Collectors() {
        val collector1 = collector(DataObject.Builder().put("key_1", "value_1").build())
        val collector2 = collector(DataObject.Builder().put("key_2", "value_2").build())
        modules.onNext(listOf(collector1, collector2))

        tracker.track(dispatch, source)

        assertEquals("value_1", dispatch.payload().getString("key_1"))
        assertEquals("value_2", dispatch.payload().getString("key_2"))
    }

    @Test
    fun track_Enriches_In_Order_Of_Collectors() {
        val collector1 = collector(DataObject.Builder().put("key_1", "value_2").build())
        val collector2 = collector(
            DataObject.Builder()
                .put("key_1", "updated_value_1")
                .put("key_2", "value_2")
                .build()
        )
        modules.onNext(listOf(collector1, collector2))

        tracker.track(dispatch, source)

        assertEquals("updated_value_1", dispatch.payload().getString("key_1"))
        assertEquals("value_2", dispatch.payload().getString("key_2"))
    }

    @Test
    fun track_Passes_The_Source_To_Collectors() {
        val collector = mockk<Collector>()
        every { collector.collect(any()) } returns DataObject.EMPTY_OBJECT
        modules.onNext(listOf(collector))

        tracker.track(dispatch, source)

        verify {
            collector.collect(match { it.source == source })
        }
    }

    @Test
    fun track_Does_Enrich_From_Collectors_That_Pass_Load_Rules() {
        val collector = TestCollector.mock("test")
        every { loadRuleEngine.rulesAllow(collector, dispatch) } returns true
        modules.onNext(listOf( collector))

        tracker.track(dispatch, source)

        verify {
            collector.collect(any())
        }
    }

    @Test
    fun track_Does_Not_Enrich_From_Collectors_That_Fail_Load_Rules() {
        val collector = TestCollector.mock("test")
        every { loadRuleEngine.rulesAllow(collector, dispatch) } returns false
        modules.onNext(listOf( collector))

        tracker.track(dispatch, source)

        verify(inverse = true) {
            collector.collect(any())
        }
    }

    @Test
    fun track_Does_Not_Register_New_Dispatch_With_Session_Manager_When_Modules_Not_Emitted_Yet() {
        tracker.track(dispatch, source)

        verify(inverse = true) {
            sessionManager.registerDispatch(dispatch)
        }
    }

    @Test
    fun track_Registers_New_Dispatch_With_Session_Manager_When_Modules_Have_Been_Emitted() {
        val collector = TestCollector.mock("test")
        tracker.track(dispatch, source)
        modules.onNext(listOf(collector))

        verify {
            sessionManager.registerDispatch(dispatch)
        }
    }

    @Test
    fun track_Registers_New_Dispatch_With_Session_Manager_Before_Collection() {
        val collector = TestCollector.mock("test")
        modules.onNext(listOf( collector))

        tracker.track(dispatch, source)

        verifyOrder {
            sessionManager.registerDispatch(dispatch)
            collector.collect(any())
        }
    }

    @Test
    fun track_Does_Not_Enrich_Or_Store_When_Tealium_Purpose_Explicitly_Blocked() {
        val collector = TestCollector.mock("test")
        every { dispatchManager.tealiumPurposeExplicitlyBlocked } returns true

        tracker.track(dispatch, source)

        verify(inverse = true) {
            collector.collect(any())
            dispatchManager.track(any())
            dispatchManager.track(any(), any())
        }
    }

    @Test
    fun track_Answers_Dropped_When_Tealium_Purpose_Explicitly_Blocked() {
        val listener = mockk<TrackResultListener>(relaxed = true)
        every { dispatchManager.tealiumPurposeExplicitlyBlocked } returns true

        tracker.track(dispatch, source, listener)

        verify {
             listener.onTrackResultReady(match { it.status == TrackResult.Status.Dropped })
        }
    }

    private fun collector(dataObject: DataObject): Collector =
        TestCollector("test") { dataObject }
}