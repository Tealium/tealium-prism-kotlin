package com.tealium.core.internal.persistence

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tealium.core.Environment
import com.tealium.core.TealiumConfig
import com.tealium.core.api.DataStore
import com.tealium.core.api.Expiry
import com.tealium.core.api.Module
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumValue
import com.tealium.tests.common.TestModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.util.concurrent.TimeUnit

@RunWith(Enclosed::class)
open class SQLModulesRepositoryTests {

    private lateinit var app: Application
    private lateinit var dbProvider: DatabaseProvider
    protected lateinit var moduleRepository: SQLModulesRepository
    protected lateinit var tealiumScope: CoroutineScope

    protected val module1 = TestModule("module1")
    protected val module2 = TestModule("module2")
    protected val module3 = TestModule("module3")

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext() as Application
        val config = TealiumConfig(
            application = app,
            "test",
            "test",
            Environment.DEV,
            listOf()
        )

        // use-in-memory DB
        dbProvider = InMemoryDatabaseProvider(config)
        tealiumScope = CoroutineScope(Dispatchers.Default)
        // Repositories
        moduleRepository = SQLModulesRepository(
            dbProvider,
        )
    }

    @RunWith(AndroidJUnit4::class)
    class StandardTests : SQLModulesRepositoryTests() {
        @Test
        fun modules_Contains_AllRegisteredModule() {
            preregisterModules()

            val storedModules = moduleRepository.modules
            assertTrue(storedModules.containsKey(module1.name))
            assertTrue(storedModules.containsKey(module2.name))
            assertTrue(storedModules.containsKey(module3.name))

            // might not work; assumes 1-indexed
            assertEquals(1L, storedModules[module1.name])
            assertEquals(2L, storedModules[module2.name])
            assertEquals(3L, storedModules[module3.name])
        }

        @Test
        fun modules_GetsUpdated_WithNewlyRegisteredModule() {
            preregisterModules()

            val originalModules = moduleRepository.modules
            moduleRepository.registerModule("module4")
            val newModules = moduleRepository.modules

            assertEquals(3, originalModules.size)
            assertEquals(4, newModules.size)
            assertFalse(originalModules.containsKey("module4"))
            assertTrue(newModules.containsKey("module4"))
        }

        @Test(expected = Exception::class)
        fun registerModule_Throws_WhenUnableToRegisterModule() {
            val mockDb: SQLiteDatabase = mockk()
            val mockDbProvider: DatabaseProvider = mockk()
            every { mockDbProvider.database } returns mockDb
            every { mockDb.insert(any(), any(), any()) } returns -1

            val moduleRepository =
                SQLModulesRepository(mockDbProvider)
            moduleRepository.registerModule("any")
        }

        @Test
        fun registerModule_CreatesNewEntry_AndReturnsId() {
            preregisterModules()

            val rowId = moduleRepository.registerModule("module4")
            val modules = moduleRepository.modules

            assertTrue(rowId > -1)
            assertEquals(rowId, modules["module4"])
        }

        @Test
        fun registerModule_ReturnsCachedEntry_IfAlreadyAvailable() {
            preregisterModules()

            val modules = moduleRepository.modules

            assertEquals(1L, modules[module1.name])
            assertEquals(1L, moduleRepository.registerModule(module1.name))
        }

        @Test
        fun getModuleIdForName_ReturnsExistingId_WhenAlreadyRegistered() {
            moduleRepository.registerModule("new_module")

            val knownId = moduleRepository.getModuleIdForName("new_module")
            val unknownId = moduleRepository.getModuleIdForName("missing_module")

            assertTrue(knownId >= 0)
            assertTrue(unknownId < 0)
        }

        @Test
        fun getModuleIdForName_ReturnsSameId_AsRegistered() {
            val registeredId = moduleRepository.registerModule("new_module")
            val retrievedId = moduleRepository.getModuleIdForName("new_module")

            assertEquals(registeredId, retrievedId)
        }
    }

    @RunWith(Parameterized::class)
    class ParameterizedExpirationTests(
        private val expirationType: ModulesRepository.ExpirationType,
        private val expectedRemoval: String
    ) : SQLModulesRepositoryTests() {

        companion object {
            @JvmStatic
            @Parameters
            fun expirationTypeParams() = arrayOf(
                arrayOf(ModulesRepository.ExpirationType.Session, "session"),
                arrayOf(ModulesRepository.ExpirationType.UntilRestart, "until_restart")
            )
        }

        @Test
        fun deleteExpired_RemovesRequestedData() {
            val moduleId = preregisterModule(module1, true)
            val moduleStore = getModuleStore(moduleId)

            assertTrue(moduleStore.keys().contains(expectedRemoval))

            moduleRepository.deleteExpired(
                expirationType,
                getTimestamp() + 2L
            )

            assertFalse(moduleStore.keys().contains(expectedRemoval))
        }

        @Test
        fun deleteExpired_RemovesRequestedData_FromAllModules() {
            val moduleIds = preregisterModules(listOf(module1, module2), true)
            moduleIds.forEach { id ->
                assertTrue(getModuleStore(id).keys().contains(expectedRemoval))
            }

            moduleRepository.deleteExpired(
                expirationType,
                getTimestamp() + 2L
            )

            moduleIds.forEach { id ->
                assertFalse(getModuleStore(id).keys().contains(expectedRemoval))
            }
        }

        @Test
        fun deleteExpired_RemoveRequestedData_And_AlsoRemovesAllExpiredData_FromAllModules() {
            val moduleIds = preregisterModules(listOf(module1, module2), true)
            moduleIds.forEach { id ->
                assertTrue(getModuleStore(id).keys().contains(expectedRemoval))
            }

            moduleRepository.deleteExpired(
                expirationType,
                getTimestamp() + 2L
            )

            moduleIds.forEach { id ->
                assertFalse(getModuleStore(id).keys().contains(expectedRemoval))
                assertFalse(getModuleStore(id).keys().contains("one_second"))
            }
        }

        @Test
        fun deleteExpired_NotifiesOnDataExpired_WithExpiredData() {
            val moduleIds = preregisterModules(listOf(module1, module2), true)
            val verifier = spyk<(Map<Long, TealiumBundle>) -> Unit>({ expired ->
                assertEquals(moduleIds.count(), expired.keys.count())
                assertTrue(expired.keys.containsAll(moduleIds))

                moduleIds.forEach {
                    assertEquals(true, expired[it]!!.getBoolean("one_second"))
                    assertEquals(true, expired[it]!!.getBoolean(expectedRemoval))
                }
            })

            moduleRepository.onDataExpired.subscribe(verifier)
            moduleRepository.deleteExpired(
                expirationType,
                getTimestamp() + 2L
            )

            verify(exactly = 1) {
                verifier(any())
            }
        }
    }

    protected fun preregisterModule(module: Module, shouldPopulate: Boolean = false): Long {
        return preregisterModules(listOf(module), shouldPopulate).first()
    }

    protected fun preregisterModules(
        modules: List<Module> = listOf(module1, module2, module3),
        shouldPopulate: Boolean = false
    ): List<Long> {
        return modules
            .map {
                if (shouldPopulate) {
                    populateModuleStore(it)
                } else {
                    moduleRepository.registerModule(it.name)
                }
            }
    }

    protected fun populateModuleStore(
        module: Module,
        block: ((DataStore.Editor) -> Unit)? = null
    ): Long {
        val moduleId = moduleRepository.registerModule(module.name)

        getModuleStore(moduleId).edit().apply {
            put(
                "one_second",
                TealiumValue.boolean(true),
                Expiry.afterTimeUnit(1, TimeUnit.SECONDS)
            )
            put(
                "one_hour",
                TealiumValue.boolean(true),
                Expiry.afterTimeUnit(1, TimeUnit.HOURS)
            )
            put("until_restart", TealiumValue.boolean(true), Expiry.UNTIL_RESTART)
            put("session", TealiumValue.boolean(true), Expiry.SESSION)
            put("forever", TealiumValue.boolean(true), Expiry.FOREVER)

            block?.invoke(this)
        }.commit()

        return moduleId
    }

    protected fun getModuleStore(moduleId: Long): DataStore {
        return ModuleStore(
            SQLKeyValueRepository(dbProvider, moduleId)
        )
    }
}