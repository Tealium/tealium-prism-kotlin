package com.tealium.core.internal.modules.deeplink

import android.net.Uri
import com.tealium.core.api.Tealium
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

class DeepLinkHandlerWrapperTests {

    @RelaxedMockK
    private lateinit var deepLinkModule: DeepLinkHandlerModule

    @RelaxedMockK
    private lateinit var observer: Observer<TealiumResult<Unit>>

    @RelaxedMockK
    private lateinit var uri: Uri

    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var moduleManager: ModuleManager
    private lateinit var onModuleManager: Subject<ModuleManager?>
    private lateinit var proxy: ModuleProxy<DeepLinkHandlerModule>

    private lateinit var deepLinkHandlerWrapper: DeepLinkHandlerWrapper
    private val scheduler = SynchronousScheduler()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { deepLinkModule.id } returns DeepLinkHandlerModule.MODULE_ID
        modules = Observables.stateSubject(listOf(deepLinkModule))
        moduleManager = ModuleManagerImpl(scheduler, modules)
        onModuleManager = Observables.replaySubject(1)
        onModuleManager.onNext(moduleManager)

        proxy = ModuleProxyImpl(DeepLinkHandlerModule::class.java, onModuleManager, scheduler)
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
    fun handle_Calls_Module_Implementation_When_Module_Enabled() {
        deepLinkHandlerWrapper.handle(uri)

        verify {
            deepLinkModule.handle(uri, null)
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
            deepLinkModule.handle(uri, uri)
        }
    }

    @Test
    fun init_Delegates_Proxy_To_Tealium() {
        val proxy = mockk<ModuleProxy<DeepLinkHandlerModule>>()
        val tealium = mockk<Tealium>()
        every { tealium.createModuleProxy(DeepLinkHandlerModule::class.java) } returns proxy

        DeepLinkHandlerWrapper(tealium)

        verify {
            tealium.createModuleProxy(DeepLinkHandlerModule::class.java)
        }
    }
}
