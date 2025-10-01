package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.misc.TealiumException

class ModuleNotEnabledException(message: String, cause: Throwable? = null) :
    TealiumException(message, cause)