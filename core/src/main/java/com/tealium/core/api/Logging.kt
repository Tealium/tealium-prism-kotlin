package com.tealium.core.api


interface Logging {
    fun debug(tag: String, msg: String)
    fun debug(tag: String, msg: () -> String)
    fun info(tag: String, msg: String)
    fun info(tag: String, msg: () -> String)
    fun error(tag: String, msg: String)
    fun error(tag: String, msg: () -> String)
}

