package com.tealium.core.api.misc

import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataItem

enum class Environment(val environment: String) : DataItemConvertible,
    DataItemConverter<Environment> {
    DEV("dev"),
    QA("qa"),
    PROD("prod");

    override fun asDataItem(): DataItem {
        return DataItem.string(this.environment)
    }

    override fun convert(dataItem: DataItem): Environment? {
        return dataItem.getString()?.let { str ->
            when (str) {
                DEV.environment -> DEV
                QA.environment -> QA
                PROD.environment -> PROD
                else -> null
            }
        }
    }
}