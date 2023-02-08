package com.tealium.core.api

import com.tealium.core.api.data.ObservableProperty

interface VisitorService {

    fun interface VisitorIdUpdatedListener {
        fun onVisitorIdUpdated(visitorId: String)
    }
    fun interface VisitorProfileUpdatedListener {
        fun onVisitorProfileUpdated(profile: VisitorProfile)
    }

    val visitorId: ObservableProperty<String, VisitorIdUpdatedListener>
    val visitorProfile: ObservableProperty<VisitorProfile, VisitorProfileUpdatedListener>

    fun resetVisitorId()
    fun clearStoredVisitorIds()
}