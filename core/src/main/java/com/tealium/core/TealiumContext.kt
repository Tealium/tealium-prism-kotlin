package com.tealium.core

import android.content.Context
import com.tealium.core.api.*
import com.tealium.core.api.data.ObservablesFactory
import com.tealium.core.internal.persistence.DataStoreProvider

class TealiumContext(
    val context: Context,
    val coreSettings: CoreSettings,
    // TODO
    val dataLayer: DataLayer,
    val events: MessengerService,
    val logger: Logging,
    // TODO - find a better place to access this?
    val observables: ObservablesFactory,
    visitorId: String, // todo
    val storageProvider: DataStoreProvider,

    private val tealium: Tealium
) {
    private val _visitorId = visitorId
    val visitorId: String
        get() {
            //TODO()
              //tealium.
            return _visitorId
        }

    fun track(dispatch: Dispatch) {
        tealium.track(dispatch)
    }
}
