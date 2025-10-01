package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.TrackResultListener

interface DispatchManager {
    val tealiumPurposeExplicitlyBlocked: Boolean
    fun track(dispatch: Dispatch)
    fun track(dispatch: Dispatch, onComplete: TrackResultListener?)
}
