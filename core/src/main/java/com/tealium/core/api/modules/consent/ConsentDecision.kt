package com.tealium.core.api.modules.consent

/**
 * Describes a decision by the user as to their chosen consent preferences. This is expected to be
 * provided by a Consent Management Provider.
 *
 * @param decisionType What type of decision this is.
 * @param purposes The purposes that have been consented to
 */
data class ConsentDecision(
    val decisionType: DecisionType,
    val purposes: Set<String>
) {

    /**
     * The type of consent decision that has been made.
     *
     * [Implicit] when a decision has not yet been made by the user, but the jurisdiction allows for
     * tracking based on implied consent.
     *
     * [Explicit] when a specific decision has been made by the user.
     */
    enum class DecisionType {
        Implicit, Explicit
    }
}