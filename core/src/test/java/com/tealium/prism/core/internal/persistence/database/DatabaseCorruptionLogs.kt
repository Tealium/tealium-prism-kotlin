package com.tealium.prism.core.internal.persistence.database

/**
 * The corruption logs [LoggingDatabaseErrorHandler] writes, spelled out once for the tests that
 * assert them.
 */
internal object DatabaseCorruptionLogs {

    fun fileDeletedLog(path: String) =
        "Database corruption detected in '$path'. The file was deleted; data in it is lost."

    fun fileDeletionFailedLog(path: String) =
        "Database corruption detected in '$path'. The file could not be deleted, so it stays " +
            "as it is."

    val inMemoryCorruptionLog =
        "Database corruption detected in ':memory:'. There is no file to delete; data in it is " +
            "lost."
}
