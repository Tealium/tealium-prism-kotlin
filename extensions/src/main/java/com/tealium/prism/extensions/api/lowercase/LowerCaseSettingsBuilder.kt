package com.tealium.prism.extensions.api.lowercase

import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.api.transform.TransformationSettingsBuilder
import com.tealium.prism.extensions.internal.LOWERCASE
import com.tealium.prism.extensions.internal.lowercase.LowerCaseInput
import com.tealium.prism.extensions.internal.lowercase.LowerCaseKeys

class LowerCaseSettingsBuilder(transformationId: String) :
    TransformationSettingsBuilder<LowerCaseSettingsBuilder>(transformationId, LOWERCASE) {

    private var allVariables: Boolean = true
    private val operations = mutableListOf<TransformationOperation<LowerCaseInput>>()

    /**
     * Sets whether to lowercase all variables or only specific ones
     */
    fun setAllVariables(all: Boolean): LowerCaseSettingsBuilder = apply {
        allVariables = all
    }

    /**
     * Adds a variable to be lowercased in place
     */
    fun addVariable(reference: ReferenceContainer): LowerCaseSettingsBuilder = apply {
        operations.add(
            TransformationOperation(reference, LowerCaseInput(reference))
        )
    }

    override fun onBuildConfiguration(): DataObject {
        return DataObject.create {
            put(LowerCaseKeys.ALL_VARIABLES, allVariables)
            put(LowerCaseKeys.OPERATIONS, operations.map(DataItemConvertible::asDataItem).asDataList())
        }
    }
}
