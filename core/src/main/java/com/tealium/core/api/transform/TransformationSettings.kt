package com.tealium.core.api.transform

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataItemUtils.asDataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.internal.misc.Converters.TransformationSettingsConverter.KEY_CONFIGURATION
import com.tealium.core.internal.misc.Converters.TransformationSettingsConverter.KEY_SCOPES
import com.tealium.core.internal.misc.Converters.TransformationSettingsConverter.KEY_TRANSFORMATION_ID
import com.tealium.core.internal.misc.Converters.TransformationSettingsConverter.KEY_TRANSFORMER_ID


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
    val configuration: DataObject = DataObject.EMPTY_OBJECT
) : DataItemConvertible {

    override fun asDataItem(): DataItem {
        return DataObject.create {
            put(KEY_TRANSFORMATION_ID, id)
            put(KEY_TRANSFORMER_ID, transformerId)
            put(KEY_SCOPES, scope.asDataList())
            put(KEY_CONFIGURATION, configuration)
        }.asDataItem()
    }
}