package com.tealium.core.api

import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.internal.modules.CollectDispatcher
import com.tealium.core.internal.modules.VisitorServiceImpl

/**
 * Extension point for optional modules
 */
object Modules {
    @JvmField
    val Collect: ModuleFactory = CollectDispatcher.Factory

    @JvmField
    val VisitorService: ModuleFactory = VisitorServiceImpl.Factory

    /**
     * Collects data related to the current connectivity type of the device.
     *
     * @see Dispatch.Keys.CONNECTION_TYPE
     */
    @JvmField
    val ConnectivityCollector: ModuleFactory =
        com.tealium.core.internal.modules.ConnectivityCollector.Factory
}
