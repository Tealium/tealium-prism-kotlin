package com.tealium.core.api.transform

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumSerializable
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.internal.misc.Deserializers.ScopedTransformationDeserializable.KEY_SCOPES
import com.tealium.core.internal.misc.Deserializers.ScopedTransformationDeserializable.KEY_TRANSFORMATION_ID
import com.tealium.core.internal.misc.Deserializers.ScopedTransformationDeserializable.KEY_TRANSFORMER_ID


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
) : TealiumSerializable {

    override fun asTealiumValue(): TealiumValue {
        return TealiumBundle.create {
            put(KEY_TRANSFORMATION_ID, id)
            put(KEY_TRANSFORMER_ID, transformerId)
            put(KEY_SCOPES, TealiumValue.convert(scope))
        }.asTealiumValue()
    }
}