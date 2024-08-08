package com.tealium.core.api.modules

import com.tealium.core.api.pubsub.Subscribable

interface VisitorService {

    val onVisitorIdUpdated: Subscribable<String>

    val onVisitorProfileUpdated: Subscribable<VisitorProfile>

    fun resetVisitorId()
    fun clearStoredVisitorIds()
}