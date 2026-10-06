package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.misc.TealiumException

/**
 * Exception class to denote that a [Module] was not found at the moment an operation was about to
 * start, either because it is disabled in the current settings or because it was never added to the
 * [TealiumConfig].
 *
 * No work was started, so retrying the operation once the module has been enabled can succeed. An
 * operation that had already started when its module was shut down fails with
 * [ModuleShutdownException] instead.
 */
class ModuleNotEnabledException(message: String, cause: Throwable? = null) :
    TealiumException(message, cause)
