package com.tealium.core.internal.dispatch

import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TrackResultListener

interface DispatchManager {
    val tealiumPurposeExplicitlyBlocked: Boolean
    fun track(dispatch: Dispatch)
    fun track(dispatch: Dispatch, onComplete: TrackResultListener?)
}
