package com.tealium.core.api

interface DeeplinkManager {
    fun handle(link: String)
    fun handle(link: String, referrer: String)
}
