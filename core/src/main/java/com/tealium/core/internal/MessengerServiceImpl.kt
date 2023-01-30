package com.tealium.core.internal

import com.tealium.core.api.Messenger
import com.tealium.core.api.MessengerService
import com.tealium.core.api.Subscribable
import com.tealium.core.api.listeners.ExternalListener
import com.tealium.core.api.listeners.Listener

class MessengerServiceImpl(
    private val eventRouter: EventRouter
): MessengerService, Subscribable<Listener> by eventRouter {

    override fun <T: ExternalListener> send(messenger: Messenger<T>) {
        eventRouter.send(messenger)
    }
}