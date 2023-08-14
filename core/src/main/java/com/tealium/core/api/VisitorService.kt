package com.tealium.core.api

import kotlinx.coroutines.flow.StateFlow

interface VisitorService {

    fun interface VisitorIdUpdatedListener {
        fun onVisitorIdUpdated(visitorId: String)
    }

    fun interface VisitorProfileUpdatedListener {
        fun onVisitorProfileUpdated(profile: VisitorProfile)
    }

    val visitorId: String
    val onVisitorIdUpdated: StateFlow<String>

    val visitorProfile: VisitorProfile
    val onVisitorProfileUpdated: StateFlow<VisitorProfile>


    fun resetVisitorId()
    fun clearStoredVisitorIds()
}