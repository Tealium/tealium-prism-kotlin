package com.tealium.core

import android.content.Context
import com.tealium.core.api.CoreSettings
import com.tealium.core.api.DataLayer
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Logging
import com.tealium.core.api.MessengerService
import com.tealium.core.api.data.ObservablesFactory

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
