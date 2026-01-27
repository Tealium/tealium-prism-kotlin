package com.tealium.prism.jstransformer

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.transform.TransformationSettingsBuilder
import com.tealium.prism.jstransformer.internal.JavaScriptTransformer

class JavaScriptTransformationSettingsBuilder(transformationId: String) :
    TransformationSettingsBuilder<JavaScriptTransformationSettingsBuilder>(
        transformationId, Modules.Types.JS_TRANSFORMER
    ) {
    private var jsCode: String? = null

    fun setJsCode(jsCode: String) = apply {
        this.jsCode = jsCode
    }

    override fun onBuildConfiguration(): DataObject {
        return DataObject.create {
            jsCode?.let { js ->
                put(JavaScriptTransformer.Keys.JS_CODE, js)
            }
        }
    }
}