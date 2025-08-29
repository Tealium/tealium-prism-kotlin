package com.tealium.mobile

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit
import com.tealium.core.api.consent.ConsentDecision
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject

class ExampleCmpAdapter(
    private val sharedPreferences: SharedPreferences
) : MutableCmpAdapter {

    constructor(context: Context) : this(context.getSharedPreferences("tealium-cmp", MODE_PRIVATE))

    private val _consentDecision: StateSubject<ConsentDecision?>

    override val allPurposes: Set<String>
        get() = setOf(Purposes.TEALIUM, Purposes.TRACKING, Purposes.FUNCTIONAL)

    override val defaultDecision: ConsentDecision =
        ConsentDecision(ConsentDecision.DecisionType.Implicit, setOf(Purposes.TEALIUM))

    init {
        val savedDecision = readDecision()
        _consentDecision = Observables.stateSubject(savedDecision)
    }

    override val id: String
        get() = "ExampleCmpAdapter"

    override val consentDecision: Observable<ConsentDecision?>
        get() = _consentDecision.asObservableState()

    override fun setConsentDecision(consentDecision: ConsentDecision) {
        saveDecision(consentDecision)
        _consentDecision.onNext(consentDecision)
    }

    private fun readDecision(): ConsentDecision {
        val decisionTypeStr = sharedPreferences.getString(KEY_DECISION_TYPE, null)
        val decisionPurposes = sharedPreferences.getStringSet(KEY_DECISION_PURPOSES, null)
        if (decisionTypeStr == null || decisionPurposes == null) {
            return defaultDecision
        }

        val decisionType = if (decisionTypeStr.lowercase() == "explicit") {
            ConsentDecision.DecisionType.Explicit
        } else {
            ConsentDecision.DecisionType.Implicit
        }

        return ConsentDecision(decisionType, decisionPurposes)
    }

    private fun saveDecision(consentDecision: ConsentDecision) {
        sharedPreferences.edit {
            putString(KEY_DECISION_TYPE, consentDecision.decisionType.name.lowercase())
            putStringSet(KEY_DECISION_PURPOSES, consentDecision.purposes)
        }
    }

    private companion object {
        const val KEY_DECISION_TYPE = "decision_type"
        const val KEY_DECISION_PURPOSES = "decision_purposes"
    }

    object Purposes {
        const val TEALIUM = "tealium"
        const val TRACKING = "tracking"
        const val FUNCTIONAL = "functional"
    }
}
