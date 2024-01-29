package com.tealium.core.api

import kotlinx.coroutines.flow.Flow

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

    val onConsentDecision: Flow<ConsentDecision>
    fun getConsentDecision(): ConsentDecision?
}