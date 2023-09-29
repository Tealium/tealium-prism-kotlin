package com.tealium.core.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.tealium.core.TealiumConfig
import com.tealium.core.api.listeners.ActivityObserver

class ActivityObserverImpl(
    config: TealiumConfig,
    private val activityListener: ActivityObserver // TODO likely needs a list or use flows?
) {

    private val application: Application = config.application
    private val activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks

    init {
        activityLifecycleCallbacks = createActivityLifecycleCallbacks()
        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }

    private fun createActivityLifecycleCallbacks(): Application.ActivityLifecycleCallbacks {
        return object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            }

            override fun onActivityStarted(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
                activityListener.onActivityResumed(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                activityListener.onActivityPaused(activity)
            }

            override fun onActivityStopped(activity: Activity) {
                activityListener.onActivityStopped(activity)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }
        }
    }
}