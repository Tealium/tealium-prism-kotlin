package com.tealium.core.api

import com.tealium.core.api.listeners.Subscribable

interface VisitorService {

    fun interface VisitorIdUpdatedListener {
        fun onVisitorIdUpdated(visitorId: String)
    }

    fun interface VisitorProfileUpdatedListener {
        fun onVisitorProfileUpdated(profile: VisitorProfile)
    }

    val onVisitorIdUpdated: Subscribable<String>

    val onVisitorProfileUpdated: Subscribable<VisitorProfile>


    fun resetVisitorId()
    fun clearStoredVisitorIds()
}