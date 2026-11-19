package com.tealium.prism.core.internal.persistence.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.persistence.database.DatabaseCorruptionLogs.inMemoryCorruptionLog
import com.tealium.tests.common.getDefaultConfig
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class InMemoryDatabaseProviderTests {

    private lateinit var config: TealiumConfig

    @Before
    fun setUp() {
        config = getDefaultConfig(ApplicationProvider.getApplicationContext<Application>())
    }

    @Test
    @Config(sdk = [27, 34])
    fun database_ReportsCorruptionsToTheGivenErrorHandler() {
        val reported = mutableListOf<SQLiteDatabase>()
        val provider = InMemoryDatabaseProvider(config, errorHandler = { db -> reported.add(db) })
        val database = provider.database

        database.reportCorruption()

        assertEquals(listOf(database), reported)
    }

    @Test
    @Config(sdk = [27, 34])
    fun database_LogsCorruption_When_GivenLoggingErrorHandler() {
        val messages = mutableListOf<String>()
        val logger = mockk<Logger>(relaxed = true)
        every { logger.error(LogCategory.PERSISTENCE, capture(messages)) } just Runs
        val provider = InMemoryDatabaseProvider(config, LoggingDatabaseErrorHandler(logger))

        provider.database.reportCorruption()

        assertEquals(listOf(inMemoryCorruptionLog), messages)
    }

    @Test
    @Config(sdk = [27, 34])
    fun database_ClosesCorruptDatabase_When_NoErrorHandlerGiven() {
        val database = InMemoryDatabaseProvider(config).database

        database.reportCorruption()

        assertFalse("the default handler must have closed the database", database.isOpen)
    }
}
