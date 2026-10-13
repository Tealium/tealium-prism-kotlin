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
     * Stored identity associations are left untouched. With a non null [ownerIdentity], that
     * identity takes ownership of the new id and its stored association is updated. With a null
     * [ownerIdentity], the new id is unowned.
     *
     * @param ownerIdentity The hashed identity present in the data layer at the time of the call,
     *  or `null` if none is present
     * @return The new anonymous visitor id
     * @throws PersistenceException When the new visitor id could not be stored. The new id is still
     *  published to [visitorId] and used until the next launch, when the stored one is restored
     */
    @Throws(PersistenceException::class)
    fun regenerate(ownerIdentity: String?): String

    /**
     * Removes the owner identity and every stored identity association, and generates a new
     * anonymous visitor id.
     *
     * With a non null [ownerIdentity], that identity is immediately associated with the new id
     * and becomes its owner, so one new association exists afterwards. With a null
     * [ownerIdentity], the new id is unowned and no association remains.
     *
     * @param ownerIdentity The hashed identity present in the data layer at the time of the call,
     *  or `null` if none is present
     * @return The new anonymous visitor id
     * @throws PersistenceException When the stored ids could not be cleared, in which case nothing
     *  is erased. The new id is still published to [visitorId] and used until the next launch, when
     *  the stored visitor id and associations are restored
     */
    @Throws(PersistenceException::class)
    fun eraseLocalHistory(ownerIdentity: String?): String

    /**
     * Notifies that an identity has been written to the data layer.
     *
     * A blank [identity] is ignored. So is the identity that already owns the current visitor id,
     * as its association is then already up to date.
     *
     * When the [identity] has been seen before, the visitor id is restored to the one it was last
     * associated with and the [identity] becomes its owner, even when that id is already the
     * current one. Otherwise, when no identity currently owns the visitor id, the [identity]
     * adopts the current visitor id; when another identity owns it, a new visitor id is generated
     * for the [identity].
     *
     * This is driven by data layer updates rather than by an api caller, so it does not throw; a
     * failed write is logged. A visitor switch is still published to [visitorId] and used until
     * the next launch, when the stored visitor id is restored and the switch is retried the next
     * time the same identity is seen.
     *
     * @param identity The identity written at the configured identity key
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

    override fun regenerate(ownerIdentity: String?): String {
        logger.debug(LogCategory.VISITOR_ID, "Regenerating current visitor id")

        return generateVisitorId().also { newId ->
            try {
                visitorStorage.changeVisitor(newId, ownerIdentity)
            } finally {
                // published even when not stored: the caller is told by the exception, and the
                // stored id is restored on the next launch
                _visitorId.onNext(newId)
            }
        }
    }

    override fun eraseLocalHistory(ownerIdentity: String?): String {
        logger.debug(LogCategory.VISITOR_ID, "Erasing stored identity associations.")

        return generateVisitorId().also { newId ->
            try {
                visitorStorage.clear(newId, ownerIdentity)
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
            // Writes the full association even when knownVisitorId already matches the current
            // visitor id. That case is reached after changeVisitor throws: it publishes the new id
            // in memory before the write is confirmed, so a repeat of the same identity sees
            // knownVisitorId == visitorId.value. If that repeat wrote only the owner, a failing
            // store would leave the stored visitor_id wrong for good, as nothing else retries it
            handleExistingIdentity(knownVisitorId, hashedNewIdentity)
        } else if (oldIdentity == null) {
            handleFirstIdentity(hashedNewIdentity)
        } else {
            handleNewIdentity(hashedNewIdentity)
        }
    }

    private fun handleExistingIdentity(knownVisitorId: String, identity: String) {
        val message = if (knownVisitorId == visitorId.value) {
            // also reached when a previous switch to this id failed to store: the id is already
            // in memory, so storing it again completes the switch without a new emission
            "Identity has been seen before and already uses the current visitor id; storing it as owner"
        } else {
            "Identity has been seen before; setting known visitor id"
        }
        logger.debug(LogCategory.VISITOR_ID, message)

        changeVisitor(knownVisitorId, identity)
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
            visitorStorage.changeVisitor(visitorId, identity)
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
            visitorStorage.changeVisitor(migratedVisitorId, null)
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