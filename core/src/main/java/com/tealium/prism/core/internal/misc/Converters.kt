package com.tealium.prism.core.internal.misc

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.internal.dispatch.transformationScopeFromString
import com.tealium.prism.core.internal.rules.conditionConverter


/**
 * Internal holder of common [DataItemConverter] implementations. They are not expected to be
 * used by an end user, so implementing them somewhere they can be obfuscated is fine.
 */
object Converters {
    object TransformationSettingsConverter : DataItemConverter<TransformationSettings> {
        const val KEY_TRANSFORMATION_ID = "transformation_id"
        const val KEY_TRANSFORMER_ID = "transformer_id"
        const val KEY_SCOPES = "scopes"
        const val KEY_CONFIGURATION = "configuration"
        const val KEY_CONDITIONS = "conditions"

        override fun convert(dataItem: DataItem): TransformationSettings? {
            val dataObject = dataItem.getDataObject() ?: return null

            val id = dataObject.getString(KEY_TRANSFORMATION_ID)
            val transformerId = dataObject.getString(KEY_TRANSFORMER_ID)
            val scopes = dataObject.getDataList(KEY_SCOPES)
                ?.mapNotNull(DataItem::getString)
                ?.map(::transformationScopeFromString)

            if (id == null || transformerId == null || scopes == null) return null

            val configuration =
                dataObject.getDataObject(KEY_CONFIGURATION) ?: DataObject.EMPTY_OBJECT
            val conditions =
                dataObject.get(KEY_CONDITIONS, conditionConverter)

            return TransformationSettings(id, transformerId, scopes.toSet(), configuration, conditions)
        }
    }
}