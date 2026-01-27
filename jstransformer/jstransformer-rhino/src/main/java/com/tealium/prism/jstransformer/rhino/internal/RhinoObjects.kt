package com.tealium.prism.jstransformer.rhino.internal

import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject

data class RhinoObjects(
    val context: Context,
    val scope: ScriptableObject
)