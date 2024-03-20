package com.tealium.core.internal.dispatch

import com.tealium.core.api.Dispatch
import com.tealium.core.api.listeners.TrackResultListener

interface DispatchManager {
    fun track(dispatch: Dispatch)
    fun track(dispatch: Dispatch, onComplete: TrackResultListener?)
}
