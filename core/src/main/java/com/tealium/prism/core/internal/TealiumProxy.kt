package com.tealium.prism.core.internal

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.DataLayer
import com.tealium.prism.core.api.modules.DeepLinkHandler
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.modules.Trace
import com.tealium.prism.core.api.modules.VisitorIdManager
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.SingleResult
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.internal.misc.AsyncProxyImpl
import com.tealium.prism.core.internal.modules.ModuleProxyImpl
import com.tealium.prism.core.internal.modules.datalayer.DataLayerWrapper
import com.tealium.prism.core.internal.modules.deeplink.DeepLinkHandlerWrapper
import com.tealium.prism.core.internal.modules.trace.TraceWrapper
import com.tealium.prism.core.internal.modules.visitorid.VisitorIdManagerWrapper
import com.tealium.prism.core.internal.pubsub.AsyncDisposableContainer

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
 * @param onShutdown The callback to call with the bound [TealiumImpl] when the [shutdown] method is called.
 */
class TealiumProxy(
    override val key: String,
    private val tealiumScheduler: Scheduler,
    private val onTealiumImplReady: Observable<TealiumResult<TealiumImpl>>,
    private val onShutdown: (TealiumImpl) -> Unit = {}
) : Tealium {

    private val moduleManagerResult: Observable<TealiumResult<ModuleManager>> =
        onTealiumImplReady.map { result ->
            val tealium = result.getOrNull()
            if (tealium != null) {
                TealiumResult.success(tealium.moduleManager)
            } else {
                TealiumResult.failure(
                    result.exceptionOrNull()
                        ?: IllegalStateException("TealiumImpl result was neither success nor failure.")
                )
            }
        }
    private val asyncProxy = AsyncProxyImpl(tealiumScheduler, onTealiumImplReady)

    override val trace: Trace = TraceWrapper(this)
    override val deepLink: DeepLinkHandler = DeepLinkHandlerWrapper(this)
    override val dataLayer: DataLayer = DataLayerWrapper(this)
    override val visitorId: VisitorIdManager = VisitorIdManagerWrapper(this)

    private val disposable = AsyncDisposableContainer(disposeOn = tealiumScheduler)

    init {
        onTealiumImplReady.filter { it.exceptionOrNull() != null }
            .asSingle(tealiumScheduler)
            .subscribe {
                doShutdown()
            }
    }

    override fun onReady(callback: Callback<Tealium>) {
        asyncProxy.getProxiedObject { tealium ->
            if (tealium != null) {
                callback.onComplete(this)
            }
        }
    }

    override fun track(name: String, data: DataObject): SingleResult<TrackResult> =
        track(name, DispatchType.Event, data)

    override fun track(
        name: String,
        type: DispatchType,
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

    override fun <T : Module> createModuleProxy(clazz: Class<T>): ModuleProxy<T> =
        ModuleProxyImpl(clazz, moduleManagerResult, tealiumScheduler)

    private fun doShutdown() {
        disposable.dispose()
    }

    /**
     * Shuts down only the [TealiumImpl] this proxy is bound to: if it was already shut down, failed
     * to initialize, or a different instance has since been created with the same [key], this
     * does nothing.
     */
    override fun shutdown() {
        asyncProxy.getProxiedObject { tealium ->
            if (tealium != null) {
                onShutdown(tealium)
            }
        }
    }
}