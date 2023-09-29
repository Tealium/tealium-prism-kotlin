package com.tealium.core.api.listeners

import android.app.Activity

interface ActivityObserver {
    fun onActivityPaused(activity: Activity?)
    fun onActivityResumed(activity: Activity)
    fun onActivityStopped(activity: Activity)
}

