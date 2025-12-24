package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.misc.TealiumException

/**
 * Exception class to denote that a particular [Module] implementation was attempted to be used, but
 * was unavailable, either due to being disabled or not available.
 */
class ModuleNotEnabledException(message: String, cause: Throwable? = null) :
    TealiumException(message, cause)