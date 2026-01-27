package com.tealium.prism.jstransformer.rhino.internal

import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.jstransformer.JavaScriptTransformerFactory
import org.mozilla.javascript.annotations.JSFunction

class ScriptableLogger(
    private val logger: Logger
) {
    @JSFunction
    fun log(vararg args: Any?) =
        log(LogLevel.DEBUG, *args)

    @JSFunction
    fun info(vararg args: Any?) =
        log(LogLevel.INFO, *args)

    @JSFunction
    fun warn(vararg args: Any?) =
        log(LogLevel.WARN, *args)

    @JSFunction
    fun error(vararg args: Any?) =
        log(LogLevel.ERROR, *args)

    private fun log(level: LogLevel, vararg args: Any?) {
        logger.log(level, JavaScriptTransformerFactory.MODULE_TYPE, args.joinToString(" "))
    }
}