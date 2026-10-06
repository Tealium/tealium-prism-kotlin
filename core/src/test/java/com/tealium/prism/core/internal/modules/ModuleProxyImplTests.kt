package com.tealium.prism.core.internal.modules

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.core.internal.settings.SdkSettings
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.TestModuleFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ModuleProxyImplTests {

    private val dispatcher = TestDispatcher("dispatcher")
    private val module = ModuleWithObservable(Observables.publishSubject(), "module")
    private val moduleFactories = listOf(
        TestModuleFactory.forModule(dispatcher),
        TestModuleFactory.forModule(module)
    )
    private val defaultSettings = moduleFactories.map { moduleFactory ->
        ModuleSettings(moduleFactory.moduleType)
    }.associateBy(ModuleSettings::moduleType)
    private lateinit var context: TealiumContext
    private lateinit var moduleManager: ModuleManagerImpl
    private lateinit var moduleManagerSubject: Subject<TealiumResult<ModuleManager>>
    private lateinit var dispatcherProxy: ModuleProxy<TestDispatcher>

    @Before
    fun setUp() {
        context = mockk<TealiumContext>()
        every { context.logger } returns SystemLogger
        moduleManager = ModuleManagerImpl(Scheduler.SYNCHRONOUS)
        moduleFactories.forEach(moduleManager::addModuleFactory)
        moduleManager.updateModuleSettings(context, SdkSettings(modules = defaultSettings)) // all enabled
        moduleManagerSubject = Observables.replaySubject(1)

        dispatcherProxy = createProxy(TestDispatcher::class.java)
    }

    @Test
    fun getModule_Queues_When_No_ModuleManager_Emitted() {
        val listener = mockk<Callback<TestDispatcher?>>(relaxed = true)

        dispatcherProxy.getModule(listener)

        verify(inverse = true) {
            listener.onComplete(any())
        }
    }

    @Test
    fun getModule_Returns_Module_When_ModuleManager_Emitted_And_Module_Available() {
        val listener = mockk<Callback<TestDispatcher?>>(relaxed = true)

        dispatcherProxy.getModule(listener)
        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))
        dispatcherProxy.getModule(listener)

        verify {
            listener.onComplete(dispatcher)
        }
    }

    @Test
    fun getModule_Returns_Null_When_ModuleManager_Emitted_And_Module_Unavailable() {
        val listener = mockk<Callback<TestDispatcher?>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(dispatcher.id))

        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))
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

        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))
        dispatcherProxy.observeModule().subscribe(observer)

        verify {
            observer.onNext(dispatcher)
        }
    }

    @Test
    fun observeModule_Emits_Null_When_The_Module_Is_Unavailable() {
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(dispatcher.id))

        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))
        dispatcherProxy.observeModule().subscribe(observer)

        verify {
            observer.onNext(null)
        }
    }

    @Test
    fun observeModule_Emits_Updated_Module_When_Enabled_And_Disabled() {
        val observer = mockk<Observer<TestDispatcher?>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(dispatcher.id))

        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))
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

        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))
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

        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))
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

        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))
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
    fun observeModules_Transforms_Observables_From_All_Module_Instances() {
        val observer = mockk<Observer<Int>>(relaxed = true)
        val module1 = moduleWithObservable(1, "module1")
        val module2 = moduleWithObservable(2, "module2")
        val mockManager = mockModuleManager(listOf(module1, module2))

        val proxy = createProxy(ModuleWithObservable::class.java, Observables.just(TealiumResult.success(mockManager)))

        proxy.observeModules(::combineAndSum)
            .subscribe(observer)

        verify {
            observer.onNext(3) // 1 + 2
        }
    }

    @Test
    fun observeModules_Emits_Updates_From_All_Modules() {
        val observer = mockk<Observer<Int>>(relaxed = true)
        val module1 = moduleWithObservable(1, "module1")
        val module2 = moduleWithObservable(2, "module2")
        val mockManager = mockModuleManager(listOf(module1, module2))

        val proxy = createProxy(ModuleWithObservable::class.java, Observables.just(TealiumResult.success(mockManager)))

        proxy.observeModules(::combineAndSum)
            .subscribe(observer)

        module1.subject.onNext(2)
        module1.subject.onNext(3)

        verifyOrder {
            observer.onNext(3) // 1 + 2
            observer.onNext(4) // 2 + 2
            observer.onNext(5) // 2 + 3
        }
    }

    @Test
    fun observeModules_Removes_Modules_That_Have_Been_Removed() {
        val observer = mockk<Observer<Int>>(relaxed = true)
        val module1 = moduleWithObservable(1, "module1")
        val module2 = moduleWithObservable(2, "module2")
        val modules = Observables.stateSubject<List<Module>>(listOf(module1, module2))
        val mockManager = mockModuleManager(modules)

        val proxy = createProxy(ModuleWithObservable::class.java, Observables.just(TealiumResult.success(mockManager)))

        proxy.observeModules(::combineAndSum)
            .subscribe(observer)

        modules.onNext(listOf(module1))

        verifyOrder {
            observer.onNext(3) // 1 + 2
            observer.onNext(1) // 1
        }
    }

    @Test
    fun observeModules_Does_Not_Emit_From_Removed_Modules() {
        val observer = mockk<Observer<Int>>(relaxed = true)
        val module1 = moduleWithObservable(1, "module1")
        val module2 = moduleWithObservable(2, "module2")
        val modules = Observables.stateSubject<List<Module>>(listOf(module1, module2))
        val mockManager = mockModuleManager(modules)

        val proxy = createProxy(ModuleWithObservable::class.java, Observables.just(TealiumResult.success(mockManager)))

        proxy.observeModules(::combineAndSum)
            .subscribe(observer)

        modules.onNext(listOf(module1)) // module2 removed
        module2.subject.onNext(10)

        verify(inverse = true) {
            observer.onNext(11) // 1 + 10
        }
    }

    @Test
    fun observeModules_Includes_New_Modules() {
        val observer = mockk<Observer<Int>>(relaxed = true)
        val module1 = moduleWithObservable(1, "module1")
        val module2 = moduleWithObservable(2, "module2")
        val module3 = moduleWithObservable(3, "module3")
        val modules = Observables.stateSubject<List<Module>>(listOf(module1, module2))
        val mockManager = mockModuleManager(modules)

        val proxy = createProxy(ModuleWithObservable::class.java, Observables.just(TealiumResult.success(mockManager)))

        proxy.observeModules(::combineAndSum)
            .subscribe(observer)

        modules.onNext(listOf(module1, module2, module3))

        verifyOrder {
            observer.onNext(3) // 1 + 2
            observer.onNext(6) // 1 + 2 + 3
        }
    }

    @Test
    fun observeModules_Does_Not_Emit_Again_If_Module_Instances_Have_Not_Changed() {
        val observer = mockk<Observer<Int>>(relaxed = true)
        val module1 = moduleWithObservable(1, "module1")
        val module2 = moduleWithObservable(2, "module2")
        val modules = Observables.stateSubject<List<Module>>(listOf(module1, module2))
        val mockManager = mockModuleManager(modules)

        val proxy = createProxy(ModuleWithObservable::class.java, Observables.just(TealiumResult.success(mockManager)))

        proxy.observeModules(::combineAndSum)
            .subscribe(observer)

        modules.onNext(listOf(module1, module2))

        verify(exactly = 1) {
            observer.onNext(3) // 1 + 2
        }
    }

    @Test
    fun observeModules_Does_Emit_Again_If_Module_Instances_Have_Changed() {
        val observer = mockk<Observer<Int>>(relaxed = true)
        val module1 = moduleWithObservable(1, "module1")
        val module2 = moduleWithObservable(2, "module2")
        val module3 = moduleWithObservable(3, "module3")
        val modules = Observables.stateSubject<List<Module>>(listOf(module1, module2))
        val mockManager = mockModuleManager(modules)

        val proxy = createProxy(ModuleWithObservable::class.java, Observables.just(TealiumResult.success(mockManager)))

        proxy.observeModules(::combineAndSum)
            .subscribe(observer)

        modules.onNext(listOf(module1, module3)) // remove 2, add 3

        verify(exactly = 1) {
            observer.onNext(3) // 1 + 2
            observer.onNext(4) // 1 + 3
        }
    }

    @Test
    fun observeModules_Does_Not_Emit_When_ModuleManager_Result_Becomes_Failure() {
        val observer = mockk<Observer<Int>>(relaxed = true)
        val module1 = moduleWithObservable(1, "module1")
        val modules = Observables.stateSubject<List<Module>>(listOf(module1))
        val mockManager = Observables.stateSubject<TealiumResult<ModuleManager>>(
            TealiumResult.success(mockModuleManager(modules))
        )

        val proxy = createProxy(ModuleWithObservable::class.java, mockManager)

        proxy.observeModules(::combineAndSum)
            .subscribe(observer)

        mockManager.onNext(TealiumResult.failure(shutdownException()))
        module1.subject.onNext(2)

        verify {
            observer.onNext(1)
        }
        verify(inverse = true) {
            observer.onNext(2)
        }
    }

    @Test
    fun executeModuleTask_Returns_Single_Failure_When_Tealium_Shutdown() {
        val observer = mockk<Observer<TealiumResult<String>>>(relaxed = true)
        moduleManagerSubject.onNext(TealiumResult.failure(shutdownException()))

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
        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))

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
        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))

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
        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))

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
        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))

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
        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))
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
        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))
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

    @Test
    fun executeAsyncModuleTask_Does_Not_Fail_With_ModuleNotEnabled_When_Module_Disabled_While_Task_Running() {
        val observer = mockk<Observer<TealiumResult<String>>>(relaxed = true)
        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))
        var capturedCallback: Callback<TealiumResult<String>>? = null

        dispatcherProxy.executeAsyncModuleTask<String> { _, callback -> capturedCallback = callback }
            .subscribe(observer)
        moduleManager.updateModuleSettings(context, disableModuleSettings(dispatcher.id))

        assertNotNull(capturedCallback)
        verify(inverse = true) { observer.onNext(any()) }

        capturedCallback!!.onComplete(TealiumResult.success("done"))

        verify(exactly = 1) { observer.onNext(any()) }
        verify { observer.onNext(match { it.getOrNull() == "done" }) }
    }

    @Test
    fun observeModuleResult_Emits_Success_With_Transformed_Value_When_Module_Enabled() {
        val observer = mockk<Observer<TealiumResult<Int>>>(relaxed = true)

        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))
        val proxy = createProxy(ModuleWithObservable::class.java)
        proxy.observeModuleResult { it.subject }.subscribe(observer)
        module.subject.onNext(1)
        module.subject.onNext(2)

        verifyOrder {
            observer.onNext(match { it.getOrNull() == 1 })
            observer.onNext(match { it.getOrNull() == 2 })
        }
        verify(exactly = 2) { observer.onNext(any()) }
    }

    @Test
    fun observeModuleResult_Emits_Nothing_Until_Modules_Ready() {
        val observer = mockk<Observer<TealiumResult<Int>>>(relaxed = true)
        val notReadyManager = ModuleManagerImpl(Scheduler.SYNCHRONOUS)
        moduleFactories.forEach(notReadyManager::addModuleFactory)

        val proxy = createProxy(
            ModuleWithObservable::class.java,
            Observables.just(TealiumResult.success(notReadyManager))
        )
        proxy.observeModuleResult { it.subject }.subscribe(observer)

        verify(exactly = 0) {
            observer.onNext(any())
            observer.onComplete()
        }

        notReadyManager.updateModuleSettings(context, SdkSettings(modules = defaultSettings))
        module.subject.onNext(1)

        verify(exactly = 1) { observer.onNext(any()) }
        verify { observer.onNext(match { it.getOrNull() == 1 }) }
    }

    @Test
    fun observeModuleResult_Emits_Success_Then_ModuleNotEnabled_Then_Success_When_Module_Disabled_And_Re_Enabled() {
        val observer = mockk<Observer<TealiumResult<Int>>>(relaxed = true)
        val moduleSubject = Observables.stateSubject(1)
        val statefulModule = ModuleWithObservable(moduleSubject, "stateful")
        val manager = ModuleManagerImpl(Scheduler.SYNCHRONOUS)
        manager.addModuleFactory(TestModuleFactory.forModule(statefulModule))
        manager.updateModuleSettings(context, enableModuleSettings(statefulModule.id))

        val proxy = createProxy(
            ModuleWithObservable::class.java,
            Observables.just(TealiumResult.success(manager))
        )
        proxy.observeModuleResult { it.subject }.subscribe(observer)
        manager.updateModuleSettings(context, disableModuleSettings(statefulModule.id))
        manager.updateModuleSettings(context, enableModuleSettings(statefulModule.id))

        verifyOrder {
            observer.onNext(match { it.getOrNull() == 1 })
            observer.onNext(match { it.exceptionOrNull() is ModuleNotEnabledException })
            observer.onNext(match { it.getOrNull() == 1 })
        }
        verify(exactly = 3) { observer.onNext(any()) }
    }

    @Test
    fun observeModuleResult_Emits_Failure_Then_Completes_When_Tealium_Failed_To_Initialize() {
        val observer = mockk<Observer<TealiumResult<Int>>>(relaxed = true)
        val initException = TealiumException("Failed to initialize")

        val proxy = createProxy(ModuleWithObservable::class.java)
        proxy.observeModuleResult { it.subject }.subscribe(observer)
        moduleManagerSubject.onNext(TealiumResult.failure(initException))
        moduleManagerSubject.onComplete()

        verifyOrder {
            observer.onNext(match { it.exceptionOrNull() === initException })
            observer.onComplete()
        }
        verify(exactly = 1) { observer.onNext(any()) }
    }

    @Test
    fun observeModuleResult_Emits_Failure_Then_Completes_When_Tealium_Shutdown() {
        val observer = mockk<Observer<TealiumResult<Int>>>(relaxed = true)
        val shutdownException = shutdownException()

        val proxy = createProxy(ModuleWithObservable::class.java)
        proxy.observeModuleResult { it.subject }.subscribe(observer)
        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))
        module.subject.onNext(1)
        moduleManagerSubject.onNext(TealiumResult.failure(shutdownException))
        moduleManagerSubject.onComplete()
        module.subject.onNext(2)

        verifyOrder {
            observer.onNext(match { it.getOrNull() == 1 })
            observer.onNext(match { it.exceptionOrNull() === shutdownException })
            observer.onComplete()
        }
        verify(exactly = 2) { observer.onNext(any()) }
    }

    @Test
    fun executeModuleTask_Returns_Failure_Then_Success_When_Module_Disabled_Then_Re_Enabled() {
        val observer = mockk<Observer<TealiumResult<String>>>(relaxed = true)
        moduleManager.updateModuleSettings(context, disableModuleSettings(dispatcher.id))
        moduleManagerSubject.onNext(TealiumResult.success(moduleManager))

        dispatcherProxy.executeModuleTask(TestDispatcher::id)
            .subscribe(observer)
        moduleManager.updateModuleSettings(context, enableModuleSettings(dispatcher.id))
        dispatcherProxy.executeModuleTask(TestDispatcher::id)
            .subscribe(observer)

        verifyOrder {
            observer.onNext(match { it.exceptionOrNull() is ModuleNotEnabledException })
            observer.onNext(match { it.getOrNull() == dispatcher.id })
        }
    }

    @Test
    fun executeModuleTask_Returns_Initialization_Exception_When_Tealium_Failed_To_Initialize() {
        val observer = mockk<Observer<TealiumResult<String>>>(relaxed = true)
        val initException = TealiumException("Failed to initialize")
        moduleManagerSubject.onNext(TealiumResult.failure(initException))

        dispatcherProxy.executeModuleTask(TestDispatcher::id)
            .subscribe(observer)
        dispatcherProxy.executeAsyncModuleTask(callbackDispatcherTask)
            .subscribe(observer)

        verify(exactly = 2) {
            observer.onNext(match { it.exceptionOrNull() === initException })
        }
    }

    private val callbackDispatcherTask: (TestDispatcher, Callback<TealiumResult<String>>) -> Unit =
        { dispatcher, callback ->
            callback.onComplete(TealiumResult.success(dispatcher.id))
        }

    private fun <T : Module> createProxy(
        clazz: Class<T>,
        modules: Observable<TealiumResult<ModuleManager>> = moduleManagerSubject,
        scheduler: Scheduler = Scheduler.SYNCHRONOUS,
    ): ModuleProxy<T> =
        ModuleProxyImpl(clazz, modules, scheduler)

    private fun shutdownException() =
        Tealium.TealiumShutdownException("Tealium Instance has been shutdown.")

    private fun moduleWithObservable(initialValue: Int, id: String) : ModuleWithObservable {
        val moduleSubject = Observables.stateSubject(initialValue)
        val module = ModuleWithObservable(moduleSubject, id)
        return module
    }

    /**
     * Returns a [ModuleManager] preloaded with the given [modules]
     */
    private fun mockModuleManager(modules: List<Module>): ModuleManager =
        mockModuleManager(Observables.stateSubject(modules))

    /**
     * Returns a [ModuleManager] backed by the given [modules], which can be updated afterwards.
     * Preloaded modules count as ready, so the lookups emit without waiting for a settings pass.
     */
    private fun mockModuleManager(modules: StateSubject<List<Module>>): ModuleManager =
        ModuleManagerImpl(Scheduler.SYNCHRONOUS, modules)

    /**
     * Utility that combines all [ModuleWithObservable.subject]s and sums their values.
     */
    private fun combineAndSum(modules: List<ModuleWithObservable>): Observable<Int> =
        Observables.combine(modules.map { it.subject }, Iterable<Int>::sum)
}