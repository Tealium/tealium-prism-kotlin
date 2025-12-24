package com.tealium.prism.lifecycle

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.api.pubsub.onFailure
import com.tealium.prism.core.api.pubsub.onSuccess
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.modules.ModuleProxyImpl
import com.tealium.prism.lifecycle.internal.LifecycleModule
import com.tealium.prism.lifecycle.internal.LifecycleWrapper
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class LifecycleWrapperTests {

    @RelaxedMockK
    private lateinit var lifecycleModule: LifecycleModule

    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var moduleManager: ModuleManager
    private lateinit var onModuleManager: Subject<ModuleManager?>
    private lateinit var proxy: ModuleProxy<LifecycleModule>

    private lateinit var lifecycleWrapper: LifecycleWrapper
    private val scheduler = Scheduler.SYNCHRONOUS

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { lifecycleModule.id } returns LifecycleModule.Factory().moduleType
        modules = Observables.stateSubject(listOf(lifecycleModule))
        moduleManager = ModuleManagerImpl(scheduler, modules)
        onModuleManager = Observables.replaySubject(1)
        onModuleManager.onNext(moduleManager)

        proxy = ModuleProxyImpl(LifecycleModule::class.java, onModuleManager, scheduler)
        lifecycleWrapper = LifecycleWrapper(proxy)
    }

    @Test
    fun methods_Report_ModuleNotEnabled() {
        modules.onNext(emptyList())

        val completion = mockk<Callback<Exception>>(relaxed = true)
        lifecycleWrapper.launch().onFailure(completion)
        lifecycleWrapper.wake().onFailure(completion)
        lifecycleWrapper.sleep().onFailure(completion)

        verify(exactly = 3) { completion.onComplete(match { it is ModuleNotEnabledException }) }
    }

    @Test
    fun methods_Report_TealiumShutdown_WhenNoModuleManager() {
        onModuleManager.onNext(null)

        val completion = mockk<Callback<Exception>>(relaxed = true)
        lifecycleWrapper.launch().onFailure(completion)
        lifecycleWrapper.wake().onFailure(completion)
        lifecycleWrapper.sleep().onFailure(completion)

        verify(exactly = 3) { completion.onComplete(match { it is Tealium.TealiumShutdownException }) }
    }

    @Test
    fun launch_ReturnsSuccess_WhenModuleEnabled() {
        val completion = mockk<Callback<Unit>>(relaxed = true)
        lifecycleWrapper.launch().onSuccess(completion)

        verify(exactly = 1) { completion.onComplete(Unit) }
    }

    @Test
    fun wake_ReturnsSuccess_WhenModuleEnabled() {
        val completion = mockk<Callback<Unit>>(relaxed = true)
        lifecycleWrapper.wake().onSuccess(completion)

        verify(exactly = 1) { completion.onComplete(Unit) }
    }

    @Test
    fun sleep_Returns_Success_When_Module_Enabled() {
        val completion = mockk<Callback<Unit>>(relaxed = true)
        lifecycleWrapper.sleep().onSuccess(completion)

        verify(exactly = 1) { completion.onComplete(Unit) }
    }

    @Test
    fun launch_CallModuleImplementation_WhenModuleEnabled() {
        lifecycleWrapper.launch()

        verify { lifecycleModule.launch(any()) }
    }

    @Test
    fun wake_CallModuleImplementation_WhenModuleEnabled() {
        lifecycleWrapper.wake()

        verify { lifecycleModule.wake(any()) }
    }

    @Test
    fun sleep_CallModuleImplementation_WhenModuleEnabled() {
        lifecycleWrapper.sleep()

        verify { lifecycleModule.sleep(any()) }
    }
}