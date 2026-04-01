package com.tealium.prism.core.api.data

/**
 * Represents a source of data used in a transformation operation,
 * which can be either a reference to existing data or a constant value.
 */
sealed class ValueSource: DataItemConvertible {

    /**
     * A reference to existing data in the payload
     */
    data class Reference(val reference: ReferenceContainer) : ValueSource()

    /**
     * Represents a constant value to be used directly
     */
    data class Constant(val value: ValueContainer) : ValueSource()

    override fun asDataItem(): DataItem {
        return when (this) {
            is Reference -> reference.asDataItem()
            is Constant -> value.asDataItem()
        }
    }

    object Converter : DataItemConverter<ValueSource> {
        override fun convert(dataItem: DataItem): ValueSource? {
            val referenceContainer = ReferenceContainer.Converter.convert(dataItem)
            if (referenceContainer != null) return Reference(referenceContainer)

            val valueContainer = ValueContainer.Converter.convert(dataItem)
            if (valueContainer != null) return Constant(valueContainer)

            return null
        }
    }
}