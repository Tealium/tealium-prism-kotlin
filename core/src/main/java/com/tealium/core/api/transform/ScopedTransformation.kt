package com.tealium.core.api.transform

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataItem
import com.tealium.core.internal.misc.Converters.ScopedTransformationConverter.KEY_SCOPES
import com.tealium.core.internal.misc.Converters.ScopedTransformationConverter.KEY_TRANSFORMATION_ID
import com.tealium.core.internal.misc.Converters.ScopedTransformationConverter.KEY_TRANSFORMER_ID


/**
 * Describes the relationship between this [ScopedTransformation] and [Transformer] that it belongs
 * to, as well as any [TransformationScope]s that it may be applicable for.
 */
data class ScopedTransformation(
    /**
     * The unique identifier for this transformation.
     */
    val id: String,

    /**
     * The identifier of the [Transformer] to use to apply the transformation identified by [id].
     */
    val transformerId: String,

    /**
     * The set of [TransformationScope]s that this transformation is applicable for.
     */
    val scope: Set<TransformationScope>
) : DataItemConvertible {

    override fun asDataItem(): DataItem {
        return DataObject.create {
            put(KEY_TRANSFORMATION_ID, id)
            put(KEY_TRANSFORMER_ID, transformerId)
            put(KEY_SCOPES, DataItem.convert(scope))
        }.asDataItem()
    }
}