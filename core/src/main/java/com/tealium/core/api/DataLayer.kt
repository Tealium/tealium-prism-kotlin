package com.tealium.core.api

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.api.listeners.Subscribable
import com.tealium.core.api.listeners.TealiumCallback

interface DataLayer {

    fun edit(block: TealiumCallback<DataStore.Editor>)

    fun put(bundle: TealiumBundle, expiry: Expiry)

    fun get(key: String, callback: TealiumCallback<TealiumValue?>)
    // TODO - Add convenience methods back in?
    fun remove(key: String)

    val onDataUpdated: Subscribable<TealiumBundle>
    // TODO - switch this to a Set instead of a List
    val onDataRemoved: Subscribable<List<String>>
}
