package com.tealium.core.api.session

import com.tealium.core.api.pubsub.Observable

interface SessionRegistry {

    /**
     * An [Observable] stream of [Session] update events. There will be an emission each time the
     * session is updated, including:
     *  - the number of events in this session has been updated
     *  - the session status has been updated (started, ended etc)
     */
    val session: Observable<Session>
}