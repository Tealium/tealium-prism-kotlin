package com.tealium.prism.core.api.session

import com.tealium.prism.core.api.pubsub.Observable

/**
 * The [SessionRegistry] allows users to subscribe to [Session] update events. Updates occur whenever
 * a new event has been submitted for tracking.
 */
interface SessionRegistry {

    /**
     * An [Observable] stream of [Session] update events. There will be an emission each time the
     * session is updated, including:
     *  - the number of events in this session has been updated
     *  - the session status has been updated (started, ended etc)
     */
    val session: Observable<Session>
}