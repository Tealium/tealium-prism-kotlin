package com.tealium.core

import com.tealium.core.api.ModuleFactory
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

}
