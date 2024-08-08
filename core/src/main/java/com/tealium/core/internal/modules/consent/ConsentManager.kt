package com.tealium.core.internal.modules.consent

import com.tealium.core.api.modules.consent.ConsentDecision
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.modules.Module

/**
 * This is the internal [ConsentManager] used by various internal components to determine the current
 * consent status of the current visitor.
 */
interface ConsentManager: Module {

    /**
     * Whether or not the [ConsentManager] is enabled.
     */
    val enabled: Boolean

    /**
     * Retrieves the current [ConsentDecision] if available.
     */
    fun getConsentDecision(): ConsentDecision?

    /**
     * The Tealium SDK can be explicitly consented to, or not.
     * This method will return whether or not the Tealium purpose is consented to, and thus, whether
     * or not any further processing of events is safe.
     *
     * @param purposes The current set of consented purposes.
     */
    fun tealiumConsented(purposes: Set<String>) : Boolean

    /**
     * Adds consent context information to the given [dispatch].
     *
     * @param dispatch The dispatch to add consent data to
     */
    fun applyConsent(dispatch: Dispatch)
}