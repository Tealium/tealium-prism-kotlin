package com.tealium.prism.jstransformer

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable

// TODO - This will need to support async init/evaluation
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
     * Evaluates the given [js] code and emits the result as a [TealiumResult] wrapping a [DataItem].
     *
     * A successful result contains the value returned by the script. A failure result indicates
     * that an error occurred during evaluation.
     *
     * @param js The JavaScript code to evaluate.
     * @return An [Observable] that emits a [TealiumResult] containing the evaluation outcome.
     */
    fun evaluateJavaScript(js: String): Observable<TealiumResult<DataItem>>
}