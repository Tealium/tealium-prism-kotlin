package com.tealium.core.internal.observables.impl

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer
import java.util.LinkedList

/**
 * The [BufferedObservable] will hold emissions in a buffer until the buffer has been filled. At
 * which point, all buffered items will be emitted in sequence. The buffer is subsequently cleared
 * ready to be repopulated.
 */
class BufferedObservable<T>(
    private val source: Observable<T>,
    private val size: Int,
) : Observable<T> {

    override fun subscribe(observer: Observer<T>): Disposable {
        return source.subscribe(
            BufferObserver(
                observer,
                size
            )
        )
    }

    class BufferObserver<T>(
        private val observer: Observer<T>,
        private val bufferSize: Int,
    ) : Observer<T> {

        private var buffer: MutableList<T> = LinkedList<T>()

        override fun onNext(value: T) {
            buffer.add(value)

            if (buffer.size >= bufferSize) {
                buffer.forEach {
                    observer.onNext(it)
                }

                buffer.clear()
            }
        }
    }
}