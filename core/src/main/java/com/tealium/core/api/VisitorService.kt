package com.tealium.core.api

import com.tealium.core.api.listeners.Subscribable

interface VisitorService {

    val onVisitorIdUpdated: Subscribable<String>

    val onVisitorProfileUpdated: Subscribable<VisitorProfile>

    fun resetVisitorId()
    fun clearStoredVisitorIds()
}