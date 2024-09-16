package com.tealium.core.api.misc

import java.lang.Exception

/**
 * Base class for all Tealium custom [Exception] implementations to inherit from
 */
open class TealiumException(
    message: String? = null,
    cause: Throwable? = null
): Exception(message, cause)
