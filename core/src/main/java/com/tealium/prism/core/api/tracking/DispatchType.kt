package com.tealium.prism.core.api.tracking

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem

/**
 * The type of [Dispatch] being sent.
 */
enum class DispatchType(val friendlyName: String): DataItemConvertible {
    /**
     * Represents an event
     */
    Event("event"),
    /**
     * Represents a view
     */
    View("view");

    override fun asDataItem(): DataItem =
        friendlyName.asDataItem()

    object Converter: DataItemConverter<DispatchType> {
        override fun convert(dataItem: DataItem): DispatchType? {
            val typeString = dataItem.getString()?.lowercase()
                ?: return null

            return entries.find { it.friendlyName == typeString }
        }
    }
}
