package com.tealium.core.internal

import android.app.Application
import com.tealium.core.api.Tealium
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumException
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.api.tracking.TrackResultListener
import com.tealium.tests.common.SynchronousScheduler
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TealiumProxyTests {

    @MockK
    private lateinit var onShutdown: (String) -> Unit
    @RelaxedMockK
    private lateinit var tealiumImpl: TealiumImpl

    private val key: String = "test"
    private lateinit var app: Application
    private lateinit var dispatch: Dispatch
    private lateinit var onTealiumImplReady: Subject<TealiumResult<TealiumImpl>>
    private lateinit var scheduler: Scheduler
    private lateinit var tealiumProxy: TealiumProxy

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        app = RuntimeEnvironment.getApplication()
        // will run all async methods synchronously, making it simpler to test
        scheduler = SynchronousScheduler()
        dispatch = Dispatch.create("Test")
        onTealiumImplReady = Observables.replaySubject(1)

        tealiumProxy = TealiumProxy(key, scheduler, onTealiumImplReady, onShutdown)
    }

    @Test
    fun init_Subscribes_Shutdown_Handler() {
        assertEquals(1, onTealiumImplReady.count)
    }

    @Test
    fun onTealiumImplReady_Queues_Events_When_Tealium_Not_Ready() {
        val listener = mockk<TrackResultListener>(relaxed = true)
        tealiumProxy.track(dispatch, listener)

        assertEquals(2, onTealiumImplReady.count) // includes shutdown sub
    }

    @Test
    fun onTealiumImplReady_Processes_Queued_Events_When_Tealium_Ready_Successfully() {
        val listener = mockk<TrackResultListener>(relaxed = true)

        tealiumProxy.track(dispatch, listener)
        assertEquals(2, onTealiumImplReady.count)  // includes shutdown sub
        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))
        assertEquals(1, onTealiumImplReady.count)  // includes shutdown sub
    }

    @Test
    fun onTealiumImplReady_Processes_Queued_Events_When_Tealium_Failed_Init() {
        val listener = mockk<TrackResultListener>(relaxed = true)

        tealiumProxy.track(dispatch, listener)
        assertEquals(2, onTealiumImplReady.count)  // includes shutdown sub
        onTealiumImplReady.onNext(TealiumResult.failure(TealiumException()))
        assertEquals(0, onTealiumImplReady.count)  // shutdown sub removed too
    }

    @Test
    fun onTealiumImplReady_Executes_Shutdown_When_Received_Exception_Result() {
        val listener = mockk<TrackResultListener>(relaxed = true)

        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))
        onTealiumImplReady.onNext(TealiumResult.failure(Tealium.TealiumShutdownException("")))
        tealiumProxy.track(dispatch, listener)

        verify(inverse = true) {
            listener.onTrackResultReady(TrackResult.Accepted(dispatch))
        }
    }

    @Test
    fun getTrace_Returns_Same_Instance() {
        val trace1 = tealiumProxy.trace
        val trace2 = tealiumProxy.trace

        assertSame(trace1, trace2)
    }

    @Test
    fun getDeeplink_Returns_Same_Instance() {
        val deeplink1 = tealiumProxy.deeplink
        val deeplink2 = tealiumProxy.deeplink

        assertSame(deeplink1, deeplink2)
    }

    @Test
    fun getTimedEvents_Returns_Same_Instance() {
        val timedEvents1 = tealiumProxy.timedEvents
        val timedEvents2 = tealiumProxy.timedEvents

        assertSame(timedEvents1, timedEvents2)
    }

    @Test
    fun getDataLayer_Returns_Same_Instance() {
        val dataLayer1 = tealiumProxy.dataLayer
        val dataLayer2 = tealiumProxy.dataLayer

        assertSame(dataLayer1, dataLayer2)
    }

    @Test
    fun getConsent_Returns_Same_Instance() {
        // TODO - consent manager not implemented yet
//        val consent1 = tealiumProxy.consent
//        val consent2 = tealiumProxy.consent
//
//        assertSame(consent1, consent2)
    }

    @Test
    fun getVisitorService_Returns_Same_Instance() {
        // TODO - this may not be true once the module is introduced
        val visitorService1 = tealiumProxy.visitorService
        val visitorService2 = tealiumProxy.visitorService

        assertSame(visitorService1, visitorService2)
    }

    @Test
    fun track_Calls_TealiumImpl_When_Created_Successfully() {
        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))
        tealiumProxy.track(dispatch)

        verify {
            tealiumImpl.track(dispatch)
        }
    }

    @Test
    fun track_Does_Not_Call_TealiumImpl_When_Created_Exceptionally() {
        onTealiumImplReady.onNext(TealiumResult.failure(Tealium.TealiumShutdownException("")))
        tealiumProxy.track(dispatch)

        verify(inverse = true) {
            tealiumImpl.track(dispatch)
        }
    }

    @Test
    fun trackWithListener_Calls_TealiumImpl_When_Created_Successfully() {
        val listener = mockk<TrackResultListener>(relaxed = true)

        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))
        tealiumProxy.track(dispatch, listener)

        verify {
            tealiumImpl.track(dispatch, listener)
        }
    }

    @Test
    fun trackWithListener_Does_Not_Call_TealiumImpl_When_Created_Exceptionally() {
        val listener = mockk<TrackResultListener>(relaxed = true)

        onTealiumImplReady.onNext(TealiumResult.failure(Tealium.TealiumShutdownException("")))
        tealiumProxy.track(dispatch, listener)

        verify(inverse = true) {
            tealiumImpl.track(dispatch, listener)
        }
    }

    @Test
    fun flushEventQueue_Calls_TealiumImpl_When_Created_Successfully() {
        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))
        tealiumProxy.flushEventQueue()

        verify {
            tealiumImpl.flushEventQueue()
        }
    }


    @Test
    fun flushEventQueue_Does_Not_Call_TealiumImpl_When_Created_Exceptionally() {
        onTealiumImplReady.onNext(TealiumResult.failure(Tealium.TealiumShutdownException("")))
        tealiumProxy.flushEventQueue()

        verify(inverse = true) {
            tealiumImpl.flushEventQueue()
        }
    }

    @Test
    fun resetVisitorId_Calls_TealiumImpl_When_Created_Successfully() {
        val listener = mockk<TealiumCallback<TealiumResult<String>>>(relaxed = true)
        every { tealiumImpl.resetVisitorId() } returns "newVisitor"

        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))
        tealiumProxy.resetVisitorId(listener)

        verify {
            tealiumImpl.resetVisitorId()
            listener.onComplete(match {
                it.getOrThrow() == "newVisitor"
            })
        }
    }

    @Test
    fun resetVisitorId_Returns_Exception_When_Created_Exceptionally() {
        val listener = mockk<TealiumCallback<TealiumResult<String>>>(relaxed = true)
        val error = Tealium.TealiumShutdownException("")

        onTealiumImplReady.onNext(TealiumResult.failure(error))
        tealiumProxy.resetVisitorId(listener)

        verify {
            listener.onComplete(match { it.exceptionOrNull() == error })
        }
    }

    @Test
    fun resetVisitorId_Does_Not_Call_TealiumImpl_When_Created_Exceptionally() {
        val listener = mockk<TealiumCallback<TealiumResult<String>>>(relaxed = true)

        onTealiumImplReady.onNext(TealiumResult.failure(Tealium.TealiumShutdownException("")))
        tealiumProxy.resetVisitorId(listener)

        verify(inverse = true) {
            tealiumImpl.resetVisitorId()
        }
    }

    @Test
    fun clearStoredVisitorIds_Calls_TealiumImpl_When_Created_Successfully() {
        val listener = mockk<TealiumCallback<TealiumResult<String>>>(relaxed = true)
        every { tealiumImpl.clearStoredVisitorIds() } returns "newVisitor"

        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))
        tealiumProxy.clearStoredVisitorIds(listener)

        verify {
            tealiumImpl.clearStoredVisitorIds()
            listener.onComplete(match {
                it.getOrThrow() == "newVisitor"
            })
        }
    }

    @Test
    fun clearStoredVisitorIds_Returns_Exception_When_Created_Exceptionally() {
        val listener = mockk<TealiumCallback<TealiumResult<String>>>(relaxed = true)
        val error = Tealium.TealiumShutdownException("")

        onTealiumImplReady.onNext(TealiumResult.failure(error))
        tealiumProxy.clearStoredVisitorIds(listener)

        verify {
            listener.onComplete(match { it.exceptionOrNull() == error })
        }
    }

    @Test
    fun clearStoredVisitorIds_Does_Not_Call_TealiumImpl_When_Created_Exceptionally() {
        val listener = mockk<TealiumCallback<TealiumResult<String>>>(relaxed = true)

        onTealiumImplReady.onNext(TealiumResult.failure(Tealium.TealiumShutdownException("")))
        tealiumProxy.clearStoredVisitorIds(listener)

        verify(inverse = true) {
            tealiumImpl.clearStoredVisitorIds()
        }
    }

    @Test
    fun createModuleProxy_Always_Returns_New_Instance() {
        val proxy1 = tealiumProxy.createModuleProxy(Dispatcher::class.java)
        val proxy2 = tealiumProxy.createModuleProxy(Dispatcher::class.java)

        assertNotSame(proxy1, proxy2)
    }

    @Test
    fun shutdown_Calls_OnShutdown() {
        every { onShutdown.invoke(any()) } just Runs

        tealiumProxy.shutdown()

        verify {
            onShutdown(key)
        }
    }
}