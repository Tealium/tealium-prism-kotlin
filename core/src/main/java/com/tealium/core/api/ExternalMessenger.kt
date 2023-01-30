package com.tealium.core.api

import com.tealium.core.api.listeners.ExternalListener
import kotlin.reflect.KClass

interface ExternalMessenger<T : ExternalListener> : Messenger<T>