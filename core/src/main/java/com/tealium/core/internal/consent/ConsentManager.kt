package com.tealium.core.internal.consent

import com.tealium.core.api.ConsentDecision
import com.tealium.core.api.ConsentStatus
import com.tealium.core.api.Dispatch

// TODO - stub only so far
interface ConsentManager {
    var consentStatus: ConsentStatus
    //TODO
    val enabled: Boolean
    fun getConsentDecision(): ConsentDecision?

    fun tealiumConsented(purposes: Set<String>) : Boolean

    fun applyConsent(dispatch: Dispatch)
}