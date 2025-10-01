package com.tealium.prism.core.api.consent

import com.tealium.prism.core.api.pubsub.Observable

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
     * An observable of the [ConsentDecision]s from the visitor.
     */
    val consentDecision: Observable<ConsentDecision?>

    /**
     * Returns all possible purposes from the CMP, if available.
     */
    val allPurposes: Set<String>?
}