package com.tealium.core.internal.dispatch

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.Subject
import kotlin.math.min

interface QueueManager {

    val inFlightEvents: Map<String, Set<String>>
    val onInFlightEvents: Observable<Map<String, Set<String>>>
    val onEnqueuedEvents: Observable<Unit>

    fun inFlightCount(dispatcher: Dispatcher): Observable<Int>

    fun getQueuedEvents(dispatcher: Dispatcher, limit: Int): List<Dispatch>

    fun storeDispatch(dispatch: Dispatch, dispatchers: Set<Dispatcher>?)

    fun deleteDispatches(dispatches: List<Dispatch>, dispatcher: Dispatcher)
    fun clear()
}

class VolatileQueueManagerImpl(
    private val queue: MutableMap<String, MutableSet<Dispatch>> = mutableMapOf(),
    private var _inFlightEvents: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    private val _onInFlightEvents: Subject<Map<String, Set<String>>> = Observables.replaySubject(1),
    private val _onEnqueuedEvents: Subject<Unit> = Observables.replaySubject(1),
) : QueueManager {

    override val inFlightEvents: Map<String, Set<String>>
        get() = _inFlightEvents.toMap()

    override val onInFlightEvents: Observable<Map<String, Set<String>>>
        get() = _onInFlightEvents.asObservable()
    override val onEnqueuedEvents: Observable<Unit>
        get() = _onEnqueuedEvents.asObservable()

    override fun inFlightCount(dispatcher: Dispatcher): Observable<Int> {
        return _onInFlightEvents.map {
            _inFlightEvents[dispatcher.name]?.size ?: 0
        }.distinct()
    }

    init {
        notifyInFlightChange()
    }

    override fun getQueuedEvents(dispatcher: Dispatcher, limit: Int): List<Dispatch> {
        val notInFlight = queue[dispatcher.name]?.filter { dispatch ->
            _inFlightEvents[dispatcher.name]?.find { inFlightId ->
                inFlightId == dispatch.id
            } == null
        } ?: return emptyList()

        if (notInFlight.isEmpty()) return emptyList()

        return notInFlight.subList(0, min(limit, notInFlight.size)).also {
            addToInflightEvents(dispatcher, it)
        }
    }

    override fun storeDispatch(dispatch: Dispatch, dispatchers: Set<Dispatcher>?) {
        // In-mem queue for now
        dispatchers?.forEach { dispatcher ->
            queue[dispatcher.name] = queue[dispatcher.name]?.apply {
                add(dispatch)
            } ?: mutableSetOf(dispatch)
        }

        _onEnqueuedEvents.onNext(Unit)
    }

    override fun deleteDispatches(dispatches: List<Dispatch>, dispatcher: Dispatcher) {
        queue[dispatcher.name] = queue[dispatcher.name]?.apply {
            dispatches.forEach {
                remove(it)
            }
        } ?: mutableSetOf()

        removeFromInflightEvents(dispatcher, dispatches)
    }

    override fun clear() {
        _inFlightEvents.clear()

        notifyInFlightChange()
    }

    private fun addToInflightEvents(dispatcher: Dispatcher, dispatches: List<Dispatch>) {
        _inFlightEvents[dispatcher.name] =
            (_inFlightEvents[dispatcher.name] ?: mutableSetOf()).apply {
                addAll(dispatches.map { it.id }.toMutableSet())
            }

        notifyInFlightChange()
    }

    private fun removeFromInflightEvents(dispatcher: Dispatcher, dispatches: List<Dispatch>) {
        if (dispatches.isEmpty()) return

        _inFlightEvents[dispatcher.name] = _inFlightEvents[dispatcher.name]?.filter { dispatchId ->
            dispatches.find { it.id == dispatchId } == null
        }?.toMutableSet() ?: mutableSetOf()

        notifyInFlightChange()
    }

    private fun notifyInFlightChange() {
        _onInFlightEvents.onNext(_inFlightEvents)
    }
}