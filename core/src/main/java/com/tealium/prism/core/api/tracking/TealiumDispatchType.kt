package com.tealium.prism.core.api.tracking

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem

enum class TealiumDispatchType(val friendlyName: String): DataItemConvertible {
    Event("event"),
    View("view");

    override fun asDataItem(): DataItem =
        friendlyName.asDataItem()

    object Converter: DataItemConverter<TealiumDispatchType> {
        override fun convert(dataItem: DataItem): TealiumDispatchType? {
            val typeString = dataItem.getString()?.lowercase()
                ?: return null

            return values().find { it.friendlyName == typeString }
        }
    }
}
