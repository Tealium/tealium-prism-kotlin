package com.tealium.prism.extensions.internal.lowercase

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.ReferenceContainer

/**
 * Defines the policy for which variables should be transformed to lowercase.
 */
sealed class LowercasePolicy(val type: String) : DataItemConvertible {

    /**
     * Indicates that all string values in the payload should be lowercased.
     */
    object AllVariables : LowercasePolicy("allvariables")

    /**
     * Indicates that only specific variables should be lowercased.
     * The [references] list contains references to the variables that should be transformed.
     */
    data class Variables(val references: List<ReferenceContainer>) : LowercasePolicy("variables")

    override fun asDataItem(): DataItem {
        return when (this) {
            is AllVariables -> type.asDataItem()
            is Variables -> references.map(ReferenceContainer::asDataItem).asDataList().asDataItem()
        }
    }
}
