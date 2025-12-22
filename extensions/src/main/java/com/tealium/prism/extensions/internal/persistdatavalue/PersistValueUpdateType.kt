package com.tealium.prism.extensions.internal.persistdatavalue

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem

enum class PersistValuesUpdateType(val type: String) : DataItemConvertible {
    ALLOW_UPDATE("allow_update"),
    KEEP_FIRST_VALUE("keep_first_value");

    override fun asDataItem(): DataItem {
        return type.asDataItem()
    }

    object Converter : DataItemConverter<PersistValuesUpdateType> {
        override fun convert(dataItem: DataItem): PersistValuesUpdateType? {
            val value = dataItem.getString()?.lowercase() ?: return null
            return values().find { it.type == value }
        }
    }
}