package com.tealium.core.api.listeners

import com.tealium.core.api.ConsentStatus

fun interface ConsentStatusUpdatedListener: Listener {
    fun onConsentStatusUpdated(status: ConsentStatus)
}