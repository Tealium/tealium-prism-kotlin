package com.tealium.core.internal.misc

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumList
import com.tealium.core.api.data.TealiumValue
import org.json.JSONStringer

fun TealiumValue.stringify(stringer: JSONStringer) {
    if (this == TealiumValue.NULL) {
        stringer.value(null)
        return
    }

    when (val it = value) {
        is TealiumList -> it.stringify(stringer)
        is TealiumBundle -> it.stringify(stringer)
        else -> stringer.value(it)
    }
}

fun TealiumList.stringify(stringer: JSONStringer) {
    stringer.array()
    for (value in this) {
        value.stringify(stringer)
    }
    stringer.endArray()
}

fun TealiumBundle.stringify(stringer: JSONStringer) {
    stringer.`object`()

    getAll().forEach { (key, value) ->
        stringer.key(key)
        value.stringify(stringer)
    }

    stringer.endObject()
}