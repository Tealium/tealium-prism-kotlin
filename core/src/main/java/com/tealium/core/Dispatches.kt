package com.tealium.core

import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TealiumDispatchType
import com.tealium.core.api.data.DataObject

// TODO - just a WIP in case there's a nicer more fluent way to create these.
object Dispatches {

    @JvmStatic
    fun event(eventName: String): DispatchBuilder {
        return BaseBuilder(eventName, TealiumDispatchType.Event)
    }

    @JvmStatic
    fun view(viewName: String): DispatchBuilder {
        return BaseBuilder(viewName, TealiumDispatchType.View)
    }

    interface DispatchBuilder {
        fun putContextData(key: String, value: String) : DispatchBuilder
        fun putContextData(dataObject: DataObject): DispatchBuilder
        fun build() : Dispatch
    }
}

class BaseBuilder(private val tealiumEvent: String, private val dispatchType: TealiumDispatchType): Dispatches.DispatchBuilder {

    private val builder: DataObject.Builder = DataObject.Builder()

    override fun putContextData(key: String, value: String) = apply {
        builder.put(key, value)
    }

    override fun putContextData(dataObject: DataObject) = apply {
        builder.putAll(dataObject)
    }

    override fun build(): Dispatch {
        return Dispatch.create(tealiumEvent, dispatchType, builder.build())
    }
}


