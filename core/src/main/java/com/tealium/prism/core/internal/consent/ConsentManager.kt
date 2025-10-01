package com.tealium.prism.core.internal.consent

import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.internal.settings.consent.ConsentConfiguration

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
     * Adds consent context information to the given [Dispatch] and enqueues it.
     *
     * If the consent decision explicitly denies the tealium purpose or if no purpose is provided,
     * then the dispatch is dropped instead.
     *
     * If consent is not ready at this time, then the [Dispatch] will be queued in a consent specific queue.
     * It will later be dequeued, once consent has a [ConsentDecision] and a [ConsentConfiguration],
     * and enqueued again for the [Dispatcher]s with the additional consent context information.
     *
     * Additionally, the [Dispatch] can also be queued in the consent specific queue to be later re-fired.
     * Re-fired events are events that have previously been sent with some implicit purposes, but later,
     * when the user gives an explicit consent, can be sent again with the additional consented purposes.
     *
     * To enable re-firing, provide a non-empty list of refireDispatchers in the [ConsentConfiguration].
     *
     * @param dispatch The [Dispatch] to add consent data to.
     * @return A [TrackResult] that can be [TrackResult.Accepted], if the dispatch is enqueued in the consent
     * or dispatchers queue, or [TrackResult.Dropped] if the dispatch is dropped.
     */
    fun applyConsent(dispatch: Dispatch): TrackResult
}