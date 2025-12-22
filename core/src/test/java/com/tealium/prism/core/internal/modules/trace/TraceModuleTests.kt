package com.tealium.prism.core.internal.modules.trace

import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.api.tracking.TrackResultListener
import com.tealium.prism.core.api.tracking.Tracker
import com.tealium.prism.core.internal.logger.ErrorEvent
import com.tealium.tests.common.mockkEditor
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TraceModuleTests {

    @RelaxedMockK
    lateinit var mockConfiguration: TraceModuleConfiguration

    @RelaxedMockK
    private lateinit var tracker: Tracker

    @RelaxedMockK
    private lateinit var dataStore: DataStore

    @MockK
    private lateinit var editor: DataStore.Editor

    private lateinit var trace: TraceModule

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { dataStore.edit() } returns editor
        mockkEditor(editor)
        mockTrackerResponse(TrackResult.accepted(mockk(), ""))

        trace = TraceModule(
            dataStore, tracker, mockConfiguration
        )
    }

    @Test(expected = TealiumException::class)
    fun forceVisitEnd_Throws_When_Not_In_Trace() {
        every { dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID) } returns null
        val callback = mockk<TrackResultListener>(relaxed = true)

        trace.forceEndOfVisit(callback)
    }

    @Test
    fun forceEndOfVisit_Does_Not_Track_Event_When_Not_In_Trace() {
        every { dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID) } returns null
        val callback = mockk<TrackResultListener>(relaxed = true)

        runCatching {
            trace.forceEndOfVisit(callback)
        }

        verify(inverse = true) {
            tracker.track(any(), any(), any())
        }
    }

    @Test
    fun forceEndOfVisit_Tracks_Dispatch_With_TraceId_In_The_Payload() {
        every { dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID) } returns "12345"
        val callback = mockk<TrackResultListener>(relaxed = true)

        trace.forceEndOfVisit(callback)

        verify {
            tracker.track(match {
                it.payload().getString(Dispatch.Keys.TEALIUM_TRACE_ID) == "12345" &&
                it.payload().getString(Dispatch.Keys.CP_TRACE_ID) == "12345"
            }, any(), any())
        }
    }

    @Test
    fun forceEndOfVisit_Tracks_Dispatch_With_Correct_Event_Name() {
        val callback = mockk<TrackResultListener>(relaxed = true)

        trace.forceEndOfVisit(callback)

        verify {
            tracker.track(match {
                it.tealiumEvent == "kill_visitor_session"
                        && it.payload().getString(Dispatch.Keys.EVENT) == "kill_visitor_session"
            }, any(), any())
        }
    }

    @Test
    fun forceEndOfVisit_Returns_Accepted_When_Dispatch_Accepted() {
        val callback = mockk<TrackResultListener>(relaxed = true)
        mockTrackerResponse(TrackResult.accepted(mockk(), ""))

        trace.forceEndOfVisit(callback)

        verify {
            callback.onTrackResultReady(match { it.status == TrackResult.Status.Accepted })
        }
    }

    @Test
    fun forceEndOfVisit_Returns_Dropped_When_Dispatch_Dropped() {
        val callback = mockk<TrackResultListener>(relaxed = true)
        mockTrackerResponse(TrackResult.dropped(mockk(), ""))

        trace.forceEndOfVisit(callback)

        verify {
            callback.onTrackResultReady(match { it.status == TrackResult.Status.Dropped })
        }
    }

    @Test
    fun join_Adds_Trace_Id_To_Storage() {
        trace.join("12345")

        verify {
            editor.put(Dispatch.Keys.TEALIUM_TRACE_ID, "12345", Expiry.SESSION)
            editor.commit()
        }
    }

    @Test
    fun leave_Removes_Trace_Id_From_Storage() {
        trace.leave()

        verify {
            editor.remove(Dispatch.Keys.TEALIUM_TRACE_ID)
            editor.commit()
        }
    }

    @Test
    fun collect_Returns_Trace_Id_In_DataObject_When_Trace_Joined() {
        every { dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID) } returns "12345"
        val dispatchContext =
            DispatchContext(DispatchContext.Source.application(), DataObject.EMPTY_OBJECT)

        val data = trace.collect(dispatchContext)

        assertEquals("12345", data.getString(Dispatch.Keys.TEALIUM_TRACE_ID))
        assertEquals("12345", data.getString(Dispatch.Keys.CP_TRACE_ID))
    }

    @Test
    fun collect_Returns_Empty_Object_When_Trace_Not_Joined() {
        every { dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID) } returns null

        val dispatchContext =
            DispatchContext(DispatchContext.Source.application(), DataObject.EMPTY_OBJECT)

        val data = trace.collect(dispatchContext)

        assertEquals(DataObject.EMPTY_OBJECT, data)
    }

    @Test
    fun collect_Returns_Trace_Data_When_Source_Is_Trace_Module() {
        every { dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID) } returns "12345"

        val dispatchContext =
            DispatchContext(
                DispatchContext.Source.module(TraceModule::class.java),
                DataObject.EMPTY_OBJECT
            )

        val data = trace.collect(dispatchContext)

        assertEquals("12345", data.getString(Dispatch.Keys.TEALIUM_TRACE_ID))
        assertEquals("12345", data.getString(Dispatch.Keys.CP_TRACE_ID))
    }

    @Test
    fun id_Matches_Factory_ModuleType() {
        assertEquals(TraceModule.Factory().moduleType, trace.id)
    }

    @Test
    fun version_Matches_Build_Version() {
        assertEquals(BuildConfig.TEALIUM_LIBRARY_VERSION, trace.version)
    }

    @Test
    fun errorEventDispatch_NotCreated_When_ShouldTrackErrors_DisabledByDefault() {
        every { mockConfiguration.trackErrors } returns false
        val errorSubject = Observables.publishSubject<ErrorEvent>()
        trace = TraceModule(dataStore, tracker, mockConfiguration, errorSubject.asObservable())

        trace.join("12345")
        errorSubject.onNext(ErrorEvent("Test Category","Test Error"))

        verify{
            tracker wasNot Called
        }
    }

    @Test
    fun errorEventDispatch_Created_When_ShouldTrackErrors_Enabled() {
        every { mockConfiguration.trackErrors } returns true
        val errorSubject = Observables.publishSubject<ErrorEvent>()
        trace = TraceModule(dataStore, tracker, mockConfiguration, errorSubject.asObservable())

        trace.join("12345")
        errorSubject.onNext(ErrorEvent("Test Category","Test Error"))

        verify {
            tracker.track(match {
                it.tealiumEvent == "tealium_error" &&
                        it.payload().getString("error_description") == "Test Category: Test Error"
            }, any())
        }

    }

    @Test
    fun errorEvent_NotTracked_WhenEnabled_ButNotInTrace() {
        every { mockConfiguration.trackErrors } returns true
        every { dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID) } returns null
        val errorSubject = Observables.publishSubject<ErrorEvent>()
        trace = TraceModule(dataStore, tracker, mockConfiguration, errorSubject.asObservable())

        errorSubject.onNext(ErrorEvent("Test Category","Test Error"))

        verify{
            tracker wasNot Called
        }
    }

    @Test
    fun errorEventTracking_Stopped_AfterLeave() {
        every { mockConfiguration.trackErrors } returns true
        val errorSubject = Observables.publishSubject<ErrorEvent>()
        trace = TraceModule(dataStore, tracker, mockConfiguration, errorSubject.asObservable())

        trace.join("12345")
        errorSubject.onNext(ErrorEvent("Test Category","Test Error"))

        verify {
            tracker.track(match {
                it.tealiumEvent == "tealium_error" &&
                        it.payload().getString("error_description") == "Test Category: Test Error"
            }, any())
        }

        trace.leave()
        errorSubject.onNext(ErrorEvent("Another Test Category","Another Test Error"))

        verify(exactly = 1) {
            tracker.track(any(), any())
        }
    }

    @Test
    fun errorEvent_NotDuplicated_InSameTrace() {
        every { mockConfiguration.trackErrors } returns true
        val errorSubject = Observables.publishSubject<ErrorEvent>()
        trace = TraceModule(dataStore, tracker, mockConfiguration, errorSubject.asObservable())

        trace.join("12345")
        errorSubject.onNext(ErrorEvent("Test Category","Test Error"))
        errorSubject.onNext(ErrorEvent("Test Category","Test Error"))

        verify(exactly = 1) {
            tracker.track(match {
                it.tealiumEvent == "tealium_error" &&
                        it.payload().getString("error_description") == "Test Category: Test Error"
            }, any())
        }
    }

    @Test
    fun errorEventTracked_OncePerCategory_AfterLeaveAndRejoin() {
        every { mockConfiguration.trackErrors } returns true
        val errorSubject = Observables.publishSubject<ErrorEvent>()
        trace = TraceModule(dataStore, tracker, mockConfiguration, errorSubject.asObservable())

        trace.join("12345")
        errorSubject.onNext(ErrorEvent("Test Category","Test Error"))

        verify {
            tracker.track(match {
                it.tealiumEvent == "tealium_error" &&
                        it.payload().getString("error_description") == "Test Category: Test Error"
            }, any())
        }

        trace.leave()
        trace.join("12345")
        errorSubject.onNext(ErrorEvent("Test Category","Test Error"))

        verify(exactly = 2) {
            tracker.track(match {
                it.tealiumEvent == "tealium_error" &&
                        it.payload().getString("error_description") == "Test Category: Test Error"
            }, any())
        }
    }

    @Test
    fun errorEventTracked_DifferentCategories_InSameTrace() {
        every { mockConfiguration.trackErrors } returns true
        val errorSubject = Observables.publishSubject<ErrorEvent>()
        trace = TraceModule(dataStore, tracker, mockConfiguration, errorSubject.asObservable())

        trace.join("12345")
        errorSubject.onNext(ErrorEvent("Test Category","Test Error"))
        errorSubject.onNext(ErrorEvent("Another Test Category","Another Test Error"))

        verify {
            tracker.track(match {
                it.tealiumEvent == "tealium_error" &&
                        it.payload().getString("error_description") == "Test Category: Test Error"
            }, any())

            tracker.track(match {
                it.tealiumEvent == "tealium_error" &&
                        it.payload().getString("error_description") == "Another Test Category: Another Test Error"
            }, any())
        }
    }

    private fun mockTrackerResponse(result: TrackResult) {
        every { tracker.track(any(), any(), any()) } answers {
            arg<TrackResultListener>(2)
                .onTrackResultReady(result)
        }
    }
}