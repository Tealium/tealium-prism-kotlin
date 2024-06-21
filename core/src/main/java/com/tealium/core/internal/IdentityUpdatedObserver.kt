package com.tealium.core.internal

import com.tealium.core.api.DataStore
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.filterNotNull
import com.tealium.core.internal.settings.CoreSettings

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