package com.tealium.core

import android.content.Context
import com.tealium.core.api.*

class TealiumContext(
    val context: Context,
    val coreSettings: CoreSettings,
    // TODO
    val dataLayer: DataLayer,
    val logger: Logging,
    // TODO - find a better place to access this?
    visitorId: String, // todo
    val storageProvider: ModuleStoreProvider,

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
