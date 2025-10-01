package com.tealium.prism.lifecycle

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConvertible

/**
 * LifecycleEvent defines trackable events related to the application lifecycle.
 */
enum class LifecycleEvent(val event: String) : DataItemConvertible {
    Launch("launch"),
    Wake("wake"),
    Sleep("sleep");

    override fun asDataItem(): DataItem {
        return DataItem.string(event)
    }
}