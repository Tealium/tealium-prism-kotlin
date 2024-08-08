package com.tealium.core.internal.modules.consent

import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.transform.DispatchScope
import com.tealium.core.api.transform.Transformer
import com.tealium.core.api.pubsub.ObservableState

/**
 * The [ConsentTransformer] is responsible for
 */
class ConsentTransformer(
    private val settings: ObservableState<ConsentSettings>
) : Transformer {

    override val id: String
        get() = "ConsentTransformer"

    val enabled: Boolean
        get() = settings.value.enabled

    override fun applyTransformation(
        transformationId: String,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        if (!enabled) {
            completion(dispatch)
            return
        }

        if (scope is DispatchScope.Dispatcher) {
            val requiredPurposes =
                settings.value.dispatcherPurposes[scope.dispatcher]
                    ?: emptyList()
            if (requiredPurposes.isNotEmpty() && !isConsented(dispatch, requiredPurposes)) {
                completion(null)
                return
            }
        }

        completion(dispatch)
    }

    private fun isConsented(dispatch: Dispatch, requiredPurposes: List<String>): Boolean {
        val consentedPurposes =
            dispatch.payload().getList(Dispatch.Keys.PURPOSES_WITH_CONSENT_ALL)?.mapNotNull {
                it.getString()
            } ?: return false

        return consentedPurposes.containsAll(requiredPurposes)
    }

}