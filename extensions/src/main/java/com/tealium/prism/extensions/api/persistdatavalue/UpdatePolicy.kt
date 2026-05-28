package com.tealium.prism.extensions.api.persistdatavalue

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem

/**
 * Defines the update policy for a persisted data value
 */
enum class UpdatePolicy(val policy: String) : DataItemConvertible {

    /**
     * Allows the value to be updated with new values. This is the default policy if not specified.
     */
    ALLOW_UPDATE("allowUpdate"),

    /**
     * Prevents the value from being updated once it has been set. If a value already exists, it will not be overwritten.
     */
    KEEP_FIRST_VALUE("keepFirstValue");

    override fun asDataItem(): DataItem {
        return policy.asDataItem()
    }

    object Converter : DataItemConverter<UpdatePolicy> {
        override fun convert(dataItem: DataItem): UpdatePolicy? {
            val value = dataItem.getString()?.lowercase() ?: return null
            return entries.find { it.policy.lowercase() == value }
        }
    }
}