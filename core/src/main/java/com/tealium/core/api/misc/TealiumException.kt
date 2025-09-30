package com.tealium.core.api.misc

/**
 * Base class for all Tealium custom exception implementations to inherit from
 */
open class TealiumException(
    message: String? = null,
    cause: Throwable? = null
): RuntimeException(message, cause)
