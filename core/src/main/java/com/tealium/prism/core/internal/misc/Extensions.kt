package com.tealium.prism.core.internal.misc

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataItem
import org.json.JSONStringer

fun DataItem.stringify(stringer: JSONStringer) {
    if (this == DataItem.NULL) {
        stringer.value(null)
        return
    }

    when (val it = value) {
        is DataList -> it.stringify(stringer)
        is DataObject -> it.stringify(stringer)
        else -> stringer.value(it)
    }
}

fun DataList.stringify(stringer: JSONStringer) {
    stringer.array()
    for (value in this) {
        value.stringify(stringer)
    }
    stringer.endArray()
}

fun DataObject.stringify(stringer: JSONStringer) {
    stringer.`object`()

    getAll().forEach { (key, value) ->
        stringer.key(key)
        value.stringify(stringer)
    }

    stringer.endObject()
}