package com.tealium.core.internal.settings.consent

import com.tealium.core.api.consent.CmpAdapter
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItemUtils.asDataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataObjectConvertible
import com.tealium.core.api.modules.Dispatcher

/**
 * An object containing the mapping between a [purposeId] (as provided by the [CmpAdapter]) and the
 * [dispatcherIds] for the [Dispatcher]s that need that purpose to be accepted in order to fire.
 *
 * @param purposeId The id of the purpose as provided by the [CmpAdapter]
 * @param dispatcherIds The id's of the [Dispatcher]s that need the given [purposeId] to be accepted in order to fire.
 */
data class ConsentPurpose(
    val purposeId: String,
    val dispatcherIds: Set<String>
): DataObjectConvertible {

    override fun asDataObject() = DataObject.create {
        put(Converter.KEY_PURPOSE_ID, purposeId)
        put(Converter.KEY_DISPATCHER_IDS, dispatcherIds.asDataList())
    }

    object Converter: DataItemConverter<ConsentPurpose> {
        const val KEY_PURPOSE_ID = "purpose_id"
        const val KEY_DISPATCHER_IDS = "dispatcher_ids"

        override fun convert(dataItem: DataItem): ConsentPurpose? {
            val dataObject = dataItem.getDataObject() ?: return null

            val purposeId = dataObject.getString(KEY_PURPOSE_ID)
            val dispatcherIds = dataObject.getDataList(KEY_DISPATCHER_IDS)?.mapNotNull { it.getString() }
                ?.toSet()

            if (purposeId == null || dispatcherIds == null) {
                return null
            }

            return ConsentPurpose(purposeId, dispatcherIds)
        }
    }
}