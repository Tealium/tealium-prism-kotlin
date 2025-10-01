package com.tealium.prism.core.internal.persistence.database

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.assertV1TablesExist
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.assertV2TablesExist
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.assertV3TablesExist
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.assertV3TablesPostUpgrade
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.createV3Database
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.upgrade
import com.tealium.tests.common.getDefaultConfig
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class DatabaseUpgradeTests {

    private lateinit var app: Application
    private lateinit var config: TealiumConfig
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext() as Application
        config = getDefaultConfig(app)

        dbFile = File(app.filesDir, "test.db")
    }

    @After
    fun tearDown() {
        try {
            dbFile.delete()
        } catch (ignore: Exception) {
        }
    }

    @Test
    fun create_V3_InMemory_CreatesAllV3Tables() {
        val persistentDb = createV3Database(config, dbFile.path)

        assertV3TablesExist(persistentDb)
    }

    @Test
    fun create_V1_InMemory_DoesNotThrow() {
        val inMemoryDb =
            com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.createV1Database(
                config.application.applicationContext
            )

        assertV1TablesExist(inMemoryDb)
    }

    @Test
    fun upgrade_InMemory_From_1_to_2_DoesNotThrow() {
        val inMemoryDb =
            com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.createV1Database(
                config.application.applicationContext
            )
        inMemoryDb.upgrade(1, 2)

        assertV2TablesExist(inMemoryDb)
    }

    @Test
    fun upgrade_InMemory_From_2_to_3_DoesNotThrow() {
        val inMemoryDb =
            com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.createV2Database(
                config.application.applicationContext
            )
        inMemoryDb.upgrade(2, 3)

        assertV3TablesPostUpgrade(inMemoryDb)
    }

    @Test
    fun upgrade_InMemory_From_1_to_3_DoesNotThrow() {
        val inMemoryDb =
            com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.createV1Database(
                config.application.applicationContext
            )
        inMemoryDb.upgrade(1, 3)

        assertV3TablesPostUpgrade(inMemoryDb)
    }

    @Test
    fun create_V3_Persistent_CreatesAllV3Tables() {
        val persistentDb = createV3Database(config, dbFile.path)

        assertV3TablesExist(persistentDb)
    }

    @Test
    fun create_V1_Persistent_DoesNotThrow() {
        val persistentDb =
            com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.createV1Database(
                config.application.applicationContext,
                dbFile.path
            )

        assertV1TablesExist(persistentDb)
    }

    @Test
    fun upgrade_Persistent_From_1_to_2_DoesNotThrow() {
        var persistentDb =
            com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.createV1Database(
                config.application.applicationContext,
                dbFile.path
            )
        persistentDb.close()
        persistentDb =
            com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.createV2Database(
                config.application.applicationContext,
                dbFile.path
            )

        assertV2TablesExist(persistentDb)
    }

    @Test
    fun upgrade_Persistent_From_2_to_3_DoesNotThrow() {
        var persistentDb =
            com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.createV2Database(
                config.application.applicationContext,
                dbFile.path
            )
        persistentDb.close()
        persistentDb = createV3Database(config, dbFile.path)

        assertV3TablesPostUpgrade(persistentDb)
    }

    @Test
    fun upgrade_Persistent_From_1_to_3_DoesNotThrow() {
        var persistentDb =
            com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.createV1Database(
                config.application.applicationContext,
                dbFile.path
            )
        persistentDb.close()
        persistentDb = createV3Database(config, dbFile.path)

        assertV3TablesPostUpgrade(persistentDb)
    }
}