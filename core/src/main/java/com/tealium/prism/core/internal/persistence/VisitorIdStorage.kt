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
 *
 * Implementations publish a new [visitorId] whether or not it could be stored, so that the id in
 * memory, emitted to subscribers and added to events, always reflects the latest switch. Caller
 * driven operations still report a failed write by throwing; visitor switches triggered
 * automatically report it by logging. When the write fails nothing is stored, so the previously
 * stored visitor id and its identity associations are restored on the next launch.
 */
interface VisitorIdStorage {

    /**
     * Observable flow of the current visitor id.
     *
     * Emits every new id, including ones that could not be stored and therefore only last until
     * the next launch.
     */
    val visitorId: ObservableState<String>

    /**
     * Replaces the current visitor id with a new anonymous one.
     *
     * Note. the new anonymous id will be associated to any identity currently set.
     *
     * @return The new anonymous visitor id
     * @throws PersistenceException When the new visitor id could not be stored. The new id is still
     *  published to [visitorId] and used until the next launch, when the stored one is restored
     */
    @Throws(PersistenceException::class)
    fun regenerate(): String

    /**
     * Removes the owner identity and every stored identity association, and generates a new
     * anonymous visitor id.
     *
     * @return The new anonymous visitor id
     * @throws PersistenceException When the stored ids could not be cleared, in which case nothing
     *  is erased. The new id is still published to [visitorId] and used until the next launch, when
     *  the stored visitor id and associations are restored
     */
    @Throws(PersistenceException::class)
    fun eraseLocalHistory(): String

    /**
     * Notifies that the identity has changed.
     *
     * When no identity is currently set, then this [identity] should be associated with the current
     * visitor id.
     *
     * When the [identity] has been seen before, then the visitor id should be updated to the
     * one previously associated with this [identity].
     *
     * This is driven by data layer updates rather than by an api caller, so it does not throw; a
     * failed write is logged. A visitor switch is still published to [visitorId] and used until
     * the next launch, when the stored visitor id is restored and the switch is retried the next
     * time the same identity is seen.
     *
     * @param identity The new identity of the user
     */
    fun identify(identity: String)
}


class VisitorIdStorageImpl(
    private val visitorStorage: VisitorStorage,
    private val migrator: VisitorIdMigrator,
    private val existingVisitorId: String? = null,
    private val logger: Logger
) : VisitorIdStorage {

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

    override fun regenerate(): String {
        logger.debug(LogCategory.VISITOR_ID, "Regenerating current visitor id")

        return generateVisitorId().also { newId ->
            try {
                visitorStorage.changeVisitor(newId)
            } finally {
                // published even when not stored: the caller is told by the exception, and the
                // stored id is restored on the next launch
                _visitorId.onNext(newId)
            }
        }
    }

    override fun eraseLocalHistory(): String {
        logger.debug(LogCategory.VISITOR_ID, "Erasing stored identity associations.")

        return generateVisitorId().also { newId ->
            try {
                visitorStorage.clear(newId)
            } finally {
                // published even when not stored: the caller is told by the exception, and the
                // stored id and associations are restored on the next launch
                _visitorId.onNext(newId)
            }
        }
    }

    override fun identify(identity: String) {
        if (identity.isBlank()) return

        val oldIdentity = visitorStorage.ownerIdentity
        val hashedNewIdentity = identity.sha256()

        if (hashedNewIdentity == oldIdentity) return

        logger.debug(LogCategory.VISITOR_ID, "Identity change has been detected.")

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
            LogCategory.VISITOR_ID,
            "Identity has been seen before; setting known visitor id"
        )

        changeVisitor(knownVisitorId, identity)
    }

    private fun handleChangedIdentity(identity: String) {
        logger.debug(
            LogCategory.VISITOR_ID,
            "Identity has been seen before; but visitor id has not changed"
        )

        try {
            visitorStorage.changeIdentity(identity)
        } catch (e: Exception) {
            logger.error(
                LogCategory.VISITOR_ID,
                "Failed to change identity.\nError: ${e.message}"
            )
        }
    }

    private fun handleFirstIdentity(identity: String) {
        logger.debug(
            LogCategory.VISITOR_ID,
            "Identity unknown; linking to current visitor id"
        )

        changeVisitor(visitorId.value, identity)
    }

    private fun handleNewIdentity(identity: String) {
        logger.debug(LogCategory.VISITOR_ID, "Identity unknown; resetting visitor id")

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
            // the id was not stored, so the previous one is restored on the next launch, and the
            // switch will be retried the next time the same identity is seen
            logger.error(
                LogCategory.VISITOR_ID,
                "Failed to change visitor.\nError: ${e.message}"
            )
        } finally {
            // published even when not stored, so the id in memory reflects the latest switch
            if (_visitorId.value != visitorId) {
                _visitorId.onNext(visitorId)
            }
        }
    }

    /**
     * Returns a new or existing visitor id in the following order of preference
     *  1. The existing visitor id from visitor storage
     *  2. A visitor id that is able to be migrated from a previous sdk version during an upgrade
     *  3. The user configured [TealiumConfig.existingVisitorId] if it's not empty; only relevant on
     *  first launch when there isn't any existing visitor id
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

        if (!existingVisitorId.isNullOrBlank()) {
            return existingVisitorId
        }

        return generateVisitorId()
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