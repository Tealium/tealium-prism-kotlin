package com.tealium.prism.core.api.misc

import com.tealium.prism.core.api.data.DataObject

class TemplateProcessorTests : CommonTemplateProcessorTests() {
    override fun process(template: String, context: DataObject) =
        TemplateProcessor.process(template, context)
}
