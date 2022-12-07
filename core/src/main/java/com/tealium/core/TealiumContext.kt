package com.tealium.core

import android.content.Context
import com.tealium.core.api.Dispatch
import com.tealium.core.api.TealiumEvent

class TealiumContext(
    val context: Context,
    private val tealium: Tealium
) {
    fun track(dispatch: Dispatch) {
        tealium.track(dispatch)
    }
}
