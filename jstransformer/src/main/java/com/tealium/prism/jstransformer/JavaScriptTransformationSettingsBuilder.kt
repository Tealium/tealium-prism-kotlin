package com.tealium.prism.jstransformer

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.transform.TransformationSettingsBuilder
import com.tealium.prism.jstransformer.internal.JavaScriptTransformer

/**
 * Builder for creating [TransformationSettings] specific to the JavaScript Transformer module.
 *
 * @param transformationId Unique identifier for the transformation settings.
 */
class JavaScriptTransformationSettingsBuilder(transformationId: String) :
    TransformationSettingsBuilder<JavaScriptTransformationSettingsBuilder>(
        transformationId, Modules.Types.JS_TRANSFORMER
    ) {
    private var jsCode: String? = null

    /**
     * Sets the JavaScript code to be executed by the transformer to modify the dispatch payload.
     *
     * @param jsCode The JavaScript code as a string. This code should be designed to manipulate the payload.
     */
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