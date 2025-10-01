package com.tealium.prism.core.internal.persistence

import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.settings.CoreSettings
import com.tealium.prism.core.internal.pubsub.filterNotNull

object IdentityUpdatedObserver {

    /**
     * Subscribes the given [visitorIdProvider] to receive identity updates from the provided
     * [dataLayer].
     *
     * Identity updates are derived from updates to the given [dataLayer] using the
     * [CoreSettings.visitorIdentityKey]. If a value exists at that key in the [dataLayer] update
     * then the value will be taken as the identity and the [visitorIdProvider] will be notified.
     *
     * @param coreSettings The [CoreSettings] observable to derive the identity key from
     * @param dataLayer The [DataStore] to monitor for identity changes
     * @param visitorIdProvider The [VisitorIdProvider] to notify of identity updates
     */
    fun subscribeIdentityUpdates(
        coreSettings: Observable<CoreSettings>,
        dataLayer: DataStore,
        visitorIdProvider: VisitorIdProvider
    ): Disposable {
        return coreSettings.map(CoreSettings::visitorIdentityKey)
            .distinct()
            .flatMapLatest { idKey ->
                identityUpdates(dataLayer, idKey)
            }.distinct()
            .subscribe(visitorIdProvider::identify)
    }

    private fun identityUpdates(dataLayer: DataStore, key: String?): Observable<String> {
        return if (key == null) {
            Observables.empty()
        } else {
            dataLayer.onDataUpdated
                .map { it.getString(key) }
                .startWith(dataLayer.getString(key))
                .filterNotNull()
        }
    }
}