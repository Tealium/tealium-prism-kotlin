package com.tealium.prism.jstransformer.internal

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfErrorEnabled
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.jstransformer.BuildConfig
import com.tealium.prism.jstransformer.JavaScriptEngineAdapter
import com.tealium.prism.jstransformer.JavaScriptTransformer
import com.tealium.prism.jstransformer.JavaScriptTransformerFactory
import org.json.JSONObject

class JavaScriptTransformerImpl(
    private val adapter: JavaScriptEngineAdapter,
    private val logger: Logger,
    private val scheduler: Scheduler,
) : JavaScriptTransformer {

    private val disposables = Disposables.composite()

    init {
        disposables.add(adapter)
    }

    override fun applyTransformation(
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: Callback<Dispatch?>
    ): Disposable {
        val userJsCode = transformation.configuration.getString(Keys.JS_CODE)
        if (userJsCode.isNullOrBlank()) {
            completion.onComplete(dispatch)
            return Disposables.disposed()
        }

        val escapedScope = JSONObject.quote(scope.toString())

        val js = """
            let track = ${createTrackFunction(dispatch)};
            ((payload, scope) => {
                let drop = () => { payload = undefined };

                (() => {
                    $userJsCode
                })()

               return JSON.stringify(payload);
            })(${dispatch.payload()}, $escapedScope)
        """.trimIndent()

        val pending = Disposables.composite()
        disposables.add(pending)
        if (pending.isDisposed) return Disposables.disposed()

        var completed = false
        // Cancellation handle returned to the caller. Disposing it before completion cancels the
        // evaluation and removes `pending` from `disposables`; completion marks it disposed, after
        // which disposing it has no effect.
        val handle = Disposables.subscription {
            if (completed) return@subscription
            disposables.remove(pending)
            pending.dispose()
        }
        pending.add(handle)

        adapter.evaluateJavaScript(js) { result ->
            // Defensive: hand the result back to the Tealium thread in case an adapter calls back
            // from elsewhere. Runs inline when already on the Tealium thread.
            scheduler.execute {
                if (completed || pending.isDisposed) return@execute
                completed = true
                disposables.remove(pending)
                handle.dispose()

                result.onSuccess { payload ->
                    val newPayload = payload.getDataObject()
                    if (newPayload == null) {
                        completion.onComplete(null)
                        return@execute
                    }

                    dispatch.replace(newPayload)
                }.onFailure { e ->
                    logger.logIfErrorEnabled(id) {
                        "Exception whilst running transformation: ${transformation.transformerId}; ${e.message}"
                    }
                    dispatch.addAll(DataObject.create { put("js_error", e.message.toString()) })
                }

                completion.onComplete(dispatch)
            }
        }.addTo(pending)

        return handle
    }

    private fun createTrackFunction(dispatch: Dispatch): String =
        if (dispatch.payload().getBoolean("js_tracking") == true) {
            """
                function() { 
                    console.warn("track was cancelled due to recursion.") 
                }
            """.trimIndent()
        } else {
            """
                function() {
                    // forward all arguments to tracker.track with correct 'this'
                    return tracker.track.apply(tracker, arguments);
                }
            """.trimIndent()
        }

    override fun onShutdown() {
        disposables.dispose()
    }

    override val id: String
        get() = JavaScriptTransformerFactory.MODULE_TYPE
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    object Keys {
        const val JS_CODE = "js_code"
    }
}