package com.tealium.core.internal

import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.data.bundle.TealiumList
import com.tealium.core.api.data.bundle.TealiumValue
import org.json.JSONStringer

fun TealiumValue.stringify(stringer: JSONStringer) {
    if (this == TealiumValue.NULL) {
        stringer.value(null)
        return
    }

    if (value is TealiumList) {
        value.stringify(stringer)
    } else if (value is TealiumBundle) {
        value.stringify(stringer)
    } else {
        stringer.value(value)
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