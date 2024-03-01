package com.tealium.core.api

import com.tealium.core.internal.observables.Observable

interface ConsentManager {
    var consentStatus: ConsentStatus
    //TODO
}

data class ConsentDecision(
    val decisionType: DecisionType,
    val purposes: Set<String>
) {
    enum class DecisionType {
        Implicit, Explicit
    }
}


interface ConsentManagementAdapter {

    val onConsentDecision: Observable<ConsentDecision>
    fun getConsentDecision(): ConsentDecision?
}