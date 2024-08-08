package com.tealium.core.internal.modules

import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.internal.modules.consent.ConsentModule
import com.tealium.core.internal.modules.consent.ConsentSettings
import com.tealium.core.internal.dispatch.QueueManager
import com.tealium.core.api.pubsub.Observables

object InternalModuleFactories {
    fun consentModuleFactory(queueManager: QueueManager): ModuleFactory {
        return object : ModuleFactory {
            override val name: String
                get() = ConsentModule.NAME

            override fun create(context: TealiumContext, settings: ModuleSettings): Module {
                val consentSettings = ConsentSettings.fromBundle(settings.bundle)

                return ConsentModule(
                    context.moduleManager.modules,
                    queueManager,
                    context.transformerRegistry,
                    null,
                    Observables.stateSubject(consentSettings)
                )
            }
        }
    }
}