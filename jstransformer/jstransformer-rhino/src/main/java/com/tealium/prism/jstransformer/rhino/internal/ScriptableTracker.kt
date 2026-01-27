package com.tealium.prism.jstransformer.rhino.internal

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.api.tracking.Tracker
import com.tealium.prism.jstransformer.internal.JavaScriptTransformer
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.annotations.JSFunction

class ScriptableTracker(
    private val tracker: Tracker,
) {
    @JSFunction
    fun track(eventName: String) =
        track(eventName, null, null)

    @JSFunction
    fun track(eventName: String, eventType: String) =
        track(eventName, eventType, null)

    @JSFunction
    fun track(eventName: String, payload: NativeObject) =
        track(eventName, null, DataObject.fromMap(payload))

    @JSFunction
    fun track(eventName: String, eventType: String, payload: NativeObject) =
        track(eventName, eventType, DataObject.fromMap(payload))

    private fun track(eventName: String, eventType: String?, payload: DataObject?) {
        val dispatchType = eventType?.let {
            DispatchType.entries.find { it.name.lowercase() == eventType.lowercase() }
        }  ?: DispatchType.Event

        val payload = payload ?: DataObject.EMPTY_OBJECT

        val filterablePayload = payload.copy { put("js_tracking", true) }

        val dispatch = Dispatch.create(eventName, dispatchType, filterablePayload)
        tracker.track(
            dispatch,
            DispatchContext.Source.module(JavaScriptTransformer::class.java)
        )
    }
}