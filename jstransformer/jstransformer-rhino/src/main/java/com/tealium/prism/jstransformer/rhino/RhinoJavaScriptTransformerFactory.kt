package com.tealium.prism.jstransformer.rhino

import com.tealium.prism.jstransformer.JavaScriptTransformerFactory
import com.tealium.prism.jstransformer.rhino.internal.RhinoJavaScriptEngineAdapter

object RhinoJavaScriptTransformerFactory :
    JavaScriptTransformerFactory({ ctx ->
        val rhino = RhinoJavaScriptEngineAdapter.setupRhinoObjects(ctx)
        RhinoJavaScriptEngineAdapter(rhino)
    })