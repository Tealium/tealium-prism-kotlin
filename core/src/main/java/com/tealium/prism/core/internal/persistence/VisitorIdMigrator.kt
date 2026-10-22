package com.tealium.prism.core.internal.persistence

interface VisitorIdMigrator {
    /**
     * Holds the migrated visitor Id if present
     */
    val visitorId: String?

    // TODO - migrate identity and lookups

    /**
     * Clears the old stored visitor id - effectively marking it as migrated.
     */
    fun delete()
}

// TODO - implement migration
class VisitorIdMigratorImpl(
    // TODO - implement
) : VisitorIdMigrator {
    override val visitorId: String?
        get() = null

    override fun delete() {

    }
}
