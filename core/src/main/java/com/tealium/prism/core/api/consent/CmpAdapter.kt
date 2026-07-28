package com.tealium.prism.core.api.consent

import com.tealium.prism.core.api.pubsub.Observable

/**
 * The [CmpAdapter] provides a consistent interface with external Consent Management
 * Providers (CMP).
 */
interface CmpAdapter {

    /**
     * The unique identifier for this [CmpAdapter].
     */
    val id: String

    /**
     * An observable of the [ConsentDecision]s from the visitor.
     *
     * Subscriptions to this observable always come from the Tealium background scheduler. You must
     * emit these decisions from a single thread and return an observable that applies
     * [Observable.subscribeOn] with a scheduler backed by the same thread.
     *
     * For example, if the underlying CMP dispatches its events on the Android main thread, push those
     * decisions into a subject from the main thread and pair it with `Scheduler.MAIN`:
     *
     * ```kotlin
     * // the CMP pushes decisions into `decisionSubject` on the main thread
     * private val decisionSubject = Observables.stateSubject<ConsentDecision?>(null)
     *
     * override val consentDecision: Observable<ConsentDecision?>
     *     get() = decisionSubject.asObservable()
     *         .subscribeOn(Scheduler.MAIN)
     * ```
     *
     * **Warning.** Emitting from a scheduler other than the one passed to [Observable.subscribeOn]
     * will cause race conditions. See [Observable.subscribeOn] for the full contract.
     */
    val consentDecision: Observable<ConsentDecision?>

    /**
     * Returns all possible purposes from the CMP, if available.
     */
    val allPurposes: Set<String>?
}