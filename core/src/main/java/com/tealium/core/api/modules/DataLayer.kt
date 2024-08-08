package com.tealium.core.api.modules

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.pubsub.Subscribable
import com.tealium.core.api.misc.TealiumCallback

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
