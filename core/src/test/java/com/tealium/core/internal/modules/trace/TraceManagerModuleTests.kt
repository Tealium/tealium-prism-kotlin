package com.tealium.core.internal.modules.trace

import com.tealium.core.BuildConfig
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.misc.TealiumException
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.api.tracking.TrackResultListener
import com.tealium.core.api.tracking.Tracker
import com.tealium.tests.common.mockkEditor
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TraceManagerModuleTests {

    @RelaxedMockK
    private lateinit var tracker: Tracker

    @RelaxedMockK
    private lateinit var dataStore: DataStore

    @MockK
    private lateinit var editor: DataStore.Editor

    private lateinit var trace: TraceManagerModule

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { dataStore.edit() } returns editor
        mockkEditor(editor)
        mockTrackerResponse(TrackResult.Accepted(mockk()))

        trace = TraceManagerModule(
            dataStore, tracker
        )
    }

    @Test(expected = TealiumException::class)
    fun killVisitorSession_Throws_When_Not_In_Trace() {
        every { dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID) } returns null
        val callback = mockk<TrackResultListener>(relaxed = true)

        trace.killVisitorSession(callback)
    }

    @Test
    fun killVisitorSession_Does_Not_Track_Event_When_Not_In_Trace() {
        every { dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID) } returns null
        val callback = mockk<TrackResultListener>(relaxed = true)

        runCatching {
            trace.killVisitorSession(callback)
        }

        verify(inverse = true) {
            tracker.track(any(), any(), any())
        }
    }

    @Test
    fun killVisitorSession_Tracks_Dispatch_With_TraceId_In_The_Payload() {
        every { dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID) } returns "12345"
        val callback = mockk<TrackResultListener>(relaxed = true)

        trace.killVisitorSession(callback)

        verify {
            tracker.track(match {
                it.payload().getString(Dispatch.Keys.TEALIUM_TRACE_ID) == "12345"
            }, any(), any())
        }
    }

    @Test
    fun killVisitorSession_Tracks_Dispatch_With_Correct_Event_Name() {
        val callback = mockk<TrackResultListener>(relaxed = true)

        trace.killVisitorSession(callback)

        verify {
            tracker.track(match {
                it.tealiumEvent == "kill_visitor_session"
                        && it.payload().getString(Dispatch.Keys.EVENT) == "kill_visitor_session"
            }, any(), any())
        }
    }

    @Test
    fun killVisitorSession_Returns_Accepted_When_Dispatch_Accepted() {
        val callback = mockk<TrackResultListener>(relaxed = true)
        mockTrackerResponse(TrackResult.Accepted(mockk()))

        trace.killVisitorSession(callback)

        verify {
            callback.onTrackResultReady(match { it is TrackResult.Accepted })
        }
    }

    @Test
    fun killVisitorSession_Returns_Dropped_When_Dispatch_Dropped() {
        val callback = mockk<TrackResultListener>(relaxed = true)
        mockTrackerResponse(TrackResult.Dropped(mockk()))

        trace.killVisitorSession(callback)

        verify {
            callback.onTrackResultReady(match { it is TrackResult.Dropped })
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
        every { dataStore.getAll() } returns DataObject.create {
            put(Dispatch.Keys.TEALIUM_TRACE_ID, "12345")
        }
        val dispatchContext =
            DispatchContext(DispatchContext.Source.application(), DataObject.EMPTY_OBJECT)

        val data = trace.collect(dispatchContext)

        assertEquals("12345", data.getString(Dispatch.Keys.TEALIUM_TRACE_ID))
    }

    @Test
    fun collect_Returns_Empty_Object_When_Trace_Not_Joined() {
        every { dataStore.getAll() } returns DataObject.EMPTY_OBJECT
        val dispatchContext =
            DispatchContext(DispatchContext.Source.application(), DataObject.EMPTY_OBJECT)

        val data = trace.collect(dispatchContext)

        assertEquals(DataObject.EMPTY_OBJECT, data)
    }

    @Test
    fun collect_Returns_Empty_Object_When_Source_Is_Trace_Module() {
        every { dataStore.getAll() } returns DataObject.create {
            put(Dispatch.Keys.TEALIUM_TRACE_ID, "12345")
        }
        val dispatchContext =
            DispatchContext(
                DispatchContext.Source.module(TraceManagerModule::class.java),
                DataObject.EMPTY_OBJECT
            )

        val data = trace.collect(dispatchContext)

        assertEquals(DataObject.EMPTY_OBJECT, data)
    }

    @Test
    fun id_Matches_Factory_Id() {
        assertEquals(TraceManagerModule.Factory.id, trace.id)
    }

    @Test
    fun version_Matches_Build_Version() {
        assertEquals(BuildConfig.TEALIUM_LIBRARY_VERSION, trace.version)
    }

    private fun mockTrackerResponse(result: TrackResult) {
        every { tracker.track(any(), any(), any()) } answers {
            arg<TrackResultListener>(2)
                .onTrackResultReady(result)
        }
    }
}