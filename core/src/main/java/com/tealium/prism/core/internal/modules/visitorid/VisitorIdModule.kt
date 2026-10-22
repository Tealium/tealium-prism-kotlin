package com.tealium.prism.core.internal.modules.visitorid

import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.modules.VisitorIdProvider
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.api.settings.modules.VisitorIdSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.persistence.VisitorIdMigrator
import com.tealium.prism.core.internal.persistence.VisitorIdMigratorImpl
import com.tealium.prism.core.internal.persistence.stores.VisitorStorage
import com.tealium.prism.core.internal.persistence.stores.VisitorStorageImpl
import com.tealium.prism.core.internal.utils.sha256
import java.util.UUID

/**
 * Owns the visitor identifiers of the current user.
 *
 * It collects [Dispatch.Keys.TEALIUM_VISITOR_ID] onto every event, switches visitor whenever a new
 * identity is detected at the configured [VisitorIdConfiguration.visitorIdentityKey], and backs the
 * operations of [com.tealium.prism.core.api.Tealium.visitorId].
 *
 * A new visitor id is published to [visitorId] even when it could not be stored, so that the id in
 * memory, emitted to subscribers and added to events, always reflects the latest switch. The
 * caller driven [regenerate] and [eraseLocalHistory] still report a failed write by throwing a
 * [PersistenceException]; the automatic [identify] and visitor changes log the failure and
 * continue. When the write fails nothing is stored, so the previously stored visitor id and its
 * identity associations are restored on the next launch.
 *
 * The module is disableable. While it is disabled there is no visitor id on dispatches, the
 * [com.tealium.prism.core.api.modules.VisitorIdManager] methods return a failure, and identity
 * switching is inactive.
 *
 * @param visitorStorage The [VisitorStorage] holding the visitor identifiers, backed by this
 *  module's own [DataStore]
 * @param migrator The [VisitorIdMigrator] providing a visitor id from a previous SDK version, if any
 * @param existingVisitorId The user configured [TealiumConfig.existingVisitorId], used on first
 *  launch when no visitor id is stored or migrated
 * @param dataLayer The [DataStore] to watch for identity updates
 * @param configuration The initial module configuration
 * @param logger The [Logger] to report visitor changes and failed writes to
 */
class VisitorIdModule(
    private val visitorStorage: VisitorStorage,
    private val migrator: VisitorIdMigrator,
    private val existingVisitorId: String?,
    private val dataLayer: DataStore,
    configuration: VisitorIdConfiguration,
    private val logger: Logger
) : Collector, VisitorIdProvider {

    private val disposables: CompositeDisposable = Disposables.composite()

    private val configuration: StateSubject<VisitorIdConfiguration> =
        Observables.stateSubject(configuration)

    private val _visitorId: StateSubject<String>

    /**
     * Observable flow of the current visitor id.
     *
     * Emits every new id, including ones that could not be stored and therefore only last until
     * the next launch.
     */
    override val visitorId: ObservableState<String>
        get() = _visitorId.asObservableState()

    init {
        val visitorId = getOrCreateVisitorId()
        _visitorId = Observables.stateSubject(visitorId)

        if (visitorId != visitorStorage.visitorId) {
            changeVisitor(visitorId, null)
        }

        IdentityUpdatedObserver.subscribeIdentityUpdates(
            this.configuration,
            dataLayer,
            ::identify
        ).addTo(disposables)
    }

    override fun collect(dispatchContext: DispatchContext): DataObject = DataObject.create {
        put(Dispatch.Keys.TEALIUM_VISITOR_ID, _visitorId.value)
    }

    override fun updateConfiguration(configuration: DataObject): Module {
        this.configuration.onNext(VisitorIdConfiguration.fromDataObject(configuration))
        return this
    }

    override fun onShutdown() {
        disposables.dispose()
    }

    /**
     * Regenerates the current visitor id as a new anonymous one.
     *
     * The identity present in the data layer at the time of the call, if any, takes ownership of
     * the new id and its stored association is updated. If no identity is present, the new id is
     * unowned.
     *
     * @return The new anonymous visitor id
     * @throws PersistenceException When the new visitor id could not be stored. The new id is still
     *  published to [visitorId] and used until the next launch, when the stored one is restored
     */
    @Throws(PersistenceException::class)
    fun regenerate(): String {
        logger.debug(LogCategory.VISITOR_ID, "Regenerating current visitor id")

        val ownerIdentity = identityInDataLayer()
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

    /**
     * Erases all stored identity associations, as hashed identities and their visitor ids, and
     * the owner identity, and generates a new anonymous visitor id.
     *
     * If an identity is present in the data layer at the time of the call, it is immediately
     * associated with the new visitor id and becomes its owner, so one new association exists
     * afterwards. Otherwise the new id is unowned and no association remains.
     *
     * @return The new anonymous visitor id
     * @throws PersistenceException When the stored ids could not be cleared, in which case nothing
     *  is erased. The new id is still published to [visitorId] and used until the next launch, when
     *  the stored visitor id and associations are restored
     */
    @Throws(PersistenceException::class)
    fun eraseLocalHistory(): String {
        logger.debug(LogCategory.VISITOR_ID, "Erasing stored identity associations.")

        val ownerIdentity = identityInDataLayer()
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

    /**
     * Reads the identity currently present in the data layer at the configured key.
     *
     * Read at call time rather than cached from data layer events, as reads already exclude
     * expired entries while no removal event is published for them until they are purged.
     *
     * @return The SHA-256 of the identity present in the data layer, or `null` if none is present
     */
    private fun identityInDataLayer(): String? {
        val key = configuration.value.visitorIdentityKey ?: return null
        val identity = dataLayer.getString(key)
        return if (identity.isNullOrBlank()) null else identity.sha256()
    }

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
    fun identify(identity: String) {
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

    /**
     * Stores [visitorId] as the current visitor id, owned by [identity] or unowned when it is
     * `null`, and publishes it when it differs from the current one.
     *
     * Visitor changes are automatic, so a failed write is logged rather than thrown, and the
     * [visitorId] is still published.
     */
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

    override val id: String = Modules.Types.VISITOR_ID
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    class Factory(
        settings: DataObject? = null
    ) : ModuleFactory {

        constructor(settingsBuilder: VisitorIdSettingsBuilder) : this(settingsBuilder.build())

        private val enforcedSettings: List<DataObject> =
            settings?.let { listOf(it) } ?: emptyList()

        override fun getEnforcedSettings(): List<DataObject> = enforcedSettings

        override val moduleType: String = Modules.Types.VISITOR_ID

        override fun create(
            moduleId: String,
            context: TealiumContext,
            configuration: DataObject
        ): Module {
            val moduleStore = context.storageProvider.getModuleStore(moduleId)

            return VisitorIdModule(
                VisitorStorageImpl(moduleStore),
                VisitorIdMigratorImpl(),
                context.config.existingVisitorId,
                context.dataLayer,
                VisitorIdConfiguration.fromDataObject(configuration),
                context.logger
            )
        }
    }
}
