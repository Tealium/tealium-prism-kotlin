package com.tealium.lifecycle

import com.tealium.core.api.Tealium
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumException
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.ModuleNotEnabledException
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.core.internal.modules.ModuleProxyImpl
import com.tealium.lifecycle.internal.LifecycleModule
import com.tealium.lifecycle.internal.LifecycleWrapper
import com.tealium.tests.common.SynchronousScheduler
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
    private val scheduler = SynchronousScheduler()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { lifecycleModule.id } returns LifecycleModule.Factory().id
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

        val completion = mockk<TealiumCallback<TealiumException?>>(relaxed = true)
        lifecycleWrapper.launch(completion = completion)
        lifecycleWrapper.wake(completion = completion)
        lifecycleWrapper.sleep(completion = completion)

        verify(exactly = 3) { completion.onComplete(match { it is ModuleNotEnabledException }) }
    }

    @Test
    fun methods_Report_TealiumShutdown_WhenNoModuleManager() {
        onModuleManager.onNext(null)

        val completion = mockk<TealiumCallback<TealiumException?>>(relaxed = true)
        lifecycleWrapper.launch(completion = completion)
        lifecycleWrapper.wake(completion = completion)
        lifecycleWrapper.sleep(completion = completion)

        verify(exactly = 3) { completion.onComplete(match { it is Tealium.TealiumShutdownException }) }
    }

    @Test
    fun launch_ReturnsSuccess_WhenModuleEnabled() {
        val completion = mockk<TealiumCallback<TealiumException?>>(relaxed = true)
        lifecycleWrapper.launch(completion = completion)

        verify(exactly = 1) { completion.onComplete(null) }
    }

    @Test
    fun wake_ReturnsSuccess_WhenModuleEnabled() {
        val completion = mockk<TealiumCallback<TealiumException?>>(relaxed = true)
        lifecycleWrapper.wake(completion = completion)

        verify(exactly = 1) { completion.onComplete(null) }
    }

    @Test
    fun sleep_Returns_Success_When_Module_Enabled() {
        val completion = mockk<TealiumCallback<TealiumException?>>(relaxed = true)
        lifecycleWrapper.sleep(completion = completion)

        verify(exactly = 1) { completion.onComplete(null) }
    }

    @Test
    fun launch_CallModuleImplementation_WhenModuleEnabled() {
        val completion = mockk<TealiumCallback<TealiumException?>>(relaxed = true)
        lifecycleWrapper.launch(completion = completion)

        verify { lifecycleModule.launch(any()) }
    }

    @Test
    fun wake_CallModuleImplementation_WhenModuleEnabled() {
        val completion = mockk<TealiumCallback<TealiumException?>>(relaxed = true)
        lifecycleWrapper.wake(completion = completion)

        verify { lifecycleModule.wake(any()) }
    }

    @Test
    fun sleep_CallModuleImplementation_WhenModuleEnabled() {
        val completion = mockk<TealiumCallback<TealiumException?>>(relaxed = true)
        lifecycleWrapper.sleep(completion = completion)

        verify { lifecycleModule.sleep(any()) }
    }
}