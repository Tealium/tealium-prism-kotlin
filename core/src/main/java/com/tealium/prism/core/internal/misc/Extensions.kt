package com.tealium.prism.core.internal.misc

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.pubsub.Disposable
import java.util.concurrent.TimeUnit
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

/**
 * Always schedules the [runnable] asynchronously on this [Scheduler], even if we are currently running on it.
 *
 * Unlike [Scheduler.execute], which may run the [runnable] inline when called from the scheduler itself,
 * this posts it to the scheduler, so it runs after the current task has finished.
 */
internal fun Scheduler.async(runnable: Runnable): Disposable =
    schedule(TimeFrame(0, TimeUnit.MILLISECONDS), runnable)
