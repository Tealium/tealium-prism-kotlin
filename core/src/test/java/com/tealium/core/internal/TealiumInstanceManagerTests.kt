package com.tealium.core.internal

import androidx.test.core.app.ApplicationProvider
import com.tealium.core.api.Tealium
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.misc.Schedulers
import com.tealium.core.api.misc.TealiumException
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.internal.misc.SchedulersImpl
import com.tealium.core.internal.pubsub.addTo
import com.tealium.tests.common.SynchronousScheduler
import com.tealium.tests.common.getDefaultConfig
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        config = account1()
        schedulers = SchedulersImpl(tealium = SynchronousScheduler(), io = SynchronousScheduler())
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

    private fun account1(profile: String = "test"): TealiumConfig =
        getDefaultConfig(ApplicationProvider.getApplicationContext(), "account_1", profile)

    private fun account2(profile: String = "test"): TealiumConfig =
        getDefaultConfig(ApplicationProvider.getApplicationContext(), "account_2", profile)
}