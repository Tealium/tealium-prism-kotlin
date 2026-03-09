package com.tealium.prism.momentsapi

import com.tealium.prism.core.api.misc.TealiumException

/**
 * Configuration exception for MomentsApi module setup errors.
 *
 * @param message Exception message describing the configuration issue
 */
class MomentsApiConfigurationException(message: String? = null, cause: Throwable? = null) :
    TealiumException(message, cause)
