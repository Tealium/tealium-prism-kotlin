package com.tealium.prism.extensions.internal.lowercase

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.extensions.internal.lowercase.LowercasePolicy.AllVariables
import com.tealium.prism.extensions.internal.lowercase.LowercasePolicy.Variables

class LowercaseConfiguration(
    val policy: LowercasePolicy
) {

    object Converter : DataItemConverter<LowercaseConfiguration> {
        const val KEY_VARIABLES = "variables"
        private val referenceConverter = ReferenceContainer.Converter

        override fun convert(dataItem: DataItem): LowercaseConfiguration? {
            val dataObject = dataItem.getDataObject() ?: return null

            val allVariablesPolicy =
                dataObject.getString(KEY_VARIABLES)?.lowercase() == AllVariables.type
            if (allVariablesPolicy) return LowercaseConfiguration(AllVariables)

            val inputsPolicy = dataObject.getDataList(KEY_VARIABLES) ?: return null
            val convertedInputs = inputsPolicy.mapNotNull(referenceConverter::convert)
            return LowercaseConfiguration(Variables(convertedInputs))
        }
    }
}
