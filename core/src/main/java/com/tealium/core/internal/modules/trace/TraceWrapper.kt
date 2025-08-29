package com.tealium.core.internal.modules.trace

import com.tealium.core.api.Tealium
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.modules.Trace
import com.tealium.core.api.pubsub.Single
import com.tealium.core.api.tracking.TrackResult

class TraceWrapper(
    private val moduleProxy: ModuleProxy<TraceModule>
) : Trace {

    constructor(
        tealium: Tealium
    ) : this(tealium.createModuleProxy(TraceModule::class.java))

    override fun killVisitorSession(): Single<TealiumResult<TrackResult>> =
        moduleProxy.executeAsyncModuleTask { trace, callback ->
            trace.killVisitorSession { trackResult ->
                callback.onComplete(TealiumResult.success(trackResult))
            }
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