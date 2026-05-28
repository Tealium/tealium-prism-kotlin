package com.tealium.prism.core.api.transform

import com.tealium.prism.core.api.data.DataObject

class TestTransformationSettingsBuilder(
    transformationId: String,
    transformerId: String = "test-transformer",
    private val config: DataObject = DataObject.Companion.EMPTY_OBJECT
): TransformationSettingsBuilder<TestTransformationSettingsBuilder>(transformationId, transformerId) {
    override fun onBuildConfiguration(): DataObject {
        return config
    }
}