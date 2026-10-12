package com.tealium.prism.core.internal.modules.visitorid

import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.filterNotNull
import com.tealium.prism.core.internal.persistence.VisitorIdStorage

object IdentityUpdatedObserver {

    /**
     * Subscribes the given [visitorIdStorage] to receive identity updates from the provided
     * [dataLayer].
     *
     * Identity updates are derived from updates to the given [dataLayer] using the
     * [VisitorIdConfiguration.visitorIdentityKey]. If a value exists at that key in the [dataLayer]
     * update then the value will be taken as the identity and the [visitorIdStorage] will be
     * notified.
     *
     * @param configuration The module configuration observable to derive the identity key from
     * @param dataLayer The [DataStore] to monitor for identity changes
     * @param visitorIdStorage The [VisitorIdStorage] to notify of identity updates
     */
    fun subscribeIdentityUpdates(
        configuration: Observable<VisitorIdConfiguration>,
        dataLayer: DataStore,
        visitorIdStorage: VisitorIdStorage
    ): Disposable {
        return configuration.map(VisitorIdConfiguration::visitorIdentityKey)
            .distinct()
            .flatMapLatest { idKey ->
                identityUpdates(dataLayer, idKey)
            }.distinct()
            .subscribe(visitorIdStorage::identify)
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
