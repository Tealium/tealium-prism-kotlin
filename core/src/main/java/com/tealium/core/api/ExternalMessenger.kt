package com.tealium.core.api

import com.tealium.core.api.listeners.ExternalListener
import kotlin.reflect.KClass

abstract class ExternalMessenger<T : ExternalListener>(listener: KClass<T>) : Messenger<T>(listener)