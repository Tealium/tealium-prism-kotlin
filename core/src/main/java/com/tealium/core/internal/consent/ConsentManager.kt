package com.tealium.core.internal.consent

import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.internal.settings.consent.ConsentConfiguration

/**
 * This is the internal [ConsentManager] used by various internal components to determine the current
 * consent status of the current visitor.
 */
interface ConsentManager {

    /**
     * The Tealium SDK can be explicitly consented to, or not.
     *
     * This method will return whether or not the Tealium purpose has been explicitly denied.
     */
    val tealiumPurposeExplicitlyBlocked: Boolean

    /**
     * And [Observable] flow of the selected [ConsentConfiguration]
     */
    val configuration: Observable<ConsentConfiguration?>

    /**
     * Adds consent context information to the given [dispatch].
     *
     * @param dispatch The dispatch to add consent data to
     */
    fun applyConsent(dispatch: Dispatch): TrackResult
}