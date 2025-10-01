package com.tealium.prism.core.api.consent

import com.tealium.prism.core.api.consent.ConsentDecision.DecisionType.Explicit
import com.tealium.prism.core.api.consent.ConsentDecision.DecisionType.Implicit
import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem

/**
 * Describes a decision by the user as to their chosen consent preferences. This is expected to be
 * provided by a Consent Management Provider.
 *
 * @param decisionType What type of decision this is.
 * @param purposes The purposes that have been consented to
 */
data class ConsentDecision(
    val decisionType: DecisionType,
    val purposes: Set<String>
) {

    /**
     * The type of consent decision that has been made.
     *
     * [Implicit] when a decision has not yet been made by the user, but the jurisdiction allows for
     * tracking based on implied consent.
     *
     * [Explicit] when a specific decision has been made by the user.
     */
    enum class DecisionType: DataItemConvertible {
        Implicit, Explicit;

        override fun asDataItem(): DataItem =
            this.name
                .lowercase()
                .asDataItem()
    }
}