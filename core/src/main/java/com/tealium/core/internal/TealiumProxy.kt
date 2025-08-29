package com.tealium.core.internal

import com.tealium.core.api.Tealium
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.DataLayer
import com.tealium.core.api.modules.DeepLinkHandler
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.modules.TimedEventsManager
import com.tealium.core.api.modules.Trace
import com.tealium.core.api.modules.VisitorService
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.SingleResult
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TealiumDispatchType
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.internal.misc.AsyncProxyImpl
import com.tealium.core.internal.modules.ModuleProxyImpl
import com.tealium.core.internal.modules.TimedEventsManagerWrapper
import com.tealium.core.internal.modules.VisitorServiceWrapper
import com.tealium.core.internal.modules.datalayer.DataLayerWrapper
import com.tealium.core.internal.modules.deeplink.DeepLinkHandlerWrapper
import com.tealium.core.internal.modules.trace.TraceWrapper
import com.tealium.core.internal.pubsub.AsyncDisposableContainer


/**
 * The [TealiumProxy] is the default [Tealium] implementation. It is a lightweight wrapper around
 * an underlying [TealiumImpl] instance.
 *
 * All methods and properties are expected to schedule their work asynchronously to ensure proper ordering
 * of events, and also to ensure that the underlying [TealiumImpl] instance is still valid.
 *
 * @param key The key that identifies the underlying [TealiumImpl] instance.
 * @param tealiumScheduler The [Scheduler] to synchronize all work on
 * @param onTealiumImplReady The [Observable] to inform this [TealiumProxy] of when the [TealiumImpl] has been created, or the error
 * @param onShutdown The callback to call when the [shutdown] method is called.
 */
class TealiumProxy(
    override val key: String,
    private val tealiumScheduler: Scheduler,
    private val onTealiumImplReady: Observable<TealiumResult<TealiumImpl>>,
    private val onShutdown: (String) -> Unit = {}
) : Tealium {

    private val moduleManager: Observable<ModuleManager?> =
        onTealiumImplReady.map { result ->
            result.getOrNull()?.moduleManager
        }
    private val asyncProxy = AsyncProxyImpl(tealiumScheduler, onTealiumImplReady)

    override val trace: Trace = TraceWrapper(this)
    override val deeplink: DeepLinkHandler = DeepLinkHandlerWrapper(this)
    override val timedEvents: TimedEventsManager = TimedEventsManagerWrapper(this)
    override val dataLayer: DataLayer = DataLayerWrapper(this)

    override val visitorService: VisitorService? = VisitorServiceWrapper(this)

    private val disposable = AsyncDisposableContainer(disposeOn = tealiumScheduler)

    init {
        onTealiumImplReady.filter { it.exceptionOrNull() != null }
            .asSingle(tealiumScheduler)
            .subscribe {
                doShutdown()
            }
    }

    override fun track(name: String, data: DataObject): SingleResult<TrackResult> =
        track(name, TealiumDispatchType.Event, data)

    override fun track(
        name: String,
        type: TealiumDispatchType,
        data: DataObject
    ): SingleResult<TrackResult> {
        val dispatch = Dispatch.create(name, type, data)
        return track(dispatch)
    }

    private fun track(dispatch: Dispatch) =
        asyncProxy.executeAsyncTask { tealium, callback ->
            tealium.track(dispatch) { trackResult ->
                callback.onComplete(TealiumResult.success(trackResult))
            }
        }

    override fun flushEventQueue() = asyncProxy.executeTask { tealium ->
        tealium.flushEventQueue()
    }

    override fun resetVisitorId() = asyncProxy.executeTask { tealium ->
        tealium.resetVisitorId()
    }

    override fun clearStoredVisitorIds() = asyncProxy.executeTask { tealium ->
        tealium.clearStoredVisitorIds()
    }

    override fun <T : Module> createModuleProxy(clazz: Class<T>): ModuleProxy<T> =
        ModuleProxyImpl(clazz, moduleManager, tealiumScheduler)

    private fun doShutdown() {
        disposable.dispose()
    }

    override fun shutdown() {
        onShutdown(key)
    }
}