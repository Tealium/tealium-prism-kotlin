package com.tealium.prism.jstransformer.rhino.internal

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.annotations.JSFunction

class ScriptableDataLayer(
    private val dataLayer: DataStore,
    private val ctx: Context,
    private val scope: ScriptableObject
) {

    object ScriptableExpiry {
        val session = Expiry.SESSION.expiryTime()
        val forever = Expiry.FOREVER.expiryTime()
        val untilRestart = Expiry.UNTIL_RESTART.expiryTime()
    }

    @JSFunction
    fun get(key: String): Any? =
        dataLayer.get(key)?.asAnyScriptable(ctx, scope)

    @JSFunction
    fun getAll(): Scriptable? =
        dataLayer.getAll().asScriptable(ctx, scope)

    @JSFunction
    fun remove(key: String) =
        dataLayer.edit()
            .remove(key)
            .commit()

    @JSFunction
    fun clear() =
        dataLayer.edit()
            .clear()
            .commit()

    @JSFunction
    fun put(key: String, value: Any) =
        put(key, value, Expiry.FOREVER)

    @JSFunction
    fun put(key: String, value: Any, expiry: Long) =
        put(key, value, Expiry.fromLongValue(expiry))

    fun put(key: String, value: Any, expiry: Expiry) =
        dataLayer.edit()
            .put(key, DataItem.convert(value), expiry)
            .commit()
}