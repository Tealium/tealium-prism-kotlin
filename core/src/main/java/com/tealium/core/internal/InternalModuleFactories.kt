package com.tealium.core.internal

import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.listeners.Listener
import com.tealium.core.internal.modules.ConsentManagerFactory

object InternalModuleFactories {

    fun consentManagerFactory(eventRouter: EventRouter<Listener>): ModuleFactory {
        return ConsentManagerFactory(eventRouter)
    }

//    fun <T: ModuleFactory> getFactory(clazz: Class<T>, eventRouter: EventRouter) : ModuleFactory? {
//        return when(clazz) {
//            ConsentManager::class.java -> consentManagerFactory(eventRouter)
//            else -> null
//        }
//    }
}