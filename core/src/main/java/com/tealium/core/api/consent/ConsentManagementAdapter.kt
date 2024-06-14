package com.tealium.core.api.consent

import com.tealium.core.api.listeners.SubscribableState

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