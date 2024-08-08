package com.tealium.core.api.modules.consent

import com.tealium.core.api.pubsub.SubscribableState

/**
 * The [ConsentManagementAdapter] provides a consistent interface with external Consent Management
 * Providers (CMP).
 */
interface ConsentManagementAdapter {

    /**
     * An observable flow of the [ConsentDecision]s from the visitor.
     */
    val consentDecision: SubscribableState<ConsentDecision?>

    /**
     * Returns all possible purposes from the CMP.
     */
    fun getAllPurposes(): Set<String>
}