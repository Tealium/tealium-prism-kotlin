package com.tealium.prism.jstransformer.rhino.internal

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.tracking.Tracker
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

        fun setupRhinoObjects(context: TealiumContext): RhinoObjects =
            setupRhinoObjects(context.tracker, context.logger, context.dataLayer)

        fun setupRhinoObjects(tracker: Tracker, logger: Logger, dataLayer: DataStore): RhinoObjects {
            val rhinoContext = Context.enter()
            rhinoContext.isInterpretedMode = true
            val scope = rhinoContext.initSafeStandardObjects()

            val scriptableLogger = ScriptableLogger(logger)
            val scriptableTracker = ScriptableTracker(tracker)
            val scriptableDataLayer = ScriptableDataLayer(dataLayer, rhinoContext, scope)
            val expiry = Context.javaToJS(ScriptableDataLayer.ScriptableExpiry, scope)

            ScriptableObject.putProperty(scope, "console", scriptableLogger)
            ScriptableObject.putProperty(scope, "tracker", scriptableTracker)
            ScriptableObject.putProperty(scope, "dataLayer", scriptableDataLayer)
            ScriptableObject.putProperty(scope, "Expiry", expiry)

            return RhinoObjects(rhinoContext, scope)
        }
    }
}