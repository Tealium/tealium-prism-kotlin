package com.tealium.prism.jstransformer.rhino.internal

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.jstransformer.JavaScriptEngineAdapter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

class RhinoJavaScriptEngineAdapter(
    private val rhino: RhinoObjects,
    private val disposable: CompositeDisposable = Disposables.composite()
) : JavaScriptEngineAdapter, Disposable by disposable {

    init {
        disposable.add(Disposables.subscription(::shutdown))
    }

    private fun shutdown() {
        rhino.context.close()
        Context.exit()
    }

    override fun evaluateJavaScript(js: String): Observable<TealiumResult<DataItem>> {
        if (isDisposed) {
            return Observables.just(TealiumResult.failure(TealiumException("Js Engine already shutdown")))
        }

        val result = try {
            val (ctx, scope) = rhino

            val evaluationResult = ctx.evaluateString(scope, js, "adapter.js", 0, null)
            val dataItem = when (evaluationResult) {
                Undefined.isUndefined(evaluationResult) -> DataItem.NULL
                is String -> DataItem.parse(evaluationResult)
                else -> DataItem.convert(evaluationResult)
            }

            TealiumResult.success(dataItem)
        } catch (e: Exception) {
            TealiumResult.failure(e)
        }

        return Observables.just(result)
    }

    companion object {

        fun setupRhinoObjects(context: TealiumContext): RhinoObjects {
            val rhinoContext = Context.enter()
            rhinoContext.isInterpretedMode = true
            val scope = rhinoContext.initSafeStandardObjects()

            val logger = ScriptableLogger(context.logger)
            val tracker = ScriptableTracker(context.tracker)
            val dataLayer = ScriptableDataLayer(context.dataLayer, rhinoContext, scope)
            val expiry = Context.javaToJS(ScriptableDataLayer.ScriptableExpiry, scope)
            val network =
                ScriptableNetworkClient(context.network.networkClient, rhinoContext, scope)
            ScriptableObject.putProperty(scope, "console", logger)
            ScriptableObject.putProperty(scope, "tracker", tracker)
            ScriptableObject.putProperty(scope, "datalayer", dataLayer)
            ScriptableObject.putProperty(scope, "Expiry", expiry)
            ScriptableObject.putProperty(scope, "network", network)

            return RhinoObjects(rhinoContext, scope)
        }
    }
}


