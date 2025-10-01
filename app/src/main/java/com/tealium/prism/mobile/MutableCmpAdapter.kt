package com.tealium.prism.mobile

import com.tealium.prism.core.api.consent.CmpAdapter
import com.tealium.prism.core.api.consent.ConsentDecision

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