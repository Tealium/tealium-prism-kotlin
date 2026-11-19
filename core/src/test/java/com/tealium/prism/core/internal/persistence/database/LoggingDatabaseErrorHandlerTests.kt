package com.tealium.prism.core.internal.persistence.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.persistence.database.DatabaseCorruptionLogs.fileDeletedLog
import com.tealium.prism.core.internal.persistence.database.DatabaseCorruptionLogs.fileDeletionFailedLog
import com.tealium.prism.core.internal.persistence.database.DatabaseCorruptionLogs.inMemoryCorruptionLog
import com.tealium.tests.common.getDefaultConfig
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class LoggingDatabaseErrorHandlerTests {

    @Test
    @Config(sdk = [23, 34])
    fun onCorruption_LogsNoFileToDelete_When_DatabaseIsInMemory() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val messages = mutableListOf<String>()
        val logger = mockk<Logger>(relaxed = true)
        every { logger.error(LogCategory.PERSISTENCE, capture(messages)) } just Runs
        val database = InMemoryDatabaseProvider(getDefaultConfig(app)).database

        LoggingDatabaseErrorHandler(logger).onCorruption(database)

        assertEquals(
            listOf(inMemoryCorruptionLog),
            messages
        )
        assertFalse("the platform must have closed the database", database.isOpen)
    }

    @Test
    @Config(sdk = [23, 34])
    fun onCorruption_LogsFileDeleted_When_PlatformDeletesTheFile() {
        val (path, messages, logger) = fileDatabaseSetup()
        val database = openFileDatabase(path)

        LoggingDatabaseErrorHandler(logger).onCorruption(database)

        assertFalse("the platform must have deleted the file", File(path).exists())
        assertEquals(
            listOf(fileDeletedLog(path)),
            messages
        )
    }

    @Test
    @Config(sdk = [23, 34])
    fun onCorruption_LogsFileKept_When_DeleteDoesNotRemoveTheFile() {
        val (path, messages, logger) = fileDatabaseSetup()
        val database = openFileDatabase(path)

        LoggingDatabaseErrorHandler(logger, delegate = {}).onCorruption(database)

        assertTrue("the file must be kept", File(path).exists())
        assertEquals(
            listOf(fileDeletionFailedLog(path)),
            messages
        )
        database.close()
        File(path).delete()
    }

    private data class FileSetup(
        val path: String,
        val messages: MutableList<String>,
        val logger: Logger
    )

    private fun fileDatabaseSetup(): FileSetup {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val messages = mutableListOf<String>()
        val logger = mockk<Logger>(relaxed = true)
        every { logger.error(LogCategory.PERSISTENCE, capture(messages)) } just Runs
        val path = app.getDatabasePath("logging-handler-${UUID.randomUUID()}.db").absolutePath
        return FileSetup(path, messages, logger)
    }

    private fun openFileDatabase(path: String): SQLiteDatabase {
        File(path).parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(path, null)
    }
}
