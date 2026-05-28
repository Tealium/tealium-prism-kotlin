package com.tealium.prism.jstransformer.rhino.internal

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/**
 * Converts a [DataItem] into its primitive value if possible, or a [Scriptable] type for [DataObject]
 * and [DataList].
 */
fun DataItem.asAnyScriptable(ctx: Context, scope: ScriptableObject): Any? {
    return when(val item = value) {
        is DataObject -> item.asScriptable(ctx, scope)
        is DataList -> item.asScriptable(ctx, scope)
        is String, is Number, is Boolean -> item
        else -> null
    }
}

/**
 * Converts a [DataObject] into its [Scriptable] equivalent.
 */
fun DataObject.asScriptable(ctx: Context, scope: ScriptableObject) : Scriptable {
    val obj = ctx.newObject(scope)
    for ((key, item) in getAll()) {
        ScriptableObject.putProperty(obj, key, item.asAnyScriptable(ctx, scope))
    }
    return obj
}

/**
 * Converts a [DataList] into its [Scriptable] equivalent.
 */
fun DataList.asScriptable(ctx: Context, scope: ScriptableObject) : Scriptable {
    val items = this.map { item -> item.asAnyScriptable(ctx, scope) }
        .toTypedArray()

    return ctx.newArray(scope, items)
}

