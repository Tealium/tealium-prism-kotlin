package com.tealium.prism.jstransformer.rhino

import com.tealium.prism.jstransformer.JavaScriptTransformerFactory
import com.tealium.prism.jstransformer.rhino.internal.RhinoJavaScriptEngineAdapter

/**
 * [JavaScriptTransformerFactory] backed by the Mozilla Rhino engine.
 *
 * Apps with a `minSdk` below 26 must enable core library desugaring: Rhino uses `java.util.function`
 * (API 24) and `java.time` (API 26) types. Without it, Tealium fails to initialize on API 23 to 25
 * devices whenever this module is a dependency, because this factory is registered automatically
 * through the manifest.
 */
object RhinoJavaScriptTransformerFactory :
    JavaScriptTransformerFactory({ ctx ->
        val rhino = RhinoJavaScriptEngineAdapter.setupRhinoObjects(ctx)
        RhinoJavaScriptEngineAdapter(rhino)
    })