package com.tealium.prism.extensions.api.lowercase

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.transform.TransformationSettingsBuilder
import com.tealium.prism.extensions.internal.LOWERCASE
import com.tealium.prism.extensions.internal.lowercase.LowercaseConfiguration
import com.tealium.prism.extensions.internal.lowercase.LowercasePolicy

/**
 * Builder for configuring a Lowercase transformation. This transformation lowercases data layer values.
 * The transformation can be applied to all variables or specific variables.
 */
class LowercaseSettingsBuilder(transformationId: String) :
    TransformationSettingsBuilder<LowercaseSettingsBuilder>(transformationId, LOWERCASE) {

    private var policy: LowercasePolicy? = null

    /**
     * Enables transformer to lowercase all string values in the payload.
     */
    fun lowercaseAllVariables(): LowercaseSettingsBuilder = apply {
        policy = LowercasePolicy.AllVariables
    }

    /**
     * Enables transformer to lowercase only specific variables in the payload.
     *
     * @param references List of [ReferenceContainer] that specify which variables should be lowercased.
     */
    fun lowercaseVariables(references: List<ReferenceContainer>): LowercaseSettingsBuilder = apply {
        policy = LowercasePolicy.Variables(references)
    }

    override fun onBuildConfiguration(): DataObject {
        return DataObject.create {
            policy?.let {
                put(LowercaseConfiguration.Converter.KEY_VARIABLES, it.asDataItem())
            }
        }
    }
}
