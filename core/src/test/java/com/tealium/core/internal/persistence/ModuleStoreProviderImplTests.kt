package com.tealium.core.internal.persistence

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.internal.persistence.database.DatabaseProvider
import com.tealium.core.internal.persistence.repositories.KeyValueRepository
import com.tealium.core.internal.persistence.repositories.ModulesRepository
import com.tealium.tests.common.TestModule
import com.tealium.tests.common.TestModuleFactory
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
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

    private val keyValueRepositoryCreator = MockRepositoryCreator(mockk())

    private lateinit var moduleStoreProviderImpl: ModuleStoreProviderImpl
    private lateinit var onDataExpired: Subject<Map<Long, DataObject>>

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
    }

    @Test
    fun getModuleStore_RegistersNewModule_WhenMissingFromModules() {
        moduleStoreProviderImpl.getModuleStore(TestModule("test"))

        assertEquals(1, keyValueRepositoryCreator.count)
        assertEquals(1, keyValueRepositoryCreator.moduleId)
    }

    @Test
    fun getModuleStore_ReturnsExistingModule_WhenAvailableInModules() {
        every { modulesRepository.modules } returns mapOf("test" to 2)

        moduleStoreProviderImpl.getModuleStore(TestModule("test"))

        assertEquals(1, keyValueRepositoryCreator.count)
        assertEquals(2, keyValueRepositoryCreator.moduleId)
    }

    @Test
    fun getModuleStore_ReturnsSameObject_ForModule_AndFactory() {
        every { modulesRepository.modules } returns mapOf("test" to 2)

        val store1 = moduleStoreProviderImpl.getModuleStore(TestModule("test"))
        val store2 = moduleStoreProviderImpl.getModuleStore(TestModuleFactory("test"))

        assertSame(store1, store2)
        assertEquals(1, keyValueRepositoryCreator.count)
        assertEquals(2, keyValueRepositoryCreator.moduleId)
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

        onDataExpired.onNext(mapOf(moduleId to DataObject.create {
            put("key1", "value1")
            put("key2", "value2")
        }, moduleId + 1 to DataObject.create {
            put("key3", "value3")
        }))
    }

    // Spyk failing on the mock, so using a custom one.
    private class MockRepositoryCreator(private val returns: KeyValueRepository) : Function2<DatabaseProvider, Long, KeyValueRepository> {
        private var _dbProvider: DatabaseProvider? = null
        private var _moduleId: Long? = null

        var count: Int = 0
            private set

        val dbProvider: DatabaseProvider
            get() = _dbProvider!!
        val moduleId: Long
            get() = _moduleId!!

        override fun invoke(p1: DatabaseProvider, p2: Long): KeyValueRepository {
            count++

            _dbProvider = p1
            _moduleId = p2

            return returns
        }
    }
}