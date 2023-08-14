package com.tealium.core.api

import com.tealium.core.api.data.TealiumBundle

interface Collector {
    fun collect(): TealiumBundle
}