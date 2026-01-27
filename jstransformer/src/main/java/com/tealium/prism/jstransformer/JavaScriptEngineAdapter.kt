package com.tealium.prism.jstransformer

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable

// TODO - This will need to support async init/evaluation
interface JavaScriptEngineAdapter: Disposable {
    fun evaluateJavaScript(js: String) : Observable<TealiumResult<DataItem>>
}