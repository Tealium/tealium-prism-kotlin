package com.tealium.prism.core.internal.modules.visitorid

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.modules.VisitorIdManager
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.SingleResult
import com.tealium.prism.core.api.pubsub.Subscribable

class VisitorIdManagerWrapper(
    private val moduleProxy: ModuleProxy<VisitorIdModule>
) : VisitorIdManager {

    constructor(
        tealium: Tealium
    ) : this(tealium.createModuleProxy(VisitorIdModule::class.java))

    private val onVisitorId: Subscribable<TealiumResult<String>> =
        moduleProxy.observeModuleResult { module -> module.visitorId }

    override fun subscribe(observer: Observer<TealiumResult<String>>): Disposable =
        onVisitorId.subscribe(observer)

    override fun current(): SingleResult<String> =
        moduleProxy.executeModuleTask { module ->
            module.visitorId.value
        }

    override fun regenerate(): SingleResult<String> =
        moduleProxy.executeModuleTask { module ->
            module.regenerate()
        }

    override fun eraseLocalHistory(): SingleResult<String> =
        moduleProxy.executeModuleTask { module ->
            module.eraseLocalHistory()
        }
}
