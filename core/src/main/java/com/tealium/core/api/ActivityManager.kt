package com.tealium.core.api

import com.tealium.core.internal.observables.ObservableState
import com.tealium.core.internal.observables.Observables

interface ActivityManager {

    enum class ApplicationStatus {
        Init, Foregrounded, Backgrounded
    }

    val applicationStatus: ObservableState<ApplicationStatus>
}

class ActivityManagerImpl : ActivityManager {
    private val _applicationStatus = Observables.stateSubject(ActivityManager.ApplicationStatus.Init)
    override val applicationStatus: ObservableState<ActivityManager.ApplicationStatus>
        get() = _applicationStatus.asObservableState()
}