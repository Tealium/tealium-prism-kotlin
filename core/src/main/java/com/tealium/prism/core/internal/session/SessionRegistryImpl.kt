package com.tealium.prism.core.internal.session

import com.tealium.prism.core.api.session.SessionRegistry

class SessionRegistryImpl(sessionManager: SessionManager) :
    SessionRegistry by sessionManager