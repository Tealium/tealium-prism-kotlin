package com.tealium.core.api

import com.tealium.core.api.data.bundle.TealiumBundle

interface Collector {
    fun collect(): TealiumBundle
}