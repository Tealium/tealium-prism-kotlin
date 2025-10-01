package com.tealium.prism.core.internal.misc

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.misc.ActivityManager.ActivityLifecycleType
import com.tealium.prism.core.api.misc.ActivityManager.ActivityStatus
import com.tealium.prism.core.api.misc.ActivityManager.ApplicationStatus
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.ReplaySubject
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.internal.pubsub.addTo
import com.tealium.prism.core.internal.utils.Singleton
import java.util.concurrent.TimeUnit

/**
 * This is the default ActivityManager implementation, expected to be subscribed to by Tealium instances
 * and not necessarily the individual components. It is expected to be initialized during
 * [Application.onCreate] to ensure that all updates are collected.
 * This allows for [Tealium] instances to loaded later in the initialization process to minimize
 * impact on startup time.
 *
 * To further minimize startup times, Activity and Application state updates are collected and
 * published on the Main Thread so as not to require the Tealium processing thread to be created
 * during the launch.
 */
class ActivityManagerImpl(
    application: Application,
    private val mainScheduler: Scheduler = LooperScheduler(),
    timeoutSeconds: Long = 10L, //TODO - decide on buffer period
    launchStatus: ApplicationStatus = ApplicationStatus.Init(),
    private val _activities: ReplaySubject<ActivityStatus> =
        Observables.replaySubject(),
    private val _applicationStatusBuffer: ReplaySubject<ApplicationStatus> =
        Observables.replaySubject(),
    private val _applicationStatus: StateSubject<ApplicationStatus> =
        Observables.stateSubject(launchStatus),
    private val activityMonitor: ActivityMonitor = ActivityCallbacks(
        application
    ),
) : ActivityManager {

    private var container = DisposableContainer()
    private var activityCount = 0

    override val activities: Observable<ActivityStatus>
        get() = _activities.asObservable()
            .subscribeOn(mainScheduler)

    override val applicationStatus: Observable<ApplicationStatus>
        get() = _applicationStatusBuffer.asObservable()
            .subscribeOn(mainScheduler)

    init {
        _applicationStatus.subscribe(_applicationStatusBuffer)
            .addTo(container)
        activityMonitor.activityUpdates.subscribe(::handleActivityChanged)
            .addTo(container)
        activityMonitor.activityUpdates.subscribe(_activities)
            .addTo(container)

        mainScheduler.schedule(TimeFrame(timeoutSeconds, TimeUnit.SECONDS)) {
            Log.d(
                BuildConfig.TAG,
                "Init grace period expired. Clearing buffered ActivityStatus/ApplicationStatus"
            )
            // clear caches
            _activities.resize(0)
            _applicationStatusBuffer.resize(1)
        }

        activityMonitor.register()
    }

    private fun handleActivityChanged(activityStatus: ActivityStatus) {
        if (activityStatus.type == ActivityLifecycleType.Resumed) {
            val appStatus = _applicationStatus.value
            activityCount++

            if (appStatus is ApplicationStatus.Backgrounded || appStatus is ApplicationStatus.Init) {
                _applicationStatus.onNext(ApplicationStatus.Foregrounded())
            }
        } else if (activityStatus.type == ActivityLifecycleType.Stopped) {
            activityCount--

            if (activityCount == 0 && !activityStatus.activity.isChangingConfigurations) {
                _applicationStatus.onNext(ApplicationStatus.Backgrounded())
            }
        }
    }

    /**
     * Internal interface for subscribing to [Activity] lifecycle changes.
     */
    interface ActivityMonitor {

        /**
         * An [Observable] to be notified of changes to the current activity lifecycle. These updates
         * will follow the standard Activity lifecycle order as defined by the Android platform
         * See [Android Lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle)
         */
        val activityUpdates: Observable<ActivityStatus>

        /**
         * Called to start subscribing to Activity updates from the Android system.
         */
        fun register()

        /**
         * Called to stop subscribing to Activity updates from the Android system.
         */
        fun unregister()
    }

    /**
     * Default implementation of [ActivityMonitor].
     *
     * @param application instance of the android [Application] used to subscribe for activity updates
     * @param _activityUpdates the [Subject] used for publishing the activity updates to
     */
    class ActivityCallbacks(
        private val application: Application,
        private val _activityUpdates: Subject<ActivityStatus> = Observables.publishSubject(),
    ) : Application.ActivityLifecycleCallbacks, ActivityMonitor {

        override val activityUpdates: Observable<ActivityStatus>
            get() = _activityUpdates.asObservable()

        override fun register() {
            application.registerActivityLifecycleCallbacks(this)
        }

        override fun unregister() {
            application.unregisterActivityLifecycleCallbacks(this)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            notify(ActivityLifecycleType.Created, activity)
        }

        override fun onActivityStarted(activity: Activity) {
            notify(ActivityLifecycleType.Started, activity)
        }

        override fun onActivityResumed(activity: Activity) {
            notify(ActivityLifecycleType.Resumed, activity)
        }

        override fun onActivityPaused(activity: Activity) {
            notify(ActivityLifecycleType.Paused, activity)
        }

        override fun onActivityStopped(activity: Activity) {
            notify(ActivityLifecycleType.Stopped, activity)
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {
            notify(ActivityLifecycleType.Destroyed, activity)
        }

        private fun notify(lifecycleType: ActivityLifecycleType, activity: Activity) {
            _activityUpdates.onNext(
                ActivityStatus(
                    lifecycleType,
                    activity
                )
            )
        }
    }

    companion object : Singleton<ActivityManagerImpl, Application>({
        ActivityManagerImpl(it)
    })
}

/**
 * ActivityManager implementation that simply passes events downstream. This implementation is what
 * is expected to be provided and subscribed to by internal components of a [Tealium] instance.
 *
 * @param activitySubject the [Subject] used to publish [ActivityStatus] updates to
 * @param appSubject the [Subject] used to publish [ApplicationStatus] updates to
 */
class ActivityManagerProxy(
    private val activitySubject: Subject<ActivityStatus> = Observables.publishSubject(),
    private val appSubject: ReplaySubject<ApplicationStatus> = Observables.replaySubject(1),
) : ActivityManager {
    override val activities: Observable<ActivityStatus>
        get() = activitySubject.asObservable()
    override val applicationStatus: Observable<ApplicationStatus>
        get() = appSubject.asObservable()
}

