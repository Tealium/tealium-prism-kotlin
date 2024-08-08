package com.tealium.core.internal.modules.consent

import com.tealium.core.api.tracking.Dispatch

// TODO - stub only so far
interface ConsentQueue {
    fun enqueue(dispatch: Dispatch)
    fun dequeueAll() : List<Dispatch>
    fun clear() : List<Dispatch>
}