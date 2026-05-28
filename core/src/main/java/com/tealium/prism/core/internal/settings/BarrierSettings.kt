package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.internal.settings.BarrierSettings.Converter.KEY_BARRIER_ID
import com.tealium.prism.core.internal.settings.BarrierSettings.Converter.KEY_CONFIGURATION
import com.tealium.prism.core.internal.settings.BarrierSettings.Converter.KEY_SCOPE

/**
 * Holds the configuration for a barrier, identifying it by [barrierId] and optionally restricting
 * which dispatch scopes it applies to via [scope].
 *
 * @param barrierId The unique identifier for this barrier.
 * @param scope The [BarrierScope] this barrier applies to.
 * @param configuration Additional barrier-specific configuration data.
 *
 * @see BarrierScope
 */
data class BarrierSettings(
    val barrierId: String,
    val scope: BarrierScope,
    val configuration: DataObject = DataObject.EMPTY_OBJECT
) : DataItemConvertible {

    override fun asDataItem(): DataItem {
        return DataObject.create {
            put(KEY_BARRIER_ID, barrierId)
            put(KEY_SCOPE, scope)
            put(KEY_CONFIGURATION, configuration)
        }.asDataItem()
    }

    object Converter: DataItemConverter<BarrierSettings> {
        const val KEY_BARRIER_ID = "barrier_id"
        const val KEY_SCOPE = "scope"
        const val KEY_CONFIGURATION = "configuration"

        override fun convert(dataItem: DataItem): BarrierSettings? {
            val dataObject = dataItem.getDataObject() ?: return null

            val id = dataObject.getString(KEY_BARRIER_ID)
            val scope = dataObject.get(KEY_SCOPE, BarrierScope.Converter)

            if (id == null || scope == null) return null

            val configuration = dataObject.getDataObject(KEY_CONFIGURATION)
                ?: DataObject.EMPTY_OBJECT

            return BarrierSettings(id, scope, configuration)
        }
    }
}