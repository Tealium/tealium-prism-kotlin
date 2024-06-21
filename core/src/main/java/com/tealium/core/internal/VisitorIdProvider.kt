package com.tealium.core.internal

import com.tealium.core.TealiumConfig
import com.tealium.core.api.DataStore
import com.tealium.core.api.logger.Logger
import com.tealium.core.internal.observables.ObservableState
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.StateSubject
import com.tealium.core.internal.utils.sha256
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
    fun resetVisitorId(): String

    /**
     * Removes all stored visitor identifiers as hashed identities, and generates a new
     * anonymous visitor id.
     */
    fun clearStoredVisitorIds()

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

    private val _visitorId: StateSubject<String> = Observables.stateSubject(getOrCreateVisitorId())
    override val visitorId: ObservableState<String>
        get() = _visitorId.asObservableState()

    override fun resetVisitorId(): String {
        logger.debug?.log("VisitorIdProvider", "Resetting current visitor id")

        return generateVisitorId().also { newId ->
            visitorStorage.changeVisitor(newId)
            _visitorId.onNext(newId)
        }
    }

    override fun clearStoredVisitorIds() {
        logger.debug?.log("VisitorIdProvider", "Clearing stored visitor ids")

        generateVisitorId().let { newId ->
            visitorStorage.clear(newId)
            _visitorId.onNext(newId)
        }
    }

    override fun identify(identity: String) {
        if (identity.isBlank()) return

        val oldIdentity = visitorStorage.currentIdentity
        val hashedNewIdentity = identity.sha256()

        if (hashedNewIdentity == oldIdentity) return

        logger.debug?.log("VisitorIdProvider", "Identity change has been detected.")

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
        logger.debug?.log(
            "VisitorIdProvider",
            "Identity has been seen before; setting known visitor id"
        )

        visitorStorage.changeVisitor(knownVisitorId, identity)

        _visitorId.onNext(knownVisitorId)
    }

    private fun handleChangedIdentity(identity: String) {
        logger.debug?.log(
            "VisitorIdProvider",
            "Identity has been seen before; but visitor id has not changed"
        )

        visitorStorage.changeIdentity(identity)
    }

    private fun handleFirstIdentity(identity: String) {
        logger.debug?.log(
            "VisitorIdProvider",
            "Identity unknown; linking to current visitor id"
        )

        visitorStorage.changeVisitor(visitorId.value, identity)
    }

    private fun handleNewIdentity(identity: String) {
        logger.debug?.log("VisitorIdProvider", "Identity unknown; resetting visitor id")

        val newVisitorId = generateVisitorId()
        visitorStorage.changeVisitor(newVisitorId, identity)

        _visitorId.onNext(newVisitorId)
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
            visitorStorage.changeVisitor(migratedVisitorId)
            migrator.delete()
            return migratedVisitorId
        }

        val visitorId = existingVisitorId ?: generateVisitorId()
        visitorStorage.changeVisitor(visitorId)
        return visitorId
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