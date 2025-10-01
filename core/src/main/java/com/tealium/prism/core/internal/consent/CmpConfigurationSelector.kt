package com.tealium.prism.core.internal.consent

import com.tealium.prism.core.api.consent.CmpAdapter
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.internal.pubsub.addTo
import com.tealium.prism.core.internal.settings.consent.ConsentConfiguration
import com.tealium.prism.core.internal.settings.consent.ConsentSettings

/**
 * A utility class that can select the right [ConsentConfiguration] based on the [ConsentSettings]
 * and [CmpAdapter].
 */
class CmpConfigurationSelector(
    private val consentSettings: ObservableState<ConsentSettings?>,
    val cmpAdapter: CmpAdapter,
    scheduler: Scheduler
) {
    private val _consentInspector: StateSubject<ConsentInspector?> = Observables.stateSubject(null)

    val configuration: ObservableState<ConsentConfiguration?>
    val consentInspector: ObservableState<ConsentInspector?>
        get() = _consentInspector.asObservableState()

    private val disposables = DisposableContainer()

    init {
        configuration = consentSettings.map(::extractConsentConfiguration)
            .withState { extractConsentConfiguration(consentSettings.value) }

        val consentDecisions = cmpAdapter.consentDecision
            .distinct()
            .observeOn(scheduler)
        configuration.combine(consentDecisions) { config, decision ->
            if (config != null && decision != null) {
                ConsentInspector(config, decision, cmpAdapter.allPurposes)
            } else {
                null
            }
        }.subscribe(_consentInspector)
            .addTo(disposables)
    }

    private fun extractConsentConfiguration(consentSettings: ConsentSettings?): ConsentConfiguration? =
        consentSettings?.configurations?.get(cmpAdapter.id)
}