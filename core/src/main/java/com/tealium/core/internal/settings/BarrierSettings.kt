package com.tealium.core.internal.settings

import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.internal.dispatch.barrierScopeFromString
import com.tealium.core.internal.settings.BarrierSettings.Converter.KEY_BARRIER_ID
import com.tealium.core.internal.settings.BarrierSettings.Converter.KEY_CONFIGURATION
import com.tealium.core.internal.settings.BarrierSettings.Converter.KEY_SCOPES

/**
 * A [BarrierSettings] describes which [Dispatcher] implementation any given [Barrier] should be
 * guarding. The [Barrier] implementation is identified by the [barrierId], and the [scope]
 * sets out which scopes that barrier is relevant for.
 *
 * @param barrierId The id of the [Barrier]
 * @param scope A set of [BarrierScope]s that the [Barrier] should be consulted on.
 *
 * @see Barrier
 * @see BarrierScope
 */
data class BarrierSettings(
    val barrierId: String,
    val scope: Set<BarrierScope>,
    val configuration: DataObject = DataObject.EMPTY_OBJECT
) : DataItemConvertible {

    override fun asDataItem(): DataItem {
        return DataObject.create {
            put(KEY_BARRIER_ID, barrierId)
            put(KEY_SCOPES, DataItem.convert(scope))
            put(KEY_CONFIGURATION, configuration)
        }.asDataItem()
    }

    object Converter: DataItemConverter<BarrierSettings> {
        const val KEY_BARRIER_ID = "barrier_id"
        const val KEY_SCOPES = "scopes"
        const val KEY_CONFIGURATION = "configuration"

        override fun convert(dataItem: DataItem): BarrierSettings? {
            val dataObject = dataItem.getDataObject() ?: return null

            val id = dataObject.getString(KEY_BARRIER_ID)
            val scopes = dataObject.getDataList(KEY_SCOPES)
                ?.mapNotNull(DataItem::getString)
                ?.map(::barrierScopeFromString)

            if (id == null || scopes == null) return null

            val configuration = dataObject.getDataObject(KEY_CONFIGURATION)
                ?: DataObject.EMPTY_OBJECT

            return BarrierSettings(id, scopes.toSet(), configuration)
        }
    }
}