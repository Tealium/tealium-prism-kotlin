package com.tealium.prism.core.internal.persistence.database

import com.tealium.prism.core.internal.persistence.database.upgrades.DatabaseUpgrade

/**
 * Thrown by [DatabaseHelper] when a [DatabaseUpgrade] step throws, wrapping the [Exception] it
 * threw. Steps never throw this themselves. An [Error] is not wrapped and propagates as it is.
 *
 * When it propagates out of `getWritableDatabase`, the transaction around `onUpgrade` has been
 * rolled back, so the file is at the version it was opened at - unless the platform deleted it
 * after detecting a corruption.
 *
 * A plain [RuntimeException]: the platform catches nothing around `onUpgrade`, so the type carries
 * no routing. What the failure means is in its [cause].
 *
 * @param targetVersion the schema version the failed step was upgrading to: its
 * [DatabaseUpgrade.targetVersion]
 * @param cause what the step threw
 */
internal class DatabaseUpgradeException(
    val targetVersion: Int,
    cause: Throwable
) : RuntimeException("Database upgrade to version $targetVersion failed", cause)
