package com.tealium.prism.core.internal.session

import com.tealium.prism.core.api.session.SessionRegistry
import com.tealium.prism.core.api.tracking.Dispatch

interface SessionManager: SessionRegistry {

    /**
     * Registers an incoming dispatch, which will extend the life of the session, and append any
     * necessary Session info on the [Dispatch] payload.
     */
    fun registerDispatch(dispatch: Dispatch)
}

