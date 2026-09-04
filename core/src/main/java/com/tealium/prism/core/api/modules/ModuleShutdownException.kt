package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.TealiumException

/**
 * Exception class to denote that the [Module] named by [moduleId] has been shut down, either
 * together with the [Tealium] instance or by a settings update that disabled it. Operations of that
 * [Module] that were still pending at that point fail with this exception.
 */
class ModuleShutdownException(
    val moduleId: String,
    message: String? = null,
    cause: Throwable? = null
) : TealiumException(message ?: "Module ($moduleId) has been shut down.", cause)
