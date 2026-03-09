package com.tealium.prism.momentsapi.internal

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.mapValuesNotNull
import com.tealium.prism.momentsapi.EngineResponse
import com.tealium.prism.momentsapi.MomentsApiRegion

object Converters {
    object MomentsApiRegionConverter : DataItemConverter<MomentsApiRegion> {
        override fun convert(dataItem: DataItem): MomentsApiRegion? {
            val originalString = dataItem.getString() ?: return null
            return when (originalString.lowercase()) {
                MomentsApiRegion.Germany.value -> MomentsApiRegion.Germany
                MomentsApiRegion.UsEast.value -> MomentsApiRegion.UsEast
                MomentsApiRegion.Sydney.value -> MomentsApiRegion.Sydney
                MomentsApiRegion.Oregon.value -> MomentsApiRegion.Oregon
                MomentsApiRegion.Tokyo.value -> MomentsApiRegion.Tokyo
                MomentsApiRegion.HongKong.value -> MomentsApiRegion.HongKong
                else -> MomentsApiRegion.Custom(originalString)
            }
        }
    }

    object EngineResponseConverter : DataItemConverter<EngineResponse> {
        const val KEY_AUDIENCES = "audiences"
        const val KEY_BADGES = "badges"
        const val KEY_FLAGS = "flags"
        const val KEY_DATES = "dates"
        const val KEY_METRICS = "metrics"
        const val KEY_PROPERTIES = "properties"

        override fun convert(dataItem: DataItem): EngineResponse? {
            val dataObject = dataItem.getDataObject() ?: return null

            val audiences = dataObject.getDataList(KEY_AUDIENCES)?.mapNotNull(DataItem::getString)
            val badges = dataObject.getDataList(KEY_BADGES)?.mapNotNull(DataItem::getString)
            val flags = dataObject.getDataObject(KEY_FLAGS)?.mapValuesNotNull(DataItem::getBoolean)
            val dates = dataObject.getDataObject(KEY_DATES)?.mapValuesNotNull(DataItem::getLong)
            val metrics = dataObject.getDataObject(KEY_METRICS)?.mapValuesNotNull(DataItem::getDouble)
            val properties = dataObject.getDataObject(KEY_PROPERTIES)?.mapValuesNotNull(DataItem::getString)

            return EngineResponse(
                audiences = audiences,
                badges = badges,
                flags = flags,
                dates = dates,
                metrics = metrics,
                properties = properties
            )
        }
    }
}

