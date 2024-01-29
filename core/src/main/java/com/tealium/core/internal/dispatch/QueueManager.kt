package com.tealium.core.internal.dispatch

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.min

interface QueueManager {

    val inFlightEvents: Map<String, Set<String>>
    val onInFlightEvents: Flow<Map<String, Set<String>>>
    val onEnqueuedEvents: Flow<Unit>

    fun inFlightCount(dispatcher: Dispatcher): Flow<Int>

    fun getQueuedEvents(dispatcher: Dispatcher, limit: Int): List<Dispatch>

    fun storeDispatch(dispatch: Dispatch, dispatchers: Set<Dispatcher>?)

    fun deleteDispatches(dispatches: List<Dispatch>, dispatcher: Dispatcher)
    fun clear()
}

class VolatileQueueManagerImpl(
    private val queue: MutableMap<String, MutableSet<Dispatch>> = mutableMapOf(),
    private var _inFlightEvents: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    private val _onInFlightEvents: MutableSharedFlow<Map<String, Set<String>>> = MutableSharedFlow(
        replay = 1
    ),
    private val _onEnqueuedEvents: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1),
    private val scope: CoroutineScope
) : QueueManager {

    override val inFlightEvents: Map<String, Set<String>>
        get() = _inFlightEvents.toMap()

    override val onInFlightEvents: Flow<Map<String, Set<String>>>
        get() = _onInFlightEvents.asSharedFlow()
    override val onEnqueuedEvents: Flow<Unit>
        get() = _onEnqueuedEvents.asSharedFlow()

    override fun inFlightCount(dispatcher: Dispatcher): Flow<Int> {
        return _onInFlightEvents.map {
            _inFlightEvents[dispatcher.name]?.size ?: 0
        }//.distinctUntilChanged()
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

        scope.launch {
            _onEnqueuedEvents.emit(Unit)
        }
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
        scope.launch {
            _onInFlightEvents.emit(_inFlightEvents)
        }
    }
}