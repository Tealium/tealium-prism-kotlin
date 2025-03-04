package com.tealium.core.internal.modules.trace

import com.tealium.core.api.Tealium
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.ModuleNotEnabledException
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.core.internal.modules.ModuleProxyImpl
import com.tealium.tests.common.SynchronousScheduler
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class TraceManagerWrapperTests {

    @RelaxedMockK
    private lateinit var traceModule: TraceManagerModule

    @RelaxedMockK
    private lateinit var observer: Observer<TealiumResult<Unit>>

    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var moduleManager: ModuleManager
    private lateinit var onModuleManager: Subject<ModuleManager?>
    private lateinit var proxy: ModuleProxy<TraceManagerModule>

    private lateinit var traceManagerWrapper: TraceManagerWrapper
    private val scheduler = SynchronousScheduler()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { traceModule.id } returns TraceManagerModule.Factory.id
        modules = Observables.stateSubject(listOf(traceModule))
        moduleManager = ModuleManagerImpl(scheduler, modules)
        onModuleManager = Observables.replaySubject(1)
        onModuleManager.onNext(moduleManager)

        proxy = ModuleProxyImpl(TraceManagerModule::class.java, onModuleManager, scheduler)
        traceManagerWrapper = TraceManagerWrapper(proxy)
    }

    @Test
    fun methods_Report_Module_Not_Enabled_When_Not_Enabled() {
        modules.onNext(emptyList())
        val observer = mockk<Observer<TealiumResult<Unit>>>(relaxed = true)

        traceManagerWrapper.join("12345").subscribe(observer)
        traceManagerWrapper.leave().subscribe(observer)
        traceManagerWrapper.killVisitorSession().subscribe(observer)

        verify(exactly = 3) {
            observer.onNext(match { it.exceptionOrNull() is ModuleNotEnabledException })
        }
    }

    @Test
    fun methods_Report_Tealium_Shutdown_When_No_ModuleManager() {
        onModuleManager.onNext(null)

        traceManagerWrapper.join("12345").subscribe(observer)
        traceManagerWrapper.leave().subscribe(observer)
        traceManagerWrapper.killVisitorSession().subscribe(observer)

        verify(exactly = 3) {
            observer.onNext(match { it.exceptionOrNull() is Tealium.TealiumShutdownException })
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
    fun killVisitorSession_Returns_Success_When_Module_Returns_Success() {
        mockModuleCallback(TealiumResult.success(Unit))

        traceManagerWrapper.killVisitorSession()
            .subscribe(observer)

        verify {
            observer.onNext(match { it.isSuccess })
        }
    }

    @Test
    fun killVisitorSession_Returns_Failure_When_Module_Returns_Failure() {
        mockModuleCallback(TealiumResult.success(Unit))

        traceManagerWrapper.killVisitorSession()
            .subscribe(observer)

        verify {
            observer.onNext(match { it.isSuccess })
        }
    }

    @Test
    fun init_Delegates_Proxy_To_Tealium() {
        val proxy = mockk<ModuleProxy<TraceManagerModule>>()
        val tealium = mockk<Tealium>()
        every { tealium.createModuleProxy(TraceManagerModule::class.java) } returns proxy

        TraceManagerWrapper(tealium)

        verify {
            tealium.createModuleProxy(TraceManagerModule::class.java)
        }
    }

    private fun mockModuleCallback(result: TealiumResult<Unit>) {
        every { traceModule.killVisitorSession(any()) } answers {
            arg<TealiumCallback<TealiumResult<Unit>>>(0)
                .onComplete(result)
        }
    }
}