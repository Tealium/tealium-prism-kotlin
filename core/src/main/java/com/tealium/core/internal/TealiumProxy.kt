package com.tealium.core.internal

import android.util.Log
import com.tealium.core.BuildConfig
import com.tealium.core.Tealium
import com.tealium.core.TealiumConfig
import com.tealium.core.api.ActivityManager
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
import com.tealium.core.internal.observables.DisposableContainer
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.ReplaySubject
import com.tealium.core.internal.observables.addTo
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
    private val activityManager: ActivityManager = ActivityManagerImpl.getInstance(config.application)
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

    private val disposable = DisposableContainer()

    init {
        tealiumScheduler.execute {
            try {
                val activityManagerProxy = subscribeActivityManager()

                val tealiumImpl = TealiumImpl(
                    config,
                    databaseProvider,
                    tealiumScheduler = tealiumScheduler,
                    networkScheduler = networkSchedulerSupplier.invoke(),
                    moduleManager = moduleManager,
                    activityManager = activityManagerProxy
                )

                onTealiumImplReady.onNext(tealiumImpl)
                onReady?.onComplete(TealiumResult.success(this))
            } catch (ex: Exception) {
                onTealiumImplReady.onNext(null)
                disposable.dispose()
                onReady?.onComplete(TealiumResult.failure(ex))
            }
        }
    }

    /**
     * The main [ActivityManagerImpl] publishes updates on the Main scheduler. Allowing internal
     * components to simply subscribe/observe on the tealium scheduler would mean that they each would
     * receive their notifications in a separate [Runnable].
     *
     * This method sets up a new ActivityManager for use within this Tealium instance, to
     * publish each update to all components in one go.
     *
     * It also schedules the activity/application notifications to be sent once the TealiumImpl
     * is finished loading. This could potentially be moved into the TealiumImpl instead, after the
     * Modules have finished loading.
     */
    private fun subscribeActivityManager(): ActivityManager {
        val activitySubject = Observables.publishSubject<ActivityManager.ActivityStatus>()
        val appSubject = Observables.publishSubject<ActivityManager.ApplicationStatus>()
        val activityManagerProxy = ActivityManagerProxy(activitySubject, appSubject)

        activityManager.applicationStatus.observeOn(tealiumScheduler)
            .combine(onTealiumImplReadyOnce) { status, _ ->
                Log.d(
                    BuildConfig.TAG,
                    "Publishing new ApplicationStatus: $status"
                )
                status
            }
            .subscribe(appSubject)
            .addTo(disposable)

        activityManager.activities.observeOn(tealiumScheduler)
            .combine(onTealiumImplReadyOnce) { status, _ ->
                Log.d(
                    BuildConfig.TAG,
                    "Publishing new ActivityStatus: $status"
                )
                status
            }
            .subscribe(activitySubject)
            .addTo(disposable)

        return activityManagerProxy
    }

    private val onTealiumImplReadyOnce: Observable<TealiumImpl?>
        get() = onTealiumImplReady
            .take(1)

    private fun onTealiumImplReady(observer: Observer<TealiumImpl?>) {
        onTealiumImplReady
            .subscribeOn(scheduler = tealiumScheduler)
            .subscribeOnce(observer)
            .addTo(disposable)
    }

    override fun track(dispatch: Dispatch) = onTealiumImplReady { tealium ->
        tealium?.track(dispatch)
    }

    override fun track(dispatch: Dispatch, onComplete: TrackResultListener) =
        onTealiumImplReady { tealium ->
            tealium?.track(dispatch, onComplete)
        }

    override fun flushEventQueue() = onTealiumImplReady { tealium ->
        tealium?.flushEventQueue()
    }

    internal fun shutdown() = onTealiumImplReady { tealium ->
        disposable.dispose()

        tealium?.shutdown()
    }
}