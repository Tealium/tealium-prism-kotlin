package com.tealium.prism.core.api.transform

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.internal.rules.conditionConverter


/**
 * Describes the relationship between this [TransformationSettings] and [Transformer] that it belongs
 * to, as well as any [TransformationScope]s that it may be applicable for.
 *
 * @param id The unique identifier for this transformation.
 * @param transformerId The identifier of the [Transformer] to use to apply the transformation identified by [id].
 * @param scope The [TransformationScope] that this transformation is applicable for.
 * @param configuration Configuration required by the transformation
 * @param conditions Any conditions that should be satisfied for this transformation to be executed
 * @param order
 *  An integer representing the order, within the [scope], that this transformation should execute.
 *  Default is [Int.MAX_VALUE], lower values execute first.
 *
 */
data class TransformationSettings(
    val id: String,
    val transformerId: String,
    val scope: TransformationScope,
    val configuration: DataObject = DataObject.EMPTY_OBJECT,
    val conditions: Rule<Condition>? = null,
    val order: Int = Int.MAX_VALUE
) {

    object Converter : DataItemConverter<TransformationSettings> {
        const val KEY_TRANSFORMATION_ID = "transformation_id"
        const val KEY_TRANSFORMER_ID = "transformer_id"
        const val KEY_SCOPE = "scope"
        const val KEY_CONFIGURATION = "configuration"
        const val KEY_CONDITIONS = "conditions"
        const val KEY_ORDER = "order"

        override fun convert(dataItem: DataItem): TransformationSettings? {
            val dataObject = dataItem.getDataObject() ?: return null

            val id = dataObject.getString(KEY_TRANSFORMATION_ID)
            val transformerId = dataObject.getString(KEY_TRANSFORMER_ID)
            val scope = dataObject.get(KEY_SCOPE, TransformationScope.Converter)

            if (id == null || transformerId == null || scope == null) return null

            val configuration =
                dataObject.getDataObject(KEY_CONFIGURATION) ?: DataObject.Companion.EMPTY_OBJECT
            val conditions = dataObject.get(KEY_CONDITIONS, conditionConverter)
            val order = dataObject.getInt(KEY_ORDER) ?: Int.MAX_VALUE

            return TransformationSettings(
                id,
                transformerId,
                scope,
                configuration,
                conditions,
                order
            )
        }
    }
}