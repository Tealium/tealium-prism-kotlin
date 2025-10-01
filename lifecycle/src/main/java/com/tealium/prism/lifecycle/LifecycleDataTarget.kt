package com.tealium.prism.lifecycle

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConvertible

/**
 * LifecycleData target defines what targets the lifecycle event data is
 * added to. [LifecycleDataTarget.LifecycleEventsOnly] is selected by default,
 * and will only add related data to lifecycle events.
 */
enum class LifecycleDataTarget(val target: String) : DataItemConvertible {
    AllEvents("allEvents"),
    LifecycleEventsOnly("lifecycleEventsOnly");

    override fun asDataItem(): DataItem {
        return DataItem.string(target)
    }
}