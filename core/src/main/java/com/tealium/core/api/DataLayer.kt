package com.tealium.core.api

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumList
import com.tealium.core.api.data.TealiumValue
import org.json.JSONArray
import org.json.JSONObject

interface DataLayer {

    fun put(key: String, value: TealiumValue)
    fun put(key: String, value: String)
    fun put(key: String, value: Int)
    fun put(key: String, value: Float)
    fun put(key: String, value: Long)
    fun put(key: String, value: Double)
    fun put(key: String, value: Boolean)
    fun put(key: String, value: TealiumBundle)
    fun put(key: String, value: TealiumList)

    // keep JSON support?
    fun put(key: String, value: JSONObject)
    fun put(key: String, value: JSONArray)

    fun put(key: String, value: Array<String>)
    fun put(key: String, value: Array<Float>)
    fun put(key: String, value: Array<Long>)
    fun put(key: String, value: Array<Double>)
    fun put(key: String, value: Array<Int>)
    fun put(key: String, value: Array<Boolean>)

    fun put(bundle: TealiumBundle)
    // todo - move to ktx?
    fun put(block: TealiumBundle.Builder.() -> Unit)

    fun remove(key: String)

    val onDataUpdated: Subscribable<DataLayerUpdatedListener>
    val onDataRemoved: Subscribable<DataLayerRemovedListener>

    fun interface DataLayerUpdatedListener {
        fun onDataUpdated(key: String, value: Any)
    }
    fun interface DataLayerRemovedListener {
        fun onDataRemoved(keys: Set<String>)
    }
}
