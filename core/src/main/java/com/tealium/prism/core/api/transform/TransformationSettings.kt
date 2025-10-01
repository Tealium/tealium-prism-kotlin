package com.tealium.prism.core.api.transform

import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.DataObjectConvertible
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.internal.misc.Converters.TransformationSettingsConverter.KEY_CONDITIONS
import com.tealium.prism.core.internal.misc.Converters.TransformationSettingsConverter.KEY_CONFIGURATION
import com.tealium.prism.core.internal.misc.Converters.TransformationSettingsConverter.KEY_SCOPES
import com.tealium.prism.core.internal.misc.Converters.TransformationSettingsConverter.KEY_TRANSFORMATION_ID
import com.tealium.prism.core.internal.misc.Converters.TransformationSettingsConverter.KEY_TRANSFORMER_ID


/**
 * Describes the relationship between this [TransformationSettings] and [Transformer] that it belongs
 * to, as well as any [TransformationScope]s that it may be applicable for.
 *
 * @param id The unique identifier for this transformation.
 * @param transformerId The identifier of the [Transformer] to use to apply the transformation identified by [id].
 * @param scope The set of [TransformationScope]s that this transformation is applicable for.
 * @param configuration Configuration required by the transformation
 */
data class TransformationSettings(
    val id: String,
    val transformerId: String,
    val scope: Set<TransformationScope>,
    val configuration: DataObject = DataObject.EMPTY_OBJECT,
    val conditions: Rule<Condition>? = null
) : DataObjectConvertible {

    override fun asDataObject() =
        DataObject.create {
            put(KEY_TRANSFORMATION_ID, id)
            put(KEY_TRANSFORMER_ID, transformerId)
            put(KEY_SCOPES, scope.asDataList())
            put(KEY_CONFIGURATION, configuration)
            if (conditions != null) {
                put(KEY_CONDITIONS, conditions)
            }
        }

}