package com.tealium.prism.core.internal.modules

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumCallback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.settings.SdkSettings
import com.tealium.tests.common.SynchronousScheduler
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.TestModuleFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.Test

class ModuleProxyImplTests {

    private val dispatcher = TestDispatcher("dispatcher")
    private val module = ModuleWithObservable(Observables.publishSubject(), "module")
    private val moduleFactories = listOf(
        TestModuleFactory.forModule(dispatcher),
        TestModuleFactory.forModule(module)
    )
    private lateinit var context: TealiumContext
    private lateinit var moduleManager: ModuleManagerImpl
    private lateinit var moduleManagerSubject: Subject<ModuleManager?>
    private lateinit var dispatcherProxy: ModuleProxy<TestDispatcher>

    @Before
    fun setUp() {
        context = mockk<TealiumContext>()
        every { context.logger } returns SystemLogger
        moduleManager = ModuleManagerImpl(SynchronousScheduler())
        moduleFactories.forEach(moduleManager::addModuleFactory)
        moduleManager.updateModuleSettings(context, SdkSettings()) // all enabled
        moduleManagerSubject = Observables.replaySubject(1)

        dispatcherProxy = createProxy(TestDispatcher::class.java)
    }

    @Test
    fun getModule_Queues_When_No_ModuleManager_Emitted() {
        val listener = mockk<TealiumCallback<TestDispatcher?>>(relaxed = true)

        dispatcherProxy.getModule(listener)

        verify(inverse = true) {
            listener.onComplete(any())
        }
    }

    @Test
    fun getModule_Returns_Module_When_ModuleManager_Emitted_And_Module_Available() {
        val listener = mockk<TealiumCallback<TestDispatcher?>>(relaxed = true)

        dispatcherProxy.getModule(listener)
        moduleManagerSubject.onNext(moduleManager)
        dispatcherProxy.getModule(listener)

        verify {
            listener.onComplete(dispatcher)
        }
    }

    @Test
    fun getModule_Returns_Null_When_ModuleManager_Emitted_And_Module_Unavailable() {
        val listener = mockk<TealiumCallback<TestDispatcher?>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(dispatcher.id))

        moduleManagerSubject.onNext(moduleManager)
        dispatcherProxy.getModule(listener)

