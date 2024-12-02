package com.tealium.core.internal

import com.tealium.core.api.Tealium
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.DataLayer
import com.tealium.core.api.modules.DeeplinkManager
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.modules.TimedEventsManager
import com.tealium.core.api.modules.TraceManager
import com.tealium.core.api.modules.VisitorService
import com.tealium.core.api.modules.consent.ConsentManager
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.api.tracking.TrackResultListener
import com.tealium.core.internal.modules.datalayer.DataLayerWrapper
import com.tealium.core.internal.modules.DeepLinkManagerWrapper
import com.tealium.core.internal.modules.ModuleProxyImpl
import com.tealium.core.internal.modules.TimedEventsManagerWrapper
import com.tealium.core.internal.modules.TraceManagerWrapper
import com.tealium.core.internal.modules.VisitorServiceWrapper
import com.tealium.core.internal.pubsub.AsyncDisposableContainer
import com.tealium.core.internal.pubsub.addTo


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

    override val trace: TraceManager = TraceManagerWrapper(this)
    override val deeplink: DeeplinkManager = DeepLinkManagerWrapper(this)
    override val timedEvents: TimedEventsManager = TimedEventsManagerWrapper(this)
    override val dataLayer: DataLayer = DataLayerWrapper(this)
    override val consent: ConsentManager
        get() = TODO()
    override val visitorService: VisitorService? = VisitorServiceWrapper(this)

    private val disposable = AsyncDisposableContainer(disposeOn = tealiumScheduler)

    init {
        onTealiumImplReady.filter { it.exceptionOrNull() != null }
            .take(1)
            .subscribeOn(tealiumScheduler)
            .subscribe {
                doShutdown()
            }
    }

    private fun onTealiumImplReady(observer: Observer<TealiumResult<TealiumImpl>>) {
        onTealiumImplReady
            .take(1)
            .subscribeOn(scheduler = tealiumScheduler)
            .subscribe(observer)
            .addTo(disposable)
    }

    private fun <T> onTealiumSuccess(
        onSuccess: TealiumCallback<TealiumResult<T>>? = null,
        task: (TealiumImpl) -> T
    ) = onTealiumImplReady { initResult ->
        try {
            val tealiumImpl = initResult.getOrThrow()
            val result = task.invoke(tealiumImpl)
            onSuccess?.onComplete(TealiumResult.success(result))
        } catch (e: Exception) {
            onSuccess?.onComplete(TealiumResult.failure(e))
        }
    }

    override fun track(dispatch: Dispatch) = onTealiumImplReady { tealium ->
        tealium.getOrNull()?.track(dispatch)
    }

    override fun track(dispatch: Dispatch, onComplete: TrackResultListener) =
        onTealiumImplReady { tealium ->
            try {
                val tealiumImpl = tealium.getOrThrow()
                tealiumImpl.track(dispatch, onComplete)
            } catch (e: Exception) {
                onComplete.onTrackResultReady(dispatch, TrackResult.Dropped)
            }
            // TODO - arrange better error handling
        }

    override fun flushEventQueue() = onTealiumSuccess { tealium ->
        tealium.flushEventQueue()
    }

    override fun resetVisitorId(callback: TealiumCallback<TealiumResult<String>>) =
        onTealiumSuccess(callback) { tealium ->
            tealium.resetVisitorId()
        }

    override fun clearStoredVisitorIds(callback: TealiumCallback<TealiumResult<String>>) =
        onTealiumSuccess(callback) { tealium ->
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