package com.tealium.core.api

interface ModuleSettings {
    val enabled: Boolean
    val settings: Map<String, Any>
}
