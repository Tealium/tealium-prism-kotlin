package com.tealium.mobile

import com.tealium.core.api.consent.CmpAdapter
import com.tealium.core.api.consent.ConsentDecision

interface MutableCmpAdapter: CmpAdapter {

    /**
     * The default decision to use when no decision has been made.
     */
    val defaultDecision: ConsentDecision

    /**
     * Updates the [ConsentDecision]
     */
    fun setConsentDecision(consentDecision: ConsentDecision)

}