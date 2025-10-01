package com.tealium.prism.core.internal.persistence

import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.persistence.stores.VisitorStorage
import com.tealium.prism.core.internal.persistence.stores.VisitorStorageImpl
import com.tealium.prism.core.internal.utils.sha256
import java.util.UUID

/**
 * Provides the interface for managing visitor identifiers as well as visitor switching.
 */
interface VisitorIdProvider {

    /**
     * Observable flow of the current visitor id.
     */
    val visitorId: ObservableState<String>

    /**
     * Resets the current visitor id to a new anonymous one.
     *
     * Note. the new anonymous id will be associated to any identity currently set.
     *
     * @return The new anonymous visitor id
     */
    @Throws(PersistenceException::class)
    fun resetVisitorId(): String

    /**
     * Removes all stored visitor identifiers as hashed identities, and generates a new
     * anonymous visitor id.
     */
    @Throws(PersistenceException::class)
    fun clearStoredVisitorIds(): String

    /**
     * Notifies that the identity has changed.
     *
     * When no identity is currently set, then this [identity] should be associated with the current
     * visitor id.
     *
     * When the [identity] has been seen before, then the visitor id should be updated to the
     * one previously associated with this [identity].
     *
     * @param identity The new identity of the user
     */
    @Throws(PersistenceException::class)
    fun identify(identity: String)
}


class VisitorIdProviderImpl(
    private val visitorStorage: VisitorStorage,
    private val migrator: VisitorIdMigrator,
    private val existingVisitorId: String? = null,
    private val logger: Logger
) : VisitorIdProvider {

    constructor(
        config: TealiumConfig,
        visitorDataStore: DataStore,
        logger: Logger
    ) : this(
        VisitorStorageImpl(visitorDataStore),
        VisitorIdMigratorImpl(),
        config.existingVisitorId,
        logger
    )

    private val _visitorId: StateSubject<String>

    init {
        val visitorId = getOrCreateVisitorId()
        _visitorId = Observables.stateSubject(visitorId)

        if (visitorId != visitorStorage.visitorId) {
            changeVisitor(visitorId, null)
        }
    }

    override val visitorId: ObservableState<String>
        get() = _visitorId.asObservableState()

    override fun resetVisitorId(): String {
        logger.debug(LogCategory.VISITOR_ID_PROVIDER, "Resetting current visitor id")

        return generateVisitorId().also { newId ->
            try {
                visitorStorage.changeVisitor(newId)
            } finally {
                _visitorId.onNext(newId)
            }
        }
    }

    override fun clearStoredVisitorIds(): String {
        logger.debug(LogCategory.VISITOR_ID_PROVIDER, "Clearing stored visitor ids")

        return generateVisitorId().also { newId ->
            try {
                visitorStorage.clear(newId)
            } finally {
                _visitorId.onNext(newId)
            }
        }
    }

    override fun identify(identity: String) {
        if (identity.isBlank()) return

        val oldIdentity = visitorStorage.currentIdentity
        val hashedNewIdentity = identity.sha256()

        if (hashedNewIdentity == oldIdentity) return

        logger.debug(LogCategory.VISITOR_ID_PROVIDER, "Identity change has been detected.")

        // check for known matching visitor id
        val knownVisitorId = visitorStorage.getKnownVisitorId(hashedNewIdentity)

        if (knownVisitorId != null) {
            if (knownVisitorId != visitorId.value) {
                handleExistingIdentity(knownVisitorId, hashedNewIdentity)
            } else {
                handleChangedIdentity(hashedNewIdentity)
            }
        } else if (oldIdentity == null) {
            handleFirstIdentity(hashedNewIdentity)
        } else {
            handleNewIdentity(hashedNewIdentity)
        }
    }

    private fun handleExistingIdentity(knownVisitorId: String, identity: String) {
        logger.debug(
            LogCategory.VISITOR_ID_PROVIDER,
            "Identity has been seen before; setting known visitor id"
        )

        changeVisitor(knownVisitorId, identity)
    }

    private fun handleChangedIdentity(identity: String) {
        logger.debug(
            LogCategory.VISITOR_ID_PROVIDER,
            "Identity has been seen before; but visitor id has not changed"
        )

        try {
            visitorStorage.changeIdentity(identity)
        } catch (e: Exception) {
            logger.error(
                LogCategory.VISITOR_ID_PROVIDER,
                "Failed to change identity.\nError: ${e.message}"
            )
        }
    }

    private fun handleFirstIdentity(identity: String) {
        logger.debug(
            LogCategory.VISITOR_ID_PROVIDER,
            "Identity unknown; linking to current visitor id"
        )

        changeVisitor(visitorId.value, identity)
    }

    private fun handleNewIdentity(identity: String) {
        logger.debug(LogCategory.VISITOR_ID_PROVIDER, "Identity unknown; resetting visitor id")

        val newVisitorId = generateVisitorId()
        changeVisitor(newVisitorId, identity)
    }

    private fun changeVisitor(visitorId: String, identity: String?) {
        try {
            if (identity != null) {
                visitorStorage.changeVisitor(visitorId, identity)
            } else {
                visitorStorage.changeVisitor(visitorId)
            }
        } catch (e: Exception) {
            logger.error(
                LogCategory.VISITOR_ID_PROVIDER,
                "Failed to change visitor.\nError: ${e.message}"
            )
        } finally {
            _visitorId.onNext(visitorId)
        }
    }

    /**
     * Returns a new or existing visitor id in the following order of preference
     *  1. The existing visitor id from visitor storage
     *  2. A visitor id that is able to be migrated from a previous sdk version during an upgrade
     *  3. The user configured [TealiumConfig.existingVisitorId]; only relevant on first launch when
     *  there isn't any existing visitor id
     *  4. A brand new anonymous visitor id
     */
    private fun getOrCreateVisitorId(): String {
        val storedVisitorId = visitorStorage.visitorId
        if (storedVisitorId != null) return storedVisitorId

        val migratedVisitorId = migrator.visitorId
        if (migratedVisitorId != null) {
            // TODO - handle possible exception
            visitorStorage.changeVisitor(migratedVisitorId)
            migrator.delete()
            return migratedVisitorId
        }

        return existingVisitorId ?: generateVisitorId()
    }

    private fun generateVisitorId(uuid: UUID = UUID.randomUUID()): String {
        return uuid.toString().replace("-", "")
    }

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
}