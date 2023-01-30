package com.tealium.core

import android.content.Context
import com.tealium.core.api.DataLayer
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Logging
import com.tealium.core.api.MessengerService

class TealiumContext(
    val context: Context,
    // TODO
    val dataLayer: DataLayer,
    val events: MessengerService,
    val logger: Logging,
//    val visitorId: String, // todo

    private val tealium: Tealium
) {
    fun track(dispatch: Dispatch) {
        tealium.track(dispatch)
    }
}
