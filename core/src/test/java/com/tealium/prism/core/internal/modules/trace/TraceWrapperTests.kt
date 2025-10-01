package com.tealium.prism.core.internal.modules.trace

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.api.tracking.TrackResultListener
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.modules.ModuleProxyImpl
import com.tealium.tests.common.SynchronousScheduler
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class TraceWrapperTests {

    @RelaxedMockK
    private lateinit var traceModule: TraceModule

    @RelaxedMockK
    private lateinit var observer: Observer<TealiumResult<Unit>>

    @RelaxedMockK
    private lateinit var killVisitorObserver: Observer<TealiumResult<TrackResult>>

    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var moduleManager: ModuleManager
    private lateinit var onModuleManager: Subject<ModuleManager?>
    private lateinit var proxy: ModuleProxy<TraceModule>

    private lateinit var traceManagerWrapper: TraceWrapper
    private val scheduler = SynchronousScheduler()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { traceModule.id } returns Modules.Types.TRACE
        modules = Observables.stateSubject(listOf(traceModule))
        moduleManager = ModuleManagerImpl(scheduler, modules)
        onModuleManager = Observables.replaySubject(1)
        onModuleManager.onNext(moduleManager)

        proxy = ModuleProxyImpl(TraceModule::class.java, onModuleManager, scheduler)
        traceManagerWrapper = TraceWrapper(proxy)
    }

    @Test
    fun methods_Report_Module_Not_Enabled_When_Not_Enabled() {
        modules.onNext(emptyList())

        traceManagerWrapper.join("12345").subscribe(observer)
        traceManagerWrapper.leave().subscribe(observer)
        traceManagerWrapper.killVisitorSession().subscribe(killVisitorObserver)

        verify {
            observer.onNext(match { it.exceptionOrNull() is ModuleNotEnabledException })
            observer.onNext(match { it.exceptionOrNull() is ModuleNotEnabledException })
            killVisitorObserver.onNext(match { it.exceptionOrNull() is ModuleNotEnabledException })
        }
    }

    @Test
    fun methods_Report_Tealium_Shutdown_When_No_ModuleManager() {
        onModuleManager.onNext(null)

        traceManagerWrapper.join("12345").subscribe(observer)
        traceManagerWrapper.leave().subscribe(observer)
        traceManagerWrapper.killVisitorSession().subscribe(killVisitorObserver)

        verify {
            observer.onNext(match { it.exceptionOrNull() is Tealium.TealiumShutdownException })
            observer.onNext(match { it.exceptionOrNull() is Tealium.TealiumShutdownException })
            killVisitorObserver.onNext(match { it.exceptionOrNull() is Tealium.TealiumShutdownException })
        }
    }

    @Test
    fun join_Returns_Success_When_Module_Enabled() {
        traceManagerWrapper.join("12345")
            .subscribe(observer)

        verify {
            observer.onNext(match { it.isSuccess })
        }
    }

    @Test
    fun join_Calls_Module_Implementation_When_Module_Enabled() {
        traceManagerWrapper.join("12345")

        verify {
            traceModule.join("12345")
        }
    }

    @Test
    fun leave_Returns_Success_When_Module_Enabled() {
        traceManagerWrapper.leave()
            .subscribe(observer)

        verify {
            observer.onNext(match { it.isSuccess })
        }
    }

    @Test
    fun leave_Calls_Module_Implementation_When_Module_Enabled() {
        traceManagerWrapper.leave()

        verify {
            traceModule.leave()
        }
    }

    @Test
    fun killVisitorSession_Calls_Module_Implementation_When_Module_Enabled() {
        traceManagerWrapper.killVisitorSession()

        verify {
            traceModule.killVisitorSession(any())
        }
    }

    @Test
    fun killVisitorSession_Returns_Success_When_Dispatch_Accepted() {
        mockModuleCallback(TrackResult.accepted(mockk(), ""))

        traceManagerWrapper.killVisitorSession()
            .subscribe(killVisitorObserver)

        verify {
            killVisitorObserver.onNext(match { it.isSuccess })
        }
    }

    @Test
    fun killVisitorSession_Returns_Success_Even_When_Dispatch_Dropped() {
        mockModuleCallback(TrackResult.dropped(mockk(), ""))

        traceManagerWrapper.killVisitorSession()
            .subscribe(killVisitorObserver)

        verify {
            killVisitorObserver.onNext(match { it.isSuccess })
        }
    }

    @Test
    fun init_Delegates_Proxy_To_Tealium() {
        val proxy = mockk<ModuleProxy<TraceModule>>()
        val tealium = mockk<Tealium>()
        every { tealium.createModuleProxy(TraceModule::class.java) } returns proxy

        TraceWrapper(tealium)

        verify {
            tealium.createModuleProxy(TraceModule::class.java)
        }
    }

    private fun mockModuleCallback(result: TrackResult) {
        every { traceModule.killVisitorSession(any()) } answers {
            arg<TrackResultListener>(0)
                .onTrackResultReady(result)
        }
    }
}