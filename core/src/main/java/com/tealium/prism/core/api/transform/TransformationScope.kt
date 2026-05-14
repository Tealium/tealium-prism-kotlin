package com.tealium.prism.core.api.transform

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.tracking.Dispatch

/**
 * Sets out the available extension points during the [Dispatch] lifecycle.
 */
sealed class TransformationScope : DataItemConvertible {

    /**
     * This scope happens directly after all data collection has been completed from any [Collector]
     * implementations in the system, but before the [Dispatch] has been queued.
     */
    object AfterCollectors : TransformationScope() {
        const val STRING_VALUE: String = "aftercollectors"
        private val dataItem = STRING_VALUE.asDataItem()

        override fun asDataItem(): DataItem {
            return dataItem
        }
    }

    /**
     * This scope happens during the process of sending [Dispatch]es to individual [Dispatcher]s but
     * this scope will be run for all [Dispatcher]s in the system.
     *
     * @see Dispatchers
     */
    object AllDispatchers : TransformationScope() {
        const val STRING_VALUE: String = "alldispatchers"
        private val dataItem = STRING_VALUE.asDataItem()

        override fun asDataItem(): DataItem {
            return dataItem
        }
    }

    /**
     * This scope happens during the process of sending [Dispatch]es to an individual [Dispatcher] as
     * identified by the list of [dispatcherIds] given.
     */
    data class Dispatchers(val dispatcherIds: List<String>) : TransformationScope() {

        constructor(dispatcherId: String, vararg otherDispatcherIds: String) : this(
            arrayOf(dispatcherId, *otherDispatcherIds).asList()
        )

        override fun asDataItem(): DataItem =
            dispatcherIds.asDataList().asDataItem()
    }

    object Converter : DataItemConverter<TransformationScope> {
        override fun convert(dataItem: DataItem): TransformationScope? {
            val stringValue = dataItem.getString()
            if (stringValue == AfterCollectors.STRING_VALUE) return AfterCollectors
            if (stringValue == AllDispatchers.STRING_VALUE) return AllDispatchers

            val listValue = dataItem.getDataList()
                ?: return null

            val dispatcherIdList = listValue.mapNotNull(DataItem::getString)
            return Dispatchers(dispatcherIdList)
        }
    }
}