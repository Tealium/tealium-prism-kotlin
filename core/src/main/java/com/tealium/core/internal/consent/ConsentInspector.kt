package com.tealium.core.internal.consent

import com.tealium.core.api.consent.ConsentDecision
import com.tealium.core.internal.settings.consent.ConsentConfiguration

data class ConsentInspector(
    val configuration: ConsentConfiguration,
    val decision: ConsentDecision,
    val allPurposes: Set<String>?
) {
    fun tealiumConsented(): Boolean =
        decision.purposes.contains(configuration.tealiumPurposeId)

    fun tealiumExplicitlyBlocked(): Boolean =
        decision.decisionType == ConsentDecision.DecisionType.Explicit
                && !tealiumConsented()

    fun allowsRefire(): Boolean =
        decision.decisionType == ConsentDecision.DecisionType.Implicit &&
                configuration.refireDispatcherIds.isNotEmpty() &&
                !allPurposesAreMatched()

    private fun allPurposesAreMatched(): Boolean {
        if (allPurposes == null) {
            // Don't yet know the purposes, so assume not all matched
            return false
        }
        return decision.matchAll(allPurposes)
    }
}