package com.tealium.prism.core.api.barriers

import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList

/**
 * The [BarrierScope] defines the available scope that can be assigned to a [Barrier] via a [BarrierSettings]
 *
 * There are only two available scopes that a [Barrier] can impact:
 *  - [All]
 *  - [Dispatchers]
 *
 * A [Barrier] scoped to [All] will be checked for its state for every [Dispatcher] before dispatching
 * events to it.
 * A [Barrier] scoped to [Dispatcher] will only be checked for its state before dispatching events to
 * the [Dispatchers] identified by the provided [dispatcherIds].
 */
sealed class BarrierScope : DataItemConvertible {
    /**
     * This [BarrierScope] will affect all [Dispatcher] implementations.
     */
    object All : BarrierScope() {
        const val STRING_VALUE = "all"
        private val dataItem = STRING_VALUE.asDataItem()

        override fun asDataItem(): DataItem {
            return dataItem
        }
    }


    /**
     * This [BarrierScope] will only affect the [Dispatcher] implementations with the matching
     * [Dispatcher.id] values provided in the [dispatcherIds] list.
     *
     * @param dispatcherIds The list of [Dispatcher] identifiers that this [BarrierScope] applies to.
     * These should match the [Dispatcher.id] values of the [Dispatcher]s that this scope should apply to.
     */
    data class Dispatchers(val dispatcherIds: List<String>) : BarrierScope() {
        constructor(dispatcherId: String, vararg dispatcherIds: String) : this(
            listOf(dispatcherId) + dispatcherIds
        )

        override fun asDataItem(): DataItem {
            return dispatcherIds.asDataList().asDataItem()
        }
    }

    object Converter: DataItemConverter<BarrierScope> {
        override fun convert(dataItem: DataItem): BarrierScope? {
            val stringValue = dataItem.getString()
            if (stringValue == All.STRING_VALUE) return All

            if (stringValue != null) return null

            val listValue = dataItem.getDataList()
                ?: return null

            val dispatcherIdList = listValue.mapNotNull(DataItem::getString)
            return Dispatchers(dispatcherIdList)
        }
    }
}