package com.tealium.prism.core.internal

import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.Schedulers
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.internal.misc.SchedulersImpl
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.modules.ModuleWithObservable
import com.tealium.prism.core.internal.modules.enableModuleSettings
import com.tealium.tests.common.ManualScheduler
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestModuleFactory
import com.tealium.tests.common.getDefaultConfig
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TealiumInstanceManagerTests {

    @MockK
    private lateinit var tealiumSupplier: (TealiumConfig, Schedulers) -> TealiumImpl

    private lateinit var config: TealiumConfig
    private lateinit var schedulers: Schedulers
    private lateinit var instances: MutableMap<String, TealiumInstanceManager.TealiumComponents>
    private lateinit var instanceManager: TealiumInstanceManager
    private val events = mutableListOf<String>()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        config = account1()
        schedulers = SchedulersImpl(tealium = Scheduler.SYNCHRONOUS, io = Scheduler.SYNCHRONOUS)
        instances = mutableMapOf()

        every { tealiumSupplier.invoke(any(), any()) } answers {
            mockk(relaxed = true)
        }

        instanceManager = TealiumInstanceManager(
            schedulers, instances, tealiumSupplier
        )
    }

    @Test
    fun create_Always_Returns_New_Tealium_Instance_For_Same_Account_And_Profile() {
        val tealium1 = instanceManager.create(config)
        val tealium2 = instanceManager.create(config)

        assertNotSame(tealium1, tealium2)
        verify(exactly = 1) {
            tealiumSupplier.invoke(config, schedulers)
        }
    }

    @Test
    fun create_Always_Returns_New_Tealium_Instance_For_Different_Account_And_Profile() {
        val tealium1 = instanceManager.create(config)
        val tealium2 = instanceManager.create(account2())

        assertNotSame(tealium1, tealium2)
        verify(exactly = 1) {
            tealiumSupplier.invoke(config, schedulers)
        }
    }

    @Test
    fun create_Only_Creates_One_TealiumImpl_For_Same_Account_And_Profile() {
        instanceManager.create(config)
        instanceManager.create(config)

        assertEquals(1, instances.size)
        verify(exactly = 1) {
            tealiumSupplier.invoke(config, schedulers)
        }
    }

    @Test
    fun create_Creates_Different_TealiumImpl_For_Different_Accounts() {
        instanceManager.create(config)
        val account2Config = account2()
        instanceManager.create(account2Config)

        assertEquals(2, instances.size)
        assertNotSame(instances[config.key]!!.instance, instances[account2Config.key]!!.instance)
        verify {
            tealiumSupplier.invoke(config, schedulers)
            tealiumSupplier.invoke(account2Config, schedulers)
        }
    }

    @Test
    fun create_Creates_Different_TealiumImpl_For_Same_Account_But_Different_Profile() {
        instanceManager.create(config)
        val account1Config = account1("test_2")
        instanceManager.create(account1Config)

        assertEquals(2, instances.size)
        assertNotSame(instances[config.key]!!.instance, instances[account1Config.key]!!.instance)
        verify {
            tealiumSupplier.invoke(config, schedulers)
            tealiumSupplier.invoke(account1Config, schedulers)
        }
    }

    @Test
    fun create_Returns_Instance_OnReady_When_Successful() {
        instanceManager.create(config) { result ->
            assertNotNull(result.getOrThrow())
        }
    }

    @Test(expected = TealiumException::class)
    fun create_Returns_Failure_When_Tealium_Fails_To_Initialize() {
        every { tealiumSupplier.invoke(any(), any()) } throws TealiumException()

        instanceManager.create(config) { result ->
            result.getOrThrow()
        }
    }

    @Test
    fun shutdown_Calls_Shutdown_On_Instance() {
        val mockTealium = mockk<TealiumImpl>(relaxed = true)
        every { tealiumSupplier.invoke(any(), any()) } returns mockTealium
        val tealium = instanceManager.create(config)

        instanceManager.shutdown(tealium)

        verify {
            mockTealium.shutdown()
        }
    }

    @Test
    fun shutdown_Removes_Existing_Instance_By_Config_Key() {
        instanceManager.create(config)
        assertEquals(1, instances.size)

        instanceManager.shutdown(config.key)
        assertEquals(0, instances.size)
    }

    @Test
    fun shutdown_Removes_Existing_Instance_By_Tealium_Key() {
        val tealium = instanceManager.create(config)
        assertEquals(1, instances.size)

        instanceManager.shutdown(tealium.key)
        assertEquals(0, instances.size)
    }

    @Test
    fun shutdown_Publishes_Shutdown_Result() {
        val tealium = instanceManager.create(config)
        val instanceSubject = instances[config.key]!!.instanceSubject

        // first event is valid impl.
        var ignoreFirst = true
        instanceSubject.subscribe {
            if (ignoreFirst) {
                ignoreFirst = false
                return@subscribe
            }
            assertTrue(it.exceptionOrNull() is Tealium.TealiumShutdownException)
        }

        instanceManager.shutdown(tealium)
    }

    @Test
    fun shutdown_Disposes_Proxy_Subscriptions() {
        val tealium = instanceManager.create(config)

        val components = instances[config.key]!!
        val disposable = mockk<Disposable>(relaxed = true)
        disposable.addTo(components.subscriptions)

        assertEquals(1, components.instanceSubject.count)
        instanceManager.shutdown(tealium)
        assertEquals(0, components.instanceSubject.count)

        verify {
            disposable.dispose()
        }
    }

    @Test
    fun shutdown_Publishes_Shutdown_Result_Before_Shutting_Down_Instance() {
        val mockTealium = mockk<TealiumImpl>(relaxed = true)
        var instanceShutdown = false
        every { mockTealium.shutdown() } answers { instanceShutdown = true }
        every { tealiumSupplier.invoke(any(), any()) } returns mockTealium
        val tealium = instanceManager.create(config)
        val instanceSubject = instances[config.key]!!.instanceSubject

        var shutdownBeforeResult: Boolean? = null
        instanceSubject.subscribe { result ->
            if (result.exceptionOrNull() is Tealium.TealiumShutdownException) {
                shutdownBeforeResult = instanceShutdown
            }
        }
        instanceManager.shutdown(tealium)

        assertEquals(false, shutdownBeforeResult)
        assertTrue(instanceShutdown)
    }

    @Test
    fun shutdown_Completes_Instance_Subject() {
        val tealium = instanceManager.create(config)
        val observer = mockk<Observer<TealiumResult<TealiumImpl>>>(relaxed = true)
        instances[config.key]!!.instanceSubject.subscribe(observer)

        instanceManager.shutdown(tealium)

        verifyOrder {
            observer.onNext(match { it.isSuccess })
            observer.onNext(match { it.exceptionOrNull() is Tealium.TealiumShutdownException })
            observer.onComplete()
        }
    }

    @Test
    fun create_Completes_Proxy_Subject_When_Tealium_Fails_To_Initialize() {
        val initException = TealiumException("Failed to initialize")
        every { tealiumSupplier.invoke(any(), any()) } throws initException
        val observer = mockk<Observer<TealiumResult<Int>>>(relaxed = true)

        val tealium = instanceManager.create(config)
        tealium.createModuleProxy(ModuleWithObservable::class.java)
            .observeModuleResult { it.subject }
            .subscribe(observer)

        verifyOrder {
            observer.onNext(match { it.exceptionOrNull() === initException })
            observer.onComplete()
        }
        verify(exactly = 1) { observer.onNext(any()) }
    }

    @Test
    fun shutdown_Does_Not_Emit_ModuleNotEnabled_To_ObserveModuleResult_Before_Shutdown_Error() {
        val module = ModuleWithObservable(Observables.stateSubject(1), "module")
        val moduleManager = ModuleManagerImpl(Scheduler.SYNCHRONOUS)
        moduleManager.addModuleFactory(TestModuleFactory.forModule(module))
        val context = mockk<TealiumContext>()
        every { context.logger } returns SystemLogger
        moduleManager.updateModuleSettings(context, enableModuleSettings(module.id))

        val mockTealium = mockk<TealiumImpl>(relaxed = true)
        every { mockTealium.moduleManager } returns moduleManager
        every { mockTealium.shutdown() } answers { moduleManager.shutdown() }
        every { tealiumSupplier.invoke(any(), any()) } returns mockTealium
        val observer = mockk<Observer<TealiumResult<Int>>>(relaxed = true)

        val tealium = instanceManager.create(config)
        tealium.createModuleProxy(ModuleWithObservable::class.java)
            .observeModuleResult { it.subject }
            .subscribe(observer)
        instanceManager.shutdown(tealium)

        verifyOrder {
            observer.onNext(match { it.getOrNull() == 1 })
            observer.onNext(match { it.exceptionOrNull() is Tealium.TealiumShutdownException })
            observer.onComplete()
        }
        verify(exactly = 2) { observer.onNext(any()) }
        verify(inverse = true) {
            observer.onNext(match { it.exceptionOrNull() is ModuleNotEnabledException })
        }
        verify { mockTealium.shutdown() }
    }

    @Test
    fun shutdown_Does_Not_Forward_Module_Emissions_To_ObserveModule_Transform() {
        val module = ModuleWithObservable(Observables.stateSubject(1), "module")
        val moduleManager = ModuleManagerImpl(Scheduler.SYNCHRONOUS)
        moduleManager.addModuleFactory(TestModuleFactory.forModule(module))
        val context = mockk<TealiumContext>()
        every { context.logger } returns SystemLogger
        moduleManager.updateModuleSettings(context, enableModuleSettings(module.id))

        val mockTealium = mockk<TealiumImpl>(relaxed = true)
        every { mockTealium.moduleManager } returns moduleManager
        every { mockTealium.shutdown() } answers {
            module.subject.onNext(2)
            moduleManager.shutdown()
        }
        every { tealiumSupplier.invoke(any(), any()) } returns mockTealium
        val observer = mockk<Observer<Int>>(relaxed = true)

        val tealium = instanceManager.create(config)
        tealium.createModuleProxy(ModuleWithObservable::class.java)
            .observeModule { it.subject }
            .subscribe(observer)
        verify(exactly = 1) { observer.onNext(1) }

        instanceManager.shutdown(tealium)

        verifyOrder {
            observer.onNext(1)
            observer.onComplete()
        }
        verify(exactly = 1) { observer.onNext(any()) }
        verify(inverse = true) { observer.onNext(2) }
        verify { mockTealium.shutdown() }
    }

    @Test
    fun create_Waits_For_Shutdown_When_Called_During_Shutdown() {
        val (_, newTealium) = supplyInstances("old", "new")
        instanceManager.create(config)

        lateinit var newProxy: Tealium
        onShutdownResult {
            newProxy = instanceManager.create(config)
        }
        instanceManager.shutdown(config.key)

        assertEquals(listOf("old created", "old shutdown", "new created"), events)
        assertSame(newTealium, instances[config.key]?.instance)
        var fetched: Tealium? = null
        instanceManager.get(config) { fetched = it }
        assertSame(newProxy, fetched)
    }

    @Test
    fun shutdown_Removes_Recreated_Instance_When_Called_During_Shutdown() {
        supplyInstances("old", "new")
        instanceManager.create(config)

        onShutdownResult {
            instanceManager.create(config)
            instanceManager.shutdown(config.key)
        }
        instanceManager.shutdown(config.key)

        assertEquals(
            listOf("old created", "old shutdown", "new created", "new shutdown"),
            events
        )
        assertTrue(instances.isEmpty())
    }

    @Test
    fun get_Returns_Recreated_Instance_When_Called_During_Shutdown() {
        supplyInstances("old", "new")
        instanceManager.create(config)

        lateinit var newProxy: Tealium
        var fetched: Tealium? = null
        onShutdownResult {
            newProxy = instanceManager.create(config)
            instanceManager.get(config) {
                events.add("get")
                fetched = it
            }
        }
        instanceManager.shutdown(config.key)

        assertEquals(listOf("old created", "old shutdown", "new created", "get"), events)
        assertSame(newProxy, fetched)
    }

    @Test
    fun shutdown_Calls_Shutdown_On_Instance_When_Subscriber_Throws() {
        val (oldTealium, newTealium) = supplyInstances("old", "new")
        instanceManager.create(config)
        val disposable = mockk<Disposable>(relaxed = true)
        disposable.addTo(instances[config.key]!!.subscriptions)

        val observerFailure = IllegalStateException("observer failure")
        onShutdownResult { throw observerFailure }
        val thrown = assertThrows(IllegalStateException::class.java) {
            instanceManager.shutdown(config.key)
        }

        assertSame(observerFailure, thrown)
        verify { oldTealium.shutdown() }
        verify { disposable.dispose() }
        assertTrue(instances.isEmpty())

        instanceManager.create(config)
        assertSame(newTealium, instances[config.key]?.instance)
    }

    @Test
    fun shutdown_Keeps_Subscriber_Exception_When_Instance_Shutdown_Also_Throws() {
        val (oldTealium, newTealium) = supplyInstances("old", "new")
        instanceManager.create(config)
        val shutdownFailure = IllegalStateException("shutdown failure")
        every { oldTealium.shutdown() } throws shutdownFailure

        val observerFailure = IllegalStateException("observer failure")
        onShutdownResult { throw observerFailure }
        val thrown = assertThrows(IllegalStateException::class.java) {
            instanceManager.shutdown(config.key)
        }

        assertSame(observerFailure, thrown)
        assertTrue(thrown.suppressed.contains(shutdownFailure))
        assertNull(instances[config.key])

        instanceManager.create(config)
        assertSame(newTealium, instances[config.key]?.instance)
    }

    @Test
    fun create_Waits_For_Shutdown_When_Subscriber_Throws() {
        val (_, newTealium) = supplyInstances("old", "new")
        instanceManager.create(config)

        onShutdownResult {
            instanceManager.create(config)
            throw IllegalStateException("observer failure")
        }
        assertThrows(IllegalStateException::class.java) {
            instanceManager.shutdown(config.key)
        }

        assertEquals(listOf("old created", "old shutdown", "new created"), events)
        assertSame(newTealium, instances[config.key]?.instance)
    }

    @Test
    fun shutdown_Does_Nothing_When_Proxy_Is_Stale() {
        val (_, newTealium) = supplyInstances("old", "new")
        val staleProxy = instanceManager.create(config)
        staleProxy.shutdown()
        instanceManager.create(config)

        staleProxy.shutdown()

        verify(exactly = 0) { newTealium.shutdown() }
        assertSame(newTealium, instances[config.key]?.instance)
    }

    @Test
    fun shutdown_Does_Nothing_When_Passed_Stale_Tealium() {
        val (_, newTealium) = supplyInstances("old", "new")
        val staleProxy = instanceManager.create(config)
        instanceManager.shutdown(staleProxy)
        instanceManager.create(config)

        instanceManager.shutdown(staleProxy)

        verify(exactly = 0) { newTealium.shutdown() }
        assertSame(newTealium, instances[config.key]?.instance)
    }

    @Test
    fun shutdown_Calls_Shutdown_On_Instance_From_Duplicate_Proxy() {
        val (sharedTealium) = supplyInstances("shared")
        instanceManager.create(config)
        val duplicateProxy = instanceManager.create(config)

        duplicateProxy.shutdown()

        verify { sharedTealium.shutdown() }
        assertTrue(instances.isEmpty())
    }

    @Test
    fun shutdown_Calls_Shutdown_On_Instance_When_Called_Before_Ready() {
        val scheduler = ManualScheduler()
        val tealiumImpl = mockk<TealiumImpl>(relaxed = true)
        val manager = TealiumInstanceManager(
            SchedulersImpl(tealium = scheduler, io = Scheduler.SYNCHRONOUS),
            instances
        ) { _, _ -> tealiumImpl }

        val proxy = manager.create(config)
        proxy.shutdown()
        assertTrue(instances.isEmpty())
        scheduler.runAll()

        verify { tealiumImpl.shutdown() }
        assertTrue(instances.isEmpty())
    }

    @Test
    fun get_Returns_Null_When_No_Instance_Created() {
        instanceManager.get(config) {
            assertNull(it)
        }
    }

    @Test
    fun get_Returns_Existing_Instance_When_Already_Created() {
        val tealium = instanceManager.create(config)
        instanceManager.get(config) {
            assertSame(tealium, it)
        }
    }

    /**
     * Makes [tealiumSupplier] return one relaxed [TealiumImpl] mock per name, in order, recording
     * "<name> created" and "<name> shutdown" into [events].
     */
    private fun supplyInstances(vararg names: String): List<TealiumImpl> {
        val impls = names.map { name ->
            mockk<TealiumImpl>(relaxed = true).also { impl ->
                every { impl.shutdown() } answers { events.add("$name shutdown") }
            }
        }
        var created = 0
        every { tealiumSupplier.invoke(any(), any()) } answers {
            events.add("${names[created]} created")
            impls[created++]
        }
        return impls
    }

    /**
     * Runs [block] when the instance currently stored for [config] publishes its shutdown result.
     */
    private fun onShutdownResult(block: () -> Unit) {
        instances[config.key]!!.instanceSubject.subscribe { result ->
            if (result.exceptionOrNull() is Tealium.TealiumShutdownException) {
                block()
            }
        }
    }

    private fun account1(profile: String = "test"): TealiumConfig =
        getDefaultConfig(ApplicationProvider.getApplicationContext(), "account_1", profile)

    private fun account2(profile: String = "test"): TealiumConfig =
        getDefaultConfig(ApplicationProvider.getApplicationContext(), "account_2", profile)
}