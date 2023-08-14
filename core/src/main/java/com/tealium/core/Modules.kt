package com.tealium.core

import com.tealium.core.api.ModuleFactory
import com.tealium.core.internal.modules.CollectDispatcher
import com.tealium.core.internal.modules.VisitorServiceImpl


/**
 * Extension point for optional modules
 */
object Modules {
    val Collect: ModuleFactory = CollectDispatcher.Factory
    val VisitorService: ModuleFactory = VisitorServiceImpl.Factory
}