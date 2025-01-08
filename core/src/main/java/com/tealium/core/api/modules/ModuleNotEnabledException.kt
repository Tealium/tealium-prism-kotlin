package com.tealium.core.api.modules

import com.tealium.core.api.misc.TealiumException

class ModuleNotEnabledException(message: String, cause: Throwable? = null) :
    TealiumException(message, cause)