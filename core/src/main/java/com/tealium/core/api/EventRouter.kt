package com.tealium.core.api

import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

interface EventRouter :
    DispatchReadyListener,
    DispatchSendListener,
    BatchDispatchSendListener,
//    DispatchQueuedListener,
//    DispatchDroppedListener,
//    RemoteCommandListener,
//    LibrarySettingsUpdatedListener,
//    ActivityObserverListener,
//    EvaluateJavascriptListener,
//    ValidationChangedListener,
//    NewSessionListener,
//    SessionStartedListener,
//    UserConsentPreferencesUpdatedListener,
//    InstanceShutdownListener,
//    VisitorIdUpdatedListener,
//    DataLayer.DataLayerUpdatedListener,
    Subscribable<Listener> {

    fun <T : Listener> send(messenger: Messenger<Listener>)

}

class EventDispatcher : EventRouter {

    private val listeners = CopyOnWriteArraySet<Listener>()

    override fun <T : Listener> send(messenger: Messenger<Listener>) {
        listeners.filterIsInstance(messenger.listenerClass.java).forEach {
            messenger.deliver(it)
        }
    }

    override fun subscribe(listener: Listener) {
        if (listener == this) return

        listeners.add(listener)
    }

    fun subscribeAll(listenerList: List<Listener>) {
        val filtered = listenerList.filterNot { it == this }

        listeners.addAll(filtered)
    }

    override fun unsubscribe(listener: Listener) {
        listeners.remove(listener)
    }

    override fun onDispatchReady(dispatch: Dispatch) {
        TODO("Not yet implemented")
    }

    override fun onDispatchSend(dispatch: Dispatch) {
        TODO("Not yet implemented")
    }

    override fun onBatchDispatchSend(dispatch: List<Dispatch>) {
        TODO("Not yet implemented")
    }
}