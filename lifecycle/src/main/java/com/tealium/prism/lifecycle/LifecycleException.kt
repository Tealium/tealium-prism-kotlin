package com.tealium.prism.lifecycle

import com.tealium.prism.core.api.misc.TealiumException

class ManualTrackingException(message: String? = null, cause: Throwable? = null) :
    TealiumException(message, cause)

class InvalidEventOrderException(message: String? = null, cause: Throwable? = null) :
    TealiumException(message, cause)