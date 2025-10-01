package com.tealium.prism.core.internal.consent

import com.tealium.prism.core.api.consent.CmpAdapter
import com.tealium.prism.core.api.consent.ConsentDecision
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.SubscribableState

class MockCmpAdapter(
    override val id: String = "vendor1",
    override val allPurposes: Set<String> = emptySet(),
) : CmpAdapter {
    private val _consentDecision: StateSubject<ConsentDecision?> =
        Observables.stateSubject(null)
    override val consentDecision: Observable<ConsentDecision?>
        get() = _consentDecision.asObservableState()

    fun setDecision(decisionType: ConsentDecision.DecisionType, purposes: Set<String>) {
        _consentDecision.onNext(ConsentDecision(decisionType, purposes))
    }
}