package com.tealium.core.internal.modules

import com.tealium.core.TealiumContext
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleSettings
import com.tealium.core.internal.ConsentManagerImpl
import com.tealium.core.internal.EventRouter

class ConsentManagerFactory(private val eventRouter: EventRouter): ModuleFactory {
    override val name: String
        get() = "ConsentManager"

    override fun create(context: TealiumContext, settings: ModuleSettings): Module? {
        return ConsentManagerImpl(context, eventRouter)
    }
}