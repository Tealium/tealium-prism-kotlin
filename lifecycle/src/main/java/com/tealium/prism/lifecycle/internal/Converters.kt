package com.tealium.prism.lifecycle.internal

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.lifecycle.LifecycleDataTarget
import com.tealium.prism.lifecycle.LifecycleDataTarget.AllEvents
import com.tealium.prism.lifecycle.LifecycleDataTarget.LifecycleEventsOnly
import com.tealium.prism.lifecycle.LifecycleEvent
import com.tealium.prism.lifecycle.LifecycleEvent.Launch
import com.tealium.prism.lifecycle.LifecycleEvent.Sleep
import com.tealium.prism.lifecycle.LifecycleEvent.Wake

object Converters {
    object LifecycleDataTargetConverter : DataItemConverter<LifecycleDataTarget> {
        override fun convert(dataItem: DataItem): LifecycleDataTarget? {
            return dataItem.getString()?.lowercase().let { str ->
                when (str) {
                    AllEvents.target.lowercase() -> AllEvents
                    LifecycleEventsOnly.target.lowercase() -> LifecycleEventsOnly
                    else -> null
                }
            }
        }
    }

    object LifecycleEventConverter : DataItemConverter<LifecycleEvent> {
        override fun convert(dataItem: DataItem): LifecycleEvent? {
            return dataItem.getString()?.lowercase().let { str ->
                when (str) {
                    Launch.event -> Launch
                    Wake.event -> Wake
                    Sleep.event -> Sleep
                    else -> null
                }
            }
        }
    }
}