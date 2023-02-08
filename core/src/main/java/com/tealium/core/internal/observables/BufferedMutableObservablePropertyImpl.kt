package com.tealium.core.internal.observables

import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class BufferedMutableObservablePropertyImpl<T, L>(
    initial: T,
    private val bufferSize: Int,
    private val onUpdate: (L, T) -> Unit
): MutableObservablePropertyImpl<T, L>(initial, onUpdate) {
    private var buffer: Queue<T> = ConcurrentLinkedQueue()

    init {
        buffer.add(initial)
    }

    override fun update(value: T) {
        while (buffer.size >= bufferSize) {
            buffer.remove()
        }

        buffer.add(value)

        super.update(value)
    }

    override fun subscribe(listener: L) {
//        while (buffer.isNotEmpty()) {
            // Clear?
//            buffer.poll()?.let { value ->
//                onUpdate(listener, value)
//            }

            // or not?
            buffer.forEach { value ->
                onUpdate(listener, value)
            }
//        }
        super.subscribe(listener)
    }
}