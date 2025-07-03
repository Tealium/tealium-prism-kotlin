package com.tealium.core.api.consent

import com.tealium.core.api.pubsub.Subscribable

/**
 * The [CmpAdapter] provides a consistent interface with external Consent Management
 * Providers (CMP).
 */
interface CmpAdapter {

    /**
     * The unique identifier for this [CmpAdapter].
     */
    val id: String

    /**
     * An observable flow of the [ConsentDecision]s from the visitor.
     */
    val consentDecision: Subscribable<ConsentDecision?>

    /**
     * Returns all possible purposes from the CMP.
     */
    val allPurposes: Set<String>
}