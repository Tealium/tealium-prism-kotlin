package com.tealium.core.internal.rules

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Condition
import com.tealium.core.api.rules.Rule

data class LoadRule(
    val id: String,
    val conditions: Rule<Condition>
): DataItemConvertible {

    override fun asDataItem(): DataItem {
        return DataObject.create {
            put(Converter.KEY_ID, id)
            put(Converter.KEY_CONDITIONS, conditions)
        }.asDataItem()
    }

    object Converter : DataItemConverter<LoadRule> {
        const val KEY_ID = "id"
        const val KEY_CONDITIONS = "conditions"

        override fun convert(dataItem: DataItem): LoadRule? {
            val dataObject = dataItem.getDataObject()
                ?: return null

            val id = dataObject.getString(KEY_ID)
                ?: return null

            val conditions = dataObject.get(KEY_CONDITIONS, conditionConverter)
                ?: return null

            return LoadRule(id, conditions)
        }
    }
}