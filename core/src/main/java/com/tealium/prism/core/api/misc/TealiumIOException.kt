package com.tealium.prism.core.api.misc

/**
 * Base Exception class for all failures relating to Input/Output operations.
 */
open class TealiumIOException(
    message: String? = null,
    cause: Throwable? = null
): TealiumException(message, cause)
