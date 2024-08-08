package com.tealium.core.internal.persistence

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.internal.persistence.repositories.ModulesRepository
import com.tealium.core.internal.persistence.repositories.SQLKeyValueRepository
import com.tealium.tests.common.TestModule
import com.tealium.tests.common.TestModuleFactory
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModuleStoreProviderImplTests {

    @RelaxedMockK
    private lateinit var dbProvider: DatabaseProvider

    @RelaxedMockK
    private lateinit var modulesRepository: ModulesRepository

    @RelaxedMockK
    private lateinit var keyValueRepositoryCreator: (DatabaseProvider, Long) -> SQLKeyValueRepository

    private lateinit var moduleStoreProviderImpl: ModuleStoreProviderImpl
    private lateinit var onDataExpired: Subject<Map<Long, TealiumBundle>>

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        onDataExpired = Observables.publishSubject()
        moduleStoreProviderImpl =
            ModuleStoreProviderImpl(dbProvider, modulesRepository, keyValueRepositoryCreator)

        // reasonable defaults
        every { modulesRepository.modules } returns emptyMap()
        every { modulesRepository.registerModule(any()) } returns 1
        every { modulesRepository.onDataExpired } returns onDataExpired
        every { keyValueRepositoryCreator.invoke(any(), any()) } returns mockk()
    }

    @Test
    fun getModuleStore_RegistersNewModule_WhenMissingFromModules() {
        moduleStoreProviderImpl.getModuleStore(TestModule("test"))

        verify {
            keyValueRepositoryCreator.invoke(any(), 1)
        }
    }

    @Test
    fun getModuleStore_ReturnsExistingModule_WhenAvailableInModules() {
        every { modulesRepository.modules } returns mapOf("test" to 2)

        moduleStoreProviderImpl.getModuleStore(TestModule("test"))

        verify {
            keyValueRepositoryCreator.invoke(any(), 2)
        }
    }

    @Test
    fun getModuleStore_ReturnsSameObject_ForModule_AndFactory() {
        every { modulesRepository.modules } returns mapOf("test" to 2)

        val store1 = moduleStoreProviderImpl.getModuleStore(TestModule("test"))
        val store2 = moduleStoreProviderImpl.getModuleStore(TestModuleFactory("test"))

        assertSame(store1, store2)
        verify(exactly = 1) {
            keyValueRepositoryCreator.invoke(any(), 2)
        }
    }

    @Test
    fun getModuleStore_FiltersExpiredData_ForCorrectModule() {
        val moduleId = 2L
        every { modulesRepository.modules } returns mapOf("test" to moduleId)

        val store1 = moduleStoreProviderImpl.getModuleStore(TestModule("test"))

        store1.onDataRemoved.subscribe { expired ->
            assertEquals(2, expired.size)
            assertEquals("key1", expired[0])
            assertEquals("key2", expired[1])
        }

        onDataExpired.onNext(mapOf(moduleId to TealiumBundle.create {
            put("key1", "value1")
            put("key2", "value2")
        }, moduleId + 1 to TealiumBundle.create {
            put("key3", "value3")
        }))
    }
}