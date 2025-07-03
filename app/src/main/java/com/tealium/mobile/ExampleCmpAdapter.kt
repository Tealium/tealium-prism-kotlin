package com.tealium.mobile

import com.tealium.core.api.consent.ConsentDecision
import com.tealium.core.api.consent.CmpAdapter
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.SubscribableState

class ExampleCmpAdapter: CmpAdapter {

    override val id: String
        get() = "ExampleCmpAdapter"

    override val consentDecision: SubscribableState<ConsentDecision?>
        get() = Observables.stateSubject(ConsentDecision(ConsentDecision.DecisionType.Implicit, setOf()))

    override val allPurposes: Set<String>
        get() = setOf()
}