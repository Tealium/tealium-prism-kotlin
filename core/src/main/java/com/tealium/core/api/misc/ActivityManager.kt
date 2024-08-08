package com.tealium.core.api.misc

import android.app.Activity
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.internal.persistence.getTimestamp

/**
 * Shared utility class for monitoring the current lifecycle state of a running application.
 *
 * This class should provide all Tealium instances with the current application status in terms of
 * whether it is currently foreground or backgrounded. In addition, it should provide information
 * enough to calculate the lifecycle metrics produced by the Lifecycle module.
 */
interface ActivityManager {

    /**
     * Possible states for the application status. The application should only be in [Init] state
     * before an activity is on show. After which point, the only possible states should be
     * [Foregrounded] or [Backgrounded]
     */
    sealed class ApplicationStatus(val timestamp: Long = getTimestamp()) {
        class Init : ApplicationStatus()
        class Foregrounded : ApplicationStatus()
        class Backgrounded : ApplicationStatus()
    }

    /**
     * Possible types of activity behaviour, which align with the possible events triggered by the
     * [android.app.Application.ActivityLifecycleCallbacks]
     */
    enum class ActivityLifecycleType {
        Created,
        Started,
        Stopped,
        Resumed,
        Paused,
        Destroyed
    }

    /**
     * This class allows for activity events to be published as a [Flow] which can therefore be
     * observed.
     */
    data class ActivityStatus(
        val type: ActivityLifecycleType,
        val activity: Activity,
        val timestamp: Long = getTimestamp()
    )

    /**
     * An [Observable] of all activity updates - this property will buffer the ActivityStatus
     * updates for a while to ensure all Activities are captured (required to maintain correct
     * foregrounding/backgrounding calculation) but also for possible AutoTracking that will need
     * a reference to actual Activity instance.
     *
     * Note. On Android it's not best practice to hold references to [Activity] in a static context,
     * therefore, the buffer will be reduced to only the latest update after a short timeout. This
     * will allow for late loaded [Tealium] instances to still receive any initial backlog of events.
     */
    val activities: Observable<ActivityStatus>

    /**
     * The current and future application status. This will initially be set to [ApplicationStatus.Init]
     * until an activity is launched
     */
    val applicationStatus: Observable<ApplicationStatus>
}
