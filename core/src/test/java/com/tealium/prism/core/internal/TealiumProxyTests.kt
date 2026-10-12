package com.tealium.prism.core.internal

import android.app.Application
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.api.tracking.TrackResultListener
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TealiumProxyTests {

    private val shutdownRequests = mutableListOf<TealiumImpl>()

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
        scheduler = Scheduler.SYNCHRONOUS
        dispatch = Dispatch.create("Test")
        onTealiumImplReady = Observables.replaySubject(1)
        every { tealiumImpl.track(any(), any()) } answers {
            arg<TrackResultListener>(2).onTrackResultReady(TrackResult.accepted(arg(1), "accepted"))
        }

        tealiumProxy = TealiumProxy(key, scheduler, onTealiumImplReady) { shutdownRequests.add(it) }
    }

    @Test
    fun init_Subscribes_Shutdown_Handler() {
        assertEquals(1, onTealiumImplReady.count)
    }

    @Test
    fun onTealiumImplReady_Queues_Events_When_Tealium_Not_Ready() {
        tealiumProxy.track("", DataObject.EMPTY_OBJECT)

        assertEquals(2, onTealiumImplReady.count) // includes shutdown sub
    }

    @Test
    fun onTealiumImplReady_Processes_Queued_Events_When_Tealium_Ready_Successfully() {
        tealiumProxy.track("", DataObject.EMPTY_OBJECT)
        assertEquals(2, onTealiumImplReady.count)  // includes shutdown sub
        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))
        assertEquals(1, onTealiumImplReady.count)  // includes shutdown sub
    }

    @Test
    fun onTealiumImplReady_Processes_Queued_Events_When_Tealium_Failed_Init() {
        tealiumProxy.track("", DataObject.EMPTY_OBJECT)
        assertEquals(2, onTealiumImplReady.count)  // includes shutdown sub
        onTealiumImplReady.onNext(TealiumResult.failure(TealiumException()))
        assertEquals(0, onTealiumImplReady.count)  // shutdown sub removed too
    }

    @Test
    fun onTealiumImplReady_Executes_Shutdown_When_Received_Exception_Result() {
        val observer = mockk<Observer<TealiumResult<TrackResult>>>(relaxed = true)
        val shutdownException = Tealium.TealiumShutdownException("")

        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))
        onTealiumImplReady.onNext(TealiumResult.failure(shutdownException))
        tealiumProxy.track("", DataObject.EMPTY_OBJECT)
            .subscribe(observer)

        verify {
            observer.onNext(match {
                it.exceptionOrNull() == shutdownException
            })
        }
    }

    @Test
    fun getTrace_Returns_Same_Instance() {
        val trace1 = tealiumProxy.trace
        val trace2 = tealiumProxy.trace

        assertSame(trace1, trace2)
    }

    @Test
    fun getDeepLink_Returns_Same_Instance() {
        val deepLink1 = tealiumProxy.deepLink
        val deepLink2 = tealiumProxy.deepLink

        assertSame(deepLink1, deepLink2)
    }

    @Test
    fun getDataLayer_Returns_Same_Instance() {
        val dataLayer1 = tealiumProxy.dataLayer
        val dataLayer2 = tealiumProxy.dataLayer

        assertSame(dataLayer1, dataLayer2)
    }

    @Test
    fun getVisitorId_Returns_Same_Instance() {
        val visitorId1 = tealiumProxy.visitorId
        val visitorId2 = tealiumProxy.visitorId

        assertSame(visitorId1, visitorId2)
    }

    @Test
    fun track_Calls_TealiumImpl_When_Created_Successfully() {
        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))
        tealiumProxy.track("event", DataObject.EMPTY_OBJECT)

        verify {
            tealiumImpl.track(match { it.tealiumEvent == "event" }, any())
        }
    }

    @Test
    fun track_Does_Not_Call_TealiumImpl_When_Created_Exceptionally() {
        onTealiumImplReady.onNext(TealiumResult.failure(Tealium.TealiumShutdownException("")))
        tealiumProxy.track("event", DataObject.EMPTY_OBJECT)

        verify(inverse = true) {
            tealiumImpl.track(any())
        }
    }

    @Test
    fun trackWithListener_Calls_TealiumImpl_When_Created_Successfully() {
        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))
        tealiumProxy.track("event", DataObject.EMPTY_OBJECT)

        verify {
            tealiumImpl.track(match { it.tealiumEvent == "event" }, any())
        }
    }

    @Test
    fun trackWithListener_Does_Not_Call_TealiumImpl_When_Created_Exceptionally() {
        onTealiumImplReady.onNext(TealiumResult.failure(Tealium.TealiumShutdownException("")))
        tealiumProxy.track("event", DataObject.EMPTY_OBJECT)

        verify(inverse = true) {
            tealiumImpl.track(match { it.tealiumEvent == "event" }, any())
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
    fun createModuleProxy_Always_Returns_New_Instance() {
        val proxy1 = tealiumProxy.createModuleProxy(Dispatcher::class.java)
        val proxy2 = tealiumProxy.createModuleProxy(Dispatcher::class.java)

        assertNotSame(proxy1, proxy2)
    }

    @Test
    fun createModuleProxy_Forwards_Initialization_Exception_To_Module_Proxy() {
        val initException = TealiumException("Failed to initialize")
        val observer = mockk<Observer<TealiumResult<String>>>(relaxed = true)
        onTealiumImplReady.onNext(TealiumResult.failure(initException))

        tealiumProxy.createModuleProxy(Dispatcher::class.java)
            .executeModuleTask(Dispatcher::id)
            .subscribe(observer)

        verify {
            observer.onNext(match { it.exceptionOrNull() === initException })
        }
    }

    @Test
    fun shutdown_Calls_OnShutdown_With_Bound_Instance_When_Ready() {
        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))

        tealiumProxy.shutdown()

        assertEquals(listOf(tealiumImpl), shutdownRequests)
    }

    @Test
    fun shutdown_Calls_OnShutdown_Once_Ready_When_Called_Before_Ready() {
        tealiumProxy.shutdown()
        assertTrue(shutdownRequests.isEmpty())

        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))

        assertEquals(listOf(tealiumImpl), shutdownRequests)
    }

    @Test
    fun shutdown_Does_Not_Call_OnShutdown_When_Already_Shutdown() {
        onTealiumImplReady.onNext(TealiumResult.success(tealiumImpl))
        onTealiumImplReady.onNext(TealiumResult.failure(Tealium.TealiumShutdownException("")))

        tealiumProxy.shutdown()

        assertTrue(shutdownRequests.isEmpty())
    }

    @Test
    fun shutdown_Does_Not_Call_OnShutdown_When_Instance_Failed_To_Initialize() {
        onTealiumImplReady.onNext(TealiumResult.failure(TealiumException("Failed to initialize")))

        tealiumProxy.shutdown()

        assertTrue(shutdownRequests.isEmpty())
    }

    @Test
    fun onReady_Calls_Callback_When_TealiumImpl_Ready() {
        val onReady = mockk<Callback<Tealium>>(relaxed = true)

        tealiumProxy.onReady(onReady)

        onTealiumImplReady.onNext(TealiumResult.success(mockk()))

        verify {
            onReady.onComplete(tealiumProxy)
        }
    }

    @Test
    fun onReady_Does_Not_Call_Callback_When_TealiumImpl_Not_Ready() {
        val onReady = mockk<Callback<Tealium>>(relaxed = true)

        tealiumProxy.onReady(onReady)

        verify(inverse = true) {
            onReady.onComplete(tealiumProxy)
        }
    }

    @Test
    fun onReady_Does_Not_Call_Callback_When_TealiumImpl_Failed() {
        val onReady = mockk<Callback<Tealium>>(relaxed = true)

        tealiumProxy.onReady(onReady)

        onTealiumImplReady.onNext(TealiumResult.failure(Exception()))

        verify(inverse = true) {
            onReady.onComplete(tealiumProxy)
        }
    }
}