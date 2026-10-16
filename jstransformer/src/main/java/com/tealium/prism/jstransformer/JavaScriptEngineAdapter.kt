package com.tealium.prism.jstransformer

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables

// TODO - This will need to support async init
/**
 * Adapter interface for a JavaScript engine capable of evaluating JavaScript code and returning
 * a result as a [DataItem].
 *
 * Implementations are responsible for managing the lifecycle of the underlying engine
 * and must release any resources when [dispose] is called.
 *
 * ## Required JS Environment
 *
 * Implementations must expose the following objects in the JS global scope before any
 * script is evaluated:
 * - `payload` - the current dispatch payload as a mutable object that can be modified by the script
 * - `scope` - the current dispatch scope as a string (e.g. "aftercollectors")
 * - `tracker` — forwards tracking calls back into the native SDK via `tracker.track` Supported signatures include:
 *   - `track(eventName, payload)` - triggers a tracking call with the specified event name and optional payload
 *   - `track(eventName, type, payload)` - triggers a tracking call with the specified event name, type, and payload
 * - `drop()` - a function that allows the script to drop the current dispatch by clearing the `payload` object
 * - `dataLayer` - an object that provides access to the SDK data layer for reading and writing persistent values. Must support at least the following methods:(`get`, `set`, `remove`, `clear`)
 * - `console` — provides logging support (e.g. `console.warn`, `console.log`)
 * - `dataLayer` — exposes the SDK data layer for reading and writing persistent values
 * - `Expiry` — provides expiry policy constants for use with `dataLayer`
 */
interface JavaScriptEngineAdapter : Disposable {

    /**
     * Evaluates the given [js] code and delivers the outcome to [completion] as a [TealiumResult]
     * wrapping a [DataItem].
     *
     * A successful result contains the value returned by the script. A failure result indicates
     * that an error occurred during evaluation.
     *
     * Threading: this method is called on the Tealium thread, and the caller disposes the returned
     * [Disposable] on the Tealium thread. [completion] must be called on the Tealium thread (see
     * [TealiumContext.schedulers][com.tealium.prism.core.api.modules.TealiumContext.schedulers]);
     * it may be called synchronously, before this method returns.
     *
     * Completion: call [completion] exactly once, unless the caller has disposed the returned
     * [Disposable].
     *
     * Cancellation: the caller may dispose the returned [Disposable] to cancel the evaluation.
     * After that, calling [completion] is not required, and the caller ignores any call. Disposing
     * after [completion] has been called must have no effect. Implementations with nothing to
     * cancel, including synchronous ones, return [Disposables.disposed].
     *
     * @param js The JavaScript code to evaluate.
     * @param completion The [Callback] to call, via [Callback.onComplete], with the outcome, as
     * described above.
     * @return A [Disposable] that cancels the in-progress evaluation, or [Disposables.disposed] if
     * there is nothing to cancel.
     */
    fun evaluateJavaScript(js: String, completion: Callback<TealiumResult<DataItem>>): Disposable
}