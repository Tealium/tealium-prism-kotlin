package com.tealium.prism.jstransformer

import com.tealium.prism.core.api.transform.Transformer

/**
 * A [Transformer] that is responsible for mutating event data using JavaScript code.
 *
 * Any events tracked by implementations should use this [JavaScriptTransformer] type as the source
 * Module for consistency.
 */
interface JavaScriptTransformer : Transformer