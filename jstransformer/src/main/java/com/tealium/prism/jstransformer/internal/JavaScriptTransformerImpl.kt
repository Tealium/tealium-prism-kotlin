package com.tealium.prism.jstransformer.internal

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfErrorEnabled
import com.tealium.prism.core.api.misc.Callback
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

        // The subscription is already completed (disposed) when the adapter evaluates inline, and
        // remains a live cancellation handle if an adapter ever completes asynchronously.
        return adapter.evaluateJavaScript(js)
            .subscribe { result ->
                result.onSuccess { payload ->
                    val newPayload = payload.getDataObject()
                    if (newPayload == null) {
                        completion.onComplete(null)
                        return@subscribe
                    }

                    dispatch.replace(newPayload)
                }.onFailure { e ->
                    logger.logIfErrorEnabled(id) {
                        "Exception whilst running transformation: ${transformation.transformerId}; ${e.message}"
                    }
                    dispatch.addAll(DataObject.create { put("js_error", e.message.toString()) })
                }

                completion.onComplete(dispatch)
            }.addTo(disposables)
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