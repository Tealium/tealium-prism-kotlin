package com.tealium.prism.core.internal.modules.deeplink

import android.net.Uri
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.misc.failure
import com.tealium.prism.core.api.misc.success
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.modules.ModuleProxyImpl
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class DeepLinkHandlerWrapperTests {

    @RelaxedMockK
    private lateinit var deepLinkModule: DeepLinkModule

    @RelaxedMockK
    private lateinit var observer: Observer<TealiumResult<Unit>>

    @RelaxedMockK
    private lateinit var uri: Uri

    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var moduleManager: ModuleManager
    private lateinit var onModuleManager: Subject<ModuleManager?>
    private lateinit var proxy: ModuleProxy<DeepLinkModule>

    private lateinit var deepLinkHandlerWrapper: DeepLinkHandlerWrapper
    private val scheduler = Scheduler.SYNCHRONOUS

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { deepLinkModule.id } returns Modules.Types.DEEP_LINK
        every { deepLinkModule.handle(any(), any(), any()) } answers {
            thirdArg<Callback<TealiumResult<Unit>>>().success(Unit)
        }
        modules = Observables.stateSubject(listOf(deepLinkModule))
        moduleManager = ModuleManagerImpl(scheduler, modules)
        onModuleManager = Observables.replaySubject(1)
        onModuleManager.onNext(moduleManager)

        proxy = ModuleProxyImpl(DeepLinkModule::class.java, onModuleManager, scheduler)
        deepLinkHandlerWrapper = DeepLinkHandlerWrapper(proxy)
    }

    @Test
    fun methods_Report_Module_Not_Enabled_When_Not_Enabled() {
        modules.onNext(emptyList())
        val observer = mockk<Observer<TealiumResult<Unit>>>(relaxed = true)

        deepLinkHandlerWrapper.handle(uri).subscribe(observer)
        deepLinkHandlerWrapper.handle(uri, uri).subscribe(observer)

        verify(exactly = 2) {
            observer.onNext(match { it.exceptionOrNull() is ModuleNotEnabledException })
        }
    }

    @Test
    fun methods_Report_Tealium_Shutdown_When_No_ModuleManager() {
        onModuleManager.onNext(null)

        deepLinkHandlerWrapper.handle(uri).subscribe(observer)
        deepLinkHandlerWrapper.handle(uri, uri).subscribe(observer)

        verify(exactly = 2) {
            observer.onNext(match { it.exceptionOrNull() is Tealium.TealiumShutdownException })
        }
    }

    @Test
    fun handle_Returns_Success_When_Module_Enabled() {
        deepLinkHandlerWrapper.handle(uri)
            .subscribe(observer)

        verify {
            observer.onNext(match { it.isSuccess })
        }
    }

    @Test
    fun handle_Returns_Failure_When_Module_Fails() {
        // deliberately not a type the proxy can produce, so the failure can only be the module's
        val failure = IllegalStateException("from the module")
        every { deepLinkModule.handle(any(), any(), any()) } answers {
            thirdArg<Callback<TealiumResult<Unit>>>().failure(failure)
        }

        deepLinkHandlerWrapper.handle(uri)
            .subscribe(observer)

        verify {
            observer.onNext(match { it.exceptionOrNull() === failure })
        }
    }

    @Test
    fun handle_Waits_For_The_Module_To_Complete_The_Callback() {
        val moduleCallback = slot<Callback<TealiumResult<Unit>>>()
        every { deepLinkModule.handle(any(), any(), capture(moduleCallback)) } just Runs

        deepLinkHandlerWrapper.handle(uri)
            .subscribe(observer)

        verify(exactly = 0) {
            observer.onNext(any())
        }

        moduleCallback.captured.success(Unit)

        verify(exactly = 1) {
            observer.onNext(match { it.isSuccess })
        }
    }

    @Test
    fun handle_Calls_Module_Implementation_When_Module_Enabled() {
        deepLinkHandlerWrapper.handle(uri)

        verify {
            deepLinkModule.handle(uri, null, any())
        }
    }

    @Test
    fun handle_With_Referrer_Returns_Success_When_Module_Enabled() {
        deepLinkHandlerWrapper.handle(uri, uri)
            .subscribe(observer)

        verify {
            observer.onNext(match { it.isSuccess })
        }
    }

    @Test
    fun handle_With_Referrer_Calls_Module_Implementation_When_Module_Enabled() {
        deepLinkHandlerWrapper.handle(uri, uri)

        verify {
            deepLinkModule.handle(uri, uri, any())
        }
    }

    @Test
    fun init_Delegates_Proxy_To_Tealium() {
        val proxy = mockk<ModuleProxy<DeepLinkModule>>()
        val tealium = mockk<Tealium>()
        every { tealium.createModuleProxy(DeepLinkModule::class.java) } returns proxy

        DeepLinkHandlerWrapper(tealium)

        verify {
            tealium.createModuleProxy(DeepLinkModule::class.java)
        }
    }
}
