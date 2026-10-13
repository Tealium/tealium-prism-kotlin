package com.tealium.prism.core.internal.modules.visitorid

import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.filterNotNull

object IdentityUpdatedObserver {

    /**
     * Subscribes the given [identify] callback to receive identity updates from the provided
     * [dataLayer].
     *
     * Identity updates are derived from writes at the latest
     * [VisitorIdConfiguration.visitorIdentityKey]: on each write, [identify] is called with the
     * value read from the [dataLayer] at that key afterwards, and not called if no value is present
     * (for example the same transaction also removed it, or it is already expired). Removals are
     * not reported, as an identity leaving the data layer never changes the visitor id. A `null`
     * key disables Visitor Switching.
     *
     * Consecutive writes of the same identity are all reported: after the identity is removed and
     * the visitor id regenerated, writing the same identity again must restore its visitor id.
     *
     * Both the identity key and the actual data inside of the [dataLayer] can change, and all of
     * those changes are reacted to, so that only the latest identity, stored at the latest identity
     * key, is identified.
     *
     * @param configuration The module configuration observable to derive the identity key from
     * @param dataLayer The [DataStore] to monitor for identity changes
     * @param identify The callback to notify of identity updates
     */
    fun subscribeIdentityUpdates(
        configuration: Observable<VisitorIdConfiguration>,
        dataLayer: DataStore,
        identify: (String) -> Unit
    ): Disposable {
        return configuration.map(VisitorIdConfiguration::visitorIdentityKey)
            .distinct()
            .flatMapLatest { idKey ->
                identityUpdates(dataLayer, idKey)
            }
            .subscribe { identity -> identify(identity) }
    }

    private fun identityUpdates(dataLayer: DataStore, key: String?): Observable<String> {
        if (key == null) return Observables.empty()

        // Re-read the store, as the event payload may not be the value actually present: a put
        // whose expiry has already passed is still published, and a transaction that both puts and
        // removes the key publishes each edit separately, whatever the edit order.
        return dataLayer.onDataUpdated
            .filter { it.get(key) != null }
            .map { dataLayer.getString(key) }
            .startWith(dataLayer.getString(key))
            .filterNotNull()
    }
}
