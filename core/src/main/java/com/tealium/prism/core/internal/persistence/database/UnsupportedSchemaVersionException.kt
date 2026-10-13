package com.tealium.prism.core.internal.persistence.database

/**
 * Signals that the file on disk cannot be brought to [newVersion] by upgrading: it is either
 * newer than this SDK ([Direction.DOWNGRADE]) or older than [DatabaseHelper.MIN_SUPPORTED_VERSION]
 * ([Direction.BELOW_MINIMUM]).
 *
 * Neither the file nor this SDK will change, so retrying later cannot help.
 */
internal class UnsupportedSchemaVersionException(
    val oldVersion: Int,
    val newVersion: Int,
    val direction: Direction,
    message: String = "${direction.describe(oldVersion, newVersion)} is not supported."
) : RuntimeException(message) {

    enum class Direction {
        /** The file is newer than this SDK. */
        DOWNGRADE,

        /**
         * The file is older than [DatabaseHelper.MIN_SUPPORTED_VERSION], so no upgrade path exists.
         */
        BELOW_MINIMUM;

        internal fun describe(oldVersion: Int, newVersion: Int): String = when (this) {
            DOWNGRADE -> "Downgrade from $oldVersion to $newVersion"
            BELOW_MINIMUM ->
                "Upgrade from $oldVersion to $newVersion, below the minimum supported " +
                    "version ${DatabaseHelper.MIN_SUPPORTED_VERSION},"
        }
    }
}
