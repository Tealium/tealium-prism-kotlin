package com.tealium.core.internal.consent

import com.tealium.core.api.ConsentDecision
import com.tealium.core.api.ConsentStatus
import com.tealium.core.api.Dispatch

// TODO - implement Consent Manager
class ConsentManagerImpl: ConsentManager {

    override var consentStatus: ConsentStatus
        get() = ConsentStatus.Consented
        set(value) {}
    override val enabled: Boolean
        get() = false

    override fun getConsentDecision(): ConsentDecision? {
        return null
    }

    override fun tealiumConsented(purposes: Set<String>): Boolean {
        return true
    }

    override fun applyConsent(dispatch: Dispatch) {

    }
}