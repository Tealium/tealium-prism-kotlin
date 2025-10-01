package com.tealium.prism.core.internal.settings.consent

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.consent.CmpAdapter
import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.mapValuesNotNull
import com.tealium.prism.core.api.modules.Dispatcher

/**
 * An object containing the data used to understand and utilize consent purposes received by the [CmpAdapter]
 *
 * @param tealiumPurposeId The purpose that needs to be accepted to allow [Tealium] to perform any action.
 * @param refireDispatcherIds The id's of the [Dispatcher]s that are allowed to refire
 *          [Dispatch]es, previously sent with implicit consent, after a user explicitly gives consent.
 * @param purposes A list of purposes, as provided by the [CmpAdapter], and the list of [Dispatcher]s
 *          that need that purpose to be accepted to fire.
 */
data class ConsentConfiguration(
    val tealiumPurposeId: String,
    val refireDispatcherIds: Set<String>,
    val purposes: Map<String, ConsentPurpose>
) {
    object Converter : DataItemConverter<ConsentConfiguration> {
        const val KEY_TEALIUM_PURPOSE_ID = "tealium_purpose_id"
        const val KEY_REFIRE_DISPATCHER_IDS = "refire_dispatcher_ids"
        const val KEY_PURPOSES = "purposes"

        override fun convert(dataItem: DataItem): ConsentConfiguration? {
            val dataObject = dataItem.getDataObject() ?: return null

            val tealiumPurposeId = dataObject.getString(KEY_TEALIUM_PURPOSE_ID)
                ?: return null

            val purposes = dataObject.getDataObject(KEY_PURPOSES)
                ?.mapValuesNotNull(ConsentPurpose.Converter::convert)
                ?: return null

            val refireDispatcherIds = dataObject.getDataList(KEY_REFIRE_DISPATCHER_IDS)
                ?.mapNotNull(DataItem::getString)
                ?.toSet()
                ?: emptySet()

            return ConsentConfiguration(tealiumPurposeId, refireDispatcherIds, purposes)
        }
    }
}