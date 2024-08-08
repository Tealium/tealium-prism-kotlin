package com.tealium.core.api.modules

interface DeeplinkManager {
    fun handle(link: String)
    fun handle(link: String, referrer: String)
}
