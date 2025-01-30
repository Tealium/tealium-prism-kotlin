package com.tealium.lifecycle

import com.tealium.core.api.misc.TealiumException

class ManualTrackingException(message: String? = null, cause: Throwable? = null) :
    TealiumException(message, cause)

class InvalidEventOrderException(message: String? = null, cause: Throwable? = null) :
    TealiumException(message, cause)