        verify {
            listener.onComplete(null)
        }
    }

    @Test
    fun observeModule_Emits_Nothing_When_The_ModuleManager_Not_Emitted() {
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)

        dispatcherProxy.observeModule().subscribe(observer)

        verify(inverse = true) {
            observer.onNext(any())
        }
    }

    @Test
    fun observeModule_Emits_Module_When_The_Module_Is_Available() {
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)

        moduleManagerSubject.onNext(moduleManager)
        dispatcherProxy.observeModule().subscribe(observer)

        verify {
            observer.onNext(dispatcher)
        }
    }

    @Test
    fun observeModule_Emits_Null_When_The_Module_Is_Unavailable() {
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(dispatcher.id))

        moduleManagerSubject.onNext(moduleManager)
        dispatcherProxy.observeModule().subscribe(observer)

        verify {
            observer.onNext(null)
        }
    }

    @Test
    fun observeModule_Emits_Updated_Module_When_Enabled_And_Disabled() {
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(dispatcher.id))

        moduleManagerSubject.onNext(moduleManager)
        dispatcherProxy.observeModule().subscribe(observer)
        moduleManager.updateModuleSettings(context, enableModuleSettings(dispatcher.id))
        moduleManager.updateModuleSettings(context, disableModuleSettings(dispatcher.id))
        moduleManager.updateModuleSettings(context, enableModuleSettings(dispatcher.id))

        verifyOrder {
            observer.onNext(null)
            observer.onNext(dispatcher)
            observer.onNext(null)
            observer.onNext(dispatcher)
        }
    }

    @Test
    fun observeModule_With_Transform_Emits_Nothing_When_The_ModuleManager_Not_Emitted() {
        val observer = mockk<Observer<Int>>(relaxed = true)

        val proxy = createProxy(ModuleWithObservable::class.java)
        proxy.observeModule { it.subject }.subscribe(observer)

        verify(inverse = true) {
            observer.onNext(any())
        }
    }

    @Test
    fun observeModule_Emits_Transformed_Module_When_The_Module_Is_Available() {
        val observer = mockk<Observer<Int>>(relaxed = true)

        moduleManagerSubject.onNext(moduleManager)
        val proxy = createProxy(ModuleWithObservable::class.java)
        proxy.observeModule { it.subject }.subscribe(observer)
        module.subject.onNext(1)
        module.subject.onNext(2)

        verify {
            observer.onNext(1)
            observer.onNext(2)
        }
    }

    @Test
    fun observeModule_With_Transform_Emits_Nothing_When_The_Module_Is_Unavailable() {
        val observer = mockk<Observer<Int>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(module.id))

        moduleManagerSubject.onNext(moduleManager)
        val proxy = createProxy(ModuleWithObservable::class.java)
        proxy.observeModule { it.subject }.subscribe(observer)
        module.subject.onNext(1)

        verify(inverse = true) {
            observer.onNext(any())
        }
    }

    @Test
    fun observeModule_Emits_Updated_Transformed_Module_When_Enabled_And_Disabled() {
        val observer = mockk<Observer<Int>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(module.id))

        moduleManagerSubject.onNext(moduleManager)
        val proxy = createProxy(ModuleWithObservable::class.java)
        proxy.observeModule { it.subject }.subscribe(observer)
        module.subject.onNext(1)
        moduleManager.updateModuleSettings(context, enableModuleSettings(module.id))
        module.subject.onNext(2)
        moduleManager.updateModuleSettings(context, disableModuleSettings(module.id))
        module.subject.onNext(3)
        moduleManager.updateModuleSettings(context, enableModuleSettings(module.id))
        module.subject.onNext(4)

        verifyOrder {
            observer.onNext(2)
            observer.onNext(4)
        }
        verify(inverse = true) {
            observer.onNext(1)
            observer.onNext(3)
        }
    }

    @Test
    fun executeModuleTask_Returns_Single_Failure_When_Tealium_Shutdown() {
        val observer = mockk<Observer<TealiumResult<String>>>(relaxed = true)
        moduleManagerSubject.onNext(null)

        dispatcherProxy.executeModuleTask(TestDispatcher::id)
            .subscribe(observer)
        dispatcherProxy.executeAsyncModuleTask(callbackDispatcherTask)
            .subscribe(observer)

        verify(exactly = 2) {
            observer.onNext(match {
                it.isFailure
                        && it.exceptionOrNull() is Tealium.TealiumShutdownException
            })
        }
    }

    @Test
    fun executeModuleTask_Returns_Single_Failure_When_Module_Unavailable() {
        val observer = mockk<Observer<TealiumResult<String>>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(dispatcher.id))
        moduleManagerSubject.onNext(moduleManager)

        dispatcherProxy.executeModuleTask(TestDispatcher::id)
            .subscribe(observer)
        dispatcherProxy.executeAsyncModuleTask(callbackDispatcherTask)
            .subscribe(observer)

        verify(exactly = 2) {
            observer.onNext(match {
                it.isFailure
                        && it.exceptionOrNull() is ModuleNotEnabledException
            })
        }
    }

    @Test
    fun executeModuleTask_Returns_Single_Failure_When_Task_Throws() {
        val observer = mockk<Observer<TealiumResult<String>>>(relaxed = true)
        val exception = IllegalStateException()
        moduleManagerSubject.onNext(moduleManager)

        dispatcherProxy.executeModuleTask<String> { _ -> throw exception }
            .subscribe(observer)
        dispatcherProxy.executeAsyncModuleTask<String> { _, _ -> throw exception }
            .subscribe(observer)

        verify(exactly = 2) {
            observer.onNext(match {
                it.isFailure
                        && it.exceptionOrNull() == exception
            })
        }
    }

    @Test
    fun executeModuleTask_Returns_Single_Success_When_Module_Enabled() {
        val observer = mockk<Observer<TealiumResult<String>>>(relaxed = true)
        moduleManagerSubject.onNext(moduleManager)

        dispatcherProxy.executeModuleTask(TestDispatcher::id)
            .subscribe(observer)
        dispatcherProxy.executeAsyncModuleTask(callbackDispatcherTask)
            .subscribe(observer)

        verify(exactly = 2) {
            observer.onNext(match {
                it.isSuccess
                        && it.getOrThrow() == dispatcher.id
            })
        }
    }

    @Test
    fun executeModuleTask_Returns_Single_That_Replays_Result() {
        val observer = mockk<Observer<TealiumResult<String>>>(relaxed = true)
        moduleManagerSubject.onNext(moduleManager)

        listOf(
            dispatcherProxy.executeModuleTask(TestDispatcher::id),
            dispatcherProxy.executeAsyncModuleTask(callbackDispatcherTask)
        ).forEach {
            it.subscribe(observer)
            it.subscribe(observer)
        }

        verify(exactly = 4) {
            observer.onNext(match {
                it.isSuccess
                        && it.getOrThrow() == dispatcher.id
            })
        }
    }

    @Test
    fun executeModuleTask_Eagerly_Fetches_Value_Regardless_Of_Subscription_Order() {
        val observer1 = mockk<Observer<TealiumResult<Int>>>(relaxed = true)
        val observer2 = mockk<Observer<TealiumResult<Int>>>(relaxed = true)
        moduleManagerSubject.onNext(moduleManager)
        val proxy = createProxy(ModuleWithObservable::class.java)

        val result1 = proxy.executeModuleTask { module ->
            module.counter.incrementAndGet()
        }
        val result2 = proxy.executeModuleTask { module ->
            module.counter.incrementAndGet()
        }
        result2.subscribe(observer2)
        result1.subscribe(observer1)

        verify {
            observer1.onNext(match { it.getOrThrow() == 1 })
            observer2.onNext(match { it.getOrThrow() == 2 })
        }
    }

    @Test
    fun executeAsyncModuleTask_With_Callback_Eagerly_Fetches_Value_Regardless_Of_Subscription_Order() {
        val observer1 = mockk<Observer<TealiumResult<Int>>>(relaxed = true)
        val observer2 = mockk<Observer<TealiumResult<Int>>>(relaxed = true)
        moduleManagerSubject.onNext(moduleManager)
        val proxy = createProxy(ModuleWithObservable::class.java)

        val result1 = proxy.executeAsyncModuleTask { module, callback ->
            callback.onComplete(TealiumResult.success(module.counter.incrementAndGet()))
        }
        val result2 = proxy.executeAsyncModuleTask { module, callback ->
            callback.onComplete(TealiumResult.success(module.counter.incrementAndGet()))
        }
        result2.subscribe(observer2)
        result1.subscribe(observer1)


        verify {
            observer1.onNext(match { it.getOrThrow() == 1 })
            observer2.onNext(match { it.getOrThrow() == 2 })
        }
    }

    private val callbackDispatcherTask: (TestDispatcher, TealiumCallback<TealiumResult<String>>) -> Unit =
        { dispatcher, callback ->
            callback.onComplete(TealiumResult.success(dispatcher.id))
        }

    private fun <T : Module> createProxy(
        clazz: Class<T>,
        modules: Observable<ModuleManager?> = moduleManagerSubject,
        scheduler: Scheduler = SynchronousScheduler(),
    ): ModuleProxy<T> =
        ModuleProxyImpl(clazz, modules, scheduler)
}