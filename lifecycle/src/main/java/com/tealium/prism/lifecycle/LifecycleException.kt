package com.tealium.prism.lifecycle

import com.tealium.prism.core.api.misc.TealiumException

/**
 * Exception class thrown when a lifecycle event was tracked manually, but autotracking is enabled.
 */
class ManualTrackingException(message: String? = null, cause: Throwable? = null) :
    TealiumException(message, cause)

/**
 * Exception class thrown when a lifecycle event was not tracked in an appropriate order.
 * e.g. multiple launch events consecutively
 */
class InvalidEventOrderException(message: String? = null, cause: Throwable? = null) :
    TealiumException(message, cause)