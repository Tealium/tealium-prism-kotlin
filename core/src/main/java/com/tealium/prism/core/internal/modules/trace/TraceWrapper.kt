package com.tealium.prism.core.internal.modules.trace

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.modules.Trace
import com.tealium.prism.core.api.pubsub.Single
import com.tealium.prism.core.api.tracking.TrackResult

class TraceWrapper(
    private val moduleProxy: ModuleProxy<TraceModule>
) : Trace {

    constructor(
        tealium: Tealium
    ) : this(tealium.createModuleProxy(TraceModule::class.java))

    override fun forceEndOfVisit(): Single<TealiumResult<TrackResult>> =
        moduleProxy.executeAsyncModuleTask { trace, callback ->
            trace.forceEndOfVisit { trackResult ->
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