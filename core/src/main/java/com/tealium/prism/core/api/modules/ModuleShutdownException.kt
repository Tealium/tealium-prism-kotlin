package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.TealiumException

/**
 * Exception class to denote that the [Module] named by [moduleId] had started an operation but was
 * shut down by a settings update before the operation could complete. Modules should fail any
 * operation still pending when they are shut down with this exception, so that callers are not
 * left waiting.
 *
 * An operation requested after a settings update has disabled the module receives
 * [ModuleNotEnabledException] instead. An operation requested after, or still running when, the
 * [Tealium] instance is shut down receives [Tealium.TealiumShutdownException].
 */
class ModuleShutdownException(
    val moduleId: String,
    message: String? = null,
    cause: Throwable? = null
) : TealiumException(message ?: "Module ($moduleId) has been shut down.", cause)
