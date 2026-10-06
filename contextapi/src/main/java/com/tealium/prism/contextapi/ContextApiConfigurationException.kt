package com.tealium.prism.contextapi

import com.tealium.prism.core.api.misc.TealiumException

/**
 * Configuration exception for ContextApi module setup errors.
 *
 * @param message Exception message describing the configuration issue
 */
class ContextApiConfigurationException(message: String? = null, cause: Throwable? = null) :
    TealiumException(message, cause)
