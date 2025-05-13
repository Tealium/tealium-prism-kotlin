package com.tealium.core.api.tracking

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataItemUtils.asDataItem

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
