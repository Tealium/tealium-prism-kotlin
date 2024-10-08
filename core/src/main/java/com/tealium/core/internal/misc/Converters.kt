package com.tealium.core.internal.misc

import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.transform.ScopedTransformation
import com.tealium.core.api.barriers.ScopedBarrier
import com.tealium.core.internal.dispatch.barrierScopeFromString
import com.tealium.core.internal.dispatch.transformationScopeFromString


/**
 * Internal holder of common [DataItemConverter] implementations. They are not expected to be
 * used by an end user, so implementing them somewhere they can be obfuscated is fine.
 */
object Converters {
    object ScopedTransformationConverter: DataItemConverter<ScopedTransformation> {
            const val KEY_TRANSFORMATION_ID = "transformation_id"
            const val KEY_TRANSFORMER_ID = "transformer_id"
            const val KEY_SCOPES = "scopes"

            override fun convert(dataItem: DataItem): ScopedTransformation? {
                val dataObject = dataItem.getDataObject() ?: return null

                val id = dataObject.getString(KEY_TRANSFORMATION_ID)
                val transformerId = dataObject.getString(KEY_TRANSFORMER_ID)
                val scopes = dataObject.getDataList(KEY_SCOPES)
                    ?.mapNotNull(DataItem::getString)
                    ?.map(::transformationScopeFromString)

                if (id == null || transformerId == null || scopes == null) return null

                return ScopedTransformation(id, transformerId, scopes.toSet())
            }
        }


    object ScopedBarrierConverter: DataItemConverter<ScopedBarrier> {
        const val KEY_BARRIER_ID = "barrier_id"
        const val KEY_SCOPES = "scopes"

        override fun convert(dataItem: DataItem): ScopedBarrier? {
            val dataObject = dataItem.getDataObject() ?: return null

            val id = dataObject.getString(KEY_BARRIER_ID)
            val scopes = dataObject.getDataList(KEY_SCOPES)
                ?.mapNotNull(DataItem::getString)
                ?.map(::barrierScopeFromString)

            if (id == null || scopes == null) return null

            return ScopedBarrier(id, scopes.toSet())
        }
    }
}