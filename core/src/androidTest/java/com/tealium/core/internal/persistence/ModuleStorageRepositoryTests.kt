package com.tealium.core.internal.persistence

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.tealium.core.Environment
import com.tealium.core.TealiumConfig
import com.tealium.tests.common.TestModule
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ModuleStorageRepositoryTests {

    lateinit var app: Application
    lateinit var moduleRepository: ModuleStorageRepositoryImpl

    private val module1 = TestModule("module1")
    private val module2 = TestModule("module2")
    private val module3 = TestModule("module3")

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext() as Application
        val config = TealiumConfig(
            application = app,
            "test",
            "test",
            Environment.DEV,
            "",
            listOf()
        )

        // use-in-memory DB
        val inMemoryDbProvider = InMemoryDatabaseProvider(config)
        // Repositories
        moduleRepository = ModuleStorageRepositoryImpl(
            inMemoryDbProvider
        )
    }

    @Test
    fun modules_Contains_AllRegisteredModule() {
        prepopulateModules()

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
        prepopulateModules()

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

        val moduleRepository = ModuleStorageRepositoryImpl(mockDbProvider)
        moduleRepository.registerModule("any")
    }

    @Test
    fun registerModule_CreatesNewEntry_AndReturnsId() {
        prepopulateModules()

        val rowId = moduleRepository.registerModule("module4")
        val modules = moduleRepository.modules

        assertTrue(rowId > -1)
        assertEquals(rowId, modules["module4"])
    }

    @Test
    fun registerModule_ReturnsCachedEntry_IfAlreadyAvailable() {
        prepopulateModules()

        val modules = moduleRepository.modules

        assertEquals(1L, modules[module1.name])
        assertEquals(1L, moduleRepository.registerModule(module1.name))
        // TODO - verify no DB call.
    }

    private fun prepopulateModules() {
        moduleRepository.registerModule(module1.name)
        moduleRepository.registerModule(module2.name)
        moduleRepository.registerModule(module3.name)
    }
}