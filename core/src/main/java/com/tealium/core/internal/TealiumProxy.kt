package com.tealium.core.internal

import com.tealium.core.Tealium
import com.tealium.core.TealiumConfig
import com.tealium.core.api.ConsentManager
import com.tealium.core.api.DataLayer
import com.tealium.core.api.DeeplinkManager
import com.tealium.core.api.Dispatch
import com.tealium.core.api.ModuleManager
import com.tealium.core.api.TealiumResult
import com.tealium.core.api.Scheduler
import com.tealium.core.api.TimedEventsManager
import com.tealium.core.api.TraceManager
import com.tealium.core.api.VisitorService
import com.tealium.core.api.listeners.Observer
import com.tealium.core.api.listeners.TealiumCallback
import com.tealium.core.api.listeners.TrackResultListener
import com.tealium.core.internal.modules.InternalModuleManager
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.core.internal.modules.VisitorServiceWrapper
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.ReplaySubject
import com.tealium.core.internal.observables.subscribeOnce
import com.tealium.core.internal.persistence.DatabaseProvider
import com.tealium.core.internal.persistence.FileDatabaseProvider

class TealiumProxy(
    private val config: TealiumConfig,
    private val onReady: TealiumCallback<TealiumResult<Tealium>>? = null,
    private val tealiumScheduler: Scheduler,
    private val networkSchedulerSupplier: () -> Scheduler,
    private val onTealiumImplReady: ReplaySubject<TealiumImpl?> = Observables.replaySubject(1),
    private val databaseProvider: DatabaseProvider = FileDatabaseProvider(config),
    private val moduleManager: InternalModuleManager = ModuleManagerImpl(
        TealiumImpl.getDefaultModules() + config.modules,
        tealiumScheduler
    ),
) : Tealium {

    override val modules: ModuleManager
        get() = moduleManager // TODO - make a wrapper to stop casting
    override val trace: TraceManager = TraceManagerWrapper(moduleManager)
    override val deeplink: DeeplinkManager = DeepLinkManagerWrapper(moduleManager)
    override val timedEvents: TimedEventsManager = TimedEventsManagerWrapper(moduleManager)
    override val dataLayer: DataLayer = DataLayerWrapper(moduleManager)
    override val consent: ConsentManager
        get() = TODO()
    override val visitorService: VisitorService? = VisitorServiceWrapper(moduleManager)

    init {
        tealiumScheduler.execute {
            try {
                val tealiumImpl = TealiumImpl(
                    config,
                    databaseProvider,
                    tealiumScheduler = tealiumScheduler,
                    networkScheduler = networkSchedulerSupplier.invoke(),
                    moduleManager = moduleManager
                )

                onTealiumImplReady.onNext(tealiumImpl)
                onReady?.onComplete(TealiumResult.success(this))
            } catch (ex: Exception) {
                onTealiumImplReady.onNext(null)
                onReady?.onComplete(TealiumResult.failure(ex))
            }
        }
    }

    private fun onTealiumImplReady(observer: Observer<TealiumImpl>) {
        onTealiumImplReady.mapNotNull { it }
            .subscribeOn(scheduler = tealiumScheduler)
            .subscribeOnce(observer)
    }

    override fun track(dispatch: Dispatch) = onTealiumImplReady { tealium ->
        tealium.track(dispatch)
    }

    override fun track(dispatch: Dispatch, onComplete: TrackResultListener) =
        onTealiumImplReady { tealium ->
            tealium.track(dispatch, onComplete)
        }

    override fun flushEventQueue() = onTealiumImplReady { tealium ->
        tealium.flushEventQueue()
    }

    internal fun shutdown() = onTealiumImplReady { tealium ->
        onTealiumImplReady.clear()
        tealium.shutdown()
    }
}