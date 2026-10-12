package com.tealium.prism.core.internal.modules.visitorid

import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
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
import com.tealium.prism.core.internal.persistence.VisitorIdStorage
import com.tealium.prism.core.internal.persistence.VisitorIdStorageImpl

/**
 * Owns the visitor identifiers of the current user.
 *
 * It collects [Dispatch.Keys.TEALIUM_VISITOR_ID] onto every event, switches visitor whenever a new
 * identity is detected at the configured [VisitorIdConfiguration.visitorIdentityKey], and backs the
 * operations of [com.tealium.prism.core.api.Tealium.visitorId].
 *
 * The module is disableable. While it is disabled there is no visitor id on dispatches, the
 * [com.tealium.prism.core.api.modules.VisitorIdManager] methods return a failure, and identity
 * switching is inactive.
 *
 * @param storage The [VisitorIdStorage] holding the visitor identifiers, backed by this module's own
 *  [DataStore]
 * @param configuration The initial module configuration
 * @param dataLayer The [DataStore] to watch for identity updates
 */
class VisitorIdModule(
    private val storage: VisitorIdStorage,
    configuration: VisitorIdConfiguration,
    dataLayer: DataStore
) : Collector, VisitorIdProvider {

    private val disposables: CompositeDisposable = Disposables.composite()

    private val configuration: StateSubject<VisitorIdConfiguration> =
        Observables.stateSubject(configuration)

    override val visitorId: ObservableState<String>
        get() = storage.visitorId

    init {
        IdentityUpdatedObserver.subscribeIdentityUpdates(this.configuration, dataLayer, storage)
            .addTo(disposables)
    }

    /**
     * Replaces the current visitor id with a new anonymous one, keeping its owner identity.
     *
     * @return The new visitor id
     * @throws PersistenceException When the new visitor id could not be stored
     */
    @Throws(PersistenceException::class)
    fun regenerate(): String = storage.regenerate()

    /**
     * Erases every stored identity association and replaces the current visitor id with a new
     * anonymous one.
     *
     * @return The new visitor id
     * @throws PersistenceException When the stored data could not be erased
     */
    @Throws(PersistenceException::class)
    fun eraseLocalHistory(): String = storage.eraseLocalHistory()

    /**
     * Notifies that the identity of the user has changed, switching visitor where required.
     *
     * @param identity The new identity of the user
     */
    fun identify(identity: String) = storage.identify(identity)

    override fun collect(dispatchContext: DispatchContext): DataObject = DataObject.create {
        put(Dispatch.Keys.TEALIUM_VISITOR_ID, storage.visitorId.value)
    }

    override fun updateConfiguration(configuration: DataObject): Module {
        this.configuration.onNext(VisitorIdConfiguration.fromDataObject(configuration))
        return this
    }

    override fun onShutdown() {
        disposables.dispose()
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
                VisitorIdStorageImpl(context.config, moduleStore, context.logger),
                VisitorIdConfiguration.fromDataObject(configuration),
                context.dataLayer
            )
        }
    }
}
