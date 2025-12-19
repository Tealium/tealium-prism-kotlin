package com.tealium.prism.extensions.internal

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.ReferenceContainer

class LowerCaseInput(
    val input: ReferenceContainer
) : DataItemConvertible {
    override fun asDataItem(): DataItem {
        return input.asDataItem()
    }

    object Converter : DataItemConverter<LowerCaseInput> {
        override fun convert(dataItem: DataItem): LowerCaseInput? {
            val referenceContainer = ReferenceContainer.Converter.convert(dataItem)
            if (referenceContainer != null) {
                return LowerCaseInput(referenceContainer)
            }
            return null
        }
    }
}
