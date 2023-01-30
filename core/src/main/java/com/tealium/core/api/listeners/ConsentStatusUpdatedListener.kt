package com.tealium.core.api.listeners

import com.tealium.core.api.ConsentStatus

interface ConsentStatusUpdatedListener: Listener {
    fun onConsentStatusUpdated(status: ConsentStatus)
}