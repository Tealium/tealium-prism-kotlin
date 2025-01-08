package com.tealium.core.internal.modules.trace

import com.tealium.core.api.Tealium
import com.tealium.core.api.misc.TealiumException
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.modules.TraceManager
import com.tealium.core.api.pubsub.Single
import com.tealium.core.api.tracking.TrackResult

class TraceManagerWrapper(
    private val moduleProxy: ModuleProxy<TraceManagerModule>
) : TraceManager {

    constructor(
        tealium: Tealium
    ) : this(tealium.createModuleProxy(TraceManagerModule::class.java))

    override fun killVisitorSession(): Single<TealiumResult<Unit>> =
        moduleProxy.executeModuleTask { trace, callback ->
            trace.killVisitorSession(callback)
        }

    override fun join(id: String): Single<TealiumResult<Unit>> =
        moduleProxy.executeModuleTask { trace ->
            trace.join(id)
        }

    override fun leave(): Single<TealiumResult<Unit>> =
        moduleProxy.executeModuleTask { trace ->
            trace.leave()
        }
}