package com.tealium.core.internal

import com.tealium.core.api.data.TealiumDeserializable
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.api.transformations.ScopedTransformation
import com.tealium.core.api.barriers.ScopedBarrier
import com.tealium.core.internal.dispatch.barrierScopeFromString
import com.tealium.core.internal.dispatch.transformationScopeFromString


/**
 * Internal holder of common [TealiumDeserializable] implementations. They are not expected to be
 * used by an end user, so implementing them somewhere they can be obfuscated is fine.
 */
object Deserializers {
    object ScopedTransformationDeserializable: TealiumDeserializable<ScopedTransformation> {
            const val KEY_TRANSFORMATION_ID = "transformation_id"
            const val KEY_TRANSFORMER_ID = "transformer_id"
            const val KEY_SCOPES = "scopes"

            override fun deserialize(value: TealiumValue): ScopedTransformation? {
                val bundle = value.getBundle() ?: return null

                val id = bundle.getString(KEY_TRANSFORMATION_ID)
                val transformerId = bundle.getString(KEY_TRANSFORMER_ID)
                val scopes = bundle.getList(KEY_SCOPES)
                    ?.mapNotNull(TealiumValue::getString)
                    ?.map(::transformationScopeFromString)

                if (id == null || transformerId == null || scopes == null) return null

                return ScopedTransformation(id, transformerId, scopes.toSet())
            }
        }


    object ScopedBarrierDeserializable: TealiumDeserializable<ScopedBarrier> {
        const val KEY_BARRIER_ID = "barrier_id"
        const val KEY_SCOPES = "scopes"

        override fun deserialize(value: TealiumValue): ScopedBarrier? {
            val bundle = value.getBundle() ?: return null

            val id = bundle.getString(KEY_BARRIER_ID)
            val scopes = bundle.getList(KEY_SCOPES)
                ?.mapNotNull(TealiumValue::getString)
                ?.map(::barrierScopeFromString)

            if (id == null || scopes == null) return null

            return ScopedBarrier(id, scopes.toSet())
        }
    }
}