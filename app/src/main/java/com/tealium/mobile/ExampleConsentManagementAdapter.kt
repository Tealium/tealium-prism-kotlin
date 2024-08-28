package com.tealium.mobile

import com.tealium.core.api.modules.consent.ConsentDecision
import com.tealium.core.api.modules.consent.ConsentManagementAdapter
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.SubscribableState

class ExampleConsentManagementAdapter: ConsentManagementAdapter {
    override val consentDecision: SubscribableState<ConsentDecision?>
        get() = Observables.stateSubject(ConsentDecision(ConsentDecision.DecisionType.Implicit, setOf()))

    override fun getAllPurposes(): Set<String> {
        return setOf()
    }
}