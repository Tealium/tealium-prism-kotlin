package com.tealium.core.internal.session

import com.tealium.core.api.session.SessionRegistry

class SessionRegistryImpl(sessionManager: SessionManager) :
    SessionRegistry by sessionManager